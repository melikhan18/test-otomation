package com.qaplatform.android.agent.service

import com.qaplatform.android.agent.capture.ScreenCaptureEngine

object CaptureBridge {

    @Volatile
    private var engine: ScreenCaptureEngine? = null

    fun bindEngine(engine: ScreenCaptureEngine?) {
        this.engine = engine
    }

    fun requestKeyframe() {
        engine?.requestKeyframe()
    }

    fun stop() {
        engine?.stop()
    }
}