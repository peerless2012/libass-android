package io.github.peerless2012.ass.media.render

import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.TextureOverlay
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.executor.AssExecutor
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class AssTexOverlay(private val handler: AssHandler, private val render: AssRender) : TextureOverlay() {

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

    private val preFbo = IntArray(1)

    private val preViewPort = IntArray(4)

    private val preTex = IntArray(1)

    private val preAlign = IntArray(1)

    private var texId = 0

    private var texDirty = true

    private var texSize = Size.ZERO

    private var fboId = 0

    private lateinit var glProgram: GlProgram

    private var vertexBufferId = 0

    private var texCoordBufferId = 0

    private lateinit var executor: AssExecutor

    override fun getTextureId(presentationTimeUs: Long): Int {
        val timeUs = if (handler.videoTime >= 0) {
            handler.videoTime
        } else {
            presentationTimeUs
        }
        val assFrame = executor.renderFrame(timeUs)

        // if content not change, just return the tex
        if (assFrame != null && assFrame.changed == 0) {
            return texId
        }

        // no content && tex is clean, just return the tex
        if (assFrame == null && !texDirty) {
            return texId
        }

        // save the pre params
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, preFbo, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, preViewPort, 0);
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, preTex, 0)
        GLES20.glGetIntegerv(GLES20.GL_UNPACK_ALIGNMENT, preAlign, 0)

        // use fbo
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texId, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GlUtil.checkGlError()

        // clear tex content
        GlUtil.clearFocusedBuffers()
        texDirty = false

        // enable blend
        GLES20.glEnable(GLES20.GL_BLEND);
        // set blend mode
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // render each frame
        assFrame?.images?.let { frames ->
            texDirty = true
            // ALPHA_8 need set pixel store to 1
            // Or the render result may error or crash
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            val preProgram = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, preProgram, 0)
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
                frame.bitmap?.let { bitmap ->
                    val r = frame.color shr 24 and 0xFF
                    val g = frame.color shr 16 and 0xFF
                    val b = frame.color shr 8 and 0xFF
                    val a = 0xFF - frame.color and 0xFF

                    val txt = GlUtil.createTexture(bitmap)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, txt)
                    GlUtil.checkGlError()

                    GLES20.glViewport(frame.x, texSize.height - bitmap.height - frame.y, bitmap.width, bitmap.height)
                    GLES20.glUniform4f(glProgram.getUniformLocation("u_Color"), r / 255f, g / 255f, b / 255f, a / 255f)

                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                    GlUtil.checkGlError()

                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                    GlUtil.deleteTexture(txt)
                }
            }
            GLES20.glUseProgram(preProgram[0])
        }

        // restore params
        GLES20.glViewport(preViewPort[0], preViewPort[1], preViewPort[2], preViewPort[3])
        GLES20.glActiveTexture(preTex[0])
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, preFbo[0])
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, preAlign[0])

        return texId
    }

    override fun getTextureSize(presentationTimeUs: Long): Size {
        return texSize
    }

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        this.texSize = videoSize
        executor = AssExecutor(render)
        render.setFrameSize(videoSize.width, videoSize.height)
        texId = GlUtil.createTexture(videoSize.width, videoSize.height, false)
        fboId = GlUtil.createFboForTexture(texId)
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

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    override fun release() {
        GlUtil.deleteFbo(fboId)
        GlUtil.deleteTexture(texId)
        GlUtil.deleteBuffer(vertexBufferId)
        GlUtil.deleteBuffer(texCoordBufferId)
        glProgram.delete()
        executor.shutdown()
        super.release()
    }
}