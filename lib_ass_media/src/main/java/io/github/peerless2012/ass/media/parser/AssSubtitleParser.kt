package io.github.peerless2012.ass.media.parser

import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.AssTrack

@UnstableApi
abstract class AssSubtitleParser(
    protected val assHandler: AssHandler,
    protected val track: AssTrack
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