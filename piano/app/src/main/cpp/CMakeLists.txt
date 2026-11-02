cmake_minimum_required(VERSION 3.22.1)
project(piano_audio_engine C CXX)

find_package(oboe REQUIRED CONFIG)
find_package(fluidsynth-android REQUIRED CONFIG)

add_library(piano_audio_engine SHARED piano_audio_engine.cpp)
target_compile_features(piano_audio_engine PRIVATE cxx_std_17)
target_compile_options(piano_audio_engine PRIVATE -Wall -Wextra)
target_link_libraries(
    piano_audio_engine
    PRIVATE fluidsynth-android::fluidsynth oboe::oboe log android
)
