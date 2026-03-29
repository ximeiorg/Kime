cmake_minimum_required(VERSION 3.18.0)

project(kime VERSION 1.0.0)

# 设置 C++ 标准
set(CMAKE_CXX_STANDARD 17)

# NDK 构建选项
option(BUILD_SHARED_LIBS "" OFF)
option(BUILD_TESTING "" OFF)

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})

# 下载并配置 Boost (必须在 add_subdirectory(librime) 之前)
include(Boost)

# 设置 Boost 已找到，让 librime 跳过 find_package
set(Boost_FOUND TRUE)
set(Boost_INCLUDE_DIRS "${CMAKE_SOURCE_DIR}/boost")
set(Boost_LIBRARIES "")
set(Boost_INCLUDE_DIR "${CMAKE_SOURCE_DIR}/boost")

# 配置 glog
option(WITH_GFLAGS "Use gflags" OFF)
option(WITH_GTEST "Use googletest" OFF)
option(WITH_UNWIND "Enable libunwind support" OFF)
add_subdirectory(librime/deps/glog)
target_compile_options(glog_internal PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")

# 配置 yaml-cpp
option(YAML_CPP_BUILD_TOOLS "" OFF)
option(YAML_CPP_BUILD_TESTS "" OFF)
option(YAML_CPP_INSTALL "" OFF)
add_subdirectory(librime/deps/yaml-cpp)

# 配置 snappy (用于 leveldb 压缩)
option(SNAPPY_BUILD_TESTS "" OFF)
option(SNAPPY_BUILD_BENCHMARKS "" OFF)
option(SNAPPY_INSTALL "" OFF)
add_subdirectory(snappy)

set(HAVE_SNAPPY TRUE)

# 配置 leveldb
option(LEVELDB_BUILD_TESTS "" OFF)
option(LEVELDB_BUILD_BENCHMARKS "" OFF)
option(LEVELDB_INSTALL "" OFF)
add_subdirectory(librime/deps/leveldb)

# 配置 marisa-trie
add_subdirectory(librime/deps/marisa-trie)
target_compile_options(marisa PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")

set(LIBMARISA marisa)
option(USE_SYSTEM_MARISA "" ON)

# 配置 OpenCC - 禁用工具和测试，只构建库
option(BUILD_DOCUMENTATION "" OFF)
option(ENABLE_GTEST "" OFF)
option(ENABLE_BENCHMARK "" OFF)
option(ENABLE_DARTS "" OFF)
option(BUILD_PYTHON "" OFF)
add_subdirectory(librime/deps/opencc)
include(OpenccWorkarounds)

# 解决 X11/keysym.h 缺失问题
set(X11Keysym "${CMAKE_SOURCE_DIR}/librime/include")
include_directories("${X11Keysym}")

# 配置 librime
option(BUILD_TEST "" OFF)
option(BUILD_STATIC "" ON)
add_subdirectory(librime)
target_compile_options(
    rime-static PRIVATE 
    "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=." 
    "-Wno-error=deprecated-declarations"
)

# 编译 JNI 库
add_subdirectory(librime_jni)