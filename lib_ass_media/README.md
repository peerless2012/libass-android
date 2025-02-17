# ASS Media
lib_ass_media is a media3 extend library for libass.

App use media3 can use this module to add ass for your player.

## Feature
There are three ways to render ass subtitle.
Which is defined in `ASSRenderType`.

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
3. Use libass in java/kotlin
    ```
    layer = ExoPlayer.Builder(this)
    .buildWithAssSupport(
        dataSourceFactory = DefaultDataSource.Factory(this),
        extractorsFactory = DefaultExtractorsFactory(),
        renderType = AssRenderType.OPEN_GL
    )
    ```

## Known Issue
### 1. Ass render has a wrong order when ass use layer param.
This only happens in `LEGACY` mode, see:
* [ASS render in a wrong order](https://github.com/androidx/media/issues/2124)
* [Fix cue render order for ass/ssa](https://github.com/androidx/media/pull/2137)

### 2. Ass render will block video when the ass is very very complex.
This only happens in `CANVAS` and `OPEN_GL` mode.

## Note
The libass will render a event to a bitmap with only alpha channel. Current we blend color and alpha to an ARGB bitmap.

But, I think this is not good enough, we can just copy the alpha data to a ALPHA_8 bitmap, blend color when we draw the bitmap.

This solution have two feature:
* less cpu use, because we do not need to blend color on cpu side.
* less memory use, because ALPHA_8 can save 3/4 memory from ARGB_8888

This roadmap has been blocked by folowing issues:
* [Cue encode error when use bitmap and config is ALPHA_8.](https://github.com/androidx/media/issues/2054)
* [Support bitmap color blend when render cue.](https://github.com/androidx/media/issues/2055)

And google seems will not accept this feature request.