cmake_minimum_required(VERSION 3.18.0)

# librime JNI 库
add_library(rime_jni SHARED
    rime_jni.cc
)

# 包含 librime 头文件
target_include_directories(rime_jni PRIVATE
    ${CMAKE_SOURCE_DIR}/librime/include
    ${CMAKE_SOURCE_DIR}/librime/src
)

# 链接 librime 和其他依赖库
target_link_libraries(rime_jni
    rime-static
    log
)