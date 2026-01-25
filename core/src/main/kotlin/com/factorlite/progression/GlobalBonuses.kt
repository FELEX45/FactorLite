package com.factorlite.progression

enum class GlobalBonusKind {
    ELITE_FREQUENCY,
    DIFFICULTY,
    CRIT_CHANCE,
    DAMAGE,
    FIRE_RATE,
    PICKUP_RADIUS,
}

enum class BonusRarity {
    COMMON,
    RARE,
    EPIC,
}

val BonusRarity.label: String
    get() = when (this) {
        BonusRarity.COMMON -> "Обычный"
        BonusRarity.RARE -> "Редкий"
        BonusRarity.EPIC -> "Эпический"
    }

data class GlobalBonusOption(
    val kind: GlobalBonusKind,
    val amount: Float, // обычно в долях: 0.08f = 8%
    val rarity: BonusRarity,
) {
    val title: String
        get() = when (kind) {
            GlobalBonusKind.ELITE_FREQUENCY -> "Частота элит"
            GlobalBonusKind.DIFFICULTY -> "Сложность"
            GlobalBonusKind.CRIT_CHANCE -> "Шанс крита"
            GlobalBonusKind.DAMAGE -> "Урон"
            GlobalBonusKind.FIRE_RATE -> "Скорость атаки"
            GlobalBonusKind.PICKUP_RADIUS -> "Радиус подбора"
        }

    val description: String
        get() {
            val pct = (amount * 100f).toInt()
            return when (kind) {
                GlobalBonusKind.ELITE_FREQUENCY -> "Получи +$pct% частота элит."
                GlobalBonusKind.DIFFICULTY -> "Получи +$pct% сложность (выше риск, выше награды)."
                GlobalBonusKind.CRIT_CHANCE -> "Получи +$pct% шанс крита."
                GlobalBonusKind.DAMAGE -> "Получи +$pct% урон."
                GlobalBonusKind.FIRE_RATE -> "Получи +$pct% скорость атаки."
                GlobalBonusKind.PICKUP_RADIUS -> "Получи +$pct% радиус подбора."
            }
        }
}

