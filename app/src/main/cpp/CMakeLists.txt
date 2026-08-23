cmake_minimum_required(VERSION 3.22.1)

project("emulatorkernel")

add_library(
    emulatorkernel
    SHARED
    emulator-core.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    emulatorkernel
    ${log-lib}
    android
    dl
)
