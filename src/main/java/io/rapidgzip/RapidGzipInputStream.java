package io.rapidgzip;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * An {@link InputStream} that reads from a gzip file using rapidgzip's
 * parallel decompression engine via Java FFM.
 *
 * <p>Thread-safety: instances are <b>not</b> thread-safe.
 *
 * <pre>{@code
 * try (var in = new RapidGzipInputStream("/data/huge.gz")) {
 *     byte[] buf = new byte[8192];
 *     int n;
 *     while ((n = in.read(buf)) != -1) {
 *         // process buf[0..n-1]
 *     }
 * }
 * }</pre>
 */
public class RapidGzipInputStream extends InputStream {

    private static final int DEFAULT_NATIVE_BUF_SIZE = 1 << 20; // 1 MiB

    private final Arena arena;
    private final MemorySegment handle;
    private final MemorySegment nativeBuf;
    private final int nativeBufSize;
    private boolean closed;

    /**
     * Open a gzip file with auto-detected parallelism.
     *
     * @param path path to the .gz file
     */
    public RapidGzipInputStream(String path) {
        this(path, 0, 0, DEFAULT_NATIVE_BUF_SIZE);
    }

    /**
     * Open a gzip file with specified parallelism.
     *
     * @param path        path to the .gz file
     * @param parallelism number of decompression threads (0 = auto)
     */
    public RapidGzipInputStream(String path, int parallelism) {
        this(path, parallelism, 0, DEFAULT_NATIVE_BUF_SIZE);
    }

    /**
     * Full constructor.
     *
     * @param path          path to the .gz file
     * @param parallelism   number of decompression threads (0 = auto)
     * @param chunkSize     chunk size in bytes (0 = default 4 MiB)
     * @param nativeBufSize size of the native read buffer
     */
    public RapidGzipInputStream(String path, int parallelism, long chunkSize, int nativeBufSize) {
        this.arena = Arena.ofConfined();
        this.nativeBufSize = nativeBufSize;
        this.nativeBuf = arena.allocate(nativeBufSize);
        this.handle = RapidGzipNative.open(arena, path, parallelism, chunkSize);
        this.closed = false;
    }

    @Override
    public int read() throws IOException {
        byte[] single = new byte[1];
        int n = read(single, 0, 1);
        return n == -1 ? -1 : (single[0] & 0xFF);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) throw new IOException("Stream closed");
        if (len == 0) return 0;

        int totalRead = 0;
        while (totalRead < len) {
            int toRead = Math.min(len - totalRead, nativeBufSize);
            long n = RapidGzipNative.read(handle, nativeBuf, toRead);
            if (n == 0) {
                return totalRead == 0 ? -1 : totalRead;
            }
            // Copy from native buffer to Java byte array
            MemorySegment.copy(nativeBuf, 0, MemorySegment.ofArray(b), off + totalRead, n);
            totalRead += (int) n;
        }
        return totalRead;
    }

    /**
     * Returns the current decompressed byte offset.
     */
    public long tell() {
        return RapidGzipNative.tell(handle);
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            RapidGzipNative.close(handle);
            arena.close();
        }
    }
}
