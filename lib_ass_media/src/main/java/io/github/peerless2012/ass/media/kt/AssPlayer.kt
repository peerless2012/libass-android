package io.github.peerless2012.ass.media.kt

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.extractor.AssMatroskaExtractor
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType

@OptIn(UnstableApi::class)
fun ExoPlayer.Builder.buildWithAssSupport(
    context: Context,
    renderType: AssRenderType = AssRenderType.LEGACY,
    dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context),
    extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory()
): ExoPlayer {
    val assHandler = AssHandler(renderType)
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
