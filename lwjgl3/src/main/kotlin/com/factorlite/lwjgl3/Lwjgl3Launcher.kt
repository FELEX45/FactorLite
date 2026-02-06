package com.factorlite.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.factorlite.FactorLiteGame
import kotlin.math.floor
import kotlin.math.min

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("FactorLite")
        // Портретное окно под телефонный формат. Цель — 1080x1920,
        // но на мониторах 1440p по высоте (и с панелью задач/рамками) оно физически не влезает,
        // поэтому Windows зажимает окно до доступной высоты. Здесь делаем предсказуемо:
        // пропорционально уменьшаем, чтобы всегда помещалось.
        val targetW = 1080
        val targetH = 1920
        val dm = Lwjgl3ApplicationConfiguration.getDisplayMode()
        val margin = 96 // запас под рамки/панель задач
        val maxW = (dm.width - margin).coerceAtLeast(640)
        val maxH = (dm.height - margin).coerceAtLeast(640)
        val scale = min(maxW.toFloat() / targetW.toFloat(), maxH.toFloat() / targetH.toFloat()).coerceAtMost(1f)
        val w = floor(targetW * scale).toInt().coerceAtLeast(360)
        val h = floor(targetH * scale).toInt().coerceAtLeast(640)
        setWindowedMode(w, h)
        useVsync(true)
        setForegroundFPS(60)
    }

    Lwjgl3Application(FactorLiteGame(), config)
}

