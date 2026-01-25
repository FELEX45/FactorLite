package com.factorlite.game

import com.badlogic.gdx.math.Vector2
import kotlin.math.max

class TargetingSystem(
    range: Float = 520f,
    private val retargetInterval: Float = 0.12f,
    private val hysteresisMul: Float = 0.8f, // переключаемся только если новая цель заметно ближе
) {
    var range: Float = range
        set(value) {
            field = value.coerceAtLeast(60f)
        }

    var target: Enemy? = null
        private set

    private var retargetTimer = 0f

    fun reset() {
        target = null
        retargetTimer = 0f
    }

    fun update(delta: Float, playerPos: Vector2, enemies: List<Enemy>) {
        val range2 = range * range

        // Если текущая цель ещё валидна — держим её, пока не убежала далеко.
        target?.let { t ->
            val dx = t.pos.x - playerPos.x
            val dy = t.pos.y - playerPos.y
            val d2 = dx * dx + dy * dy
            if (t.hp <= 0f || d2 > range2 * 1.1f * 1.1f) {
                target = null
            }
        }

        retargetTimer = max(0f, retargetTimer - delta)
        if (retargetTimer > 0f && target != null) return
        retargetTimer = retargetInterval

        var best: Enemy? = target
        var bestD2 = Float.POSITIVE_INFINITY
        target?.let { t ->
            val dx = t.pos.x - playerPos.x
            val dy = t.pos.y - playerPos.y
            bestD2 = dx * dx + dy * dy
        }

        for (e in enemies) {
            if (e.hp <= 0f) continue
            val dx = e.pos.x - playerPos.x
            val dy = e.pos.y - playerPos.y
            val d2 = dx * dx + dy * dy
            if (d2 > range2) continue

            val shouldSwitch = best == null || d2 < bestD2 * hysteresisMul
            if (shouldSwitch) {
                best = e
                bestD2 = d2
            }
        }

        target = best
    }
}

