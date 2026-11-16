package dev.loat.msmp_vision.config.files.chunks;

import dev.loat.config_lib.annotation.Annotation;


public class ChunksConfig {

    @Annotation.Comment("""
        How chunks that are not loaded are handled while a screenshot is taken.
        NONE: only chunks that are already loaded (ex. by a player) are rendered.
        LOAD: missing chunks are loaded from disk, chunks that do not exist yet are generated.
    """)
    public Mode mode = Mode.LOAD;

    @Annotation.Comment("""
        Maximum number of chunks that may be loaded (or generated) for a single request.
        Chunks beyond this limit are rendered as empty space. Only chunks inside the camera's view are considered.
    """)
    @Annotation.Key("max-chunk-loads")
    public int maxChunkLoads = 2048;

    @Annotation.Comment("""
        Maximum time in seconds spent on loading chunks for a single request. The server thread is blocked during this time,
        keep it well below the server's watchdog (max-tick-time, 60s by default). Remaining chunks are rendered as empty space.
    """)
    @Annotation.Key("load-timeout-seconds")
    public int loadTimeoutSeconds = 10;

    @Annotation.Comment("""
        How long in seconds chunks of a screenshot stay loaded afterwards (loading only, they are not ticked).
        Makes repeated screenshots of the same area fast, because the chunks do not have to be read from disk again.
        0 = off, Minecraft then unloads them a few seconds after the screenshot.
    """)
    @Annotation.Key("keep-loaded-seconds")
    public int keepLoadedSeconds = 30;

    public static enum Mode {
        NONE,
        LOAD
    }
}
