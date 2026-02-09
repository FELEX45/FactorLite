package com.factorlite.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Простые настройки аудио (сохранение в Preferences).
 */
object AudioSettings {
    private val prefs: Preferences by lazy { Gdx.app.getPreferences("factorlite_prefs") }

    private const val KEY_SFX_ENABLED = "sfxEnabled"
    private const val KEY_MUSIC_ENABLED = "musicEnabled"
    private const val KEY_SFX_VOL = "sfxVol"
    private const val KEY_MUSIC_VOL = "musicVol"

    fun loadAndApply() {
        Audio.enabled = prefs.getBoolean(KEY_SFX_ENABLED, true)
        Bgm.enabled = prefs.getBoolean(KEY_MUSIC_ENABLED, true)
        Audio.sfxVolume = prefs.getFloat(KEY_SFX_VOL, Audio.sfxVolume).coerceIn(0f, 1f)
        Bgm.volume = prefs.getFloat(KEY_MUSIC_VOL, Bgm.volume).coerceIn(0f, 1f)
        Bgm.applySettings()
    }

    fun save() {
        prefs.putBoolean(KEY_SFX_ENABLED, Audio.enabled)
        prefs.putBoolean(KEY_MUSIC_ENABLED, Bgm.enabled)
        prefs.putFloat(KEY_SFX_VOL, Audio.sfxVolume.coerceIn(0f, 1f))
        prefs.putFloat(KEY_MUSIC_VOL, Bgm.volume.coerceIn(0f, 1f))
        prefs.flush()
    }
}

