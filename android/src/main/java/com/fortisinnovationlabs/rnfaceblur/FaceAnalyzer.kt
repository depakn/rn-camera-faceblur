package com.fortisinnovationlabs.rnfaceblur

import android.annotation.SuppressLint
import android.media.Image
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(lifecycle: Lifecycle, private val overlay: Overlay, private val onFacesDetected: (List<Face>) -> Unit) : ImageAnalysis.Analyzer, LifecycleObserver {
  private val options = FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
    .setMinFaceSize(0.15f)
    .build()
  private val detector = FaceDetection.getClient(options)

  init {
    lifecycle.addObserver(this)
  }

  override fun analyze(imageProxy: ImageProxy) {
    overlay.setPreviewSize(Size(imageProxy.width, imageProxy.height))

    Log.d("FaceAnalyzer: ", "w: ${imageProxy.width}, h: ${imageProxy.height}")
    detectFaces(imageProxy)
  }

  private val successListener = OnSuccessListener<List<Face>> { faces ->
    overlay.setFaces(faces)
    onFacesDetected(faces)
  }

  private val failureListener = OnFailureListener { e ->
    Log.e(TAG, "Face analysis failure.", e)
  }

  @SuppressLint("UnsafeOptInUsageError")
  private fun detectFaces(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
      val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
      detector.process(image)
        .addOnSuccessListener(successListener)
        .addOnFailureListener(failureListener)
        .addOnCompleteListener {
          imageProxy.close()
        }
    }
  }

  companion object {
    private const val TAG = "FaceAnalyzer"
  }
}
