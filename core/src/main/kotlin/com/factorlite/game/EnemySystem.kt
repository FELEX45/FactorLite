package com.factorlite.game

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import kotlin.math.max

class EnemySystem {
    /**
     * Обновляет движение врагов + стрельбу дальников.
     * Стрельба делается через callback, чтобы не зависеть от CombatSystem.
     */
    fun updateEnemies(
        delta: Float,
        runTime: Float,
        playerPos: Vector2,
        enemies: List<Enemy>,
        spawnEnemyShot: (pos: Vector2, dir: Vector2, damage: Float, speed: Float, radius: Float) -> Unit,
    ) {
        for (e in enemies) {
            if (e.hp <= 0f) continue
            val toPlayer = Vector2(playerPos.x - e.pos.x, playerPos.y - e.pos.y)
            val dist2 = toPlayer.len2()

            // Движение: дальник держит дистанцию, остальные прут в контакт
            val desiredMin = if (e.kind == EnemyKind.RANGED) 240f else 0f
            val desiredMax = if (e.kind == EnemyKind.RANGED) 420f else Float.POSITIVE_INFINITY
            val move = when {
                e.kind != EnemyKind.RANGED -> true
                dist2 < desiredMin * desiredMin -> true // отходим
                dist2 > desiredMax * desiredMax -> true // подходим
                else -> false
            }
            if (move && !toPlayer.isZero(0.0001f)) {
                // Если слишком близко — уходим от игрока
                val dir = if (e.kind == EnemyKind.RANGED && dist2 < desiredMin * desiredMin) toPlayer.scl(-1f) else toPlayer
                dir.nor().scl(e.speed)
                e.pos.mulAdd(dir, delta)
            }

            // Стрельба дальника
            if (e.kind == EnemyKind.RANGED) {
                e.shootCooldown = max(0f, e.shootCooldown - delta)
                val shootRange = 520f
                if (e.shootCooldown <= 0f && dist2 <= shootRange * shootRange) {
                    fireEnemyShot(e, runTime, playerPos, spawnEnemyShot)
                }
            }
        }
    }

    private fun fireEnemyShot(
        e: Enemy,
        runTime: Float,
        playerPos: Vector2,
        spawnEnemyShot: (pos: Vector2, dir: Vector2, damage: Float, speed: Float, radius: Float) -> Unit,
    ) {
        val dir = Vector2(playerPos.x - e.pos.x, playerPos.y - e.pos.y)
        if (dir.isZero(0.0001f)) return
        dir.nor()

        val speed = 420f
        val damage = 9f
        spawnEnemyShot(Vector2(e.pos.x, e.pos.y), dir, damage, speed, 5f)

        // Темп стрельбы: чуть ускоряется со временем
        val baseCd = 1.35f
        val mul = (1f + (runTime / 160f)).coerceAtMost(1.7f)
        e.shootCooldown = baseCd / mul

        // Небольшой разброс выстрелов у элитки (чуть хаотичнее)
        if (e.isElite && MathUtils.random() < 0.12f) {
            e.shootCooldown *= 0.85f
        }
    }
}

