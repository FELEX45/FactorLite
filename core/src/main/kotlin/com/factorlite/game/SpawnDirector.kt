package com.factorlite.game

import com.badlogic.gdx.math.MathUtils

/**
 * Минимальный директор спавна:
 * хранит таймер и выдаёт "сколько врагов надо заспавнить" за кадр.
 *
 * Это позволяет держать логику темпа отдельно от конкретной реализации spawnEnemy().
 */
class SpawnDirector(
    private val maxSpawnsPerFrame: Int = 6,
) {
    private var spawnTimer = 0f

    fun reset(initialDelay: Float = 0.2f) {
        spawnTimer = initialDelay
    }

    fun update(delta: Float, runTime: Float, aliveEnemies: Int): Int {
        // Базовый темп: интервал от 1.2 сек к ~0.25 сек
        val minInterval = 0.25f
        val maxInterval = 1.2f
        val t = (runTime / 90f).coerceIn(0f, 1f)
        val baseInterval = MathUtils.lerp(maxInterval, minInterval, t)

        // Мягкий “pressure” при толпе: не cap, а замедление темпа.
        val crowd = (aliveEnemies / 120f).coerceAtLeast(0f)
        val crowdMul = (1f + crowd).coerceAtMost(4.5f)
        val interval = baseInterval * crowdMul

        spawnTimer -= delta
        var spawns = 0
        while (spawnTimer <= 0f && spawns < maxSpawnsPerFrame) {
            spawnTimer += interval
            spawns++
        }
        return spawns
    }
}

