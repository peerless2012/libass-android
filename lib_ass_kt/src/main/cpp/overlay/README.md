# EFFECTS_ATLAS overlay

The native code behind `AssRenderType.EFFECTS_ATLAS`. It draws ASS subtitles with OpenGL ES straight onto each decoded video frame, inside media3's video effects pipeline.

It also builds on a desktop, where it has unit tests.

## The idea

libass doesn't output a picture. `ass_render_frame` returns a list of `ASS_Image`s: small bitmaps, each with a position and a single color. Blended one over the other in list order, they make the subtitles.

Rather than compositing them into a full-frame RGBA picture on the CPU and uploading that on every frame, the overlay hands the images to the GPU as they are:

1. **Pack.** The bitmaps are packed side by side into an 8-bit texture, the atlas page. A bitmap
   that shows up several times in a frame is stored once per page. If the bitmaps don't all fit in a
   texture of `GL_MAX_TEXTURE_SIZE`, the rest go in a second page, then a third...
2. **Upload.** Only the packed area of each page is uploaded: one byte per texel, and only as large
   as the subtitles, not the frame.
3. **Draw.** Each image becomes a quad carrying its color as a vertex attribute, so a whole page is
   one draw call. The fragment shader scales the color by the texel's coverage and blends it,
   premultiplied, "over" the frame. Quads keep libass's list order, so images stack as libass meant.

On GLES 3, pages are `GL_R8` textures uploaded through a pixel buffer object.
On GLES 2, they are `GL_ALPHA` textures uploaded from client memory.

### Limitations

- It doesn't support HDR or Dolby Vision video.

### Build and run the tests

From this directory:

```sh
cmake -S . -B builddir
cmake --build builddir
ctest --test-dir builddir --output-on-failure
```

The GL tests expect the GPU to blend exactly as the 8-bit integer math does. On Mesa's software
renderer, llvmpipe (as with `LIBGL_ALWAYS_SOFTWARE=1`), they need Mesa 26.0 or later: before it,
llvmpipe rounds some blends down instead of to nearest, and the tests fail by one step.
