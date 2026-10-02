# ASS Media
A media3 extension library for libass.

Apps that use media3 can use this module to add libass into their player.

## Feature
There are three ways to render ass subtitles which are defined in `AssRenderType`.

| Type | Feature | Anim | HDR/DV | Block render/UI |
| :----: | :----: | :----: | :----: | :----: |
| CUES | SubtitleView & Cue | ❌ | ✅ | ❌ |
| EFFECTS_CANVAS | Effect | ✅ | ❓ | ✅ |
| EFFECTS_OPEN_GL | Effect | ✅ | ❓ | ✅ |
| OVERLAY_CANVAS | Overlay | ✅ | ✅ | ✅ |
| OVERLAY_OPEN_GL | Overlay | ✅ | ✅ | ❌ |

* [OverlayShaderProgram does not support HDR colors yet](https://github.com/androidx/media/issues/723)
* [Why does TextOverLay support hdr, but not Bitmap support?](https://github.com/androidx/media/issues/2383)

### 1. CUES
The ass/ssa subtitles will be parsed and transcoded into bytes, and decoded into a bitmap when rendered.

This type does not support the dynamic feature, because all subtitles and their time are static.

However, since the subtitles are transcodeed, they will be to expensiveto render. All the work is done in the parse thread.

### 2. EFFECTS_CANVAS
The ass/ssa subtitles will be calculated and rendered at runtime using the media3 effect feature, this supports all dynamic features.

This needs the screen size to create an off-screen bitmap and render the libass bitmap pieces.

If a dynamic feature is too complex, libass will take too long to long to calculate and the render will be blocked.

### 3. EFFECTS_OPEN_GL
Just like `EFFECTS_CANVAS`, but uses OpenGL to render. and the offscreen text is created to render the bitmap pieces.

According to tests, the `EFFECTS_OPEN_GL` will take a 1/3 of the time to render.

### 4. OVERLAY_CANVAS
The ass/ssa subtitles will be calculated at runtime, and an `Overlay` widget is added in `SubtitleView` to render the subtitles.

The `libass` render result will copy into a bitmap, and draw in the `Canvas`.

This will block UI thread when rendering.

### 4. OVERLAY_OPEN_GL
Just like `OVERLAY_CANVAS`, but the `libass` render result will passed to `OpenGL` as a texture to avoid creating temporary bitmap.

It uses about half as much memory as `OVERLAY_CANVAS`.

The `libass` renderer and `OpenGL` draw on separate thread, so it will not block the UI thread like `OVERLAY_CANVAS`.


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
   implementation "io.github.peerless2012:ass-media:x.x.x"
    ```
3. Using libass-media in your java/kotlin code
    ```
    player = ExoPlayer.Builder(this)
    .buildWithAssSupport(
        this,
        AssRenderType.OPEN_GL
    )
    ```
4. Adding external subtitles
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
   NOTE: Make sure the `id` is set and different from the media self track size. The recommend value is 128 or larger.

## Configuration

`AssHandlerConfig` provides options to tune subtitle rendering behavior. Pass it to `buildWithAssSupport`:

```kotlin
player = ExoPlayer.Builder(this)
    .buildWithAssSupport(
        context = this,
        renderType = AssRenderType.OVERLAY_OPEN_GL,
        config = AssHandlerConfig(
            maxRenderPixels = 1920 * 1080
        )
    )
```

### Parameters

| Parameter | Default | Description |
| :----: | :----: | :---- |
| `glyphSize` | 10000 | Maximum number of glyph cache entries in libass |
| `cacheSize` | 128 | Maximum bitmap cache size in MB for libass |
| `maxRenderPixels` | 0 | Maximum pixel count for subtitle rendering (0 = no limit) |

### Render Downscaling (`maxRenderPixels`)

On high-resolution devices (e.g., 4K TVs), rendering subtitles at full resolution can be very CPU and memory intensive, especially for complex ASS animations. `maxRenderPixels` limits the resolution at which libass renders subtitles.

**How it works:**

```
Target frame size: 3840 x 2160 = 8,294,400 pixels
maxRenderPixels:   1920 x 1080 = 2,073,600

scale = sqrt(2,073,600 / 8,294,400) = 0.5
Actual render size: 1920 x 1080
```

1. libass renders subtitle bitmaps at the downscaled size (e.g., 1080p)
2. The rendered subtitle images are then scaled up to the actual surface/video size during display
3. GPU hardware handles the upscaling, which is nearly free

**Recommended values:**

| Value | Equivalent | Use case |
| :---- | :---- | :---- |
| `0` | No limit | Default, render at full resolution |
| `2_073_600` | 1080p | 4K devices with normal CPU |
| `3_686_400` | 1440p | 4K devices with strong CPU |
| `921_600` | 720p | Low-end devices or complex ASS animations |

**Mode-specific behavior:**

- **OVERLAY_OPEN_GL**: Renders at reduced size, scales `glViewport` coordinates to surface size
- **OVERLAY_CANVAS**: Renders at reduced size, uses `drawBitmap` with scaled `RectF`
- **EFFECTS_OPEN_GL**: Creates smaller FBO, uses `getVertexTransformation` to scale up in the OverlayEffect pipeline
- **EFFECTS_CANVAS**: Renders at reduced size, scales canvas draw coordinates
- **CUES**: Not affected (pre-renders all subtitles at parse time)
