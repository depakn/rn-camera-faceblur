package com.fortisinnovationlabs.rnfaceblur

import android.graphics.*
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.Face
import android.view.Surface
import java.io.ByteArrayOutputStream

class VideoProcessor(private val width: Int, private val height: Int) {
  private val paint = Paint().apply {
    color = Color.YELLOW
    style = Paint.Style.STROKE
    strokeWidth = 5f
  }

  fun processFrame(image: ImageProxy, faces: List<Face>): Bitmap {
    val bitmap = image.toBitmap()
    val rotatedBitmap = rotateImage(bitmap, -90f)
    val canvas = Canvas(rotatedBitmap)

    val scaleX = rotatedBitmap.width.toFloat() / image.width.toFloat()
    val scaleY = rotatedBitmap.height.toFloat() / image.height.toFloat()

    faces.forEach { face ->
      val bounds = face.boundingBox

      val left = bounds.left.toFloat() // Dont change
      val top = bounds.top * scaleY // Dont change
      val right = bounds.right.toFloat() + bounds.left.toFloat() // Needs change
      val bottom = bounds.bottom.toFloat() +bounds.top.toFloat() // Needs change

      canvas.drawRect(left, top, right, bottom, paint)

      Log.d("VideoProcessor: ", "bl: ${rotatedBitmap.width}, br: ${bounds.right}")
    }

    return rotatedBitmap
  }

  fun drawToSurface(bitmap: Bitmap, surface: Surface?) {
    surface?.let {
      val canvas = it.lockCanvas(null)
      canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
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
