# Visual Test

Renders every case in `cases/` with the server and compares the result with a screenshot of the Minecraft client.
Run by `.github/workflows/visual-test.yml` (manually, on pull requests to `main` and on pushes to `dev`).

## A case

Two files with the same name in `cases/`:

```
cases/desert_village.png    reference screenshot from the client
cases/desert_village.json   parameters of the server screenshot
```

```json
{
  "commands": ["time set 1000"],
  "request": {
    "position": [81.5, 76.62, 19.5],
    "rotation": [90.0, 10.0],
    "fov": 110.0,
    "render_distance": 12,
    "biome_blend": 2,
    "brightness": 0.5,
    "mipmap_levels": 4
  },
  "thresholds": { "mae": 0.08, "bad_pixels": 0.35 }
}
```

| Field        | Description |
|--------------|-------------|
| `request`    | Parameters of `vision:screenshot`. `width`/`height` default to the size of the PNG. |
| `commands`   | Optional server commands run (via RCON) right before the request, ex. `time set 1000`. |
| `thresholds` | Optional, overrides `mae`, `bad_pixels`, `pixel_tolerance`, `block` (defaults in `run.py`). |
| `skip`       | `true` skips the case. |

Files starting with `_` are ignored (see `_example.json`).

## Metrics

Both images are averaged over blocks of `block` x `block` pixels (default 4) first, so a shift of a pixel or two does
not count as an error. Then:

* `mae`: mean absolute difference, 0..1.
* `bad_pixels`: share of blocks where any channel differs by more than `pixel_tolerance` (default 32 of 255).
* `Δ brightness`: signed mean difference per channel in percent, positive = server brighter. Good for spotting
  systematic errors (ex. grass 20 % too bright).
* The artifact contains `<name>.compare.png` (client | server | difference x4) and `<name>.server.png`.

The default thresholds are guesses. Start with `report-only` (workflow input `mode`), look at the numbers of a good run
and set thresholds slightly above them.

## Taking reference screenshots

The scene must be reproducible, so:

* **World:** same seed as `level-seed` in `server.properties`, or put a `world.zip` here (it replaces the seed).
* **Position/rotation:** `/tp @s x y z yaw pitch`, then read the values from F3. F3 shows the feet, the camera is
  1.62 blocks higher. Use that height in `position`.
* **Time and weather:** `/time set 1000` and `/weather clear`, the same value goes into `commands`.
* **Client options:** F1 (no HUD), Clouds *Off* (the clouds drift with the game time and cannot match), same
  render distance, brightness, biome blend and mipmap levels as in the request. The client option *Biome Blend 5x5*
  is `biome_blend: 2` (radius).
* **FOV:** the client multiplies the FOV by 1.1 while flying. Stand on the ground or set *FOV Effects* to 0 and use
  the same value in the request.
* **Size:** the PNG decides the size of the server image. Stay below the limits in `config.yml`.
* No mobs, items or particles in the picture, the server does not render entities.

## Run locally

```
./gradlew build
pip install -r tests/visual/requirements.txt
python tests/visual/run.py --mod-jar build/libs/msmp-vision-mod-<version>.jar --mode report-only
```

Needs Java 25. Results are in `tests/visual/results/`.
