cmake_minimum_required(VERSION 3.22.1)
project("droidspice")

add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp
)

# This CMakeLists.txt lives in app/src/main/cpp
set(MAIN_DIR "${CMAKE_CURRENT_LIST_DIR}/..")

# Headers: app/src/main/cpp/include/...
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}/include"
)

# Prebuilt ngspice shared lib: app/src/main/jniLibs/<ABI>/libngspice.so
add_library(ngspice SHARED IMPORTED)
set_target_properties(ngspice PROPERTIES
        IMPORTED_LOCATION
        "${MAIN_DIR}/jniLibs/${ANDROID_ABI}/libngspice.so"
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        ngspice
)
