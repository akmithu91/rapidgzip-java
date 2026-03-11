package io.rapidgzip;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads the native rapidgzip_ffi shared library from within the JAR.
 * <p>
 * The .so is stored at {@code native/linux-x86_64/librapidgzip_ffi.so} inside the JAR
 * and is extracted to a temp directory on first use.
 */
final class NativeLoader {

    private static volatile SymbolLookup LOOKUP;
    private static volatile Path EXTRACTED_LIB;

    private NativeLoader() {}

    /**
     * Returns a {@link SymbolLookup} for the rapidgzip_ffi native library,
     * extracting from the JAR if not yet done.
     */
    static SymbolLookup lookup() {
        if (LOOKUP == null) {
            synchronized (NativeLoader.class) {
                if (LOOKUP == null) {
                    LOOKUP = load();
                }
            }
        }
        return LOOKUP;
    }

    private static SymbolLookup load() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        if (!os.contains("linux")) {
            throw new UnsupportedOperationException(
                    "rapidgzip-java only supports Linux. Detected OS: " + os);
        }
        if (!arch.equals("amd64") && !arch.equals("x86_64")) {
            throw new UnsupportedOperationException(
                    "rapidgzip-java only supports x86_64. Detected arch: " + arch);
        }

        String resourcePath = "native/linux-x86_64/librapidgzip_ffi.so";

        try (InputStream in = NativeLoader.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException(
                        "Native library not found in JAR at: " + resourcePath);
            }

            Path tempDir = Files.createTempDirectory("rapidgzip-java-");
            tempDir.toFile().deleteOnExit();

            Path libFile = tempDir.resolve("librapidgzip_ffi.so");
            Files.copy(in, libFile, StandardCopyOption.REPLACE_EXISTING);
            libFile.toFile().deleteOnExit();

            EXTRACTED_LIB = libFile;

            System.load(libFile.toAbsolutePath().toString());
            return SymbolLookup.loaderLookup();

        } catch (IOException e) {
            throw new RuntimeException("Failed to extract native library", e);
        }
    }
}
