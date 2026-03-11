/**
 * rapidgzip FFI wrapper — exposes a plain C API for Java FFM (Foreign Function & Memory).
 *
 * Functions:
 *   rgzip_open    — open a .gz file for parallel decompression
 *   rgzip_read    — read decompressed bytes into a caller-provided buffer
 *   rgzip_tell    — current decompressed byte offset
 *   rgzip_close   — release all resources
 *   rgzip_strerror— return last error string for a handle
 */

#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <mutex>

#include <filereader/Standard.hpp>
#include <rapidgzip/ParallelGzipReader.hpp>

/* ---------- opaque handle ---------- */

struct RGZipHandle {
    std::unique_ptr<rapidgzip::ParallelGzipReader<>> reader;
    std::string lastError;
};

/* ---------- C API ---------- */

extern "C" {

/**
 * Open a gzip file for parallel decompression.
 *
 * @param path           Path to the .gz file.
 * @param parallelism    Number of decompression threads (0 = auto-detect).
 * @param chunk_size     Chunk size in bytes for parallel splitting (0 = default 4 MiB).
 * @return               Opaque handle, or nullptr on failure (call rgzip_strerror(nullptr) is undefined;
 *                       the caller should just check for nullptr).
 */
void* rgzip_open(const char* path, int parallelism, uint64_t chunk_size) {
    auto* h = new (std::nothrow) RGZipHandle();
    if (!h) return nullptr;

    try {
        auto fileReader = std::make_unique<rapidgzip::StandardFileReader>(std::string(path));
        uint64_t cs = (chunk_size == 0) ? (4ULL << 20) : chunk_size;
        size_t   par = (parallelism <= 0) ? 0 : static_cast<size_t>(parallelism);

        h->reader = std::make_unique<rapidgzip::ParallelGzipReader<>>(
            std::move(fileReader), par, cs);

    } catch (const std::exception& e) {
        h->lastError = e.what();
        delete h;
        return nullptr;
    }
    return h;
}

/**
 * Read decompressed bytes.
 *
 * @param handle   Handle from rgzip_open.
 * @param buf      Destination buffer.
 * @param len      Max bytes to read.
 * @return         Bytes actually read, 0 on EOF, -1 on error.
 */
int64_t rgzip_read(void* handle, char* buf, int64_t len) {
    if (!handle || !buf || len <= 0) return -1;
    auto* h = static_cast<RGZipHandle*>(handle);
    try {
        size_t n = h->reader->read(buf, static_cast<size_t>(len));
        return static_cast<int64_t>(n);
    } catch (const std::exception& e) {
        h->lastError = e.what();
        return -1;
    }
}

/**
 * Return the current decompressed-stream offset.
 */
int64_t rgzip_tell(void* handle) {
    if (!handle) return -1;
    auto* h = static_cast<RGZipHandle*>(handle);
    try {
        return static_cast<int64_t>(h->reader->tell());
    } catch (const std::exception& e) {
        h->lastError = e.what();
        return -1;
    }
}

/**
 * Close the reader and free all resources.
 */
void rgzip_close(void* handle) {
    if (!handle) return;
    auto* h = static_cast<RGZipHandle*>(handle);
    delete h;
}

/**
 * Return the last error message (valid until next call on same handle).
 */
const char* rgzip_strerror(void* handle) {
    if (!handle) return "null handle";
    auto* h = static_cast<RGZipHandle*>(handle);
    return h->lastError.c_str();
}

} /* extern "C" */
