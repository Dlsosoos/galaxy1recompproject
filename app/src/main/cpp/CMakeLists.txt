cmake_minimum_required(VERSION 3.22.1)
project(galaxyrecomp)

# Coloque aqui os .cpp gerados pelo recompilador (PowerPC Gekko -> C++) em cpp/recomp/
file(GLOB RECOMP_SRC ${CMAKE_SOURCE_DIR}/recomp/*.cpp)

add_library(galaxyrecomp SHARED native-lib.cpp ${RECOMP_SRC})
find_library(log-lib log)
target_link_libraries(galaxyrecomp ${log-lib})
