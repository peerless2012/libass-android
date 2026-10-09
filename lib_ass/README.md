# ASS
A native build library with prefab features, this module contains libass.so and its header files.

Apps that want to use libass in C/C++ can use this module.

## How to use
1. Add MavenCentral to your project
    ```
    allprojects {
        repositories {
            mavenCentral()
        }
    }
    ```
2. Enable the prefab feature.
    ```
    android {
        buildFeatures {
            prefab true
        }
    }
    ```
3. Add dependency
    ```
    implementation "io.github.peerless2012:ass:x.x.x"
    ```
4. Add the prebuild in `CMakeLists.txt`
    ```
    # Add these two lines.
    find_package(lib_ass REQUIRED CONFIG)
    target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE lib_ass::ass)
    ```
5. Add the libass headers in your c/c++ code
    ```
    #include "ass.h"
    ```