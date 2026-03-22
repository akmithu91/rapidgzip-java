package io.rapidgzip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * High-level API for reading a gzip file line-by-line using rapidgzip
 * parallel decompression.
 *
 * <h2>Usage examples</h2>
 *
 * <b>Stream API (recommended for large files):</b>
 * <pre>{@code
 * try (var reader = new RapidGzipLineReader("/data/huge.csv.gz")) {
 *     reader.lines()
 *           .filter(line -> line.contains("ERROR"))
 *           .forEach(System.out::println);
 * }
 * }</pre>
 *
 * <b>Iterator API:</b>
 * <pre>{@code
 * try (var reader = new RapidGzipLineReader("/data/huge.csv.gz")) {
 *     for (String line : reader) {
 *         process(line);
 *     }
 * }
 * }</pre>
 *
 * <b>Callback API:</b>
 * <pre>{@code
 * try (var reader = new RapidGzipLineReader("/data/huge.csv.gz")) {
 *     reader.forEachLine(line -> {
 *         System.out.println(line);
 *     });
 * }
 * }</pre>
 */
public class RapidGzipLineReader implements AutoCloseable, Iterable<String> {

    private final RapidGzipInputStream gzipStream;
    private final BufferedReader bufferedReader;
    private final boolean fastUtf8;
    private final byte[] readBuffer;
    private byte[] lineBuffer;
    private int readPos;
    private int readLimit;
    private int lineLength;
    private boolean eof;
    private boolean closed;

    /**
     * Open a gzip file for line-by-line reading with UTF-8 encoding.
     *
     * @param path path to the .gz file
     */
    public RapidGzipLineReader(String path) {
        this(path, 0, StandardCharsets.UTF_8, 1 << 16);
    }

    /**
     * Open a gzip file with specified parallelism.
     *
     * @param path        path to the .gz file
     * @param parallelism number of decompression threads (0 = auto)
     */
    public RapidGzipLineReader(String path, int parallelism) {
        this(path, parallelism, StandardCharsets.UTF_8, 1 << 16);
    }

    /**
     * Full constructor.
     *
     * @param path             path to the .gz file
     * @param parallelism      number of threads (0 = auto)
     * @param charset          character encoding
     * @param javaBufferSize   BufferedReader internal buffer size
     */
    public RapidGzipLineReader(String path, int parallelism, Charset charset, int javaBufferSize) {
        this.gzipStream = new RapidGzipInputStream(path, parallelism);
        this.fastUtf8 = StandardCharsets.UTF_8.equals(charset);
        if (fastUtf8) {
            int byteBufferSize = Math.max(1 << 20, javaBufferSize);
            this.readBuffer = new byte[byteBufferSize];
            this.lineBuffer = new byte[Math.max(1024, javaBufferSize)];
            this.bufferedReader = null;
            this.readPos = 0;
            this.readLimit = 0;
            this.lineLength = 0;
            this.eof = false;
        } else {
            this.bufferedReader = new BufferedReader(
                    new InputStreamReader(gzipStream, charset), javaBufferSize);
            this.readBuffer = null;
            this.lineBuffer = null;
            this.readPos = 0;
            this.readLimit = 0;
            this.lineLength = 0;
            this.eof = false;
        }
        this.closed = false;
    }

    /**
     * Read the next line, or {@code null} if EOF.
     */
    public String readLine() throws IOException {
        if (fastUtf8) {
            return readLineUtf8();
        }
        return bufferedReader.readLine();
    }

    private String readLineUtf8() throws IOException {
        while (true) {
            if (readPos >= readLimit) {
                if (eof) {
                    if (lineLength == 0) {
                        return null;
                    }
                    if (lineBuffer[lineLength - 1] == '\r') {
                        lineLength--;
                    }
                    String lastLine = new String(lineBuffer, 0, lineLength, StandardCharsets.UTF_8);
                    lineLength = 0;
                    return lastLine;
                }

                int n = gzipStream.read(readBuffer, 0, readBuffer.length);
                if (n == -1) {
                    eof = true;
                    continue;
                }
                readPos = 0;
                readLimit = n;
            }

            int lineEnd = readPos;
            while (lineEnd < readLimit && readBuffer[lineEnd] != '\n') {
                lineEnd++;
            }

            if (lineEnd < readLimit) {
                int chunkLength = lineEnd - readPos;
                if (lineLength == 0) {
                    int end = chunkLength;
                    if (end > 0 && readBuffer[readPos + end - 1] == '\r') {
                        end--;
                    }
                    String line = new String(readBuffer, readPos, end, StandardCharsets.UTF_8);
                    readPos = lineEnd + 1;
                    return line;
                }

                appendReadSlice(readPos, chunkLength);
                if (lineLength > 0 && lineBuffer[lineLength - 1] == '\r') {
                    lineLength--;
                }
                String line = new String(lineBuffer, 0, lineLength, StandardCharsets.UTF_8);
                lineLength = 0;
                readPos = lineEnd + 1;
                return line;
            }

            appendReadSlice(readPos, readLimit - readPos);
            readPos = readLimit;
        }
    }

    private void appendReadSlice(int start, int length) {
        if (length <= 0) {
            return;
        }
        ensureLineCapacity(lineLength + length);
        System.arraycopy(readBuffer, start, lineBuffer, lineLength, length);
        lineLength += length;
    }

    private void ensureLineCapacity(int requiredSize) {
        if (requiredSize <= lineBuffer.length) {
            return;
        }
        int newSize = Math.max(requiredSize, lineBuffer.length << 1);
        lineBuffer = Arrays.copyOf(lineBuffer, newSize);
    }

    /**
     * Returns a lazily-populated {@link Stream} of lines.
     * The stream should be used within a try-with-resources or closed after use.
     */
    public Stream<String> lines() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator(),
                        Spliterator.ORDERED | Spliterator.NONNULL),
                false);
    }

    /**
     * Invoke the given action for every line in the file.
     */
    public void forEachLine(Consumer<String> action) throws IOException {
        String line;
        while ((line = readLine()) != null) {
            action.accept(line);
        }
    }

    /**
     * Returns the approximate decompressed bytes consumed so far.
     */
    public long bytesRead() {
        return gzipStream.tell();
    }

    @Override
    public Iterator<String> iterator() {
        return new Iterator<>() {
            private String next;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (next != null) return true;
                try {
                    next = readLine();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (next == null) {
                    done = true;
                    return false;
                }
                return true;
            }

            @Override
            public String next() {
                if (!hasNext()) throw new NoSuchElementException();
                String result = next;
                next = null;
                return result;
            }
        };
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (bufferedReader != null) {
                bufferedReader.close(); // closes underlying streams
            } else {
                gzipStream.close();
            }
        }
    }
}
