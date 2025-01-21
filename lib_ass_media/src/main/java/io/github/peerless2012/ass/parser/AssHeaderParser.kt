package io.github.peerless2012.ass.parser

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
object AssHeaderParser {
    fun parse(format: Format): ByteArray {
        val header1 = format.initializationData[0].decodeToString()
        assert(header1.startsWith("Format:"))

        val header2 = format.initializationData[1].decodeToString()

        val lines = header2.lines().toMutableList()
        val index = lines.indexOfFirst {
            it.startsWith("[Events]")
        }
        if (index >= 0 && lines[index + 1].startsWith("Format:")) {
            lines[index + 1] = header1
        }
        val result = lines.joinToString(separator = "\n")
        return result.toByteArray()
    }
}
