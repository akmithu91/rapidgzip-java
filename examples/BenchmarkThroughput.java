import io.rapidgzip.RapidGzipInputStream;
import io.rapidgzip.RapidGzipLineReader;

public class BenchmarkThroughput {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: BenchmarkThroughput <raw|line> <file.gz> <threads> <seconds>");
            System.exit(1);
        }

        String mode = args[0];
        String file = args[1];
        int threads = Integer.parseInt(args[2]);
        long seconds = Long.parseLong(args[3]);

        long deadlineNanos = System.nanoTime() + seconds * 1_000_000_000L;
        long start = System.nanoTime();
        long bytes;

        switch (mode) {
            case "raw" -> bytes = runRaw(file, threads, deadlineNanos);
            case "line" -> bytes = runLine(file, threads, deadlineNanos);
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }

        long elapsedNanos = Math.max(1L, System.nanoTime() - start);
        double gib = bytes / (1024.0 * 1024.0 * 1024.0);
        double sec = elapsedNanos / 1_000_000_000.0;
        double gibPerSec = gib / sec;

        System.out.printf("mode=%s bytes=%,d elapsed=%.3fs throughput=%.3f GiB/s%n",
                mode, bytes, sec, gibPerSec);
    }

    private static long runRaw(String file, int threads, long deadlineNanos) throws Exception {
        byte[] buf = new byte[1 << 20];
        long total = 0;
        try (var in = new RapidGzipInputStream(file, threads)) {
            while (System.nanoTime() < deadlineNanos) {
                int n = in.read(buf);
                if (n == -1) {
                    break;
                }
                total += n;
            }
        }
        return total;
    }

    private static long runLine(String file, int threads, long deadlineNanos) throws Exception {
        try (var reader = new RapidGzipLineReader(file, threads)) {
            while (System.nanoTime() < deadlineNanos) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
            }
            return reader.bytesRead();
        }
    }
}
