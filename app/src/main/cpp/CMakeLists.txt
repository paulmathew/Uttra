
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



# Experiment 7.9H: Pixel 7a ARM64 optimization.
# Opt-in only: other ARM64 devices may not support these instructions.
option(
        UTTRA_ENABLE_PIXEL7A_ARM64_OPT
        "Enable experimental ARM64 DOTPROD + FP16 optimization"
        OFF
)

if(ANDROID
        AND CMAKE_BUILD_TYPE STREQUAL "Debug"
        AND ANDROID_ABI STREQUAL "arm64-v8a"
        AND UTTRA_ENABLE_PIXEL7A_ARM64_OPT)

    set(GGML_CPU_ARM_ARCH
            "armv8.2-a+dotprod+fp16"
            CACHE STRING "" FORCE)

    unset(HAVE_DOTPROD CACHE)
    unset(HAVE_FP16_VECTOR_ARITHMETIC CACHE)

else()
    # Clear the previously forced architecture when opting out.
    # CMake may retain values from earlier builds.
    if(GGML_CPU_ARM_ARCH STREQUAL "armv8.2-a+dotprod+fp16")
        unset(GGML_CPU_ARM_ARCH CACHE)
        unset(HAVE_DOTPROD CACHE)
        unset(HAVE_FP16_VECTOR_ARITHMETIC CACHE)
    endif()
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
