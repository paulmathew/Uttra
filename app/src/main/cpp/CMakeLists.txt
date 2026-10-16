
cmake_minimum_required(VERSION 3.22.1)

project(uttra_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Locate the Whisper.cpp Git submodule.
set(WHISPER_DIR
        "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp"
)

# Disable unnecessary example and test builds.
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# Optimize native inference for the development build.
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(-O3)
endif()

# Build Whisper.cpp and its GGML dependencies.
add_subdirectory(
        "${WHISPER_DIR}"
        "${CMAKE_CURRENT_BINARY_DIR}/whisper-build"
)

# Keep our existing JNI library.
add_library(
        uttra_native
        SHARED
        uttra_jni.cpp
)

target_link_libraries(
        uttra_native
        PRIVATE
        whisper
        log
)
