package io.github.peerless2012.ass.media.render

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream

/**
 * Wraps the player's `MediaCodecVideoRenderer` to report the stream changes that decide the
 * timestamps its video effects receive to [mapper].
 */
@UnstableApi
internal class AssVideoRendererWrapper(
    renderer: Renderer,
    private val mapper: AssFrameTimeMapper
) : ForwardingRenderer(renderer) {

    override fun enable(
        configuration: RendererConfiguration,
        formats: Array<out Format>,
        stream: SampleStream,
        positionUs: Long,
        joining: Boolean,
        mayRenderStartOfStream: Boolean,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId
    ) {
        mapper.onEnabled(startPositionUs, offsetUs)
        super.enable(
            configuration,
            formats,
            stream,
            positionUs,
            joining,
            mayRenderStartOfStream,
            startPositionUs,
            offsetUs,
            mediaPeriodId
        )
    }

    override fun replaceStream(
        formats: Array<out Format>,
        stream: SampleStream,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId
    ) {
        mapper.onStreamReplaced(startPositionUs, offsetUs)
        super.replaceStream(formats, stream, startPositionUs, offsetUs, mediaPeriodId)
    }

    override fun resetPosition(positionUs: Long, sampleStreamIsResetToKeyFrame: Boolean) {
        mapper.onPositionReset()
        super.resetPosition(positionUs, sampleStreamIsResetToKeyFrame)
    }

    override fun reset() {
        mapper.onReset()
        super.reset()
    }
}
