package com.factorlite.progression

enum class WeaponKind {
    BLASTER,
    REVOLVER,
    SWORD,
}

enum class WeaponModKind {
    EXTRA,     // доп. пуля / доп. цель (для меча)
    RICOCHET,  // рикошет / цепочка (для меча)
    PIERCE,    // пробивание / дальность (для меча)
}

enum class PassiveKind {
    DAMAGE,
    FIRE_RATE,
    MOVE_SPEED,
    CRIT_CHANCE,
    CRIT_DAMAGE,
    MAGNET,
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

data class PassiveInstance(
    val kind: PassiveKind,
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
        override val description: String =
            when (weaponKind) {
                WeaponKind.BLASTER -> "Усиливает урон и чуть ускоряет стрельбу."
                WeaponKind.REVOLVER -> "Усиливает урон и чуть ускоряет стрельбу."
                WeaponKind.SWORD -> "Усиливает урон и чуть ускоряет взмах."
            }
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
        override val title: String = when (weaponKind) {
            WeaponKind.BLASTER -> when (upgrade) {
                WeaponUpgradeKind.DAMAGE -> "Бластер"
                WeaponUpgradeKind.FIRE_RATE -> "Бластер"
                WeaponUpgradeKind.PROJECTILE_SPEED -> "Бластер"
                WeaponUpgradeKind.ACCURACY -> "Бластер"
                WeaponUpgradeKind.RANGE -> "Бластер"
            }
            WeaponKind.REVOLVER -> when (upgrade) {
                WeaponUpgradeKind.DAMAGE -> "Револьвер"
                WeaponUpgradeKind.FIRE_RATE -> "Револьвер"
                WeaponUpgradeKind.PROJECTILE_SPEED -> "Револьвер"
                WeaponUpgradeKind.ACCURACY -> "Револьвер"
                WeaponUpgradeKind.RANGE -> "Револьвер"
            }
            WeaponKind.SWORD -> when (upgrade) {
                WeaponUpgradeKind.DAMAGE -> "Меч"
                WeaponUpgradeKind.FIRE_RATE -> "Меч"
                WeaponUpgradeKind.RANGE -> "Меч"
                WeaponUpgradeKind.PROJECTILE_SPEED -> "Меч"
                WeaponUpgradeKind.ACCURACY -> "Меч"
            }
        }

        override val description: String = statLine
    }

    data class AddPassive(val passiveKind: PassiveKind) : UpgradeOption() {
        override val title: String = "Пассивка: ${passiveKind.uiName}"
        override val description: String = "Добавить новую пассивку."
    }

    data class UpgradePassive(
        val passiveKind: PassiveKind,
        override val rarity: UpgradeRarity,
        val steps: Int,
        override val levelLabel: String,
        private val statLine: String,
    ) : UpgradeOption() {
        override val title: String = passiveKind.uiName
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
        WeaponKind.BLASTER -> "Бластер"
        WeaponKind.REVOLVER -> "Револьвер"
        WeaponKind.SWORD -> "Меч"
    }

val PassiveKind.uiName: String
    get() = when (this) {
        PassiveKind.DAMAGE -> "+Урон"
        PassiveKind.FIRE_RATE -> "+Скорость атаки"
        PassiveKind.MOVE_SPEED -> "+Скорость бега"
        PassiveKind.CRIT_CHANCE -> "+Шанс крита"
        PassiveKind.CRIT_DAMAGE -> "+Крит. урон"
        PassiveKind.MAGNET -> "+Магнит"
    }

val WeaponModKind.uiName: String
    get() = when (this) {
        WeaponModKind.EXTRA -> "Доп. выстрел"
        WeaponModKind.RICOCHET -> "Рикошет"
        WeaponModKind.PIERCE -> "Пробивание"
    }
