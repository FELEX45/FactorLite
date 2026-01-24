package com.factorlite.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.factorlite.FactorLiteGame

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("FactorLite")
        setWindowedMode(960, 540)
        useVsync(true)
        setForegroundFPS(60)
    }

    Lwjgl3Application(FactorLiteGame(), config)
}

