cmake_minimum_required(VERSION 3.17)
project(rapidgzip_ffi CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# RAPIDGZIP_ROOT is passed in automatically by the Maven build.
# For standalone usage:  cmake .. -DRAPIDGZIP_ROOT=/path/to/rapidgzip/librapidarchive
set(RAPIDGZIP_ROOT "" CACHE PATH
    "Path to rapidgzip librapidarchive root (contains CMakeLists.txt and src/)")

if(NOT RAPIDGZIP_ROOT OR NOT EXISTS "${RAPIDGZIP_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR
        "RAPIDGZIP_ROOT not set or invalid: '${RAPIDGZIP_ROOT}'\n"
        "Run:  mvn package   (auto-clones rapidgzip)\n"
        "Or:   cmake .. -DRAPIDGZIP_ROOT=/path/to/rapidgzip/librapidarchive")
endif()

set(RAPIDGZIP_SRC "${RAPIDGZIP_ROOT}/src")

option(LIBRAPIDARCHIVE_WITH_ISAL       "ISA-L SIMD-accelerated inflate" ON)
option(LIBRAPIDARCHIVE_WITH_RPMALLOC   "rpmalloc for faster allocations" ON)
option(LIBRAPIDARCHIVE_USE_ZLIB_NG     "zlib-ng instead of zlib" ON)
option(LIBRAPIDARCHIVE_USE_SYSTEM_ZLIB  "Use system zlib" OFF)
option(LIBRAPIDARCHIVE_BUILD_TESTS      "Build tests" OFF)
option(LIBRAPIDARCHIVE_BUILD_TOOLS      "Build CLI tools" OFF)
option(LIBRAPIDARCHIVE_BUILD_BENCHMARKS "Build benchmarks" OFF)

add_subdirectory("${RAPIDGZIP_ROOT}" "${CMAKE_CURRENT_BINARY_DIR}/librapidarchive")

add_library(rapidgzip_ffi SHARED
    "${CMAKE_CURRENT_SOURCE_DIR}/rapidgzip_ffi.cpp"
)

target_include_directories(rapidgzip_ffi PRIVATE
    "${RAPIDGZIP_SRC}"
    "${RAPIDGZIP_SRC}/core"
    "${RAPIDGZIP_SRC}/filereader"
    "${RAPIDGZIP_SRC}/rapidgzip"
)

target_link_libraries(rapidgzip_ffi PRIVATE pthread)

if(TARGET zlibstatic)
    target_link_libraries(rapidgzip_ffi PRIVATE zlibstatic)
    target_compile_definitions(rapidgzip_ffi PRIVATE WITH_ZLIB)
endif()

if(TARGET isal)
    target_link_libraries(rapidgzip_ffi PRIVATE ISAL::isal)
    target_compile_definitions(rapidgzip_ffi PRIVATE WITH_ISAL)
endif()

if(TARGET rpmalloc)
    target_link_libraries(rapidgzip_ffi PRIVATE rpmalloc)
endif()

target_compile_options(rapidgzip_ffi PRIVATE -O3 -Wall -Wextra)

set_target_properties(rapidgzip_ffi PROPERTIES
    OUTPUT_NAME "rapidgzip_ffi"
    SOVERSION 1
)

install(TARGETS rapidgzip_ffi LIBRARY DESTINATION lib)
