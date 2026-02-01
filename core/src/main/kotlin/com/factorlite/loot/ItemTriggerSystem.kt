package com.factorlite.loot

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2

/**
 * Система триггеров предметов (в стиле survivor-like):
 * - onHit/onKill/onDamage/update
 *
 * Важно: здесь НЕТ привязки к конкретным классам Enemy/Player — всё через лямбды.
 * Это позволит позже вынести сущности из GameScreen без переписывания предметов.
 */
class ItemTriggerSystem {
    data class LightningFx(
        val a: Vector2,
        val b: Vector2,
        var ttl: Float,
    )

    val items: MutableList<ItemInstance> = ArrayList()
    val lightningFx: MutableList<LightningFx> = ArrayList()

    private var shieldCooldown = 0f
    private var shieldReady = false
    private var toxicTimer = 0f

    fun reset() {
        items.clear()
        lightningFx.clear()
        shieldCooldown = 0f
        shieldReady = false
        toxicTimer = 0f
    }

    fun addItem(item: ItemInstance) {
        items.add(item)
    }

    fun bestRarity(kind: ItemKind): Rarity? {
        var best: Rarity? = null
        for (it in items) {
            if (it.kind != kind) continue
            if (best == null || it.rarity.ordinal > best!!.ordinal) best = it.rarity
        }
        return best
    }

    fun update(delta: Float) {
        toxicTimer = maxOf(0f, toxicTimer - delta)

        shieldCooldown = maxOf(0f, shieldCooldown - delta)
        if (shieldCooldown <= 0f) shieldReady = true

        if (lightningFx.isNotEmpty()) {
            val it = lightningFx.iterator()
            while (it.hasNext()) {
                val fx = it.next()
                fx.ttl -= delta
                if (fx.ttl <= 0f) it.remove()
            }
        }
    }

    data class PlayerHitResult(
        val blocked: Boolean,
    )

    /**
     * Вызывается при получении игроком урона (контакт/пуля/что угодно).
     * Возвращает, был ли удар заблокирован щитом.
     */
    fun onPlayerHit(): PlayerHitResult {
        val shieldR = bestRarity(ItemKind.SHIELD_CHARM)
        if (shieldR != null && shieldReady) {
            shieldReady = false
            shieldCooldown = shieldR.shieldCooldownSec
            return PlayerHitResult(blocked = true)
        }

        val toxR = bestRarity(ItemKind.TOXIC_BARREL)
        if (toxR != null) {
            toxicTimer = toxR.toxicDurationSec
        }
        return PlayerHitResult(blocked = false)
    }

    fun toxicActive(): Boolean = toxicTimer > 0f

    fun toxicRadius(): Float {
        val r = bestRarity(ItemKind.TOXIC_BARREL) ?: Rarity.COMMON
        return r.toxicRadius
    }

    fun toxicDps(): Float {
        val r = bestRarity(ItemKind.TOXIC_BARREL) ?: Rarity.COMMON
        return r.toxicDps
    }

    /**
     * ДПС токсичного облака (если активно).
     */
    fun <E> applyToxicDamage(
        delta: Float,
        damageMul: Float,
        radiusMul: Float,
        playerPos: Vector2,
        enemies: Iterable<E>,
        isAlive: (E) -> Boolean,
        getPos: (E) -> Vector2,
        damageEnemy: (E, Float) -> Unit,
    ) {
        if (!toxicActive()) return
        val radius = toxicRadius() * radiusMul.coerceIn(0.4f, 3.0f)
        val r2 = radius * radius
        val dps = toxicDps()
        val dmg = dps * delta * damageMul.coerceIn(0.2f, 3.0f)

        for (e in enemies) {
            if (!isAlive(e)) continue
            val p = getPos(e)
            val dx = p.x - playerPos.x
            val dy = p.y - playerPos.y
            if (dx * dx + dy * dy <= r2) {
                damageEnemy(e, dmg)
            }
        }
    }

    /**
     * Прок молний по попаданию (LIGHTNING_ORB).
     *
     * Внимание: для visited используется identityHashCode, чтобы не зависеть от equals/hashCode у сущностей.
     */
    fun <E> onEnemyHit(
        hit: E,
        playerPos: Vector2,
        isAlive: (E) -> Boolean,
        getPos: (E) -> Vector2,
        damageEnemy: (E, Float) -> Unit,
        findNearest: (x: Float, y: Float, visitedIds: Set<Int>, maxRange2: Float) -> E?,
    ) {
        val r = bestRarity(ItemKind.LIGHTNING_ORB) ?: return
        if (MathUtils.random() >= r.lightningProcChance) return

        val maxJumps = r.lightningChains
        val maxRange = 320f
        val maxRange2 = maxRange * maxRange

        val visitedIds = HashSet<Int>(8)
        var current: E? = hit
        var damage = r.lightningBaseDamage

        for (jump in 0..maxJumps) {
            val c = current ?: break
            if (!isAlive(c)) break

            val id = System.identityHashCode(c)
            if (!visitedIds.add(id)) break

            damageEnemy(c, damage)

            val pos = getPos(c)
            val next = findNearest(pos.x, pos.y, visitedIds, maxRange2)

            if (next != null) {
                val np = getPos(next)
                lightningFx.add(LightningFx(Vector2(pos.x, pos.y), Vector2(np.x, np.y), ttl = 0.10f))
            } else {
                // Чтобы эффект был видим — “удар” от игрока к цели
                lightningFx.add(LightningFx(Vector2(playerPos.x, playerPos.y), Vector2(pos.x, pos.y), ttl = 0.10f))
            }

            current = next
            damage *= 0.80f
        }
    }

    /**
     * Хил по убийству (BURGER_DROP).
     * Возвращает, сколько HP добавить (0 если не сработало/нет предмета).
     */
    fun rollBurgerHeal(): Float {
        val r = bestRarity(ItemKind.BURGER_DROP) ?: return 0f
        if (MathUtils.random() >= r.burgerDropChance) return 0f
        return r.burgerHealAmount
    }
}

