package com.fortisinnovationlabs.rnfaceblur

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.google.mlkit.vision.face.Face
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val CAMERA_REQUEST_CODE = 1001
private val REQUIRED_PERMISSIONS =
  arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

class RnFaceBlurViewManager(private val reactContext: ReactApplicationContext) :
  SimpleViewManager<FrameLayout>() {

  private lateinit var cameraExecutor: ExecutorService
  private var camera: Camera? = null
  private lateinit var preview: Preview
  private lateinit var previewView: PreviewView
  private var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
  private lateinit var cameraProvider: ProcessCameraProvider
  private lateinit var frameLayout: FrameLayout
  private lateinit var textureView: TextureView
  private lateinit var overlay: Overlay
  private lateinit var videoProcessor: VideoProcessor
  private var latestFaces: List<Face> = emptyList()
  private var videoEncoder: VideoEncoder? = null
  private var isRecording = AtomicBoolean(false)
  private var frameProcessor: ImageAnalysis? = null
  private var faceAnalyzer: ImageAnalysis? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private var outputFile: File? = null

  override fun getName() = "RnFaceBlurView"

  override fun createViewInstance(reactContext: ThemedReactContext): FrameLayout {
    frameLayout = FrameLayout(reactContext)

    previewView = PreviewView(reactContext)
    previewView.layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )

    textureView = TextureView(reactContext)
    textureView.layoutParams = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )

    overlay = Overlay(reactContext)
    val layoutOverlay = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )

    frameLayout.addView(textureView)
    frameLayout.addView(overlay, layoutOverlay)

    cameraExecutor = Executors.newSingleThreadExecutor()
    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startCameraWithPermissionCheck()
      }

      override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        // Handle size changes if needed
      }

      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopCamera()
        cameraProvider.unbindAll()
        return true
      }

      override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Handle texture updates if needed
      }
    }

    return frameLayout
  }

  @ReactProp(name = "color")
  fun setColor(view: FrameLayout, color: String?) {
    color?.let { view.setBackgroundColor(Color.parseColor(it)) }
  }

  private fun checkPermissions(): Boolean {
    val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    return permissions.all {
      ContextCompat.checkSelfPermission(reactContext, it) == PackageManager.PERMISSION_GRANTED
    }
  }

  private fun startCameraWithPermissionCheck() {
    if (checkPermissions()) {
      startCamera()
    } else {
      // Handle permission request or inform the user
      Log.e("RnFaceBlurViewManager", "Permissions for camera and audio are not granted.")
      requestPermissions()
    }
  }

  private fun requestPermissions() {
    val activity = reactContext.currentActivity as? Activity
    if (activity != null) {
      ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, CAMERA_REQUEST_CODE)
    } else {
      Log.e("RnFaceBlurViewManager", "Activity is null, cannot request permissions.")
    }
  }

  private fun startCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(reactContext)
    cameraProviderFuture.addListener({
      try {
        cameraProvider = cameraProviderFuture.get()
        bindCameraUseCases()
      } catch (e: Exception) {
        Log.e("CustomError:", "Use case binding failed", e)
      }
    }, ContextCompat.getMainExecutor(reactContext))
  }

  private fun bindCameraUseCases() {
    val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

    val lifecycleOwner = reactContext.currentActivity as? LifecycleOwner
      ?: throw IllegalStateException("Invalid LifecycleOwner")

    preview = Preview.Builder().build()

    val surfaceProvider = Preview.SurfaceProvider { request ->
      val texture = textureView.surfaceTexture
      val surface = Surface(texture)
      request.provideSurface(surface, ContextCompat.getMainExecutor(reactContext)) { }
    }

    preview.setSurfaceProvider(surfaceProvider)
    videoProcessor = VideoProcessor(textureView.width, textureView.height);

    faceAnalyzer = ImageAnalysis.Builder()
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .build()
      .also {
        it.setAnalyzer(cameraExecutor, FaceAnalyzer(lifecycleOwner.lifecycle, overlay) { faces ->
          latestFaces = faces
        })
      }

    frameProcessor = ImageAnalysis.Builder()
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .build()
      .also {
        it.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
          if (isRecording.get()) {
            try {
              val isFrontFacing = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
              val processedFrame = videoProcessor.processFrame(imageProxy, latestFaces, isFrontFacing)
              Log.d("RnFaceBlurViewManager", "processedFrameWidth: ${processedFrame.width}, height: ${processedFrame.height}");
              videoProcessor.drawToSurface(processedFrame, videoEncoder?.inputSurface)
              videoEncoder?.drainEncoder(false)
              Log.d("RnFaceBlurViewManager", "Frame processed and drawn to encoder surface")
            } catch (e: Exception) {
              Log.e("RnFaceBlurViewManager", "Error processing frame: ${e.message}")
            }
          }
          imageProxy.close()
        })
      }

    try {
      cameraProvider.unbindAll()
      camera = cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        faceAnalyzer,
        frameProcessor
      )

      overlay.setOrientation(reactContext.resources.configuration.orientation)
      overlay.setIsFrontFacing(cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)

    } catch (exc: Exception) {
      Log.e("RnFaceBlurViewManager", "Use case binding failed", exc)
    }
  }

  private fun flipCamera() {
    cameraSelector =
      if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
        CameraSelector.DEFAULT_FRONT_CAMERA
      } else {
        CameraSelector.DEFAULT_BACK_CAMERA
      }
    startCameraWithPermissionCheck()
  }

  private fun startRecording() {
    Log.d("RnFaceBlurViewManager", "startRecording called")
    if (!isRecording.getAndSet(true)) {
      outputFile = File(reactContext.getExternalFilesDir(null), "processed_video.mp4")
      videoEncoder = VideoEncoder(textureView.width, textureView.height, outputFile!!)  // Use fixed dimensions
      Log.d("RnFaceBlurViewManager", "Recording started. Output file: ${outputFile?.absolutePath}")
    } else {
      Log.d("RnFaceBlurViewManager", "Recording was already in progress")
    }
  }

  private fun stopRecording() {
    Log.d("RnFaceBlurViewManager", "stopRecording called")
    if (isRecording.getAndSet(false)) {
      // Add a delay to ensure all frames are processed
      mainHandler.postDelayed({
        try {
          videoEncoder?.stop()
          Log.d("RnFaceBlurViewManager", "Video encoder stopped")

          // Check if the file was created and has content
          outputFile?.let { file ->
            if (file.exists() && file.length() > 0) {
              Log.d("RnFaceBlurViewManager", "Video file created successfully: ${file.absolutePath}")
              Log.d("RnFaceBlurViewManager", "Video file size: ${file.length()} bytes")
            } else {
              Log.e("RnFaceBlurViewManager", "Video file was not created or is empty: ${file.absolutePath}")
            }
          }
        } catch (e: Exception) {
          Log.e("RnFaceBlurViewManager", "Error stopping video encoder: ${e.message}")
          e.printStackTrace()
        } finally {
          videoEncoder = null
          outputFile = null
        }
        Log.d("RnFaceBlurViewManager", "Recording stopped")
      }, 1000) // 1 second delay
    } else {
      Log.d("RnFaceBlurViewManager", "Recording was not in progress")
    }
  }

  private fun stopCamera() {
    Log.d("RnFaceBlurViewManager", "stopCamera called")
    try {
      stopRecording()
      frameProcessor?.clearAnalyzer()
      faceAnalyzer?.clearAnalyzer()
      camera?.cameraControl?.enableTorch(false)
      camera = null
      cameraProvider.unbindAll()
      cameraExecutor.shutdown()
      Log.d("RnFaceBlurViewManager", "Camera stopped and resources released")
    } catch (exc: Exception) {
      Log.e("RnFaceBlurViewManager", "Error stopping camera: ${exc.message}")
    } finally {
      frameProcessor = null
      faceAnalyzer = null
    }
  }

  override fun getCommandsMap(): Map<String, Int> {
    return mapOf(
      "startCamera" to COMMAND_START_RECORDING,
      "stopCamera" to COMMAND_STOP_RECORDING,
      "flipCamera" to COMMAND_FLIP_CAMERA
    )
  }

  override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any>? {
    return MapBuilder.of(
      "topStartCamera",
      MapBuilder.of("registrationName", "onStartCamera"),
      "topStopCamera",
      MapBuilder.of("registrationName", "onStopCamera"),
      "topFlipCamera",
      MapBuilder.of("registrationName", "onFlipCamera")
    )
  }

  override fun receiveCommand(root: FrameLayout, commandId: Int, args: ReadableArray?) {
    when (commandId) {
      COMMAND_START_RECORDING -> {
        startRecording()
      }
      COMMAND_STOP_RECORDING -> stopRecording()
      COMMAND_FLIP_CAMERA -> flipCamera()
    }
  }

  override fun onDropViewInstance(view: FrameLayout) {
    super.onDropViewInstance(view)
    stopCamera()
  }

  companion object {
    private const val COMMAND_START_RECORDING = 1
    private const val COMMAND_STOP_RECORDING = 2
    private const val COMMAND_FLIP_CAMERA = 3
  }
}
