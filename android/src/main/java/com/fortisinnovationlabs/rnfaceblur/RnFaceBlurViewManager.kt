package com.fortisinnovationlabs.rnfaceblur

import android.graphics.Color
import android.widget.FrameLayout
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.annotations.ReactProp

class RnFaceBlurViewManager(private val reactContext: ReactApplicationContext) {

  @ReactProp(name = "color")
  fun setColor(view: FrameLayout, color: String?) {
    color?.let { view.setBackgroundColor(Color.parseColor(it)) }
  }
}
