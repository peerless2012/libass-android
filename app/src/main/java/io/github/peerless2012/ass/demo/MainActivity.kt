package io.github.peerless2012.ass.demo

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.google.android.material.appbar.MaterialToolbar
import com.google.common.collect.ImmutableList
import io.github.peerless2012.ass.media.kt.buildWithAssSupport
import io.github.peerless2012.ass.media.type.AssRenderType

class MainActivity : AppCompatActivity() {

    private var url = "http://192.168.0.11:8080/files/f.mp4"

    private var subtitle = "http://192.168.0.11:8080/files/test-subs-layer.ass"

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
        val toolbar = findViewById<MaterialToolbar>(R.id.main_toolbar)
        setSupportActionBar(toolbar)


        player = ExoPlayer.Builder(this)
            .buildWithAssSupport(
                dataSourceFactory = DefaultDataSource.Factory(this),
                extractorsFactory = DefaultExtractorsFactory(),
                renderType = AssRenderType.CANVAS
            )
        playerView = findViewById(R.id.main_player)
        playerView.player = player
        val config = MediaItem.SubtitleConfiguration
            .Builder(Uri.parse(subtitle))
            .setMimeType("text/x-ssa")
            .setLanguage("en")
            .setId("100")
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setSubtitleConfigurations(ImmutableList.of(config))
        player.setMediaItem(mediaItem.build())
        player.prepare()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.menu_url-> switchUrl()
            R.id.menu_audio -> selectTrack(androidx.media3.common.C.TRACK_TYPE_AUDIO)
            R.id.menu_sub -> selectTrack(androidx.media3.common.C.TRACK_TYPE_TEXT)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun switchUrl() {
        val urlInput = EditText(this).also {
            it.setText(url)
        }
        AlertDialog.Builder(this)
            .setTitle("Set url")
            .setPositiveButton("Confirm"
            ) { dialog, which ->
                url = urlInput.text.toString()
                player.stop()
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
            }
            .setNegativeButton("Cancel", null)
            .setView(urlInput)
            .create()
            .show()
    }

    @OptIn(UnstableApi::class)
    private fun selectTrack(trackType: Int) {
        TrackSelectionDialogBuilder(this, "Select track", player, trackType)
            .setShowDisableOption(true)
            .build()
            .show()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

}