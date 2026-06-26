cmake_minimum_required(VERSION 3.13)
project(pag_cmp_jvm LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LIBPAG_ROOT "" CACHE PATH "Path to a local Tencent/libpag checkout")
set(LIBPAG_LIBRARY "" CACHE FILEPATH "Path to the built desktop libpag dynamic library")

if (NOT LIBPAG_ROOT)
    message(FATAL_ERROR "LIBPAG_ROOT is required, for example -DLIBPAG_ROOT=/Users/mumu/projects/android/libpag")
endif ()

if (NOT LIBPAG_LIBRARY)
    message(FATAL_ERROR "LIBPAG_LIBRARY is required, for example -DLIBPAG_LIBRARY=/path/to/libpag.dylib")
endif ()

find_package(JNI REQUIRED)

add_library(pag_cmp_jvm SHARED pag_cmp_jvm.cpp)

target_include_directories(pag_cmp_jvm PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${LIBPAG_ROOT}/include
)

target_link_libraries(pag_cmp_jvm PRIVATE ${LIBPAG_LIBRARY})

if (APPLE)
    set_target_properties(pag_cmp_jvm PROPERTIES
        INSTALL_RPATH "@loader_path"
        BUILD_RPATH "@loader_path"
    )
endif ()
