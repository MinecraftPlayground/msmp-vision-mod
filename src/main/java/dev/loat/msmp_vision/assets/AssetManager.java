package dev.loat.msmp_vision.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.loat.msmp_vision.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Provides the vanilla client resources (blockstates, models, textures) on a dedicated server.
 *
 * <p>The official {@code client.jar} of the running Minecraft version is downloaded from Mojang once,
 * the needed parts are extracted to disk and then read from there. No Mojang assets are shipped with the mod.</p>
 */
public final class AssetManager {

    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String MARKER_FILE = ".version";
    private static final String ASSET_PREFIX = "assets/minecraft/";
    private static final List<String> EXTRACTED_DIRECTORIES = List.of("blockstates/", "models/", "textures/");

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final Path root;
    private final String version;
    private CompletableFuture<Void> ready;

    /**
     * Creates a manager.
     *
     * @param root Directory the assets are extracted to.
     * @param version Minecraft version whose client jar is used.
     */
    public AssetManager(Path root, String version) {
        this.root = root.toAbsolutePath().normalize();
        this.version = version;
    }

    /**
     * Starts downloading and extracting the assets in the background if not already done.
     * Calling this multiple times returns the same future.
     *
     * @return A future completing when all assets are available.
     */
    public synchronized CompletableFuture<Void> initialize() {
        if (this.ready == null) {
            this.ready = CompletableFuture.runAsync(this::prepare);
        }
        return this.ready;
    }

    /**
     * Reads a file below the extracted asset root.
     *
     * @param path Relative path such as {@code assets/minecraft/models/block/stone.json}.
     * @return The file content, or {@code null} if it does not exist.
     */
    public byte[] read(String path) {
        Path file = this.root.resolve(path).normalize();
        if (!file.startsWith(this.root) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            Logger.warning("Failed to read asset '%s': %s".formatted(path, e.getMessage()));
            return null;
        }
    }

    /**
     * Reads and parses a JSON file below the asset root.
     *
     * @param path Relative path.
     * @return The parsed object, or {@code null} if missing or invalid.
     */
    public JsonObject readJson(String path) {
        byte[] bytes = this.read(path);
        if (bytes == null) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            Logger.warning("Invalid JSON in asset '%s': %s".formatted(path, e.getMessage()));
            return null;
        }
    }

    private void prepare() {
        try {
            if (this.isUpToDate()) {
                Logger.info("Client assets for %s are ready.".formatted(this.version));
                return;
            }
            this.downloadAndExtract();
            Files.writeString(this.root.resolve(MARKER_FILE), this.version);
            Logger.info("Client assets for %s are ready.".formatted(this.version));
        } catch (IOException e) {
            throw new CompletionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
    }

    private boolean isUpToDate() throws IOException {
        Path marker = this.root.resolve(MARKER_FILE);
        return Files.isRegularFile(marker) && this.version.equals(Files.readString(marker).trim());
    }

    private void downloadAndExtract() throws IOException, InterruptedException {
        Files.createDirectories(this.root);

        JsonObject manifest = JsonParser.parseString(this.fetchString(MANIFEST_URL)).getAsJsonObject();
        String versionUrl = null;
        for (JsonElement entry : manifest.getAsJsonArray("versions")) {
            JsonObject object = entry.getAsJsonObject();
            if (object.get("id").getAsString().equals(this.version)) {
                versionUrl = object.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new IOException("Minecraft version '%s' not found in the Mojang version manifest.".formatted(this.version));
        }

        JsonObject client = JsonParser.parseString(this.fetchString(versionUrl)).getAsJsonObject()
            .getAsJsonObject("downloads").getAsJsonObject("client");
        String url = client.get("url").getAsString();
        String sha1 = client.get("sha1").getAsString();

        Path jar = this.root.resolve("client.jar");
        Logger.info("Downloading Minecraft client jar (%d MiB)...".formatted(client.get("size").getAsLong() / 1024 / 1024));
        this.download(url, jar, sha1);

        Logger.info("Extracting client assets...");
        try {
            this.extract(jar);
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    private String fetchString(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP %d while fetching %s".formatted(response.statusCode(), url));
        }
        return response.body();
    }

    private void download(String url, Path target, String expectedSha1) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP %d while downloading %s".formatted(response.statusCode(), url));
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = new DigestInputStream(response.body(), digest)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedSha1)) {
            Files.deleteIfExists(target);
            throw new IOException("Client jar checksum mismatch.");
        }
    }

    private void extract(Path jar) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !this.isExtracted(name)) {
                    continue;
                }
                Path target = this.root.resolve(name).normalize();
                if (!target.startsWith(this.root)) {
                    continue; // zip-slip guard
                }
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private boolean isExtracted(String entryName) {
        if (!entryName.startsWith(ASSET_PREFIX)) {
            return false;
        }
        String relative = entryName.substring(ASSET_PREFIX.length());
        return EXTRACTED_DIRECTORIES.stream().anyMatch(relative::startsWith);
    }
}
