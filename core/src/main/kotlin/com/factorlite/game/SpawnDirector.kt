package com.factorlite.game

import com.badlogic.gdx.math.MathUtils

/**
 * Минимальный директор спавна:
 * хранит таймер и выдаёт "сколько врагов надо заспавнить" за кадр.
 *
 * Это позволяет держать логику темпа отдельно от конкретной реализации spawnEnemy().
 */
class SpawnDirector(
    private val maxSpawnsPerFrame: Int = 10,
) {
    private var spawnTimer = 0f

    fun reset(initialDelay: Float = 0.2f) {
        spawnTimer = initialDelay
    }

    fun update(delta: Float, runTime: Float, aliveEnemies: Int): Int {
        // Базовый темп: интервал от 0.95 сек к ~0.14 сек
        val minInterval = 0.14f
        val maxInterval = 0.95f
        val t = (runTime / 160f).coerceIn(0f, 1f)
        val baseInterval = MathUtils.lerp(maxInterval, minInterval, t)

        // Мягкий “pressure” при толпе: не cap, а замедление темпа.
        val crowd = (aliveEnemies / 160f).coerceAtLeast(0f)
        val crowdMul = (1f + crowd).coerceAtMost(3.8f)
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

