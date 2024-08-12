package com.fortisinnovationlabs.rnfaceblur

import android.graphics.*
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.face.Face
import java.io.ByteArrayOutputStream
import android.view.Surface

class VideoProcessor(private val width: Int, private val height: Int) {
  private val paint = Paint().apply {
    color = Color.YELLOW
    style = Paint.Style.STROKE
    strokeWidth = 5f
  }

  fun processFrame(image: ImageProxy, faces: List<Face>): Bitmap {
    val bitmap = image.toBitmap()
    val canvas = Canvas(bitmap)

    // Draw bounding boxes
    faces.forEach { face ->
      val bounds = face.boundingBox
      canvas.drawRect(bounds, paint)
    }

    return bitmap
  }

  fun drawToSurface(bitmap: Bitmap, surface: Surface?) {
    surface?.let {
      val canvas = it.lockCanvas(null)
      canvas.drawBitmap(bitmap, 0f, 0f, null)
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
}
