package com.fortisinnovationlabs.rnfaceblur

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VideoEncoder(
  private val width: Int,
  private val height: Int,
  private val outputFile: File
) {
  private val encoder: MediaCodec
  private val muxer: MediaMuxer
  private var trackIndex: Int = -1
  private var muxerStarted = false
  private val isRunning = AtomicBoolean(true)
  private var frameCount = 0
  val inputSurface: Surface

  init {
    Log.d("VideoEncoder", "Initializing VideoEncoder with width: $width, height: $height")
    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
    format.setInteger(MediaFormat.KEY_BIT_RATE, 10000000)
    format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

    encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    inputSurface = encoder.createInputSurface()

    muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    encoder.start()
  }

  fun drainEncoder(endOfStream: Boolean) {
    val TIMEOUT_USEC = 10000L
    if (endOfStream) {
      Log.d("VideoEncoder", "Signaling end of input stream")
      encoder.signalEndOfInputStream()
    }

    while (true) {
      val bufferInfo = MediaCodec.BufferInfo()
      val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
      if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
        if (!endOfStream) {
          break
        }
        Log.d("VideoEncoder", "No output available, spinning to await EOS")
      } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        if (muxerStarted) {
          throw RuntimeException("Format changed twice")
        }
        val newFormat = encoder.getOutputFormat()
        Log.d("VideoEncoder", "Encoder output format changed: $newFormat")
        trackIndex = muxer.addTrack(newFormat)
        muxer.start()
        muxerStarted = true
        Log.d("VideoEncoder", "Muxer started")
      } else if (outputBufferIndex < 0) {
        Log.w("VideoEncoder", "Unexpected result from encoder.dequeueOutputBuffer: $outputBufferIndex")
      } else {
        val encodedData = encoder.getOutputBuffer(outputBufferIndex)
        if (encodedData != null) {
          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            bufferInfo.size = 0
          }

          if (bufferInfo.size != 0) {
            if (!muxerStarted) {
              throw RuntimeException("muxer hasn't started")
            }

            encodedData.position(bufferInfo.offset)
            encodedData.limit(bufferInfo.offset + bufferInfo.size)
            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
            frameCount++
            Log.d("VideoEncoder", "Encoded frame written to muxer. Total frames: $frameCount")
          }

          encoder.releaseOutputBuffer(outputBufferIndex, false)

          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.d("VideoEncoder", "End of stream reached")
            break
          }
        }
      }
    }
  }

  fun stop() {
    Log.d("VideoEncoder", "Stopping VideoEncoder")
    if (isRunning.getAndSet(false)) {
      try {
        drainEncoder(true)
        encoder.stop()
        encoder.release()
        muxer.stop()
        muxer.release()
        Log.d("VideoEncoder", "Encoder stopped and released")
        Log.d("VideoEncoder", "Video saved to: ${outputFile.absolutePath}")
        Log.d("VideoEncoder", "Total frames processed: $frameCount")
        if (outputFile.exists()) {
          Log.d("VideoEncoder", "Output file size: ${outputFile.length()} bytes")
        } else {
          Log.e("VideoEncoder", "Output file does not exist!")
        }
      } catch (e: Exception) {
        Log.e("VideoEncoder", "Error stopping encoder: ${e.message}")
        e.printStackTrace()
      }
    }
  }
}
