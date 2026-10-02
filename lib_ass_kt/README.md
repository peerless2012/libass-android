# ASS Kt
A kotlin wrapper for libass's native api.

Apps that want to use libass in java/kotlin can use this module.

## How to use
1. Add MavenCentral to your project
    ```
    allprojects {
        repositories {
            mavenCentral()
        }
    }
    ```
2. Add the dependency.
    ```
   implementation "io.github.peerless2012:ass-kt:x.x.x"
    ```
3. Use libass-kt your java/kotlin code
    ```
    val ass = ASS()
    ```