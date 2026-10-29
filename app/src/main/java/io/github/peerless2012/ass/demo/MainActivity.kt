package io.github.peerless2012.ass.demo

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import io.github.peerless2012.ass.media.AssHandlerConfig

class MainActivity : AppCompatActivity() {

    private var url = "http://192.168.3.6:8080/files/c.mkv"

    private var renderType = AssRenderType.EFFECTS_ATLAS

    private lateinit var player: ExoPlayer

    private lateinit var playerView: PlayerView

    private lateinit var droppedFramesView: TextView

    private val droppedFramesUpdater = object : Runnable {
        @OptIn(UnstableApi::class)
        override fun run() {
            val counters = player.videoDecoderCounters
            counters?.ensureUpdated()
            droppedFramesView.text = "Dropped frames: ${counters?.droppedBufferCount ?: 0}"
            droppedFramesView.postDelayed(this, 500)
        }
    }

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            playUrl(uri.toString())
        }
    }

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

        var startPosition = 0*1000L
        if (savedInstanceState != null) {
            url = savedInstanceState.getString(KEY_URL, url)
            renderType = AssRenderType.valueOf(savedInstanceState.getString(KEY_RENDER_TYPE, renderType.name))
            startPosition = savedInstanceState.getLong(KEY_POSITION)
        }

        player = ExoPlayer.Builder(this)
            .buildWithAssSupport(
                this,
                renderType,
                AssHandlerConfig(maxRenderPixels = 720*480),
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
        player.setMediaItem(mediaItem.build(), startPosition)
        player.prepare()

        droppedFramesView = findViewById(R.id.main_dropped_frames)
        droppedFramesView.post(droppedFramesUpdater)
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
            R.id.menu_open_file -> openFileLauncher.launch(arrayOf("video/*"))
            R.id.menu_render_type -> selectRenderType()
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
                playUrl(urlInput.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .setView(urlInput)
            .create()
            .show()
    }

    private fun selectRenderType() {
        val renderTypes = AssRenderType.entries
        AlertDialog.Builder(this)
            .setTitle("Render type")
            .setSingleChoiceItems(renderTypes.map { it.name }.toTypedArray(), renderTypes.indexOf(renderType)) { dialog, which ->
                dialog.dismiss()
                if (renderTypes[which] != renderType) {
                    // The render type is fixed when the player is built, so recreate the activity to rebuild it.
                    renderType = renderTypes[which]
                    recreate()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun playUrl(newUrl: String) {
        url = newUrl
        player.stop()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }

    @OptIn(UnstableApi::class)
    private fun selectTrack(trackType: Int) {
        TrackSelectionDialogBuilder(this, "Select track", player, trackType)
            .setShowDisableOption(true)
            .build()
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_URL, url)
        outState.putString(KEY_RENDER_TYPE, renderType.name)
        outState.putLong(KEY_POSITION, player.currentPosition)
    }

    override fun onDestroy() {
        droppedFramesView.removeCallbacks(droppedFramesUpdater)
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_RENDER_TYPE = "render_type"
        private const val KEY_POSITION = "position"
    }

}