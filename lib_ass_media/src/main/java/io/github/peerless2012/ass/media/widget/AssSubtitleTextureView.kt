package io.github.peerless2012.ass.media.widget

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import androidx.annotation.WorkerThread
import androidx.media3.common.C
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import io.github.peerless2012.ass.AssTexType
import io.github.peerless2012.ass.media.AssHandler
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 12/02/25 9:59 PM
 * @Version V1.0
 * @Description
 */
@UnstableApi
class AssSubtitleTextureView : TextureView, AssSubtitleRender, TextureView.SurfaceTextureListener {

    private val assHandler: AssHandler

    constructor(context: Context, assHandler: AssHandler) : this(context, null, assHandler)

    constructor(context: Context, attrs: AttributeSet?, assHandler: AssHandler) : this(
        context,
        attrs,
        0,
        assHandler
    )

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, assHandler: AssHandler) :
            super(context, attrs, defStyleAttr) {
        this.assHandler = assHandler
        isOpaque = false
        surfaceTextureListener = this
    }

    interface Renderer {
        @WorkerThread
        fun onSurfaceCreated()
        @WorkerThread
        fun onSurfaceChanged(width: Int, height: Int)
        @WorkerThread
        fun onDrawFrame(timestampNanos: Long): Boolean
        @WorkerThread
        fun onSurfaceDestroyed()
    }

    private var renderThread: AssRenderThread? = null

    override fun requestRender(timestampNanos: Long) {
        renderThread?.requestRender(timestampNanos)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        AssRenderThread(surface, width, height, AssRender(assHandler)).also {
            renderThread = it
            it.start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.onSurfaceSizeChanged(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderThread?.release()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    companion object {

    }

    private class AssRenderThread(
        private val surfaceTexture: SurfaceTexture,
        private var width: Int,
        private var height: Int,
        private val render: Renderer
    ) : HandlerThread("AssTexRenderThread"), Handler.Callback {

        private val TAG = "AssTexRenderThread"

        private val MSG_INIT = 1
        private val MSG_DRAW = 2
        private val MSG_SURFACE_SIZE_CHANGED = 3
        private val MSG_RELEASE = 4

        private lateinit var handler: Handler

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        override fun start() {
            super.start()
            handler = Handler(looper, this)
            handler.sendEmptyMessage(MSG_INIT)
        }

        fun requestRender(timestampNanos: Long) {
            handler.removeMessages(MSG_DRAW)
            handler.obtainMessage(MSG_DRAW, timestampNanos).sendToTarget()
        }

        fun onSurfaceSizeChanged(width: Int, height: Int) {
            this.width = width
            handler.sendEmptyMessage(MSG_SURFACE_SIZE_CHANGED)
        }

        fun release() {
            handler.sendEmptyMessage(MSG_RELEASE)
        }

        override fun handleMessage(msg: Message): Boolean {
            try {
                when (msg.what) {
                    MSG_INIT -> initInternal()
                    MSG_DRAW -> drawInternal(msg.obj as Long)
                    MSG_SURFACE_SIZE_CHANGED -> sizeChangedInternal(width, height)
                    MSG_RELEASE -> releaseInternal()
                }
            } catch (e: Exception) {
                Log.e(TAG, "GL thread error", e)
                releaseInternal()
            }
            return true
        }

        private fun initInternal() {
            try {
                eglDisplay = GlUtil.getDefaultEglDisplay()
                eglContext = GlUtil.createEglContext(eglDisplay)
                eglSurface = GlUtil.createEglSurface(eglDisplay, surfaceTexture, C.COLOR_TRANSFER_SDR, false)
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                render.onSurfaceCreated()
                this.sizeChangedInternal(width, height)
            } catch (e: GlUtil.GlException) {
                Log.e(TAG, "Failed to initialize EGL", e)
                throw IllegalStateException("EGL initialization failed", e)
            }
        }

        private fun sizeChangedInternal(width: Int, height: Int) {
            render.onSurfaceChanged(width, height)
        }

        private fun drawInternal(timestampNanos: Long) {
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return
            if (render.onDrawFrame(timestampNanos)) {
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            }
        }

        private fun releaseInternal() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                try {
                    render.onSurfaceDestroyed()
                    GlUtil.destroyEglSurface(eglDisplay, eglSurface)
                    GlUtil.destroyEglContext(eglDisplay, eglContext)
                } catch (e: GlUtil.GlException) {
                    Log.e(TAG, "Failed to release EGL resources", e)
                } finally {
                    eglDisplay = EGL14.EGL_NO_DISPLAY
                    eglContext = EGL14.EGL_NO_CONTEXT
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
            }
            quit()
        }
    }

    private class AssRender(private val assHandler: AssHandler) : Renderer {

        private val vertexShaderCode = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord.xy;
            }
        """.trimIndent()

        // alpha
        private val fragmentShaderCode = """
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform sampler2D u_Texture;
            uniform vec4 u_Color;
            void main() {
                float alpha = texture2D(u_Texture, v_TexCoord).a;
                gl_FragColor = u_Color * alpha;

            }
        """.trimIndent()

        private val rectangleCoords = floatArrayOf(
            -1f,  1f,  // Top left
            1f,  1f,  // Top right
            -1f, -1f,  // Bottom left
            1f, -1f   // Bottom right
        )

        private val textureCoords = floatArrayOf(
            0f, 0f,  // Top left
            1f, 0f,  // Top right
            0f, 1f,  // Bottom left
            1f, 1f   // Bottom right
        )

        private var surfaceDirty = true

        private var surfaceSize = Size.ZERO

        private lateinit var glProgram: GlProgram

        private var vertexBufferId = 0

        private var texCoordBufferId = 0

        override fun onSurfaceCreated() {
            glProgram = GlProgram(vertexShaderCode, fragmentShaderCode)
            GlUtil.checkGlError()

            val vertexBuffer = ByteBuffer.allocateDirect(rectangleCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(rectangleCoords)
            vertexBuffer.position(0)

            val texCordBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureCoords)
            texCordBuffer.position(0)

            val buffers = IntArray(2)
            GLES20.glGenBuffers(2, buffers, 0)
            vertexBufferId = buffers[0]
            texCoordBufferId = buffers[1]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, rectangleCoords.size * 4, vertexBuffer, GLES20.GL_STATIC_DRAW)
            GlUtil.checkGlError()
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, texCoordBufferId)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, textureCoords.size * 4, texCordBuffer, GLES20.GL_STATIC_DRAW)
            GlUtil.checkGlError()
            // ALPHA_8 need set pixel store to 1
            // Or the render result may error or crash
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

            // enable blend
            GLES20.glEnable(GLES20.GL_BLEND);
            // set blend mode
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }

        override fun onSurfaceChanged(width: Int, height: Int) {
            surfaceSize = Size(width, height)
            assHandler.render?.setFrameSize(width, height)
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(timestampNanos: Long): Boolean {
            val assFrame = assHandler.render?.renderFrame(timestampNanos / 1000, AssTexType.TEXTURE)

            // if content not change, just return the tex
            if (assFrame != null && assFrame.changed == 0) {
                return false
            }

            // no content && tex is clean, just return the tex
            if (assFrame == null && !surfaceDirty) {
                return false
            }

            // clear tex content
            GlUtil.clearFocusedBuffers()
            surfaceDirty = false

            // render each frame
            assFrame?.images?.let { frames ->
                surfaceDirty = true
                glProgram.use()
                val aPosition = glProgram.getAttributeArrayLocationAndEnable("a_Position")
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
                GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, 0)
                GlUtil.checkGlError()
                val aTexCoord = glProgram.getAttributeArrayLocationAndEnable("a_TexCoord")
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, texCoordBufferId)
                GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, 0)
                GlUtil.checkGlError()
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

                frames.forEach { frame ->
                    if (frame.tex > 0 ) {
                        val r = frame.color shr 24 and 0xFF
                        val g = frame.color shr 16 and 0xFF
                        val b = frame.color shr 8 and 0xFF
                        val a = 0xFF - frame.color and 0xFF

                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frame.tex)
                        GlUtil.checkGlError()

                        GLES20.glViewport(frame.x, surfaceSize.height - frame.y - frame.h, frame.w, frame.h)
                        GLES20.glUniform4f(glProgram.getUniformLocation("u_Color"), r / 255f, g / 255f, b / 255f, a / 255f)

                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                        GlUtil.checkGlError()

                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                        GlUtil.deleteTexture(frame.tex)
                    }
                }
            }
            GLES20.glViewport(0, 0, surfaceSize.width, surfaceSize.height)
            return true
        }

        override fun onSurfaceDestroyed() {
            GlUtil.deleteBuffer(vertexBufferId)
            GlUtil.deleteBuffer(texCoordBufferId)
            glProgram.delete()
        }

    }
}