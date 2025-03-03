package io.github.peerless2012.ass.media.parser

import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.AssTrack

@OptIn(UnstableApi::class)
class AssSubtitleParser(
    private val assHandler: AssHandler,
    private val track: AssTrack,
/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2/20/25 9:45 PM
 * @Version V1.0
 * @Description
 */
@UnstableApi
abstract class AssSubtitleParser(
    protected val assHandler: AssHandler,
    protected val track: ASSTrack
): SubtitleParser {

    private var surfaceSizeDirty = true

    init {
        updateRenderSize()
        assHandler.onSurfaceSizeChanged {
            surfaceSizeDirty = true
        }
    }

    /**
     * Update frame size
     */
    private fun updateRenderSize() {
        if (surfaceSizeDirty) {
            val surfaceSize = assHandler.surfaceSize
            if (surfaceSize.width > 0 && surfaceSize.height > 0) {
                assHandler.render?.setFrameSize(surfaceSize.width, surfaceSize.height)
            }
            surfaceSizeDirty = false
        }
    }

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>
    ) {
        updateRenderSize()
    }

}