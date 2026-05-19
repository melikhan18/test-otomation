package com.devicefarm.agent.service

import com.devicefarm.agent.capture.ScreenCaptureEngine

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