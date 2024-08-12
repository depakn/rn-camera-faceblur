package com.fortisinnovationlabs.rnfaceblur

import android.graphics.Color
import android.util.Log
import android.widget.FrameLayout
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp

class RnFaceBlurViewManager(private val reactContext: ReactApplicationContext) :
        ViewGroupManager<FrameLayout>() {

  override fun getName() = REACT_CLASS

  override fun createViewInstance(p0: ThemedReactContext): FrameLayout {
    return FrameLayout(reactContext)
  }

  @ReactProp(name = "color")
  fun setColor(view: FrameLayout, color: String?) {
    color?.let { view.setBackgroundColor(Color.parseColor(it)) }
  }

  override fun getCommandsMap(): Map<String, Int> {
    return mapOf(
      "startCamera" to COMMAND_START_CAMERA,
      "stopCamera" to COMMAND_STOP_CAMERA,
      "flipCamera" to COMMAND_FLIP_CAMERA
    )
  }

  override fun receiveCommand(root: FrameLayout, commandId: Int, args: ReadableArray?) {
    when (commandId) {
      COMMAND_START_CAMERA -> startRecording()
      COMMAND_STOP_CAMERA -> stopRecording()
      COMMAND_FLIP_CAMERA -> flipCamera()
    }
  }

  private fun startRecording() {
    Log.d("RNEvents", "Starting Recording")
  }

  private fun stopRecording() {
    Log.d("RNEvents", "Stop Recording")
  }

  private fun flipCamera() {
    Log.d("RNEvents", "Flip Camera")
  }

  companion object {
    private const val REACT_CLASS = "RnFaceBlurView"
    private const val COMMAND_START_CAMERA = 1
    private const val COMMAND_STOP_CAMERA = 2
    private const val COMMAND_FLIP_CAMERA = 3
  }
}
