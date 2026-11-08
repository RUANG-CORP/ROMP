cmake_minimum_required(VERSION 3.22.1)

project("samp_cef_mobile")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/cef
    ${CMAKE_CURRENT_SOURCE_DIR}/util
)

add_library(
    samp_cef_mobile
    SHARED
    cef/cef_net.cpp
    util/CJavaWrapper.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    samp_cef_mobile
    ${log-lib}
)

