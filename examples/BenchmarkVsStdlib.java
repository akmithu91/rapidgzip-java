import io.rapidgzip.RapidGzipLineReader;
import java.io.*;
import java.util.zip.GZIPInputStream;

public class BenchmarkVsStdlib {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) { System.err.println("Usage: BenchmarkVsStdlib <large-file.gz>"); System.exit(1); }
        String file = args[0];
        try (var r = new RapidGzipLineReader(file)) { r.lines().limit(1000).count(); }
        long t0 = System.nanoTime();
        long rapidLines;
        try (var r = new RapidGzipLineReader(file)) { rapidLines = r.lines().count(); }
        long rapidMs = (System.nanoTime() - t0) / 1_000_000;
        t0 = System.nanoTime();
        long stdLines = 0;
        try (var br = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new FileInputStream(file), 1 << 16)))) {
            while (br.readLine() != null) stdLines++;
        }
        long stdMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("Lines: %,d / %,d%nrapidgzip: %,d ms%nstdlib: %,d ms%nSpeedup: %.2fx%n",
            rapidLines, stdLines, rapidMs, stdMs, (double)stdMs / rapidMs);
    }
}
