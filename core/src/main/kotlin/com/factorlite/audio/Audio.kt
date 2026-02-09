package com.factorlite.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Минимальный аудио-менеджер для демо:
 * - пытается играть реальные файлы из assets/sfx/
 * - если файлов нет, играет короткие синтезированные "бипы" (чтобы звук был даже без ассетов)
 *
 * Поддерживаемые форматы: ogg/wav (внутренние ассеты libGDX).
 */
object Audio : Disposable {
    var enabled: Boolean = true
    var sfxVolume: Float = 0.8f

    private var inited = false
    private val cache = HashMap<String, Sound?>()

    // Синтез: один поток + один AudioDevice, чтобы не блокировать render().
    private val exec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FactorLite-Audio").apply { isDaemon = true }
    }
    private var device: AudioDevice? = null

    fun init() {
        if (inited) return
        inited = true
    }

    fun uiClick() = play("ui_click", fallback = { beep(980f, 0.030f, 0.35f) })
    fun pause() = play("pause", fallback = { beep(220f, 0.060f, 0.40f) })
    fun resume() = play("resume", fallback = { beep(330f, 0.050f, 0.40f) })
    fun levelUp() = play("levelup", fallback = { arpeggio(floatArrayOf(660f, 880f, 990f), 0.045f, 0.40f) })
    fun chestOpen() = play("chest_open", fallback = { arpeggio(floatArrayOf(440f, 660f), 0.055f, 0.45f) })
    fun kill() = play("kill", fallback = { beep(520f, 0.040f, 0.30f) })
    fun playerHit() = play("player_hit", fallback = { beep(140f, 0.055f, 0.55f) })
    fun victory() = play("victory", fallback = { arpeggio(floatArrayOf(523f, 659f, 784f), 0.060f, 0.45f) })
    fun gameOver() = play("gameover", fallback = { arpeggio(floatArrayOf(392f, 311f, 262f), 0.070f, 0.45f) })

    private fun play(name: String, fallback: () -> Unit) {
        if (!enabled) return
        val v = sfxVolume.coerceIn(0f, 1f)
        if (v <= 0.001f) return

        val s = getSound(name)
        if (s != null) {
            s.play(v)
        } else {
            fallback()
        }
    }

    private fun getSound(name: String): Sound? {
        val cached = cache[name]
        if (cached != null || cache.containsKey(name)) return cached

        fun tryLoad(path: String): Sound? {
            val fh = Gdx.files.internal(path)
            if (!fh.exists()) return null
            return runCatching { Gdx.audio.newSound(fh) }.getOrNull()
        }

        val sound = tryLoad("sfx/$name.ogg") ?: tryLoad("sfx/$name.wav")
        cache[name] = sound
        return sound
    }

    private fun beep(freqHz: Float, durSec: Float, gain: Float) {
        val sr = 44100
        val n = (durSec * sr).toInt().coerceAtLeast(1)
        val buf = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / sr.toFloat()
            // плавное затухание, чтобы не щёлкало
            val env = (1f - (i.toFloat() / n.toFloat())).coerceIn(0f, 1f)
            buf[i] = (sin(2.0 * PI * freqHz * t).toFloat()) * env * gain
        }
        write(buf)
    }

    private fun arpeggio(freqs: FloatArray, stepSec: Float, gain: Float) {
        for (f in freqs) beep(f, stepSec, gain)
    }

    private fun write(samples: FloatArray) {
        exec.execute {
            try {
                val dev = device ?: Gdx.audio.newAudioDevice(44100, true).also { device = it }
                dev.writeSamples(samples, 0, samples.size)
            } catch (_: Throwable) {
                // если аудио устройство недоступно — просто молчим
            }
        }
    }

    override fun dispose() {
        for (s in cache.values) {
            try { s?.dispose() } catch (_: Throwable) {}
        }
        cache.clear()
        try { device?.dispose() } catch (_: Throwable) {}
        device = null
        exec.shutdownNow()
    }
}

