package io.github.peerless2012.ass.media.extractor

import androidx.annotation.OptIn
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.mkv.EbmlProcessor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.text.AssSubtitleExtractorOutput
import io.github.peerless2012.ass.media.type.AssRenderType

@OptIn(UnstableApi::class)
open class AssMatroskaExtractor(
    subtitleParserFactory: SubtitleParser.Factory,
    private val assHandler: AssHandler,
    flags: Int = 0
) : MatroskaExtractor(subtitleParserFactory, flags) {

    private var currentAttachmentName: String? = null
    private var currentAttachmentMime: String? = null
    private var currentAttachmentData: ByteArray? = null

    internal val subtitleSample = subtitleSampleField.get(this) as ParsableByteArray

    override fun getElementType(id: Int): Int {
        return when (id) {
            ID_ATTACHMENTS -> EbmlProcessor.ELEMENT_TYPE_MASTER
            ID_ATTACHED_FILE -> EbmlProcessor.ELEMENT_TYPE_MASTER
            ID_FILE_NAME -> EbmlProcessor.ELEMENT_TYPE_STRING
            ID_FILE_MIME_TYPE -> EbmlProcessor.ELEMENT_TYPE_STRING
            ID_FILE_DATA -> EbmlProcessor.ELEMENT_TYPE_BINARY
            else -> super.getElementType(id)
        }
    }

    override fun isLevel1Element(id: Int): Boolean {
        return super.isLevel1Element(id) || id == ID_ATTACHMENTS
    }

    override fun startMasterElement(id: Int, contentPosition: Long, contentSize: Long) {
        when (id) {
            ID_EBML -> {
                if (assHandler.renderType != AssRenderType.CUES) {
                    val currentExtractor = extractorOutput.get(this) as ExtractorOutput
                    if (currentExtractor !is AssSubtitleExtractorOutput) {
                        extractorOutput.set(
                            this,
                            AssSubtitleExtractorOutput(currentExtractor, assHandler, this)
                        )
                    }
                }
                super.startMasterElement(id, contentPosition, contentSize)
            }
            ID_ATTACHED_FILE -> clearAttachment()
            else -> super.startMasterElement(id, contentPosition, contentSize)
        }
    }

    override fun endMasterElement(id: Int) {
        when (id) {
            ID_VIDEO -> {
                // We need to get the video dimensions very early
                val track = getCurrentTrack(id)
                assHandler.setVideoSize(track.width, track.height)
                super.endMasterElement(id)
            }
            ID_ATTACHED_FILE -> {
                val attachmentName = requireNotNull(currentAttachmentName)
                val attachmentMime = requireNotNull(currentAttachmentMime)
                val attachmentData = requireNotNull(currentAttachmentData)

                // Detect fonts like the spec says: https://github.com/ietf-wg-cellar/matroska-specification/blob/1a943ead9bb4c67b02b966cebafe51c8c074e703/cellar-matroska/attachments.md?plain=1#L87-L111
                val isFont = attachmentMime in fontMimeTypes ||
                    (attachmentMime == "application/octet-stream" &&
                        fontExtensions.any { attachmentName.lowercase().endsWith(it) })
                if (isFont) {
                    assHandler.addFont(attachmentName, attachmentData)
                }
                clearAttachment()
            }
            else -> super.endMasterElement(id)
        }
    }

    override fun stringElement(id: Int, value: String) {
        when (id) {
            ID_FILE_NAME -> currentAttachmentName = value
            ID_FILE_MIME_TYPE -> currentAttachmentMime = value
            else -> super.stringElement(id, value)
        }
    }

    override fun binaryElement(id: Int, contentSize: Int, input: ExtractorInput) {
        when (id) {
            ID_FILE_DATA -> {
                currentAttachmentData = ByteArray(contentSize)
                input.readFully(currentAttachmentData!!, 0, contentSize)
            }
            else -> super.binaryElement(id, contentSize, input)
        }
    }

    private fun clearAttachment() {
        currentAttachmentName = null
        currentAttachmentMime = null
        currentAttachmentData = null
    }

    companion object {
        const val ID_EBML = 0x1A45DFA3
        const val ID_VIDEO = 0xE0
        const val ID_ATTACHMENTS = 0x1941A469
        const val ID_ATTACHED_FILE = 0x61A7
        const val ID_FILE_NAME = 0x466E
        const val ID_FILE_MIME_TYPE = 0x4660
        const val ID_FILE_DATA = 0x465C

        val fontMimeTypes = listOf(
            "font/ttf",
            "font/otf",
            "font/sfnt",
            "font/woff",
            "font/woff2",
            "application/font-sfnt",
            "application/font-woff",
            "application/x-truetype-font",
            "application/vnd.ms-opentype",
            "application/x-font-ttf",
        )

        val fontExtensions = listOf(".ttf", ".otf", ".ttc", ".otc")

        val extractorOutput = MatroskaExtractor::class.java.getDeclaredField("extractorOutput").apply {
            isAccessible = true
        }
        val subtitleSampleField = MatroskaExtractor::class.java.getDeclaredField("subtitleSample").apply {
            isAccessible = true
        }
    }
}
