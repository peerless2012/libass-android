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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.google.android.material.appbar.MaterialToolbar
import com.google.common.collect.ImmutableList
import io.github.peerless2012.ass.media.kt.buildWithAssSupport
import io.github.peerless2012.ass.media.type.AssRenderType
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private var url = "http://192.168.3.6:8080/files/c.mkv"

    private lateinit var player: ExoPlayer

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
        playerView = findViewById(R.id.main_player)

        player = ExoPlayer.Builder(this)
            .buildWithAssSupport(
                this,
                AssRenderType.OVERLAY_OPEN_GL,
                playerView.subtitleView
            )
        playerView.player = player
        val enConfig = MediaItem.SubtitleConfiguration
            .Builder(Uri.parse("http://192.168.3.6:8080/files/e.ass"))
            .setMimeType(MimeTypes.TEXT_SSA)
            .setLanguage("en")
            .setLabel("External ass en")
            .setId("129")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val jpConfig = MediaItem.SubtitleConfiguration
            .Builder(Uri.parse("http://192.168.3.6:8080/files/f-jp.ass"))
            .setMimeType(MimeTypes.TEXT_SSA)
            .setLanguage("jp")
            .setLabel("External ass jp")
            .setId("130")
            .build()
        val zhConfig = MediaItem.SubtitleConfiguration
            .Builder(Uri.parse("http://192.168.3.6:8080/files/f-zh.ass"))
            .setMimeType(MimeTypes.TEXT_SSA)
            .setLanguage("zh")
            .setLabel("External ass zh")
            .setId("121")
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(url)
//            .setSubtitleConfigurations(ImmutableList.of(enConfig, jpConfig, zhConfig))
        player.setMediaItem(mediaItem.build(), 30*1000)
        player.prepare()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    @OptIn(UnstableApi::class)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.menu_gc-> System.gc()
            R.id.menu_url-> switchUrl()
            R.id.menu_audio -> selectTrack(C.TRACK_TYPE_AUDIO)
            R.id.menu_sub -> selectTrack(C.TRACK_TYPE_TEXT)
            R.id.menu_sub_add -> {
                player.currentMediaItem?.let {
                    val url = "http://192.168.199.138:8080/files/f-d.ass"
                    val preSubtitleConfigurations = it.localConfiguration?.subtitleConfigurations
                    if (preSubtitleConfigurations == null || preSubtitleConfigurations.find { it.uri.toString() == url } == null) {
                        val dynamicConfig = MediaItem.SubtitleConfiguration
                            .Builder(url.toUri())
                            .setMimeType(MimeTypes.TEXT_SSA)
                            .setLanguage("zh-en")
                            .setLabel("External dynamic ass")
                            .setId("199")
                            .build()
                        val subtitleConfigurations = if (preSubtitleConfigurations == null) {
                            ImmutableList.of(dynamicConfig)
                        } else {
                            ImmutableList.of(dynamicConfig) + preSubtitleConfigurations
                        }
                        val newMediaItem = it.buildUpon().setSubtitleConfigurations(subtitleConfigurations).build()
                        player.setMediaItem(newMediaItem, false)
                    }
                }
            }
            R.id.menu_resize_fit -> playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            R.id.menu_resize_crop -> playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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