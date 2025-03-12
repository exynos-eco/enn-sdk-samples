cmake_minimum_required(VERSION 3.4.1)

# Include the headers directory
include_directories(include)

# Add your library
add_library(
        enn_jni
        SHARED
        enn_jni.cc
)

add_library(
        enn_service_so
        SHARED
        IMPORTED
)

set_target_properties(
        enn_service_so
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libenn_public_api_ndk_v1.so
)

find_library(
        log-lib
        log
)

target_link_libraries(
        enn_jni
        enn_service_so
        ${log-lib}
)