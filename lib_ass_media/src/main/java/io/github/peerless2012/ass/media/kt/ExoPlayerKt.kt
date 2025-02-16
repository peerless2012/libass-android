package io.github.peerless2012.ass.media.kt

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.extractor.AssMatroskaExtractor
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory

@OptIn(UnstableApi::class)
fun ExoPlayer.Builder.buildWithAssSupport(
    dataSourceFactory: DataSource.Factory,
    extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory(),
    useEffectsRenderer: Boolean = true
): ExoPlayer {
    val assHandler = AssHandler(useEffectsRenderer)
    val assSubtitleParserFactory = AssSubtitleParserFactory(assHandler)

    val mediaSourceFactory = DefaultMediaSourceFactory(
        dataSourceFactory,
        extractorsFactory.withAssMkvSupport(assSubtitleParserFactory, assHandler)
    )

    val player = this
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    assHandler.init(player)
    return player
}

@OptIn(UnstableApi::class)
fun ExtractorsFactory.withAssMkvSupport(
    assSubtitleParserFactory: AssSubtitleParserFactory,
    assHandler: AssHandler
): ExtractorsFactory {
    return ExtractorsFactory {
        createExtractors()
            .filter { it !is MatroskaExtractor }
            .plus(AssMatroskaExtractor(assSubtitleParserFactory, assHandler))
            .toTypedArray()
    }
}
