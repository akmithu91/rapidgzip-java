import io.rapidgzip.RapidGzipLineReader;
import java.util.Map;
import java.util.stream.Collectors;

public class StreamExample {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) { System.err.println("Usage: StreamExample <file.gz>"); System.exit(1); }
        try (var reader = new RapidGzipLineReader(args[0])) {
            Map<Character, Long> counts = reader.lines()
                .filter(l -> !l.isEmpty())
                .collect(Collectors.groupingBy(l -> l.charAt(0), Collectors.counting()));
            counts.entrySet().stream()
                .sorted(Map.Entry.<Character, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> System.out.printf("  '%c' -> %,d%n", e.getKey(), e.getValue()));
        }
    }
}
