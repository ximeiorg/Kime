cmake_minimum_required(VERSION 3.18.0)

# ── 调试日志与耗时埋点开关 ──
# 默认 OFF；可通过环境变量按需开启：
#   T9_ENABLE_VERBOSE_LOG=ON T9_ENABLE_TIMING=ON ./gradlew assembleDebug
option(T9_ENABLE_VERBOSE_LOG "Enable verbose T9 debug logging" OFF)
option(T9_ENABLE_TIMING      "Enable T9 timing instrumentation" OFF)

# 环境变量覆盖（不持久化到 CMakeCache，每次构建重新评估）
if(DEFINED ENV{T9_ENABLE_VERBOSE_LOG} AND NOT "$ENV{T9_ENABLE_VERBOSE_LOG}" STREQUAL "")
    set(T9_ENABLE_VERBOSE_LOG "$ENV{T9_ENABLE_VERBOSE_LOG}" CACHE BOOL "" FORCE)
endif()
if(DEFINED ENV{T9_ENABLE_TIMING} AND NOT "$ENV{T9_ENABLE_TIMING}" STREQUAL "")
    set(T9_ENABLE_TIMING "$ENV{T9_ENABLE_TIMING}" CACHE BOOL "" FORCE)
endif()

# 使用 file(GLOB CONFIGURE_DEPENDS) 替代 aux_source_directory，
# 让 CMake 在源文件新增/删除时自动重新配置。
file(GLOB RIME_T9_SRC CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/*.cc)

# rime-t9-objs：完整插件构建（需要 RIME 引擎头文件）
# 作为 librime plugin 子项目构建时编译；独立测试构建时跳过（无 RIME 头文件）
if(NOT T9_BUILD_TESTS)
  add_library(rime-t9-objs OBJECT ${RIME_T9_SRC})
  if(BUILD_SHARED_LIBS)
    set_target_properties(rime-t9-objs PROPERTIES POSITION_INDEPENDENT_CODE ON)
  endif()

  set(plugin_name "rime-t9" PARENT_SCOPE)
  set(plugin_objs $<TARGET_OBJECTS:rime-t9-objs> PARENT_SCOPE)
  set(plugin_deps ${rime_library} PARENT_SCOPE)
  set(plugin_modules "t9" PARENT_SCOPE)

  if(T9_ENABLE_VERBOSE_LOG)
    target_compile_definitions(rime-t9-objs PRIVATE T9_ENABLE_VERBOSE_LOG=1)
  endif()
  if(T9_ENABLE_TIMING)
    target_compile_definitions(rime-t9-objs PRIVATE T9_ENABLE_TIMING=1)
  endif()
endif()

# ── T9 单元测试（GoogleTest） ──
# 对应规划文档 §4.4：librime-t9 独立测试开关
# 在 Android 构建中保持 OFF（默认）；CI/Linux 构建中通过 -DT9_BUILD_TESTS=ON 启用
option(T9_BUILD_TESTS "Build T9 plugin unit tests" OFF)

if(T9_BUILD_TESTS)
  enable_testing()

  # 复用 librime/deps/googletest（作为 plugin 构建时 PROJECT_SOURCE_DIR 指向 librime 源码目录）
  # 允许外部通过 -DT9_GTEST_DIR=... 指定，否则按层级查找
  if(NOT DEFINED T9_GTEST_DIR)
    set(T9_GTEST_DIR "${PROJECT_SOURCE_DIR}/deps/googletest")
    if(NOT EXISTS "${T9_GTEST_DIR}/CMakeLists.txt")
      # 回退：作为独立项目构建时从 jni 顶层查找
      set(T9_GTEST_DIR "${CMAKE_SOURCE_DIR}/librime/deps/googletest")
    endif()
  endif()

  if(EXISTS "${T9_GTEST_DIR}/CMakeLists.txt")
    # 防止 GoogleTest 在子项目中覆盖编译器标志
    set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
    add_subdirectory("${T9_GTEST_DIR}" "${CMAKE_BINARY_DIR}/t9-googletest")

    # 纯算法源文件（不依赖 RIME 引擎，避免链接 rime-static 导致依赖 OpenCC/marisa 等第三方库）
    # 排除 t9_processor.cc / t9_module.cc（依赖 RIME Context/Menu/Candidate 等）
    # t9_script_translator.cc：完整翻译器复刻（依赖 gear/script_translator.h，无纯算法子集）
    # t9_filter.cc 保留：通过 T9_ALGO_ONLY_BUILD 编译守卫，仅编译 converter 部分（无 RIME 依赖）
    file(GLOB T9_ALGO_SRC CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/*.cc)
list(FILTER T9_ALGO_SRC EXCLUDE REGEX "t9_processor\\.cc$|t9_module\\.cc$|t9_date_translator\\.cc$|t9_user_translator\\.cc$|t9_digit_userdict\\.cc$|t9_script_translator\\.cc$")
    add_library(t9-algo-objs OBJECT ${T9_ALGO_SRC})
    target_include_directories(t9-algo-objs PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src
        ${PROJECT_SOURCE_DIR}/include
        ${CMAKE_SOURCE_DIR}/librime/include)
    # t9_filter.cc 在 T9_ALGO_ONLY_BUILD 下仅编译 converter（无 RIME 依赖）
    target_compile_definitions(t9-algo-objs PRIVATE T9_ALGO_ONLY_BUILD)
    if(T9_ENABLE_VERBOSE_LOG)
      target_compile_definitions(t9-algo-objs PRIVATE T9_ENABLE_VERBOSE_LOG=1)
    endif()
    if(T9_ENABLE_TIMING)
      target_compile_definitions(t9-algo-objs PRIVATE T9_ENABLE_TIMING=1)
    endif()

    file(GLOB T9_TEST_SOURCES test/*_test.cc)
    add_executable(t9_test ${T9_TEST_SOURCES} test/t9_test_main.cc)
    target_link_libraries(t9_test PRIVATE gtest t9-algo-objs)
    target_include_directories(t9_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
    # 测试只测试纯算法层，与 t9-algo-objs 一致使用 T9_ALGO_ONLY_BUILD
    target_compile_definitions(t9_test PRIVATE T9_ALGO_ONLY_BUILD)

    # 纯算法测试不需要 RIME 引擎初始化
    add_test(NAME t9_test COMMAND t9_test)
  endif()
endif()
