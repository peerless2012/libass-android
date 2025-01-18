package io.github.peerless2012.ass.demo

import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import io.github.peerless2012.ass.AssKeeper
import io.github.peerless2012.ass.extractor.withAssMkvSupport
import io.github.peerless2012.ass.factory.AssRenderFactory
import io.github.peerless2012.ass.factory.AssSubtitleParserFactory
import okhttp3.OkHttpClient


class MainActivity : AppCompatActivity() {

//    private val url = "http://192.168.0.254:8096/Videos/f5eff7c7-53de-684c-36cd-f4c7cefc99e3/stream?static=true&mediaSourceId=f5eff7c753de684c36cdf4c7cefc99e3&streamOptions=%7B%7D"
    private val url = "http://192.168.0.26:8080/files/c.mkv"

    private lateinit var player:ExoPlayer

    private lateinit var playerView: PlayerView

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<Button>(R.id.main_track).setOnClickListener {
            selectTrack()
        }
        val okHttpClient = OkHttpClient.Builder()
            .build()

        playerView = findViewById(R.id.main_player)

        val assKeeper = AssKeeper()
        val assSubtitleParserFactory = AssSubtitleParserFactory(assKeeper)
        val mediaFactory = DefaultMediaSourceFactory(
            OkHttpDataSource.Factory(okHttpClient),
            DefaultExtractorsFactory().withAssMkvSupport(assSubtitleParserFactory, assKeeper)
        ).setSubtitleParserFactory(assSubtitleParserFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaFactory)
            .setRenderersFactory(AssRenderFactory(baseContext))
            .build()
        player.addListener(assKeeper)
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }

    @OptIn(UnstableApi::class)
    private fun selectTrack() {
        TrackSelectionDialogBuilder(this, "aa", player, androidx.media3.common.C.TRACK_TYPE_TEXT).build().show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}