package com.factorlite.game

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.factorlite.content.Balance
import com.factorlite.progression.BonusRarity
import com.factorlite.progression.GlobalBonusKind
import com.factorlite.progression.GlobalBonusOption

class ShrineSystem {
    data class Shrine(
        val pos: Vector2,
        val radius: Float = 48f,
        val requiredSec: Float = 2.6f,
        var progressSec: Float = 0f,
        var consumed: Boolean = false,
    )

    val shrines: MutableList<Shrine> = ArrayList()

    private var nextSpawnAt: Float = 35f

    fun reset() {
        shrines.clear()
        nextSpawnAt = Balance.cfg.shrines.firstSpawnAt
    }

    /**
     * @return choices when a shrine is completed; otherwise null
     */
    fun update(delta: Float, runTime: Float, playerPos: Vector2, arenaHalfW: Float, arenaHalfH: Float): List<GlobalBonusOption>? {
        val sb = Balance.cfg.shrines
        if (runTime >= nextSpawnAt && shrines.size < sb.maxOnMap) {
            spawnShrineAround(playerPos, arenaHalfW, arenaHalfH)
            // дальше чуть чаще
            nextSpawnAt = runTime + MathUtils.random(sb.nextIntervalMin, sb.nextIntervalMax)
        }

        for (s in shrines) {
            if (s.consumed) continue
            val dx = s.pos.x - playerPos.x
            val dy = s.pos.y - playerPos.y
            val inside = dx * dx + dy * dy <= s.radius * s.radius
            if (inside) {
                s.progressSec += delta
                if (s.progressSec >= s.requiredSec) {
                    s.consumed = true
                    return rollChoices()
                }
            } else {
                // небольшой спад, чтобы "выход из круга" был наказуем, но не ультра-жёстко
                s.progressSec = (s.progressSec - delta * 1.2f).coerceAtLeast(0f)
            }
        }

        shrines.removeAll { it.consumed }
        return null
    }

    private fun spawnShrineAround(playerPos: Vector2, arenaHalfW: Float, arenaHalfH: Float) {
        val sb = Balance.cfg.shrines
        val dist = MathUtils.random(sb.spawnDistMin, sb.spawnDistMax)
        val ang = MathUtils.random(0f, MathUtils.PI2)
        val x0 = playerPos.x + MathUtils.cos(ang) * dist
        val y0 = playerPos.y + MathUtils.sin(ang) * dist
        // Не даём заспавниться за “картой” (вне арены/границ движения игрока)
        val x = MathUtils.clamp(x0, -arenaHalfW + sb.edgeMargin, arenaHalfW - sb.edgeMargin)
        val y = MathUtils.clamp(y0, -arenaHalfH + sb.edgeMargin, arenaHalfH - sb.edgeMargin)
        shrines.add(Shrine(pos = Vector2(x, y)))
    }

    private fun rollChoices(): List<GlobalBonusOption> {
        val out = ArrayList<GlobalBonusOption>(3)
        val kinds = GlobalBonusKind.entries.toMutableList()
        repeat(3) {
            if (kinds.isEmpty()) return@repeat
            val k = kinds.removeAt(MathUtils.random(kinds.lastIndex))
            val rarity = rollRarity()
            val amount = amountFor(k, rarity)
            out += GlobalBonusOption(k, amount, rarity)
        }
        return out
    }

    private fun amountFor(kind: GlobalBonusKind, rarity: BonusRarity): Float {
        // Подогнано под “ощущение” Megabonk-скринов: часто встречаются 5%/8%/21%.
        return when (kind) {
            GlobalBonusKind.DIFFICULTY -> when (rarity) {
                BonusRarity.COMMON -> 0.08f
                BonusRarity.RARE -> 0.15f
                BonusRarity.EPIC -> 0.22f
            }
            GlobalBonusKind.ELITE_FREQUENCY -> when (rarity) {
                BonusRarity.COMMON -> 0.08f
                BonusRarity.RARE -> 0.21f
                BonusRarity.EPIC -> 0.33f
            }
            GlobalBonusKind.CRIT_CHANCE -> when (rarity) {
                BonusRarity.COMMON -> 0.05f
                BonusRarity.RARE -> 0.10f
                BonusRarity.EPIC -> 0.15f
            }
            GlobalBonusKind.DAMAGE -> when (rarity) {
                BonusRarity.COMMON -> 0.06f
                BonusRarity.RARE -> 0.12f
                BonusRarity.EPIC -> 0.18f
            }
            GlobalBonusKind.FIRE_RATE -> when (rarity) {
                BonusRarity.COMMON -> 0.05f
                BonusRarity.RARE -> 0.10f
                BonusRarity.EPIC -> 0.15f
            }
            GlobalBonusKind.PICKUP_RADIUS -> when (rarity) {
                BonusRarity.COMMON -> 0.10f
                BonusRarity.RARE -> 0.20f
                BonusRarity.EPIC -> 0.35f
            }
        }
    }

    private fun rollRarity(): BonusRarity {
        val r = MathUtils.random()
        return when {
            r < 0.72f -> BonusRarity.COMMON
            r < 0.96f -> BonusRarity.RARE
            else -> BonusRarity.EPIC
        }
    }
}

