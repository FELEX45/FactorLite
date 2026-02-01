package com.factorlite.game

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.factorlite.content.Balance
import kotlin.math.max

class EnemySystem {
    /**
     * Обновляет движение врагов + стрельбу дальников.
     * Стрельба делается через callback, чтобы не зависеть от CombatSystem.
     */
    fun updateEnemies(
        delta: Float,
        runTime: Float,
        difficultyMul: Float,
        enemySpeedMul: Float,
        playerPos: Vector2,
        arenaHalfW: Float,
        arenaHalfH: Float,
        enemies: List<Enemy>,
        spawnEnemyShot: (pos: Vector2, dir: Vector2, damage: Float, speed: Float, radius: Float) -> Unit,
    ) {
        val rb = Balance.cfg.spawning.enemy.rangedCombat
        val baseMul = enemySpeedMul.coerceIn(0.35f, 1.25f)
        for (e in enemies) {
            if (e.hp <= 0f) continue
            val spMul = (baseMul * e.slowMul.coerceIn(0.35f, 1f)).coerceIn(0.20f, 1.25f)
            val toPlayer = Vector2(playerPos.x - e.pos.x, playerPos.y - e.pos.y)
            val dist2 = toPlayer.len2()

            // Движение: дальник держит дистанцию, остальные прут в контакт
            val desiredMin = if (e.kind == EnemyKind.RANGED) rb.desiredMinDist else 0f
            val desiredMax = if (e.kind == EnemyKind.RANGED) rb.desiredMaxDist else Float.POSITIVE_INFINITY
            val move = when {
                e.kind != EnemyKind.RANGED -> true
                dist2 < desiredMin * desiredMin -> true // отходим
                dist2 > desiredMax * desiredMax -> true // подходим
                else -> false
            }
            if (move && !toPlayer.isZero(0.0001f)) {
                // Если слишком близко — уходим от игрока
                val dir = if (e.kind == EnemyKind.RANGED && dist2 < desiredMin * desiredMin) toPlayer.scl(-1f) else toPlayer
                dir.nor().scl(e.speed * spMul)
                e.pos.mulAdd(dir, delta)
            }

            // Важно: враги не должны уходить "за карту" (игрок туда не может).
            // Держим их в пределах арены с небольшим запасом по радиусу.
            val m = e.radius + 2f
            e.pos.x = MathUtils.clamp(e.pos.x, -arenaHalfW + m, arenaHalfW - m)
            e.pos.y = MathUtils.clamp(e.pos.y, -arenaHalfH + m, arenaHalfH - m)

            // Стрельба дальника
            if (e.kind == EnemyKind.RANGED) {
                // Замедление должно влиять и на темп стрельбы (логично ощущается как "слоу").
                e.shootCooldown = max(0f, e.shootCooldown - delta * spMul)
                val shootRange = rb.shootRange
                if (e.shootCooldown <= 0f && dist2 <= shootRange * shootRange) {
                    fireEnemyShot(e, runTime, difficultyMul, playerPos, spawnEnemyShot)
                }
            }
        }
    }

    private fun fireEnemyShot(
        e: Enemy,
        runTime: Float,
        difficultyMul: Float,
        playerPos: Vector2,
        spawnEnemyShot: (pos: Vector2, dir: Vector2, damage: Float, speed: Float, radius: Float) -> Unit,
    ) {
        val rb = Balance.cfg.spawning.enemy.rangedCombat
        val dir = Vector2(playerPos.x - e.pos.x, playerPos.y - e.pos.y)
        if (dir.isZero(0.0001f)) return
        dir.nor()

        val speed = rb.projectileSpeed
        val damage = rb.baseDamage * (if (rb.scaleDamageWithDifficulty) difficultyMul else 1f)
        spawnEnemyShot(Vector2(e.pos.x, e.pos.y), dir, damage, speed, rb.projectileRadius)

        // Темп стрельбы: чуть ускоряется со временем
        val baseCd = rb.baseCooldownSec
        val mul = (1f + (runTime / rb.cooldownTimeRampSeconds)).coerceAtMost(rb.cooldownMulCap)
        e.shootCooldown = baseCd / mul

        // Небольшой разброс выстрелов у элитки (чуть хаотичнее)
        if (e.isElite && MathUtils.random() < rb.eliteExtraShotChance) {
            e.shootCooldown *= rb.eliteCooldownMul
        }
    }
}

