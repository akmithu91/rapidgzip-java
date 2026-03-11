# rapidgzip-java

Parallel gzip decompression for Java using [rapidgzip](https://github.com/mxmlnkn/rapidgzip)
via the **Foreign Function & Memory (FFM)** API. Ships as a **single JAR** with the native
`.so` bundled inside.

**Linux x86_64 only. Java 21+.**

## Quick Start

```bash
cd rapidgzip-java
mvn package
# Output: target/rapidgzip-java-1.0.0.jar  (~576 KB)
```

That's it. Maven automatically:
1. Clones rapidgzip from GitHub (with all submodules)
2. Builds the native `.so` via cmake (with ISA-L, zlib-ng, rpmalloc)
3. Compiles the Java FFM wrapper
4. Packages everything into a single JAR

## Prerequisites

```bash
# Ubuntu / Debian
sudo apt install openjdk-21-jdk-headless maven g++ cmake nasm git
```

## What `mvn package` does

| Maven Phase          | Plugin            | Action                                          |
|----------------------|-------------------|-------------------------------------------------|
| `initialize`         | `antrun`          | Create `native/build/` and resource dirs         |
| `initialize`         | `exec` (bash)     | `git clone --recursive` rapidgzip into `target/` |
| `generate-resources` | `exec` (cmake)    | Configure and build `librapidgzip_ffi.so`        |
| `generate-resources` | `exec` (cp)       | Stage `.so` into JAR resources                   |
| `compile`            | `compiler`        | `javac --enable-preview` for FFM                 |
| `package`            | `jar`             | Bundle classes + native `.so` into JAR           |

## Maven Commands

```bash
mvn package                        # full build (clone + native + java + jar)
mvn clean package                  # force fresh clone + rebuild
mvn package -Dskip.native          # skip native build, reuse existing .so
mvn package -Drapidgzip.tag=v0.14.3  # pin a specific rapidgzip version
mvn clean                          # wipe target/ (including clone) + native/build/
```

## Running Your Code

```bash
# Java 21 (FFM is preview)
java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     -cp target/rapidgzip-java-1.0.0.jar:your-app.jar \
     com.example.YourApp

# Java 22+ (FFM finalized)
java --enable-native-access=ALL-UNNAMED \
     -cp target/rapidgzip-java-1.0.0.jar:your-app.jar \
     com.example.YourApp
```

## Usage

### Line-by-line with Stream API

```java
import io.rapidgzip.RapidGzipLineReader;

try (var reader = new RapidGzipLineReader("/data/huge.csv.gz")) {
    reader.lines()
          .filter(line -> line.startsWith("ERROR"))
          .forEach(System.out::println);
}
```

### For-each loop

```java
try (var reader = new RapidGzipLineReader("/data/logs.gz", 8)) { // 8 threads
    for (String line : reader) {
        process(line);
    }
}
```

### Callback (lowest overhead)

```java
try (var reader = new RapidGzipLineReader("/data/logs.gz")) {
    reader.forEachLine(line -> { /* ... */ });
}
```

### Raw InputStream

```java
import io.rapidgzip.RapidGzipInputStream;

try (var in = new RapidGzipInputStream("/data/archive.gz")) {
    byte[] buf = new byte[65536];
    int n;
    while ((n = in.read(buf)) != -1) {
        outputStream.write(buf, 0, n);
    }
}
```

## Project Layout

```
rapidgzip-java/
├── pom.xml                                 <- the only build file you need
├── .gitignore
├── native/
│   ├── CMakeLists.txt                      <- builds librapidgzip_ffi.so
│   └── rapidgzip_ffi.cpp                   <- C wrapper (5 functions)
├── src/main/java/io/rapidgzip/
│   ├── RapidGzipLineReader.java            <- lines(), iterator, callback
│   ├── RapidGzipInputStream.java           <- drop-in InputStream
│   ├── RapidGzipNative.java               <- FFM downcall bindings
│   ├── NativeLoader.java                  <- extracts .so from JAR at runtime
│   └── RapidGzipException.java
└── examples/
    ├── BasicUsage.java
    ├── StreamExample.java
    └── BenchmarkVsStdlib.java
```

## Architecture

```
+--------------------------------------------------+
|                  Your Java App                    |
+--------------------------------------------------+
|  RapidGzipLineReader   |  RapidGzipInputStream   |
+--------------------------------------------------+
|              RapidGzipNative (FFM)                |
|         MethodHandle downcalls to C API           |
+--------------------------------------------------+
|           NativeLoader (extracts .so)             |
+--------------------------------------------------+
|          librapidgzip_ffi.so (inside JAR)         |
|  +------------+----------+--------+-----------+   |
|  | rapidgzip  |  zlib-ng | ISA-L  | rpmalloc  |  |
|  | (parallel  | (inflate)| (SIMD  | (fast     |  |
|  |  decomp)   |          | accel) |  malloc)  |  |
|  +------------+----------+--------+-----------+   |
+--------------------------------------------------+
```

## API Reference

### RapidGzipLineReader

| Method | Description |
|---|---|
| `new RapidGzipLineReader(path)` | Auto parallelism, UTF-8 |
| `new RapidGzipLineReader(path, threads)` | Explicit thread count |
| `String readLine()` | Next line, `null` at EOF |
| `Stream<String> lines()` | Lazy stream of all lines |
| `void forEachLine(Consumer)` | Callback per line |
| `Iterator<String> iterator()` | For-each compatible |
| `long bytesRead()` | Decompressed bytes so far |
| `void close()` | Release native resources |

### RapidGzipInputStream

| Method | Description |
|---|---|
| `new RapidGzipInputStream(path)` | Auto parallelism |
| `new RapidGzipInputStream(path, threads)` | Explicit thread count |
| `int read(byte[], off, len)` | Standard InputStream |
| `long tell()` | Decompressed byte offset |
| `void close()` | Release native resources |

## Performance

rapidgzip shines on **large files** (hundreds of MB to many GB) where it splits the gzip
stream into chunks and decompresses across all CPU cores. On small files (< ~100 MB), the
thread pool startup cost means `java.util.zip.GZIPInputStream` may be comparable.

## License

- rapidgzip: MIT / Apache-2.0
- ISA-L: BSD-3-Clause
- zlib-ng: zlib license
- This wrapper: MIT
