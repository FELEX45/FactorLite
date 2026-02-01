package com.factorlite.game

import com.badlogic.gdx.math.MathUtils
import com.factorlite.content.Balance
import kotlin.math.pow

/**
 * Минимальный директор спавна:
 * хранит таймер и выдаёт "сколько врагов надо заспавнить" за кадр.
 *
 * Это позволяет держать логику темпа отдельно от конкретной реализации spawnEnemy().
 */
class SpawnDirector {
    private var spawnTimer = 0f

    fun reset(initialDelay: Float = 0.2f) {
        spawnTimer = initialDelay
    }

    fun update(delta: Float, runTime: Float, aliveEnemies: Int, intervalMul: Float = 1f): Int {
        val sb = Balance.cfg.spawning.spawnDirector
        // Базовый темп: интервал от 0.95 сек к ~0.14 сек
        val t0 = (runTime / sb.rampSeconds).coerceIn(0f, 1f)
        // rampPow > 1 делает старт мягче (медленнее рост), rampPow < 1 — агрессивнее.
        val t = t0.pow(sb.rampPow.coerceAtLeast(0.05f))
        val baseInterval = MathUtils.lerp(sb.maxInterval, sb.minInterval, t)

        // Мягкий “pressure” при толпе: не cap, а замедление темпа.
        val crowd = (aliveEnemies / sb.crowdDiv).coerceAtLeast(0f)
        val crowdMul = (1f + crowd).coerceAtMost(sb.crowdMulCap)
        // Мягкий старт: в начале спавним реже.
        val startMul = if (sb.startSoftSeconds > 0.01f && runTime < sb.startSoftSeconds) {
            val tt = (runTime / sb.startSoftSeconds).coerceIn(0f, 1f)
            MathUtils.lerp(sb.startSoftMul, 1f, tt)
        } else {
            1f
        }

        // Эндгейм-спайк: перед боссом спавним чаще.
        val endMul = if (runTime >= sb.endSpikeStartSec && sb.endSpikeRampSeconds > 0.01f) {
            val tt = ((runTime - sb.endSpikeStartSec) / sb.endSpikeRampSeconds).coerceIn(0f, 1f)
            MathUtils.lerp(1f, sb.endSpikeMul, tt)
        } else if (runTime >= sb.endSpikeStartSec) {
            sb.endSpikeMul
        } else {
            1f
        }

        val interval = baseInterval * crowdMul * startMul * endMul * intervalMul.coerceIn(0.5f, 3.0f)

        spawnTimer -= delta
        var spawns = 0
        while (spawnTimer <= 0f && spawns < sb.maxSpawnsPerFrame) {
            spawnTimer += interval
            spawns++
        }
        return spawns
    }
}

