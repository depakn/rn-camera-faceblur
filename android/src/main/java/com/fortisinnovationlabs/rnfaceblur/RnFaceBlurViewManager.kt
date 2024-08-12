package com.fortisinnovationlabs.rnfaceblur

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val CAMERA_REQUEST_CODE = 1001
private val REQUIRED_PERMISSIONS =
  arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

class RnFaceBlurViewManager(private val reactContext: ReactApplicationContext) :
  SimpleViewManager<FrameLayout>() {

  private lateinit var cameraExecutor: ExecutorService
  private var camera: Camera? = null
  private var videoCapture: VideoCapture<Recorder>? = null
  private var recording: Recording? = null
  private lateinit var preview: Preview
  private lateinit var cameraSelector: CameraSelector
  private lateinit var cameraProvider: ProcessCameraProvider
  private lateinit var frameLayout: FrameLayout
  private lateinit var previewView: PreviewView
  private lateinit var textureView: TextureView
  private lateinit var overlay: Overlay

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

    val recorder = Recorder.Builder()
      .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
      .build()
    videoCapture = VideoCapture.withOutput(recorder)

    val imageAnalyzer = ImageAnalysis.Builder()
      .build()
      .also {
        it.setAnalyzer(cameraExecutor, FaceAnalyzer(lifecycleOwner.lifecycle, overlay))
      }

    try {
      cameraProvider.unbindAll()
      camera = cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        videoCapture,
        imageAnalyzer
      )

      // Update the overlay with the correct orientation and camera facing
      overlay.setOrientation(reactContext.resources.configuration.orientation)
      overlay.setIsFrontFacing(cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)

    } catch (exc: Exception) {
      Log.e("CustomError:", "Use case binding failed", exc)
    }
  }

  private fun stopCamera() {
    recording?.stop()
    recording = null
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
    val videoCapture = this.videoCapture ?: return

    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
      .format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, name)
      put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
      }
    }

    val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
      reactContext.contentResolver,
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    )
      .setContentValues(contentValues)
      .build()

    recording = videoCapture.output
      .prepareRecording(reactContext, mediaStoreOutputOptions)
      .apply {
        if (ContextCompat.checkSelfPermission(
            reactContext,
            Manifest.permission.RECORD_AUDIO
          ) == PackageManager.PERMISSION_GRANTED
        ) {
          withAudioEnabled()
        }
      }
      .start(ContextCompat.getMainExecutor(reactContext)) { recordEvent ->
        when (recordEvent) {
          is VideoRecordEvent.Start -> {
            // Handle start of recording
          }
          is VideoRecordEvent.Finalize -> {
            if (!recordEvent.hasError()) {
              // Video capture succeeded and saved to gallery
              Log.d("RnFaceBlurViewManager", "Video capture succeeded: ${recordEvent.outputResults.outputUri}")
            } else {
              // Video capture failed
              Log.e("RnFaceBlurViewManager", "Video capture failed: ${recordEvent.error}")
              recording?.close()
              recording = null
            }
          }
        }
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
      COMMAND_STOP_RECORDING -> stopCamera()
      COMMAND_FLIP_CAMERA -> flipCamera()
    }
  }

  companion object {
    private const val COMMAND_START_RECORDING = 1
    private const val COMMAND_STOP_RECORDING = 2
    private const val COMMAND_FLIP_CAMERA = 3
  }
}
