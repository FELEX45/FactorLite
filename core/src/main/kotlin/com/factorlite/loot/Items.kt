package com.factorlite.loot

import com.badlogic.gdx.math.MathUtils

enum class Rarity {
    COMMON,
    RARE,
    EPIC,
    LEGENDARY,
}

enum class ItemKind {
    PIZZA_DROP,        // шанс отхилиться при убийстве
    BALL_LIGHTNING,    // шанс доп. удара молнией по попаданию
    POCKET_JIDZH,      // при получении урона — токсичное облако
    SHIELD_CHARM,      // щит раз в N секунд

    ACE,               // +10% к удаче (редкости лута)
    HEART,             // +20 к max HP и +20 HP сразу
    MIDAS,             // +15% к получению GOLD
    BOOK,              // каждые 2 секунды даёт +5 XP
    WOLF_FANG,         // 5% шанс восстановить 10 HP (на убийство)
    SANDALS,           // +3% к скорости передвижения
}

val ItemKind.uiName: String
    get() = when (this) {
        ItemKind.PIZZA_DROP -> "Пицца"
        ItemKind.BALL_LIGHTNING -> "Шаровая молния"
        ItemKind.POCKET_JIDZH -> "Карманный Жидж"
        ItemKind.SHIELD_CHARM -> "Оберег щита"

        ItemKind.ACE -> "Туз"
        ItemKind.HEART -> "Сердце"
        ItemKind.MIDAS -> "Мидас"
        ItemKind.BOOK -> "Книга"
        ItemKind.WOLF_FANG -> "Клык волка"
        ItemKind.SANDALS -> "Сандали"
    }

val ItemKind.fixedRarity: Rarity
    get() = when (this) {
        ItemKind.PIZZA_DROP -> Rarity.COMMON
        ItemKind.BALL_LIGHTNING -> Rarity.EPIC
        ItemKind.POCKET_JIDZH -> Rarity.EPIC
        ItemKind.SHIELD_CHARM -> Rarity.RARE
        ItemKind.ACE -> Rarity.COMMON
        ItemKind.HEART -> Rarity.COMMON
        ItemKind.MIDAS -> Rarity.RARE
        ItemKind.BOOK -> Rarity.EPIC
        ItemKind.WOLF_FANG -> Rarity.RARE
        ItemKind.SANDALS -> Rarity.COMMON
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
        ItemKind.PIZZA_DROP ->
            ItemOption(
                item,
                "Пицца (${item.rarity.uiName})",
                "${(item.rarity.burgerDropChance * 100).toInt()}% шанс получить пиццу за убийство врага. Пицца лечит на ${item.rarity.burgerHealAmount.toInt()} HP.",
            )
        ItemKind.BALL_LIGHTNING ->
            ItemOption(
                item,
                "Шаровая молния (${item.rarity.uiName})",
                "${(item.rarity.lightningProcChance * 100).toInt()}% шанс призвать молнию по попаданию. Урон: ${item.rarity.lightningBaseDamage.toInt()}, цепь: +${item.rarity.lightningChains}.",
            )
        ItemKind.POCKET_JIDZH ->
            ItemOption(
                item,
                "Карманный Жидж (${item.rarity.uiName})",
                "При получении урона выпускает токсичное облако (${item.rarity.toxicDurationSec}s, радиус ${item.rarity.toxicRadius.toInt()}, ${item.rarity.toxicDps.toInt()} DPS).",
            )
        ItemKind.SHIELD_CHARM ->
            ItemOption(
                item,
                "Оберег щита (${item.rarity.uiName})",
                "Блокирует 1 удар раз в ${item.rarity.shieldCooldownSec.toInt()} секунд.",
            )

        ItemKind.ACE ->
            ItemOption(
                item,
                "Туз (${item.rarity.uiName})",
                "Увеличивает удачу на 10%. (Повышает шанс редких предметов из сундуков.)",
            )
        ItemKind.HEART ->
            ItemOption(
                item,
                "Сердце (${item.rarity.uiName})",
                "Увеличивает общий пул HP на 20 и мгновенно лечит на 20 HP.",
            )
        ItemKind.MIDAS ->
            ItemOption(
                item,
                "Мидас (${item.rarity.uiName})",
                "Увеличивает получение GOLD на 15%.",
            )
        ItemKind.BOOK ->
            ItemOption(
                item,
                "Книга (${item.rarity.uiName})",
                "Каждые 2 секунды выдаёт +5 XP.",
            )
        ItemKind.WOLF_FANG ->
            ItemOption(
                item,
                "Клык волка (${item.rarity.uiName})",
                "5% шанс восстановить 10 HP при убийстве врага.",
            )
        ItemKind.SANDALS ->
            ItemOption(
                item,
                "Сандали (${item.rarity.uiName})",
                "Увеличивает скорость передвижения на 3%.",
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

fun rollRarityByChestCost(cost: Int, luckBonus: Float = 0f): Rarity {
    // Чем дороже сундук (в рамках забега), тем выше ожидание редкостей.
    // MVP-тиры по цене: 20/40/80/160/320...
    return when {
        cost < 40 -> rollRarityCommonChestWithLuck(luckBonus)
        cost < 160 -> {
            val r = (MathUtils.random() - luckBonus.coerceIn(0f, 0.65f)).coerceIn(0f, 1f)
            when {
                r < 0.55f -> Rarity.COMMON
                r < 0.88f -> Rarity.RARE
                r < 0.985f -> Rarity.EPIC
                else -> Rarity.LEGENDARY
            }
        }
        else -> {
            val r = (MathUtils.random() - luckBonus.coerceIn(0f, 0.65f)).coerceIn(0f, 1f)
            when {
                r < 0.35f -> Rarity.COMMON
                r < 0.74f -> Rarity.RARE
                r < 0.965f -> Rarity.EPIC
                else -> Rarity.LEGENDARY
            }
        }
    }
}

private fun rollRarityCommonChestWithLuck(luckBonus: Float): Rarity {
    val r = (MathUtils.random() - luckBonus.coerceIn(0f, 0.65f)).coerceIn(0f, 1f)
    return when {
        r < 0.72f -> Rarity.COMMON
        r < 0.94f -> Rarity.RARE
        r < 0.995f -> Rarity.EPIC
        else -> Rarity.LEGENDARY
    }
}

fun rollItemKind(): ItemKind {
    // MVP: равновероятно
    val all = ItemKind.entries
    return all[MathUtils.random(all.size - 1)]
}

fun ItemKind.toFixedInstance(): ItemInstance = ItemInstance(this, fixedRarity)
