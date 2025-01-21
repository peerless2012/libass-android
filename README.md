# libass-android
libass-android is a collection of libraries for implementing [libass](https://github.com/libass/libass) for Android.

It use [libass-cmake](https://github.com/peerless2012/libass-cmake) to bulid libass.

## NOTICE
* This project is on dev, apis and functions may change later.
* All this module will publish to maven center when it is ready.

## Modules
### lib_ass
lib_ass is a native build library with prefab feature, this module contains libass.so and the header files.

App want to use libass in C/C++ can use this module.

### lib_ass_kt
lib_ass_kt is a kotlin wraper for libass native api. 

App want to use libass in java/kotlin can use this module. 

### lib_ass_media
lib_ass_media is a media3 extend library for libass.

App use media3 can use this module to add ass for your player.

## NOTE
The libass will render a event to a bitmap with only alpha channel. Current we blend color and alpha to an ARGB bitmap.

But, I think this is not good enough, we can just copy the alpha data to a ALPHA_8 bitmap, blend color when we draw the bitmap.

This solution have two feature:
* less cpu use, because we do not need to blend color on cpu side.
* less memory use, because ALPHA_8 can save 3/4 memory from ARGB_8888

This roadmap has been blocked by folowing issues:
* [Cue encode error when use bitmap and config is ALPHA_8.](https://github.com/androidx/media/issues/2054)
* [Support bitmap color blend when render cue.](https://github.com/androidx/media/issues/2055)

And google seems will not accept this feature request.
