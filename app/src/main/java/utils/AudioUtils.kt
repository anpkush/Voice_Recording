package com.bmdu.voicerecorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

object AudioUtils {

    fun trimAudio(inputPath: String, outputPath: String, startMs: Int, endMs: Int) {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            val trackCount = extractor.trackCount
            var audioTrackIndex = -1
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex < 0) return

            extractor.selectTrack(audioTrackIndex)

            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val dstIndex = muxer.addTrack(format)

            muxer.start()

            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val bufferSize = 1024 * 1024
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                } else {
                    val sampleTime = extractor.sampleTime
                    if (sampleTime > endMs * 1000L) break
                    bufferInfo.presentationTimeUs = sampleTime
                    bufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME
                    muxer.writeSampleData(dstIndex, buffer, bufferInfo)
                    extractor.advance()
                }
            }

            muxer.stop()
            muxer.release()
            extractor.release()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
