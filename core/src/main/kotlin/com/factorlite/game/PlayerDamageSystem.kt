package com.factorlite.game

import com.badlogic.gdx.math.MathUtils
import com.factorlite.loot.ItemTriggerSystem
import kotlin.math.max

class PlayerDamageSystem(
    private var maxHp: Float = 100f,
    private val invulnDurationSec: Float = 0.6f,
) {
    var hp: Float = maxHp
        private set

    var invuln: Float = 0f
        private set

    fun reset() {
        hp = maxHp
        invuln = 0f
    }

    fun setMaxHp(newMax: Float) {
        val m = newMax.coerceAtLeast(1f)
        maxHp = m
        if (hp > maxHp) hp = maxHp
    }

    fun update(delta: Float) {
        invuln = max(0f, invuln - delta)
    }

    fun canTakeDamage(): Boolean = invuln <= 0f

    data class HitResult(
        val blocked: Boolean,
        val died: Boolean,
    )

    fun applyHit(damage: Float, itemSystem: ItemTriggerSystem, dodgeChance: Float): HitResult {
        if (damage <= 0f) return HitResult(blocked = false, died = false)
        if (!canTakeDamage()) return HitResult(blocked = true, died = false)

        // Врождёнка/эффект: шанс уклонения (до предметов и до получения урона).
        val dodge = dodgeChance.coerceIn(0f, 0.80f)
        if (dodge > 0f && MathUtils.random() < dodge) {
            // Небольшая неуязвимость, чтобы не словить мгновенно следующий хит.
            invuln = invulnDurationSec * 0.60f
            return HitResult(blocked = true, died = false)
        }

        if (itemSystem.onPlayerHit().blocked) {
            return HitResult(blocked = true, died = false)
        }

        hp -= damage
        invuln = invulnDurationSec
        if (hp <= 0f) {
            hp = 0f
            return HitResult(blocked = false, died = true)
        }
        return HitResult(blocked = false, died = false)
    }

    fun heal(amount: Float) {
        if (amount <= 0f) return
        hp = (hp + amount).coerceAtMost(maxHp)
    }
}

