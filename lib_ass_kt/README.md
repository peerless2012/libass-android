# ASS Kt
A kotlin wrapper for libass native api.

App want to use libass in java/kotlin can use this module.

## How to use
1. Add MavenCenter to your project
    ```
    allprojects {
        repositories {
            mavenCentral()
        }
    }
    ```
2. Add dependency.
    ```
   implementation "io.github.peerless2012:ass-kt:x.x.x"
    ```
3. Use libass in java/kotlin
    ```
    val ass = ASS()
    ```