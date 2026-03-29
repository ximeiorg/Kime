# OpenccWorkarounds.cmake - Workarounds for OpenCC on Android

# Fix for missing dl library on Android
if(ANDROID)
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -llog")
endif()

# Disable unnecessary components
set(ENABLE_DARTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Disable building opencc tools (executables) on Android by not including tools subdirectory
# This is done by setting a flag that the OpenCC CMakeLists.txt checks
if(ANDROID OR NOT BUILD_SHARED_LIBS)
    # Create a dummy target to satisfy any potential dependencies
    if(NOT TARGET opencc_tools)
        add_custom_target(opencc_tools)
    endif()
endif()