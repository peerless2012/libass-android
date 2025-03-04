package io.github.peerless2012.ass.media

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.MimeTypes.TEXT_SSA
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.AssTrack
import io.github.peerless2012.ass.Ass
import io.github.peerless2012.ass.media.common.ROLE_FLAG_EXTERNAL_SUBTITLES
import io.github.peerless2012.ass.media.parser.AssHeaderParser
import io.github.peerless2012.ass.media.render.AssOverlayManager
import io.github.peerless2012.ass.media.type.AssRenderType

/**
 * Handles ASS subtitle rendering and integration with ExoPlayer.
 *
 * This class listens to ExoPlayer events and manages the creation, selection, and rendering of ASS
 * subtitle tracks.
 * @param renderType The subtitle render type.
 */
@OptIn(UnstableApi::class)
class AssHandler(val renderType: AssRenderType) : Listener {

    /** The ASS instance used for creating tracks and renderers. This is lazy to avoid loading
     * libass if the played media does not have ASS tracks. */
    val ass by lazy { Ass() }

    /** The current ASS renderer. It's created as soon as a ASS track is detected. */
    var render: AssRender? = null
        private set

    /** The currently selected ASS track. */
    var track: AssTrack? = null
        private set

    /** The available ASS tracks in the current media. */
    private val availableTracks = mutableMapOf<String, AssTrack>()

    /** The size of the video track. */
    private var videoSize = Size.ZERO

    /** The size of the surface on which subtitles are rendered. */
    var surfaceSize = Size.ZERO
        private set

    /** Callback for listening to surface size changes. */
    private var surfaceSizeCallback: ((Size) -> Unit)? = null

    /** The overlay manager for toggling the effects renderer. */
    private var overlayManager: AssOverlayManager? = null

    /**
     * Initializes the handler with the provided ExoPlayer instance.
     * @param player The ExoPlayer instance to attach to.
     */
    fun init(player: ExoPlayer) {
        player.addListener(this)
        if (renderType != AssRenderType.LEGACY) {
            overlayManager = AssOverlayManager(player, renderType == AssRenderType.OPEN_GL)
        }
    }

    /**
     * Handles transitions between media items in the player and resets everything to the initial
     * state.
     */
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        Log.i("AssHandler", "onMediaItemTransition: item = ${mediaItem}, reason = $reason")
        render = null
        track = null
        availableTracks.clear()
        videoSize = Size.ZERO
        overlayManager?.disable()
    }

    /**
     * Handles changes to the tracks available in the current media.
     * Configures the selected ASS track if available.
     * @param tracks The selected tracks.
     */
    override fun onTracksChanged(tracks: Tracks) {
        Log.i("AssHandler", "onTracksChanged $tracks")
        val selectedVideoTrack = getSelectedVideoTrack(tracks)
        if (selectedVideoTrack != null) {
            setVideoSize(selectedVideoTrack.width, selectedVideoTrack.height)
        }

        val selectedAssTrack = getSelectedAssTrack(tracks)
        if (selectedAssTrack == null) {
            Log.i("AssHandler", "subtitle track disabled")
            track = null
            render?.setTrack(null)
            overlayManager?.disable()
            return
        }

        val track = availableTracks.firstNotNullOfOrNull {
            // When media without external subtitles, format id will not change.
            // When media with external subtitles, format will become like 1:1 .
            // So to compat both situation, we just use endsWith.
            if (selectedAssTrack.id!!.endsWith(it.key)) {
                it.value
            } else {
                null
            }
        }
        if (track == null || this.track == track) return

        Log.i("AssHandler", "subtitle track changed to $selectedAssTrack")
        this.track = track
        val render = requireNotNull(render)
        render.setStorageSize(videoSize.width, videoSize.height)
        render.setTrack(track)
        overlayManager?.enable(render)
    }

    /**
     * Handles changes to the surface size for video playback.
     * Notifies the callback if the size has changed.
     * @param width The new width of the surface.
     * @param height The new height of the surface.
     */
    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        super.onSurfaceSizeChanged(width, height)
        Log.i("AssHandler", "onSurfaceSizeChanged: width = $width, height = $height")
        if (surfaceSize.width == width && surfaceSize.height == height) return
        surfaceSize = Size(width, height)
        surfaceSizeCallback?.invoke(surfaceSize)
    }

    /**
     * Sets a callback to handle changes to the surface size.
     * @param callback The callback function to invoke when the surface size changes.
     */
    fun onSurfaceSizeChanged(callback: (Size) -> Unit) {
        this.surfaceSizeCallback = callback
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        this.videoSize = Size(videoSize.width, videoSize.height)
        Log.i("AssHandler", "onVideoSizeChanged: width = ${videoSize.width}, height = ${videoSize.height}")
    }

    /**
     * Updates the video size for the ASS renderer. Called as soon as the video size is known in
     * order to properly render subtitles.
     * @param width The width of the video.
     * @param height The height of the video.
     */
    fun setVideoSize(width: Int, height: Int) {
        Log.i("AssHandler", "setVideoSize: width = $width, height = $height")
        videoSize = Size(width, height)
    }

    /**
     * Returns true if the current media has ASS tracks, false otherwise.
     */
    fun hasTracks(): Boolean {
        return availableTracks.isNotEmpty()
    }

    /**
     * Creates a new ASS track from the given format and saves it in the [availableTracks].
     * The renderer and libass are also created if needed.
     * @param format The format of the ASS track.
     * @return The created ASS track.
     */
    fun createTrack(format: Format): AssTrack {
        Log.i("AssHandler", "createTrack: format = $format")
        // Ensure the renderer is created before creating tracks.
        createRenderIfNeeded()

        val track = ass.createTrack()
        if (format.initializationData.size > 0) {
            val header = AssHeaderParser.parse(format, renderType != AssRenderType.LEGACY)
            track.readBuffer(header)
        }
        availableTracks[format.id!!] = track

        return track
    }

    /**
     * Ensures the ASS renderer is created if it does not already exist.
     */
    private fun createRenderIfNeeded() {
        if (render != null) return
        render = ass.createRender().also { render ->
            if (videoSize.isValid) {
                render.setStorageSize(videoSize.width, videoSize.height)
            }
            if (surfaceSize.isValid) {
                render.setFrameSize(surfaceSize.width, surfaceSize.height)
            }
        }
    }

    /**
     * Reads a dialogue into the track of the given [trackId].
     * TODO This should move to executor.
     */
    fun readTrackDialogue(
        trackId: String?,
        start: Long,
        duration: Long,
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size
    ) {
        availableTracks[trackId]?.readChunk(start, duration, data, offset, length)
    }

    /**
     * Retrieves the selected video track, if any.
     */
    private fun getSelectedVideoTrack(tracks: Tracks): Format? {
        return tracks.groups.find { group ->
            if (group.isSelected) {
                (0 until group.length).any { index ->
                    val track = group.getTrackFormat(index)
                    MimeTypes.isVideo(track.sampleMimeType)
                }
            } else {
                false
            }
        }?.getTrackFormat(0)
    }

    /**
     * Retrieves the ID of the selected ASS track, if any.
     * @param tracks The selected tracks.
     * @return The ID of the selected ASS track, or null if none.
     */
    private fun getSelectedAssTrack(tracks: Tracks): Format? {
        return tracks.groups.find { group ->
            if (group.isSelected) {
                (0 until group.length).any { index ->
                    val track = group.getTrackFormat(index)
                    track.sampleMimeType == TEXT_SSA || track.codecs == TEXT_SSA
                }
            } else {
                false
            }
        }?.getTrackFormat(0)
    }

    /**
     * Checks if the size is valid (both width and height are greater than 0).
     */
    private val Size.isValid
        get() = width > 0 && height > 0
}