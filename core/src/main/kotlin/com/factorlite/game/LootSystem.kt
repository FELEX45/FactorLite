package com.factorlite.game

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.factorlite.loot.ItemInstance
import com.factorlite.loot.ItemOption
import com.factorlite.loot.makeItemOption
import com.factorlite.loot.rollItemKind
import com.factorlite.loot.rollRarityByChestCost
import com.factorlite.progression.RunProgression
import kotlin.math.sqrt

/**
 * Loot/экономика забега:
 * - XP-сферы
 * - gold монетки
 * - сундуки (спавн, авто-открытие по близости, генерация выбора)
 *
 * Важно: сам UI/RunState остаётся наверху (GameScreen), а LootSystem отдаёт "события".
 */
class LootSystem {
    data class Chest(
        val pos: Vector2,
        val cost: Int,
        val rarityCost: Int = cost,
        val radius: Float = 16f,
        val isElite: Boolean = false,
    )

    data class XpOrb(
        val pos: Vector2,
        val value: Int,
        val vel: Vector2 = Vector2(),
        var ttl: Float = 90f,
    )

    data class GoldOrb(
        val pos: Vector2,
        val value: Int,
        val vel: Vector2 = Vector2(),
        var ttl: Float = 90f,
    )

    data class OpenChestResult(
        val choices: List<ItemOption>,
    )

    val chests: MutableList<Chest> = ArrayList()
    val xpOrbs: MutableList<XpOrb> = ArrayList()
    val goldOrbs: MutableList<GoldOrb> = ArrayList()

    var gold: Int = 0
        private set

    private var nextChestCost: Int = 20
    private var chestSpawnTimer = 0f

    fun reset() {
        chests.clear()
        xpOrbs.clear()
        goldOrbs.clear()
        gold = 0
        nextChestCost = 20
        chestSpawnTimer = 3f
    }

    fun onEnemyKilled(pos: Vector2, xpReward: Int, goldReward: Int, isElite: Boolean) {
        if (isElite) {
            chests.add(
                Chest(
                    pos = Vector2(pos.x, pos.y),
                    cost = 0,
                    rarityCost = 160,
                    isElite = true,
                ),
            )
        }

        xpOrbs.add(
            XpOrb(
                pos = Vector2(pos.x, pos.y),
                value = xpReward,
                vel = Vector2(MathUtils.random(-24f, 24f), MathUtils.random(-24f, 24f)),
                ttl = 90f,
            ),
        )

        goldOrbs.add(
            GoldOrb(
                pos = Vector2(pos.x, pos.y),
                value = goldReward,
                vel = Vector2(MathUtils.random(-28f, 28f), MathUtils.random(-28f, 28f)),
                ttl = 90f,
            ),
        )
    }

    fun updateChestSpawns(
        delta: Float,
        arenaHalfW: Float,
        arenaHalfH: Float,
        playerPos: Vector2,
    ) {
        chestSpawnTimer -= delta
        if (chestSpawnTimer > 0f) return
        chestSpawnTimer = 9f

        // Спавним сундук в пределах арены, но не слишком близко к игроку
        val tries = 20
        for (i in 0 until tries) {
            val x = MathUtils.random(-arenaHalfW + 60f, arenaHalfW - 60f)
            val y = MathUtils.random(-arenaHalfH + 60f, arenaHalfH - 60f)
            val dx = x - playerPos.x
            val dy = y - playerPos.y
            if (dx * dx + dy * dy < 220f * 220f) continue
            chests.add(Chest(Vector2(x, y), cost = nextChestCost))
            break
        }
    }

    fun tryOpenChestByProximity(playerPos: Vector2): OpenChestResult? {
        // Если игрок рядом с сундуком и хватает золота — открываем автоматически
        val openRadius = 56f
        val openR2 = openRadius * openRadius

        val it = chests.iterator()
        while (it.hasNext()) {
            val c = it.next()
            val dx = c.pos.x - playerPos.x
            val dy = c.pos.y - playerPos.y
            if (dx * dx + dy * dy > openR2) continue
            if (gold < c.cost) return null

            gold -= c.cost
            it.remove()

            val choices = rollChestChoices(c.rarityCost)

            // Следующая стоимость (только для “обычных” сундуков)
            if (!c.isElite) nextChestCost = (nextChestCost * 2).coerceAtMost(99999)
            return OpenChestResult(choices)
        }
        return null
    }

    fun applyPickedItem(item: ItemInstance) {
        // Сейчас ничего не делаем: предметы применяются через ItemTriggerSystem, который живёт отдельно.
        // Этот метод оставлен как точка расширения (например, статистика/ачивки).
    }

    fun updateXpOrbs(delta: Float, playerPos: Vector2, progression: RunProgression): Boolean {
        if (xpOrbs.isEmpty()) return false

        val magnetRadius = progression.getMagnetRadiusPx()
        val magnetR2 = magnetRadius * magnetRadius
        val pickupRadius = 24f
        val pickupR2 = pickupRadius * pickupRadius

        var leveledUp = false

        val it = xpOrbs.iterator()
        while (it.hasNext()) {
            val o = it.next()
            o.ttl -= delta
            if (o.ttl <= 0f) {
                it.remove()
                continue
            }

            val dx = playerPos.x - o.pos.x
            val dy = playerPos.y - o.pos.y
            val d2 = dx * dx + dy * dy

            if (d2 <= pickupR2) {
                if (progression.addXp(o.value)) leveledUp = true
                it.remove()
                continue
            }

            if (d2 <= magnetR2) {
                val d = sqrt(d2).coerceAtLeast(1f)
                val t = (1f - (d / magnetRadius)).coerceIn(0f, 1f)
                val speed = 120f + 520f * t
                o.vel.set(dx / d, dy / d).scl(speed)
            } else {
                o.vel.scl(0.90f)
            }

            o.pos.mulAdd(o.vel, delta)
        }

        return leveledUp
    }

    fun updateGoldOrbs(delta: Float, playerPos: Vector2, progression: RunProgression) {
        if (goldOrbs.isEmpty()) return

        val magnetRadius = progression.getMagnetRadiusPx()
        val magnetR2 = magnetRadius * magnetRadius
        val pickupRadius = 26f
        val pickupR2 = pickupRadius * pickupRadius

        val it = goldOrbs.iterator()
        while (it.hasNext()) {
            val o = it.next()
            o.ttl -= delta
            if (o.ttl <= 0f) {
                it.remove()
                continue
            }

            val dx = playerPos.x - o.pos.x
            val dy = playerPos.y - o.pos.y
            val d2 = dx * dx + dy * dy

            if (d2 <= pickupR2) {
                gold += o.value
                it.remove()
                continue
            }

            if (d2 <= magnetR2) {
                val d = sqrt(d2).coerceAtLeast(1f)
                val t = (1f - (d / magnetRadius)).coerceIn(0f, 1f)
                val speed = 140f + 640f * t
                o.vel.set(dx / d, dy / d).scl(speed)
            } else {
                o.vel.scl(0.90f)
            }

            o.pos.mulAdd(o.vel, delta)
        }
    }

    private fun rollChestChoices(rarityCost: Int): List<ItemOption> {
        val opts = ArrayList<ItemOption>(3)
        var guard = 0
        while (opts.size < 3 && guard++ < 50) {
            val item = ItemInstance(rollItemKind(), rollRarityByChestCost(rarityCost))
            val opt = makeItemOption(item)
            if (opts.any { it.item.kind == opt.item.kind }) continue
            opts.add(opt)
        }
        return opts
    }
}

