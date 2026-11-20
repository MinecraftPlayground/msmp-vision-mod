#!/usr/bin/env python3
"""
Visual regression test for msmp-vision-mod.

Starts a Fabric server with the freshly built mod, renders every case in ``tests/visual/cases`` through the
``vision:screenshot`` MSMP method and compares the result with the client reference screenshot of that case.

A case is a pair of files with the same name::

    cases/<name>.json   parameters of the screenshot (see README.md)
    cases/<name>.png    reference screenshot taken with the Minecraft client

Files whose name starts with ``_`` are ignored.
"""
from __future__ import annotations

import argparse
import base64
import fnmatch
import io
import json
import math
import os
import secrets
import shutil
import socket
import struct
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import websocket
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent

MSMP_PORT = 25576
RCON_PORT = 25575
USER_AGENT = "msmp-vision-mod-ci (https://github.com/MinecraftPlayground/msmp-vision-mod)"

# Everything else in the server directory is deleted before each run (world, logs, mods, ...).
KEEP_IN_SERVER_DIR = {"libraries", "versions", ".fabric", "config"}

DEFAULT_THRESHOLDS = {
    "mae": 0.08,            # mean absolute error of the (block-averaged) images, 0..1
    "bad_pixels": 0.35,     # share of blocks whose largest channel difference exceeds pixel_tolerance, 0..1
    "pixel_tolerance": 32,  # per-channel difference (0..255) from which a block counts as different
    "block": 4,             # edge length in pixels of the blocks both images are averaged over before comparing
}

# Commands that run once before the first case, so every run starts under the same conditions.
STARTUP_COMMANDS = ["weather clear 1000000"]


# --------------------------------------------------------------------------------------------------------------------
# Small helpers
# --------------------------------------------------------------------------------------------------------------------

def log(message: str) -> None:
    print(message, flush=True)


def read_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def http_get(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def download(url: str, target: Path) -> Path:
    if not target.exists():
        log(f"Downloading {url}")
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = target.with_suffix(target.suffix + ".part")
        partial.write_bytes(http_get(url))
        partial.rename(target)
    return target


# --------------------------------------------------------------------------------------------------------------------
# Server setup
# --------------------------------------------------------------------------------------------------------------------

def prepare_server(mod_jar: Path, gradle: dict[str, str], secret: str, rcon_password: str) -> Path:
    """Creates a clean Fabric server directory containing the mod and returns it."""
    minecraft = gradle["minecraft_version"]
    loader = gradle["loader_version"]
    fabric_api_version = gradle.get("fabric_api_version", "")
    cache = HERE / ".cache"
    server = HERE / ".server"

    server.mkdir(parents=True, exist_ok=True)
    for child in server.iterdir():
        if child.name in KEEP_IN_SERVER_DIR:
            continue
        shutil.rmtree(child) if child.is_dir() else child.unlink()

    # Fabric server launcher
    installers = json.loads(http_get("https://meta.fabricmc.net/v2/versions/installer"))
    installer = next(item["version"] for item in installers if item.get("stable"))
    launcher = download(
        f"https://meta.fabricmc.net/v2/versions/loader/{minecraft}/{loader}/{installer}/server/jar",
        cache / f"fabric-server-{minecraft}-{loader}-{installer}.jar",
    )
    shutil.copy(launcher, server / "fabric-server.jar")

    # Fabric API (the exact version from gradle.properties if it exists on Modrinth)
    query = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([minecraft])})
    versions = json.loads(http_get(f"https://api.modrinth.com/v2/project/fabric-api/version?{query}"))
    if not versions:
        raise SystemExit(f"No Fabric API version for Minecraft {minecraft} found on Modrinth.")
    chosen = next((v for v in versions if v["version_number"] == fabric_api_version), versions[0])
    if chosen["version_number"] != fabric_api_version:
        log(f"::warning::Fabric API {fabric_api_version} not found, using {chosen['version_number']}")
    file = next((f for f in chosen["files"] if f.get("primary")), chosen["files"][0])
    api_jar = download(file["url"], cache / file["filename"])

    mods = server / "mods"
    mods.mkdir()
    shutil.copy(api_jar, mods / api_jar.name)
    shutil.copy(mod_jar, mods / mod_jar.name)

    (server / "eula.txt").write_text("eula=true\n", encoding="utf-8")

    # server.properties: CI defaults, then the overrides of the repository (seed, ...)
    properties = {
        "management-server-enabled": "true",
        "management-server-host": "127.0.0.1",
        "management-server-port": str(MSMP_PORT),
        "management-server-secret": secret,
        "management-server-tls-enabled": "false",
        "enable-rcon": "true",
        "rcon.port": str(RCON_PORT),
        "rcon.password": rcon_password,
        "online-mode": "false",
        "pause-when-empty-seconds": "-1",
        "max-tick-time": "-1",
        "sync-chunk-writes": "false",
        "view-distance": "2",
        "simulation-distance": "2",
        "spawn-monsters": "false",
        "spawn-animals": "false",
    }
    overrides = HERE / "server.properties"
    if overrides.exists():
        properties.update(read_properties(overrides))
    (server / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in properties.items()), encoding="utf-8")

    # Mod configuration (partial files are fine, the config library fills in the missing keys)
    config_source = HERE / "config.yml"
    if config_source.exists():
        target = server / "config" / "msmp" / "vision"
        target.mkdir(parents=True, exist_ok=True)
        shutil.copy(config_source, target / "config.yml")

    # Optional fixed world instead of a seed
    world_zip = HERE / "world.zip"
    if world_zip.exists():
        extract = server / "_world_extract"
        with zipfile.ZipFile(world_zip) as archive:
            archive.extractall(extract)
        level = next(extract.rglob("level.dat"), None)
        if level is None:
            raise SystemExit("world.zip does not contain a level.dat")
        level.parent.rename(server / properties.get("level-name", "world"))
        shutil.rmtree(extract)
        log("Using the world from world.zip")

    return server


def wait_for_log(log_file: Path, process: subprocess.Popen, needles: list[str], timeout: float, label: str) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        text = log_file.read_text(encoding="utf-8", errors="replace") if log_file.exists() else ""
        if any(needle in text for needle in needles):
            return
        if "Failed to prepare client assets" in text:
            raise RuntimeError("The mod could not prepare the client assets, see server log.")
        if process.poll() is not None:
            raise RuntimeError(f"The server exited with code {process.returncode} while waiting for: {label}")
        time.sleep(1)
    raise TimeoutError(f"Timeout while waiting for: {label}")


# --------------------------------------------------------------------------------------------------------------------
# RCON and MSMP clients
# --------------------------------------------------------------------------------------------------------------------

class Rcon:
    """Minimal RCON client (only what is needed to run a few commands)."""

    def __init__(self, port: int, password: str, host: str = "127.0.0.1") -> None:
        self.socket = socket.create_connection((host, port), timeout=60)
        self.next_id = 0
        request_id, _ = self._exchange(3, password)
        if request_id == -1:
            raise RuntimeError("RCON authentication failed")

    def command(self, command: str) -> str:
        return self._exchange(2, command)[1]

    def close(self) -> None:
        self.socket.close()

    def _exchange(self, kind: int, payload: str) -> tuple[int, str]:
        self.next_id += 1
        body = struct.pack("<ii", self.next_id, kind) + payload.encode("utf-8") + b"\0\0"
        self.socket.sendall(struct.pack("<i", len(body)) + body)
        length = struct.unpack("<i", self._read(4))[0]
        data = self._read(length)
        request_id, _ = struct.unpack("<ii", data[:8])
        return request_id, data[8:-2].decode("utf-8", errors="replace")

    def _read(self, count: int) -> bytes:
        data = b""
        while len(data) < count:
            chunk = self.socket.recv(count - len(data))
            if not chunk:
                raise ConnectionError("RCON connection closed")
            data += chunk
        return data


class MsmpError(Exception):
    pass


class Msmp:
    """Minimal JSON-RPC client for the Minecraft Server Management Protocol (WebSocket)."""

    def __init__(self, port: int, secret: str, host: str = "127.0.0.1") -> None:
        self.socket = websocket.create_connection(
            f"ws://{host}:{port}", header=[f"Authorization: Bearer {secret}"], timeout=30)
        self.next_id = 0

    def call(self, method: str, params: list, timeout: float) -> dict:
        self.next_id += 1
        self.socket.settimeout(timeout)
        self.socket.send(json.dumps({"jsonrpc": "2.0", "id": self.next_id, "method": method, "params": params}))
        while True:  # notifications of the server may arrive in between
            message = json.loads(self.socket.recv())
            if message.get("id") == self.next_id:
                if "error" in message:
                    raise MsmpError(json.dumps(message["error"]))
                return message["result"]

    def close(self) -> None:
        self.socket.close()


def connect_msmp(secret: str, process: subprocess.Popen | None, timeout: float) -> Msmp:
    deadline = time.time() + timeout
    last: Exception | None = None
    while time.time() < deadline:
        if process is not None and process.poll() is not None:
            raise RuntimeError(f"The server exited with code {process.returncode}")
        try:
            return Msmp(MSMP_PORT, secret)
        except Exception as error:  # noqa: BLE001 - keep trying until the deadline
            last = error
            time.sleep(2)
    raise TimeoutError(f"Could not connect to the management server: {last}")


# --------------------------------------------------------------------------------------------------------------------
# Comparison
# --------------------------------------------------------------------------------------------------------------------

def block_average(image: np.ndarray, block: int) -> np.ndarray:
    height, width, channels = image.shape
    height -= height % block
    width -= width % block
    cropped = image[:height, :width]
    return cropped.reshape(height // block, block, width // block, block, channels).mean(axis=(1, 3))


def compare_images(reference: Image.Image, rendered: Image.Image, thresholds: dict) -> dict:
    a = np.asarray(reference.convert("RGB"), dtype=np.float32)
    b = np.asarray(rendered.convert("RGB"), dtype=np.float32)
    block = max(1, int(thresholds["block"]))
    da = block_average(a, block)
    db = block_average(b, block)
    difference = np.abs(da - db)
    mse = float(((da - db) ** 2).mean())
    mean_a = da.mean(axis=(0, 1))
    return {
        "mae": float(difference.mean() / 255.0),
        "bad_pixels": float((difference.max(axis=2) > thresholds["pixel_tolerance"]).mean()),
        "psnr": 99.0 if mse == 0 else 10.0 * math.log10(255.0 ** 2 / mse),
        # Signed brightness difference per channel in percent (server relative to client): positive = server brighter.
        "delta_percent": [float(x) for x in (db.mean(axis=(0, 1)) - mean_a) / np.maximum(mean_a, 1.0) * 100.0],
    }


def comparison_image(reference: Image.Image, rendered: Image.Image) -> Image.Image:
    """Reference | server | difference (x4, red)."""
    a = np.asarray(reference.convert("RGB"), dtype=np.int16)
    b = np.asarray(rendered.convert("RGB"), dtype=np.int16)
    heat = np.clip(np.abs(a - b).max(axis=2) * 4, 0, 255).astype(np.uint8)
    diff = np.zeros(a.shape, dtype=np.uint8)
    diff[..., 0] = heat
    gap = 6
    canvas = Image.new("RGB", (reference.width * 3 + gap * 2, reference.height), (24, 24, 24))
    canvas.paste(reference.convert("RGB"), (0, 0))
    canvas.paste(rendered.convert("RGB"), (reference.width + gap, 0))
    canvas.paste(Image.fromarray(diff), (reference.width * 2 + gap * 2, 0))
    return canvas


# --------------------------------------------------------------------------------------------------------------------
# Cases
# --------------------------------------------------------------------------------------------------------------------

@dataclass
class Case:
    name: str
    spec: dict
    reference: Path


@dataclass
class Result:
    name: str
    status: str  # passed | failed | error | skipped
    message: str = ""
    metrics: dict = field(default_factory=dict)
    thresholds: dict = field(default_factory=dict)
    render_time_ms: int = 0


def load_cases(directory: Path, pattern: str) -> tuple[list[Case], list[Result]]:
    cases: list[Case] = []
    problems: list[Result] = []
    for path in sorted(directory.glob("*.json")):
        name = path.stem
        if name.startswith("_") or not fnmatch.fnmatch(name, pattern):
            continue
        reference = path.with_suffix(".png")
        try:
            spec = json.loads(path.read_text(encoding="utf-8"))
            if "request" not in spec:
                raise ValueError("missing 'request' object")
            if not reference.exists():
                raise ValueError(f"missing reference image {reference.name}")
        except (ValueError, OSError) as error:
            problems.append(Result(name, "error", f"Invalid case: {error}"))
            continue
        cases.append(Case(name, spec, reference))
    return cases, problems


def run_case(case: Case, msmp: Msmp, rcon: Rcon | None, output: Path, timeout: float) -> Result:
    if case.spec.get("skip"):
        return Result(case.name, "skipped", "skip: true")

    thresholds = {**DEFAULT_THRESHOLDS, **case.spec.get("thresholds", {})}
    reference = Image.open(case.reference).convert("RGB")
    request = dict(case.spec["request"])
    request.setdefault("width", reference.width)
    request.setdefault("height", reference.height)

    if rcon is not None:
        for command in case.spec.get("commands", []):
            rcon.command(command)

    response = None
    deadline = time.time() + 300
    while response is None:
        try:
            response = msmp.call("vision:screenshot", [request], timeout)
        except MsmpError as error:
            if "being prepared" in str(error) and time.time() < deadline:
                time.sleep(5)
                continue
            return Result(case.name, "error", f"Request failed: {error}", thresholds=thresholds)

    rendered = Image.open(io.BytesIO(base64.b64decode(response["image"]))).convert("RGB")
    rendered.save(output / f"{case.name}.server.png")
    render_time = int(response.get("render_time_ms", 0))

    if rendered.size != reference.size:
        return Result(case.name, "error",
                      f"Size mismatch: reference {reference.size}, server {rendered.size}. "
                      f"Set width/height in the request or remove them to use the reference size.",
                      thresholds=thresholds, render_time_ms=render_time)

    metrics = compare_images(reference, rendered, thresholds)
    comparison_image(reference, rendered).save(output / f"{case.name}.compare.png")

    failures = []
    if metrics["mae"] > thresholds["mae"]:
        failures.append(f"mae {metrics['mae']:.4f} > {thresholds['mae']}")
    if metrics["bad_pixels"] > thresholds["bad_pixels"]:
        failures.append(f"bad_pixels {metrics['bad_pixels']:.3f} > {thresholds['bad_pixels']}")
    status = "failed" if failures else "passed"
    return Result(case.name, status, "; ".join(failures), metrics, thresholds, render_time)


def run_cases(cases: list[Case], msmp: Msmp, rcon: Rcon | None, output: Path, timeout: float) -> list[Result]:
    results = []
    for case in cases:
        log(f"::group::Case {case.name}")
        started = time.time()
        try:
            result = run_case(case, msmp, rcon, output, timeout)
        except Exception as error:  # noqa: BLE001 - one broken case must not stop the others
            result = Result(case.name, "error", f"{type(error).__name__}: {error}")
        log(f"{result.status.upper()} {case.name} ({time.time() - started:.1f}s) {result.message}")
        if result.metrics:
            log(json.dumps(result.metrics))
        log("::endgroup::")
        results.append(result)
    return results


# --------------------------------------------------------------------------------------------------------------------
# Report
# --------------------------------------------------------------------------------------------------------------------

ICONS = {"passed": "✅", "failed": "❌", "error": "⚠️", "skipped": "⏭️"}


def write_report(results: list[Result], output: Path, mode: str) -> str:
    lines = ["## Visual test", "",
             f"Mode: `{mode}` · {sum(r.status == 'passed' for r in results)}/{len(results)} passed", "",
             "| Case | Result | MAE | Bad blocks | PSNR | Δ brightness R/G/B | Render |",
             "|---|---|---|---|---|---|---|"]
    for r in results:
        if r.metrics:
            m, t = r.metrics, r.thresholds
            delta = " / ".join(f"{x:+.1f}%" for x in m["delta_percent"])
            lines.append(f"| `{r.name}` | {ICONS[r.status]} | {m['mae']:.4f} (≤ {t['mae']}) | "
                         f"{m['bad_pixels']:.1%} (≤ {t['bad_pixels']:.0%}) | {m['psnr']:.1f} dB | {delta} | {r.render_time_ms} ms |")
        else:
            lines.append(f"| `{r.name}` | {ICONS[r.status]} | {r.message} | | | | |")
    lines += ["", "Images (`*.compare.png` = reference | server | difference x4) are in the artifact `visual-test-results`."]
    markdown = "\n".join(lines) + "\n"

    output.joinpath("summary.md").write_text(markdown, encoding="utf-8")
    output.joinpath("report.json").write_text(json.dumps([r.__dict__ for r in results], indent=2), encoding="utf-8")
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        with open(summary_file, "a", encoding="utf-8") as handle:
            handle.write(markdown)
    return markdown


# --------------------------------------------------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------------------------------------------------

def stop_server(process: subprocess.Popen, rcon: Rcon | None) -> None:
    if process.poll() is None:
        try:
            if rcon is not None:
                rcon.command("stop")
        except Exception:  # noqa: BLE001
            pass
        try:
            process.wait(timeout=60)
        except subprocess.TimeoutExpired:
            process.kill()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mod-jar", required=True, type=Path, help="The built mod jar")
    parser.add_argument("--cases", default="*", help="Glob for the case names to run")
    parser.add_argument("--mode", choices=["enforce", "report-only"], default="enforce",
                        help="report-only never fails because of thresholds")
    parser.add_argument("--memory", default="4G", help="Heap of the server")
    parser.add_argument("--timeout", type=float, default=300, help="Seconds a single screenshot may take")
    args = parser.parse_args()

    output = HERE / "results"
    shutil.rmtree(output, ignore_errors=True)
    output.mkdir(parents=True)

    cases, problems = load_cases(HERE / "cases", args.cases)
    if not cases and not problems:
        log("::error::No cases found (expected pairs of <name>.json and <name>.png in tests/visual/cases)")
        return 1

    secret = secrets.token_hex(20)  # the server requires exactly 40 alphanumeric characters
    rcon_password = secrets.token_hex(8)
    gradle = read_properties(ROOT / "gradle.properties")
    server_dir = prepare_server(args.mod_jar.resolve(), gradle, secret, rcon_password)

    server_log = server_dir / "server.log"
    process = subprocess.Popen(
        ["java", f"-Xmx{args.memory}", "-jar", "fabric-server.jar", "nogui"],
        cwd=server_dir, stdout=server_log.open("w"), stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)

    rcon: Rcon | None = None
    msmp: Msmp | None = None
    results: list[Result] = list(problems)
    try:
        log("Starting the server (the first start downloads Minecraft and the libraries) ...")
        wait_for_log(server_log, process, ['Done ('], 900, "server start")
        wait_for_log(server_log, process, ["Client assets ready ("], 600, "client assets")
        rcon = Rcon(RCON_PORT, rcon_password)
        msmp = connect_msmp(secret, process, 60)
        for command in STARTUP_COMMANDS:
            rcon.command(command)
        results += run_cases(cases, msmp, rcon, output, args.timeout)
    except Exception as error:  # noqa: BLE001
        log(f"::error::{type(error).__name__}: {error}")
        results.append(Result("(setup)", "error", f"{type(error).__name__}: {error}"))
    finally:
        if msmp is not None:
            msmp.close()
        stop_server(process, rcon)
        if rcon is not None:
            rcon.close()
        if server_log.exists():
            shutil.copy(server_log, output / "server.log")
            if any(r.status in ("error",) for r in results):
                log("::group::Last lines of the server log")
                log("\n".join(server_log.read_text(errors="replace").splitlines()[-80:]))
                log("::endgroup::")

    write_report(results, output, args.mode)
    broken = [r for r in results if r.status == "error"]
    failed = [r for r in results if r.status == "failed"]
    if broken:
        return 1
    if failed and args.mode == "enforce":
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
