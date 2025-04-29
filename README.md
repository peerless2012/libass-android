# libass-android
[![ass - Version](https://img.shields.io/maven-central/v/io.github.peerless2012/ass?label=ass)](https://central.sonatype.com/artifact/io.github.peerless2012/ass)
[![ass-kt - Version](https://img.shields.io/maven-central/v/io.github.peerless2012/ass-kt?label=ass-kt)](https://central.sonatype.com/artifact/io.github.peerless2012/ass-kt)
[![ass-media - Version](https://img.shields.io/maven-central/v/io.github.peerless2012/ass-media?label=ass-media)](https://central.sonatype.com/artifact/io.github.peerless2012/ass-media)

A collection of libraries for implementing [libass](https://github.com/libass/libass) for Android.

It use [libass-cmake](https://github.com/peerless2012/libass-cmake) to build libass.

## Modules
### [lib_ass](./lib_ass)
A native build library with prefab feature, this module contains libass.so and the header files.

App want to use libass in C/C++ can use this module.

### [lib_ass_kt](./lib_ass_kt)
A kotlin wrapper for libass native api. 

App want to use libass in java/kotlin can use this module. 

### [lib_ass_media](./lib_ass_media)
A media3 extend library for libass.

App use media3 can use this module to add ass for your player.

## Who is using
| Icon                                      | Name   |
|------------------------------------------|--------|
| <img src="https://raw.githubusercontent.com/FongMi/TV/refs/heads/fongmi/other/image/icon.png" alt="影視TV" width="50" /> | [影視TV](https://github.com/FongMi/TV)  |
| <img src="https://private-user-images.githubusercontent.com/50815957/438631485-e9b4d86b-6ce8-4550-bde2-bdf9a3818644.png?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NDU5MTQwNDYsIm5iZiI6MTc0NTkxMzc0NiwicGF0aCI6Ii81MDgxNTk1Ny80Mzg2MzE0ODUtZTliNGQ4NmItNmNlOC00NTUwLWJkZTItYmRmOWEzODE4NjQ0LnBuZz9YLUFtei1BbGdvcml0aG09QVdTNC1ITUFDLVNIQTI1NiZYLUFtei1DcmVkZW50aWFsPUFLSUFWQ09EWUxTQTUzUFFLNFpBJTJGMjAyNTA0MjklMkZ1cy1lYXN0LTElMkZzMyUyRmF3czRfcmVxdWVzdCZYLUFtei1EYXRlPTIwMjUwNDI5VDA4MDIyNlomWC1BbXotRXhwaXJlcz0zMDAmWC1BbXotU2lnbmF0dXJlPWEzYjE2MDZiYjkyZGRjNTJmZTVkMTE1YWMyM2MzOWRkNmQ2ZmUwZGJkMDFkODU3NjA5Yzg3NzNmNjVhOGQxMzkmWC1BbXotU2lnbmVkSGVhZGVycz1ob3N0In0.ipK_rg9l26nFyu3qUIK1R7YdEe2mG6e3wktNHhyHEsM" alt="AfuseKt" width="50" /> | [AfuseKt](https://github.com/AttemptD/AfuseKt-release)  |

## Issue
If you have issue when use, [create](https://github.com/peerless2012/libass-android/issues/new) an issue.

## PR
If you want to fix or add feature, [create](https://github.com/peerless2012/libass-android/compare) a pr.
