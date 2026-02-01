package com.factorlite.game

import com.badlogic.gdx.math.Vector2

enum class EnemyKind {
    NORMAL,
    FAST,
    TANK,
    RANGED,
}

data class Enemy(
    val pos: Vector2,
    var hp: Float,
    val maxHp: Float = hp,
    val speed: Float,
    val contactDamage: Float,
    val radius: Float = 14f,
    val xpReward: Int = 1,
    val goldReward: Int = 1,
    val kind: EnemyKind = EnemyKind.NORMAL,
    val isElite: Boolean = false,
    val isBoss: Boolean = false,
    var shootCooldown: Float = 0f,
    // --- Status effects (минимальная система) ---
    var slowMul: Float = 1f,
    var slowTimer: Float = 0f,

    var burnStacks: Int = 0,
    var burnTimer: Float = 0f,
    var burnDpsPerStack: Float = 0f,

    var bleedStacks: Int = 0,
    var bleedTimer: Float = 0f,
    var bleedDpsPerStack: Float = 0f,
)

