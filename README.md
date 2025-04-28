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

## Issue
If you have issue when use, [create](https://github.com/peerless2012/libass-android/issues/new) an issue.

## PR
If you want to fix or add feature, [create](https://github.com/peerless2012/libass-android/compare) a pr.