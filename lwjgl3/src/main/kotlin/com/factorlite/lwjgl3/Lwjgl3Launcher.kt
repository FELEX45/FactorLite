package com.factorlite.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.factorlite.FactorLiteGame

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("FactorLite")
        // Портретное окно под телефонный формат (9:16). Для 9:21 можно поставить 540x1260.
        setWindowedMode(540, 960)
        useVsync(true)
        setForegroundFPS(60)
    }

    Lwjgl3Application(FactorLiteGame(), config)
}

