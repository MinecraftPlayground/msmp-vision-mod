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
  # Supersampling factor (1 = off).
  # @default: 1
  ssaa: 1
  # @deprecated: This key is no longer used and can be removed safely.
  biomeBlend: 1
  # Render distance in blocks.
  # @default: 64
  render-distance: 64
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
  # Maximum render distance in blocks.
  # @default: 128
  max-render-distance: 256
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
render:
  # Number of render worker threads (meshing and rasterization).
  # 0 = automatic (available processors - 1, at least 1).
  # @default: 0
  threads: 0
  # Edge length of a raster tile in pixels. One tile is one parallel work unit.
  # @default: 64
  tile-size: 64
  # Fog starts at this fraction of the render distance.
  # @default: 0.75
  fog-start-factor: 0.75
  # Skip back-facing triangles. Disable only for debugging.
  # @default: true
  backface-culling: true
# Maximum time in seconds a single screenshot request may take.
# @default: 30
request-timeout-seconds: 30
```
 
`defaults` are used for every optional parameter a request omits. Requests that exceed a value in `limits` are rejected instead of being clamped. Rendering cost grows with `width * height * ssaa^2` and with the render distance, so lower the limits on weak servers.
 
 
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
| `render_distance` | int           | no       | Render distance in blocks. Default from config                                     |
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
 
Only chunks that are currently loaded are rendered; unloaded areas show up as empty space.
 
> If you want more methods or parameters for other purposes, please [open an issue](https://github.com/MinecraftPlayground/msmp-vision-mod/issues/new?template=new_method_or_parameter_suggestion.yml)
 
 
## License
 
[LGPL-3.0](LICENSE)
