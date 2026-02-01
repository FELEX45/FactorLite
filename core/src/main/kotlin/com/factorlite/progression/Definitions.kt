package com.factorlite.progression

enum class WeaponKind {
    FROSTSTAFF,
    FIRESTAFF,
    REVOLVER,
    POISON_TRAP,
    KATANA,
    DAGGER,
    POISON_AURA,
}

enum class WeaponModKind {
    EXTRA,     // доп. пуля / доп. цель (для меча)
    RICOCHET,  // рикошет / цепочка (для меча)
    PIERCE,    // пробивание / дальность (для меча)
}

enum class RingKind {
    SPEED,         // +% скорость передвижения
    WIND,          // +% шанс уклонения
    VAMPIRE,       // +% лайфстил (от нанесённого урона)
    LUCKY,         // +% шанс крита (крит-урон базово x2)
    VITALITY,      // +% max HP
    DAMAGE,        // +% урон
    QUICK_HAND,    // +% скорость атаки
    MAGNET,        // +% радиус подбора
    MIND,          // +% получение опыта
}

enum class UpgradeRarity(val weight: Float, val steps: Int) {
    COMMON(weight = 0.72f, steps = 1),
    RARE(weight = 0.23f, steps = 2),
    EPIC(weight = 0.045f, steps = 4),
    LEGENDARY(weight = 0.005f, steps = 7),
}

val UpgradeRarity.label: String
    get() = when (this) {
        UpgradeRarity.COMMON -> "Common"
        UpgradeRarity.RARE -> "Rare"
        UpgradeRarity.EPIC -> "Epic"
        UpgradeRarity.LEGENDARY -> "Legendary"
    }

data class WeaponInstance(
    val kind: WeaponKind,
    var level: Int = 1,
    var cooldown: Float = 0f,
    var extraLevel: Int = 0,
    var ricochetLevel: Int = 0,
    var pierceLevel: Int = 0,
    // Паттерн-апгрейды (то, что игрок реально выбирает на карточках)
    var damageLevel: Int = 0,
    var cooldownLevel: Int = 0,
    var projectileSpeedLevel: Int = 0,
    var accuracyLevel: Int = 0,
    var rangeLevel: Int = 0,
)

data class RingInstance(
    val kind: RingKind,
    var level: Int = 1,
)

sealed class UpgradeOption {
    abstract val title: String
    abstract val description: String
    open val rarity: UpgradeRarity? = null
    open val levelLabel: String? = null

    data class AddWeapon(val weaponKind: WeaponKind) : UpgradeOption() {
        override val title: String = "Оружие: ${weaponKind.uiName}"
        override val description: String = "Добавить новое оружие."
    }

    data class UpgradeWeapon(val weaponKind: WeaponKind) : UpgradeOption() {
        override val title: String = "Улучшить: ${weaponKind.uiName}"
        override val description: String = "Усиливает оружие."
    }

    enum class WeaponUpgradeKind {
        DAMAGE,
        FIRE_RATE,
        PROJECTILE_SPEED,
        ACCURACY,
        RANGE,
    }

    /**
     * Конкретные карточки апгрейдов паттерна (вместо абстрактного "уровня").
     */
    data class WeaponUpgrade(
        val weaponKind: WeaponKind,
        val upgrade: WeaponUpgradeKind,
        override val rarity: UpgradeRarity,
        val steps: Int,
        override val levelLabel: String,
        private val statLine: String,
    ) : UpgradeOption() {
        override val title: String = weaponKind.uiName

        override val description: String = statLine
    }

    data class AddRing(val ringKind: RingKind) : UpgradeOption() {
        override val title: String = "Кольцо: ${ringKind.uiName}"
        override val description: String = "Добавить новое кольцо."
    }

    data class UpgradeRing(
        val ringKind: RingKind,
        override val rarity: UpgradeRarity,
        val steps: Int,
        override val levelLabel: String,
        private val statLine: String,
    ) : UpgradeOption() {
        override val title: String = ringKind.uiName
        override val description: String = statLine
    }

    data class WeaponMod(
        val weaponKind: WeaponKind,
        val mod: WeaponModKind,
        override val rarity: UpgradeRarity,
        override val levelLabel: String,
        private val statLine: String,
    ) : UpgradeOption() {
        override val title: String = weaponKind.uiName
        override val description: String = statLine
    }
}

val WeaponKind.uiName: String
    get() = when (this) {
        WeaponKind.FROSTSTAFF -> "Посох холода"
        WeaponKind.FIRESTAFF -> "Посох огня"
        WeaponKind.REVOLVER -> "Револьвер"
        WeaponKind.POISON_TRAP -> "Ядовитая ловушка"
        WeaponKind.KATANA -> "Катана"
        WeaponKind.DAGGER -> "Кинжал"
        WeaponKind.POISON_AURA -> "Облако яда"
    }

val RingKind.uiName: String
    get() = when (this) {
        RingKind.SPEED -> "Кольцо скорости"
        RingKind.WIND -> "Кольцо ветра"
        RingKind.VAMPIRE -> "Кольцо вампира"
        RingKind.LUCKY -> "Кольцо везучести"
        RingKind.VITALITY -> "Кольцо жизненной силы"
        RingKind.DAMAGE -> "Кольцо урона"
        RingKind.QUICK_HAND -> "Кольцо быстрой руки"
        RingKind.MAGNET -> "Магнитное кольцо"
        RingKind.MIND -> "Кольцо разума"
    }

val WeaponModKind.uiName: String
    get() = when (this) {
        WeaponModKind.EXTRA -> "Доп. выстрел"
        WeaponModKind.RICOCHET -> "Рикошет"
        WeaponModKind.PIERCE -> "Пробивание"
    }
