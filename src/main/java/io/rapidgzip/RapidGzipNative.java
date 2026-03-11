package io.rapidgzip;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Low-level Java FFM bindings to the rapidgzip_ffi native library.
 * <p>
 * This class maps C functions to Java {@link MethodHandle}s using the
 * Foreign Function &amp; Memory API (JEP 454, finalized in Java 22, preview in 21).
 *
 * <pre>
 * C API:
 *   void*       rgzip_open(const char* path, int parallelism, uint64_t chunk_size);
 *   int64_t     rgzip_read(void* handle, char* buf, int64_t len);
 *   int64_t     rgzip_tell(void* handle);
 *   void        rgzip_close(void* handle);
 *   const char* rgzip_strerror(void* handle);
 * </pre>
 */
final class RapidGzipNative {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = NativeLoader.lookup();

    // ── Method handles ──

    static final MethodHandle OPEN;
    static final MethodHandle READ;
    static final MethodHandle TELL;
    static final MethodHandle CLOSE;
    static final MethodHandle STRERROR;

    static {
        OPEN = LINKER.downcallHandle(
                LOOKUP.find("rgzip_open").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS,          // returns void*
                        ValueLayout.ADDRESS,          // const char* path
                        ValueLayout.JAVA_INT,         // int parallelism
                        ValueLayout.JAVA_LONG          // uint64_t chunk_size
                )
        );

        READ = LINKER.downcallHandle(
                LOOKUP.find("rgzip_read").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,        // returns int64_t
                        ValueLayout.ADDRESS,          // void* handle
                        ValueLayout.ADDRESS,          // char* buf
                        ValueLayout.JAVA_LONG          // int64_t len
                )
        );

        TELL = LINKER.downcallHandle(
                LOOKUP.find("rgzip_tell").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,        // returns int64_t
                        ValueLayout.ADDRESS            // void* handle
                )
        );

        CLOSE = LINKER.downcallHandle(
                LOOKUP.find("rgzip_close").orElseThrow(),
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS            // void* handle
                )
        );

        STRERROR = LINKER.downcallHandle(
                LOOKUP.find("rgzip_strerror").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.ADDRESS,          // returns const char*
                        ValueLayout.ADDRESS            // void* handle
                )
        );
    }

    private RapidGzipNative() {}

    /**
     * Open a gzip file for parallel decompression.
     */
    static MemorySegment open(Arena arena, String path, int parallelism, long chunkSize) {
        try {
            MemorySegment pathSeg = arena.allocateFrom(path);
            MemorySegment handle = (MemorySegment) OPEN.invokeExact(pathSeg, parallelism, chunkSize);
            if (handle.equals(MemorySegment.NULL)) {
                throw new RapidGzipException("Failed to open: " + path);
            }
            return handle;
        } catch (RapidGzipException e) {
            throw e;
        } catch (Throwable t) {
            throw new RapidGzipException("FFM call failed", t);
        }
    }

    /**
     * Read decompressed bytes into the given native buffer.
     */
    static long read(MemorySegment handle, MemorySegment buf, long len) {
        try {
            long n = (long) READ.invokeExact(handle, buf, len);
            if (n < 0) {
                String err = strerror(handle);
                throw new RapidGzipException("Read error: " + err);
            }
            return n;
        } catch (RapidGzipException e) {
            throw e;
        } catch (Throwable t) {
            throw new RapidGzipException("FFM call failed", t);
        }
    }

    /**
     * Return current decompressed byte offset.
     */
    static long tell(MemorySegment handle) {
        try {
            return (long) TELL.invokeExact(handle);
        } catch (Throwable t) {
            throw new RapidGzipException("FFM call failed", t);
        }
    }

    /**
     * Close the reader and free resources.
     */
    static void close(MemorySegment handle) {
        try {
            CLOSE.invokeExact(handle);
        } catch (Throwable t) {
            throw new RapidGzipException("FFM call failed", t);
        }
    }

    /**
     * Get the last error string from the handle.
     */
    static String strerror(MemorySegment handle) {
        try {
            MemorySegment errPtr = (MemorySegment) STRERROR.invokeExact(handle);
            if (errPtr.equals(MemorySegment.NULL)) {
                return "unknown error";
            }
            return errPtr.reinterpret(1024).getString(0);
        } catch (Throwable t) {
            return "failed to retrieve error: " + t.getMessage();
        }
    }
}
