cmake_minimum_required(VERSION 3.18.0)

project(onnx_prediction VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 17)

set(ONNXRUNTIME_DIR "${CMAKE_SOURCE_DIR}/onnxruntime")

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION "${ONNXRUNTIME_DIR}/lib/${ANDROID_ABI}/libonnxruntime.so"
    INTERFACE_INCLUDE_DIRECTORIES "${ONNXRUNTIME_DIR}/include"
)

add_library(onnx_jni SHARED
    onnx_jni.cc
)

target_include_directories(onnx_jni PRIVATE
    ${ONNXRUNTIME_DIR}/include
)

target_link_libraries(onnx_jni
    onnxruntime
    log
)

target_link_options(onnx_jni PRIVATE "-Wl,-z,max-page-size=16384")