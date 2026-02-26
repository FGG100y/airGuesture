cmake_minimum_required(VERSION 3.18.1)

add_library(
        gesture-lib
        SHARED
        native-lib.cpp
        GestureAnalyzer.cpp
)

find_library(log-lib log)

target_link_libraries(
        gesture-lib
        ${log-lib}
)