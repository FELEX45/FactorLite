package com.factorlite.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.utils.Disposable

/**
 * Фоновая музыка (streaming Music).
 *
 * Кладём файл в:
 * - assets/music/bgm.ogg (предпочтительно, работает на Desktop+Android)
 * - либо assets/music/bgm.mp3 (может работать на Android, но на Desktop не всегда)
 */
object Bgm : Disposable {
    var enabled: Boolean = true
    var volume: Float = 0.55f

    private var music: Music? = null
    private var currentKey: String? = null

    fun playDefault() {
        play("bgm")
    }

    fun play(key: String) {
        if (!enabled) return
        if (currentKey == key && music != null) return

        stop()

        val m = loadMusic(key) ?: return
        currentKey = key
        music = m
        m.isLooping = true
        m.volume = volume.coerceIn(0f, 1f)
        m.play()
    }

    fun stop() {
        currentKey = null
        music?.runCatching { stop() }
        music?.runCatching { dispose() }
        music = null
    }

    fun applySettings() {
        val m = music ?: return
        m.volume = if (enabled) volume.coerceIn(0f, 1f) else 0f
    }

    private fun loadMusic(key: String): Music? {
        // Предпочитаем ogg (кроссплатформенно), потом mp3.
        val ogg = Gdx.files.internal("music/$key.ogg")
        if (ogg.exists()) return runCatching { Gdx.audio.newMusic(ogg) }.getOrNull()

        val mp3 = Gdx.files.internal("music/$key.mp3")
        if (mp3.exists()) return runCatching { Gdx.audio.newMusic(mp3) }.getOrNull()

        return null
    }

    override fun dispose() {
        stop()
    }
}

