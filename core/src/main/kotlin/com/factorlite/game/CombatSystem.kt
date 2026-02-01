package com.factorlite.game

import com.badlogic.gdx.math.Vector2
import com.factorlite.content.Balance
import com.factorlite.progression.WeaponKind

/**
 * Минимальный CombatSystem:
 * - хранит и обновляет снаряды игрока и врагов
 * - применяет попадания
 *
 * Остальная логика (спавн снарядов, урон по контакту, таргетинг) пока остаётся сверху.
 */
class CombatSystem {
    data class Projectile(
        val pos: Vector2,
        val vel: Vector2,
        val damage: Float,
        val radius: Float = 4f,
        val source: WeaponKind,
        var pierceLeft: Int = 0,
        var ricochetLeft: Int = 0,
    )

    data class EnemyProjectile(
        val pos: Vector2,
        val vel: Vector2,
        val damage: Float,
        val radius: Float = 4f,
    )

    val projectiles: MutableList<Projectile> = ArrayList()
    val enemyProjectiles: MutableList<EnemyProjectile> = ArrayList()

    fun reset() {
        projectiles.clear()
        enemyProjectiles.clear()
    }

    fun spawnProjectile(p: Projectile) {
        projectiles.add(p)
    }

    fun spawnEnemyProjectile(p: EnemyProjectile) {
        enemyProjectiles.add(p)
    }

    fun <E> updatePlayerProjectiles(
        delta: Float,
        arenaHalfW: Float,
        arenaHalfH: Float,
        enemies: List<E>,
        isAlive: (E) -> Boolean,
        getPos: (E) -> Vector2,
        getRadius: (E) -> Float,
        damageEnemy: (E, Float) -> Unit,
        onEnemyHit: (enemy: E, source: WeaponKind, damage: Float) -> Unit,
        findNearestEnemyExcluding: (exclude: E, fromX: Float, fromY: Float, maxRange2: Float) -> E?,
    ) {
        val margin = Balance.cfg.combat.projectiles.playerArenaMargin
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.pos.mulAdd(p.vel, delta)

            // Выход за арену — удаляем
            if (p.pos.x < -arenaHalfW - margin || p.pos.x > arenaHalfW + margin || p.pos.y < -arenaHalfH - margin || p.pos.y > arenaHalfH + margin) {
                it.remove()
                continue
            }

            // Попадание по врагу
            for (e in enemies) {
                if (!isAlive(e)) continue
                val ep = getPos(e)
                val dx = ep.x - p.pos.x
                val dy = ep.y - p.pos.y
                val r = getRadius(e) + p.radius
                if (dx * dx + dy * dy <= r * r) {
                    damageEnemy(e, p.damage)
                    onEnemyHit(e, p.source, p.damage)

                    // Пробивание
                    if (p.pierceLeft > 0) {
                        p.pierceLeft -= 1
                        break
                    }

                    // Рикошет
                    if (p.ricochetLeft > 0) {
                        p.ricochetLeft -= 1
                        val cfg = Balance.cfg.combat.projectiles
                        val maxRange = cfg.ricochetMaxAcquireRange
                        val maxRange2 = maxRange * maxRange
                        val next = findNearestEnemyExcluding(e, p.pos.x, p.pos.y, maxRange2)
                        if (next != null) {
                            val np = getPos(next)
                            val dir = Vector2(np.x - p.pos.x, np.y - p.pos.y)
                            if (!dir.isZero(0.0001f)) {
                                val speed = p.vel.len().coerceAtLeast(cfg.ricochetMinSpeed)
                                p.vel.set(dir.nor().scl(speed))
                                break
                            }
                        }
                        it.remove()
                        break
                    }

                    it.remove()
                    break
                }
            }
        }
    }

    fun updateEnemyProjectiles(
        delta: Float,
        arenaHalfW: Float,
        arenaHalfH: Float,
        playerPos: Vector2,
        playerRadius: Float,
        canDamagePlayer: Boolean,
        onHitPlayer: (damage: Float) -> Boolean, // true -> applied damage; false -> blocked/ignored
    ) {
        if (enemyProjectiles.isEmpty()) return
        val margin = Balance.cfg.combat.projectiles.enemyArenaMargin
        val it = enemyProjectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.pos.mulAdd(p.vel, delta)

            // Выход за арену — удаляем
            if (p.pos.x < -arenaHalfW - margin || p.pos.x > arenaHalfW + margin || p.pos.y < -arenaHalfH - margin || p.pos.y > arenaHalfH + margin) {
                it.remove()
                continue
            }

            // Попадание по игроку
            val dx = p.pos.x - playerPos.x
            val dy = p.pos.y - playerPos.y
            val r = p.radius + playerRadius
            if (dx * dx + dy * dy <= r * r) {
                if (canDamagePlayer) {
                    onHitPlayer(p.damage)
                }
                it.remove()
            }
        }
    }
}

