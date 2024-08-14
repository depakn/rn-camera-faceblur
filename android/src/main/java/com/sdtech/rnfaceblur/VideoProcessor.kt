package com.sdtech.rnfaceblur

import android.graphics.*
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.Face
import android.view.Surface
import java.io.ByteArrayOutputStream

class VideoProcessor(width: Int, height: Int) {
  private var previewWidth: Int = width
  private var previewHeight: Int = height

  init {
    System.loadLibrary("main")
  }

  fun processFrame(image: ImageProxy, faces: List<Face>, isFrontFacing: Boolean): Bitmap {
    val bitmap = image.toBitmap()
    val angle = if (isFrontFacing) -90f else 90f
    val rotatedBitmap = rotateImage(bitmap, angle)

    val scaleX = rotatedBitmap.width.toFloat() / image.height.toFloat()
    val scaleY = rotatedBitmap.height.toFloat() / image.width.toFloat()

    Log.d("VideoProcessor", "w: ${image.width}, h: ${image.height}")

    // Create a mutable copy of the rotated bitmap
    val mutableBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    faces.forEach { face ->
      val bounds = face.boundingBox

      val left = (scaleX * bounds.left.toFloat()).toInt().coerceAtLeast(0)
      val top = (scaleY * bounds.top.toFloat()).toInt().coerceAtLeast(0)
      val right = (scaleX * bounds.right.toFloat()).toInt().coerceAtMost(mutableBitmap.width)
      val bottom = (scaleY * bounds.bottom.toFloat()).toInt().coerceAtMost(mutableBitmap.height)

      val width = right - left
      val height = bottom - top

      if (width > 0 && height > 0) {
        // Extract the face region
        val faceBitmap = Bitmap.createBitmap(mutableBitmap, left, top, width, height)

        // Apply blur to the face region
        val blurredFace = blurBitmap(faceBitmap)

        // Draw the blurred face back onto the canvas
        canvas.drawBitmap(blurredFace, left.toFloat(), top.toFloat(), null)
      }
    }

    return mutableBitmap
  }

  private fun blurBitmap(source: Bitmap): Bitmap {
    val scaleFactor = 0.25f
    val radius = 10

    // Downscale
    val width = (source.width * scaleFactor).toInt()
    val height = (source.height * scaleFactor).toInt()
    val scaledBitmap = Bitmap.createScaledBitmap(source, width, height, true)

    // Blur
    val pixels = IntArray(width * height)
    scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    nativeStackBlur(pixels, width, height, radius)
    scaledBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

    // Upscale
    return Bitmap.createScaledBitmap(scaledBitmap, source.width, source.height, true)
  }

  private external fun nativeStackBlur(pix: IntArray, w: Int, h: Int, radius: Int)

  fun drawToSurface(bitmap: Bitmap, surface: Surface?) {
    surface?.let {
      val canvas = it.lockCanvas(null)
      canvas.drawBitmap(bitmap, null, Rect(0, 0, previewWidth, previewHeight), null)
      it.unlockCanvasAndPost(canvas)
    }
  }

  private fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
  }

  private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
  }
}
