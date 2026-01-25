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

data class WeaponInstance(
    val kind: WeaponKind,
    var level: Int = 1,
    var cooldown: Float = 0f,
    var extraLevel: Int = 0,
    var ricochetLevel: Int = 0,
    var pierceLevel: Int = 0,
)

data class PassiveInstance(
    val kind: PassiveKind,
    var level: Int = 1,
)

sealed class UpgradeOption {
    abstract val title: String
    abstract val description: String

    data class AddWeapon(val weaponKind: WeaponKind) : UpgradeOption() {
        override val title: String = "Оружие: ${weaponKind.uiName}"
        override val description: String = "Добавить новое оружие."
    }

    data class UpgradeWeapon(val weaponKind: WeaponKind) : UpgradeOption() {
        override val title: String = "Улучшить: ${weaponKind.uiName}"
        override val description: String = "Повышает уровень оружия."
    }

    data class AddPassive(val passiveKind: PassiveKind) : UpgradeOption() {
        override val title: String = "Пассивка: ${passiveKind.uiName}"
        override val description: String = "Добавить новую пассивку."
    }

    data class UpgradePassive(val passiveKind: PassiveKind) : UpgradeOption() {
        override val title: String = "Улучшить: ${passiveKind.uiName}"
        override val description: String = "Повышает уровень пассивки."
    }

    data class WeaponMod(val weaponKind: WeaponKind, val mod: WeaponModKind) : UpgradeOption() {
        override val title: String = "${weaponKind.uiName}: ${mod.uiName}"
        override val description: String =
            when (weaponKind) {
                WeaponKind.SWORD -> when (mod) {
                    WeaponModKind.EXTRA -> "+1 дополнительная цель (удар по нескольким)."
                    WeaponModKind.RICOCHET -> "Цепной удар по ещё одной цели."
                    WeaponModKind.PIERCE -> "+дальность удара."
                }
                else -> when (mod) {
                    WeaponModKind.EXTRA -> "+1 снаряд за атаку."
                    WeaponModKind.RICOCHET -> "+1 рикошет по цели."
                    WeaponModKind.PIERCE -> "+1 пробивание цели."
                }
            }
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
