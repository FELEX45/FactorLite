package com.factorlite.loot

import com.badlogic.gdx.math.MathUtils

enum class Rarity {
    COMMON,
    RARE,
    EPIC,
    LEGENDARY,
}

enum class ItemKind {
    BURGER_DROP,     // шанс отхилиться при убийстве
    LIGHTNING_ORB,   // шанс доп. удара молнией по попаданию
    TOXIC_BARREL,    // при получении урона — токсичное облако
    SHIELD_CHARM,    // щит раз в N секунд
}

val ItemKind.uiName: String
    get() = when (this) {
        ItemKind.BURGER_DROP -> "Боргар"
        ItemKind.LIGHTNING_ORB -> "Сфера молнии"
        ItemKind.TOXIC_BARREL -> "Бочка с токсинами"
        ItemKind.SHIELD_CHARM -> "Оберег щита"
    }

data class ItemInstance(
    val kind: ItemKind,
    val rarity: Rarity,
)

data class ItemOption(
    val item: ItemInstance,
    val title: String,
    val description: String,
)

// --- Tuning by rarity (MVP, но уже “ощутимо”) ---

val Rarity.sortKey: Int
    get() = when (this) {
        Rarity.COMMON -> 0
        Rarity.RARE -> 1
        Rarity.EPIC -> 2
        Rarity.LEGENDARY -> 3
    }

val Rarity.burgerDropChance: Float
    get() = when (this) {
        Rarity.COMMON -> 0.02f
        Rarity.RARE -> 0.03f
        Rarity.EPIC -> 0.045f
        Rarity.LEGENDARY -> 0.065f
    }

val Rarity.burgerHealAmount: Float
    get() = when (this) {
        Rarity.COMMON -> 6f
        Rarity.RARE -> 7f
        Rarity.EPIC -> 9f
        Rarity.LEGENDARY -> 12f
    }

val Rarity.lightningProcChance: Float
    get() = when (this) {
        Rarity.COMMON -> 0.25f
        Rarity.RARE -> 0.35f
        Rarity.EPIC -> 0.45f
        Rarity.LEGENDARY -> 0.60f
    }

val Rarity.lightningBaseDamage: Float
    get() = when (this) {
        Rarity.COMMON -> 10f
        Rarity.RARE -> 14f
        Rarity.EPIC -> 20f
        Rarity.LEGENDARY -> 30f
    }

/** Сколько дополнительных “прыжков” молнии делать после первого удара. */
val Rarity.lightningChains: Int
    get() = when (this) {
        Rarity.COMMON -> 0
        Rarity.RARE -> 1
        Rarity.EPIC -> 2
        Rarity.LEGENDARY -> 3
    }

val Rarity.toxicDurationSec: Float
    get() = when (this) {
        Rarity.COMMON -> 2.2f
        Rarity.RARE -> 2.8f
        Rarity.EPIC -> 3.4f
        Rarity.LEGENDARY -> 4.2f
    }

val Rarity.toxicRadius: Float
    get() = when (this) {
        Rarity.COMMON -> 110f
        Rarity.RARE -> 125f
        Rarity.EPIC -> 145f
        Rarity.LEGENDARY -> 170f
    }

val Rarity.toxicDps: Float
    get() = when (this) {
        Rarity.COMMON -> 18f
        Rarity.RARE -> 24f
        Rarity.EPIC -> 32f
        Rarity.LEGENDARY -> 44f
    }

val Rarity.shieldCooldownSec: Float
    get() = when (this) {
        Rarity.COMMON -> 20f
        Rarity.RARE -> 16f
        Rarity.EPIC -> 12f
        Rarity.LEGENDARY -> 9f
    }

fun makeItemOption(item: ItemInstance): ItemOption {
    return when (item.kind) {
        ItemKind.BURGER_DROP ->
            ItemOption(
                item,
                "Боргар (${item.rarity.uiName})",
                "${(item.rarity.burgerDropChance * 100).toInt()}% шанс получить бургер за убийство врага. Бургер лечит на ${item.rarity.burgerHealAmount.toInt()} HP.",
            )
        ItemKind.LIGHTNING_ORB ->
            ItemOption(
                item,
                "Сфера молнии (${item.rarity.uiName})",
                "${(item.rarity.lightningProcChance * 100).toInt()}% шанс призвать молнию по попаданию. Урон: ${item.rarity.lightningBaseDamage.toInt()}, цепь: +${item.rarity.lightningChains}.",
            )
        ItemKind.TOXIC_BARREL ->
            ItemOption(
                item,
                "Бочка с токсинами (${item.rarity.uiName})",
                "При получении урона выпускает токсичное облако (${item.rarity.toxicDurationSec}s, радиус ${item.rarity.toxicRadius.toInt()}, ${item.rarity.toxicDps.toInt()} DPS).",
            )
        ItemKind.SHIELD_CHARM ->
            ItemOption(
                item,
                "Оберег щита (${item.rarity.uiName})",
                "Блокирует 1 удар раз в ${item.rarity.shieldCooldownSec.toInt()} секунд.",
            )
    }
}

val Rarity.uiName: String
    get() = when (this) {
        Rarity.COMMON -> "Обычный"
        Rarity.RARE -> "Редкий"
        Rarity.EPIC -> "Эпик"
        Rarity.LEGENDARY -> "Легендарный"
    }

fun rollRarityCommonChest(): Rarity {
    val r = MathUtils.random()
    return when {
        r < 0.72f -> Rarity.COMMON
        r < 0.94f -> Rarity.RARE
        r < 0.995f -> Rarity.EPIC
        else -> Rarity.LEGENDARY
    }
}

fun rollRarityByChestCost(cost: Int): Rarity {
    // Чем дороже сундук (в рамках забега), тем выше ожидание редкостей.
    // MVP-тиры по цене: 20/40/80/160/320...
    return when {
        cost < 40 -> rollRarityCommonChest()
        cost < 160 -> {
            val r = MathUtils.random()
            when {
                r < 0.55f -> Rarity.COMMON
                r < 0.88f -> Rarity.RARE
                r < 0.985f -> Rarity.EPIC
                else -> Rarity.LEGENDARY
            }
        }
        else -> {
            val r = MathUtils.random()
            when {
                r < 0.35f -> Rarity.COMMON
                r < 0.74f -> Rarity.RARE
                r < 0.965f -> Rarity.EPIC
                else -> Rarity.LEGENDARY
            }
        }
    }
}

fun rollItemKind(): ItemKind {
    // MVP: равновероятно
    val all = ItemKind.entries
    return all[MathUtils.random(all.size - 1)]
}

