cmake_minimum_required(VERSION 3.22.1)
project(nuvioseekthumb LANGUAGES C CXX)

# Seek thumbnail keyframe decoder (ported from Nuvio Arc, GPL-3.0). It links no FFmpeg: MPV's FFmpeg, already in
# the APK through mpv-android-lib, is loaded at run time with dlopen (thumb_backend_mpv.cpp).
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

add_library(
    nuvioseekthumb
    SHARED
    thumb_shim.cpp
    hevc_strips.cpp
    thumb_backend_mpv.cpp
    thumbcolor.c
    thumbdovi.c
)
target_include_directories(nuvioseekthumb PRIVATE "${CMAKE_CURRENT_LIST_DIR}")
set_source_files_properties(thumb_backend_mpv.cpp PROPERTIES
    INCLUDE_DIRECTORIES "${CMAKE_CURRENT_LIST_DIR}/third_party/ffmpeg-a7522f3fef")
target_compile_features(nuvioseekthumb PRIVATE cxx_std_17)
target_compile_options(nuvioseekthumb PRIVATE -O2 -Wall -Wextra -Wno-deprecated-declarations -Wno-unused-parameter)
target_link_options(nuvioseekthumb PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(nuvioseekthumb ${log-lib} ${jnigraphics-lib} dl m)
