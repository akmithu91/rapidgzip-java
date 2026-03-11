package io.rapidgzip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
        this.bufferedReader = new BufferedReader(
                new InputStreamReader(gzipStream, charset), javaBufferSize);
        this.closed = false;
    }

    /**
     * Read the next line, or {@code null} if EOF.
     */
    public String readLine() throws IOException {
        return bufferedReader.readLine();
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
        while ((line = bufferedReader.readLine()) != null) {
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
                    next = bufferedReader.readLine();
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
            bufferedReader.close(); // closes underlying streams
        }
    }
}
