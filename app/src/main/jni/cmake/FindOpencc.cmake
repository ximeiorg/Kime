# FindOpencc.cmake - Custom finder for OpenCC built via add_subdirectory

set(Opencc_FOUND TRUE)
set(Opencc_INCLUDE_PATH "${CMAKE_SOURCE_DIR}/librime/deps/opencc/src")
set(Opencc_LIBRARY libopencc)

# Export Opencc_STATIC definition
if(Opencc_STATIC)
    add_definitions(-DOpencc_BUILT_AS_STATIC)
endif()