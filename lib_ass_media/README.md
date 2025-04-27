# ASS Media
A media3 extend library for libass.

App use media3 can use this module to add ass for your player.

## Feature
There are three ways to render ass subtitle.
Which is defined in `AssRenderType`.

```
/**
 * ASS render type
 */
enum class AssRenderType {

    /**
     * Use SubtitleView render.
     */
    LEGACY,

    /**
     * Use Effect(Powered by canvas)
     */
    CANVAS,

    /**
     * Use Effect(Powered by OpenGL)
     */
    OPEN_GL

}
```

### 1. LEGACY
The ass/ssa subtitle will be parsed and transcode to bytes, and decode to bitmap when render.

This type not support dynamic feature, because all subtitle and it time is static.

But since the subtitle is transcode, it will not cost too much time when render. All work is done in parse thread.

### 2. CANVAS
The ass/ssa subtitle will be cal and render at runtime use media3 effect feature, and this will support all dynamic features.

And this need to create a screen size offscreen bitmap to render the libass bitmap pieces.

But when the dynamic feature is too complex, and libass will cost too much time to cal, the render will be blocked.

### 3. OPEN_GL
Just like `CANVAS`, but use OpenGL to render. and the offscreen tex is create to render the bitmap pieces.

Due to test, the `OPEN_GL` will save 1/3 time when render.

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
   implementation "io.github.peerless2012:ass-media:x.x.x"
    ```
3. Use libass-media in java/kotlin
    ```
    player = ExoPlayer.Builder(this)
    .buildWithAssSupport(
        this,
        AssRenderType.OPEN_GL
    )
    ```
4. Add external subtitles.
   ```
   val enConfig = MediaItem.SubtitleConfiguration
         .Builder(Uri.parse("http://192.168.0.254:80/files/f-en.ass"))
         .setMimeType(MimeTypes.TEXT_SSA)
         .setLanguage("en")
         .setLabel("External ass en")
         .setId("129")
         .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
         .build()
   val jpConfig = MediaItem.SubtitleConfiguration
         .Builder(Uri.parse("http://192.168.0.254:80/files/f-jp.ass"))
         .setMimeType(MimeTypes.TEXT_SSA)
         .setLanguage("jp")
         .setLabel("External ass jp")
         .setId("130")
         .build()
   val zhConfig = MediaItem.SubtitleConfiguration
         .Builder(Uri.parse("http://192.168.0.254:80/files/f-zh.ass"))
         .setMimeType(MimeTypes.TEXT_SSA)
         .setLanguage("zh")
         .setLabel("External ass zh")
         .setId("131")
         .build()
   val mediaItem = MediaItem.Builder()
         .setUri(url)
         .setSubtitleConfigurations(ImmutableList.of(enConfig, jpConfig, zhConfig))
   ```
   NOTE: Make sure the `id` is set and different from media self track size. Recommend bigger than 128 or more bigger.

## Known Issue
### 1. Ass render has a wrong order when ass use layer param.
This only happens in `LEGACY` mode, see:
* [ASS render in a wrong order](https://github.com/androidx/media/issues/2124)
* [Fix cue render order for ass/ssa](https://github.com/androidx/media/pull/2137)

### 2. Ass render subtitle time error when not start at begin.
This happens in `CANVAS` and `OPEN_GL` mode.
* [Align subtitle output with video frame timestamps](https://github.com/androidx/media/issues/2289#issuecomment-2831754204)

### 3. ResizeMode not work.
This happens in `CANVAS` and `OPEN_GL` mode.
* [onVideoSizeChanged not called when setVideoEffects called.](https://github.com/androidx/media/issues/2284)