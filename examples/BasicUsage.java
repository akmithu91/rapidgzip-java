import io.rapidgzip.RapidGzipLineReader;

public class BasicUsage {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) { System.err.println("Usage: BasicUsage <file.gz>"); System.exit(1); }
        try (var reader = new RapidGzipLineReader(args[0])) {
            long count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (++count <= 10) System.out.println(line);
            }
            System.out.printf("%nTotal: %,d lines%n", count);
        }
    }
}
