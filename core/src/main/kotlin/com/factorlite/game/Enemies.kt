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
    val speed: Float,
    val contactDamage: Float,
    val radius: Float = 14f,
    val xpReward: Int = 1,
    val goldReward: Int = 1,
    val kind: EnemyKind = EnemyKind.NORMAL,
    val isElite: Boolean = false,
    var shootCooldown: Float = 0f,
)

