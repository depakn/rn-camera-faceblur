package com.fortisinnovationlabs.rnfaceblur

import android.graphics.*
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.Face
import android.view.Surface
import java.io.ByteArrayOutputStream

class VideoProcessor(width: Int, height: Int) {
  private var previewWidth: Int = width
  private var previewHeight: Int = height
  private val paint = Paint().apply {
    color = Color.YELLOW
    style = Paint.Style.STROKE
    strokeWidth = 2f
  }

  fun processFrame(image: ImageProxy, faces: List<Face>, isFrontFacing: Boolean): Bitmap {
    val bitmap = image.toBitmap()
    val angle = if (isFrontFacing) -90f else 90f
    val rotatedBitmap = rotateImage(bitmap, angle)
    val canvas = Canvas(rotatedBitmap)

    val scaleX = rotatedBitmap.width.toFloat() / image.height.toFloat()
    val scaleY = rotatedBitmap.height.toFloat() / image.width.toFloat()

    Log.d("VideoProcessor: ", "w: ${image.width}, h: ${image.height}")

    faces.forEach { face ->
      val bounds = face.boundingBox

      val left = scaleX * bounds.left.toFloat()
      val top = scaleY * bounds.top.toFloat()
      val right = scaleX * bounds.right.toFloat()
      val bottom = scaleY * bounds.bottom.toFloat()

      canvas.drawRect(left, top, right, bottom, paint)
    }

    return rotatedBitmap
  }

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

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, previewWidth, previewHeight, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, previewWidth, previewHeight), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
  }

  private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
  }
}
