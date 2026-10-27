<img src="assets/icon.png" width="64" align="right">

# MSMP Vision

A server-side Fabric mod that extends the [Minecraft Server Management Protocol](https://minecraft.wiki/w/Minecraft_Server_Management_Protocol) (MSMP) by providing a function for taking screenshots entirely on the server. No client and no GPU are required, the image is rendered by a multi-threaded software rasterizer.

This mod is designed for tooling, dashboards, automation systems, external monitoring tools, and integrations that need a picture of the world without running a game client.


## Installation

1. Download the mod `.jar` and place it in your server's `mods/` folder (requires [Fabric API](https://modrinth.com/mod/fabric-api)).
2. Enable the Management Server in `server.properties`:
   ```properties
   management-server-enabled=true
   ```
3. Start the server. The Management Server will listen on `localhost:25576` by default.

On the first start the mod downloads the official client `.jar` of the running Minecraft version from Mojang and extracts the block models and textures to `<server_root_dir>/config/msmp/vision/client_assets/`. No Mojang assets are shipped with the mod. Until this is finished, screenshot requests fail with *"Client assets are still being prepared"*.


## Configuration

On first start, the mod generates a configuration file at `<server_root_dir>/config/msmp/vision/config.yml`:

```yaml
# Main configuration file for MSMP Vision.

# Thread settings of the renderer.
threads:
  # Number of render worker threads (meshing and rasterization).
  # 0 = automatic (available processors - 1, at least 1).
  # @default: 0
  render: 0
# Values used when a request does not specify a setting.
defaults:
  # Output width in pixels.
  # @default: 256
  width: 256
  # Output height in pixels.
  # @default: 256
  height: 256
  # Vertical field of view in degrees.
  # @default: 70.0
  fov: 70.0
  # Render distance in chunks (1 chunk = 16 blocks), like in Minecraft's video settings.
  # @default: 4
  render-distance-chunks: 4
  # Supersampling factor (1 = off).
  # @default: 1
  ssaa: 1
  # Biome tint blend radius in blocks (0 = off).
  # @default: 1
  biome-blend: 1
# Upper limits that requests are validated against. Requests exceeding them are rejected.
limits:
  # Maximum output width.
  # @default: 1920
  max-width: 1920
  # Maximum output height.
  # @default: 1080
  max-height: 1080
  # Maximum render distance in chunks (1 chunk = 16 blocks).
  # @default: 32
  max-render-distance-chunks: 32
  # Maximum supersampling factor.
  # @default: 3
  max-ssaa: 3
  # Maximum number of internally rendered pixels (width * height * ssaa^2).
  # @default: 8000000
  max-rendered-pixels: 8000000
  # Minimum field of view in degrees.
  # @default: 30.0
  min-fov: 30.0
  # Maximum field of view in degrees.
  # @default: 110.0
  max-fov: 110.0
# Tuning options of the rasterizer.
rendering:
  # Edge length of a raster tile in pixels. One tile is one parallel work unit.
  # @default: 64
  tile-size: 64
  # Fog starts at this fraction of the render distance. Minecraft itself only fogs the last ~10%.
  # @default: 0.9
  fog-start-factor: 0.9
  # Skip back-facing triangles. Disable only for debugging.
  # @default: true
  backface-culling: true
  # Deflate level of the PNG encoder, 0 (no compression) to 9 (smallest file).
  # 1 encodes about 2.5x faster than 6 for a file that is roughly 30% larger.
  # @default: 1
  png-compression-level: 1
# Settings for chunks that are not loaded when a screenshot is requested.
chunks:
  # How chunks that are not loaded are handled while a screenshot is taken.
  # NONE: only chunks that are already loaded (ex. by a player) are rendered.
  # LOAD: missing chunks are loaded from disk, chunks that do not exist yet are generated.
  # @possible: NONE | LOAD
  # @default: 'LOAD'
  mode: LOAD
  # Maximum number of chunks that may be loaded (or generated) for a single request.
  # Chunks beyond this limit are rendered as empty space. Only chunks inside the camera's view are considered.
  # @default: 2048
  max-chunk-loads: 2048
  # Maximum time in seconds spent on loading chunks for a single request. The server thread is blocked during this time,
  # keep it well below the server's watchdog (max-tick-time, 60s by default). Remaining chunks are rendered as empty space.
  # @default: 10
  load-timeout-seconds: 10
# Maximum time in seconds a single screenshot request may take (without chunk loading).
# @default: 30
request-timeout-seconds: 30
```

`defaults` are used for every optional parameter a request omits. Requests that exceed a value in `limits` are rejected instead of being clamped. Rendering cost grows with `width * height * ssaa^2` and with the square of the render distance (a render distance of 16 chunks can mean up to ~1000 chunks in view, so raise `chunks.max-chunk-loads` accordingly), so lower the limits on weak servers.

**Chunk loading:** With `chunks.mode: LOAD` (default) the mod loads the chunks inside the camera's view on demand, so screenshots also work where no player is. Chunks that were never generated are generated, which can take a while and creates world data. Use `NONE` if screenshots must never change the world. MSMP calls run on the **server thread**: the server does not tick while a screenshot is being loaded and rendered, so keep `load-timeout-seconds` and `request-timeout-seconds` well below the watchdog (`max-tick-time`, 60 s by default).


## RPC Methods

The mod currently provides the following MSMP RPC method. It is also automatically discoverable through the standard `rpc.discover` MSMP endpoint.

| Method              | Description                                                         |
| ------------------- | ------------------------------------------------------------------- |
| `vision:screenshot` | Renders a server-side screenshot and returns it as a Base64 PNG     |

### `vision:screenshot`

Vectors are transferred as arrays, exactly like in the `entity` namespace.

| Parameter         | Type          | Required | Description                                                                        |
| ----------------- | ------------- | -------- | ---------------------------------------------------------------------------------- |
| `position`        | `[x, y, z]`   | yes      | Camera position                                                                    |
| `rotation`        | `[yaw, pitch]`| no       | Camera rotation in degrees (yaw `0` = south, `90` = west; pitch positive = down). Default `[0, 0]` |
| `dimension`       | string        | no       | Dimension resource key. Default `minecraft:overworld`                              |
| `width`           | int           | no       | Output width in pixels. Default from config                                        |
| `height`          | int           | no       | Output height in pixels. Default from config                                       |
| `fov`             | number        | no       | Vertical field of view in degrees. Default from config                             |
| `render_distance` | int           | no       | Render distance in chunks (1 chunk = 16 blocks), like in Minecraft's video settings. Default from config |
| `ssaa`            | int           | no       | Supersampling factor, `1` = off. Default from config                               |
| `biome_blend`     | int           | no       | Biome tint blend radius in blocks, `0` = off. Default from config                  |

Example request:

```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "vision:screenshot",
    "params": [{
        "position": [128.5, 80.0, -32.3],
        "rotation": [90.0, 0.0],
        "width": 512,
        "height": 288
    }]
}
```

Example response:

```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
        "image": "iVBORw0KGgoAAAANSUhEUg...",
        "format": "png",
        "width": 512,
        "height": 288,
        "render_time_ms": 412
    }
}
```

Chunks that are not loaded are loaded on demand (see `chunks` in the config). Whatever cannot be loaded within the limits shows up as empty space.

> If you want more methods or parameters for other purposes, please [open an issue](https://github.com/MinecraftPlayground/msmp-vision-mod/issues/new?template=new_method_or_parameter_suggestion.yml)


## License

[LGPL-3.0](LICENSE)
