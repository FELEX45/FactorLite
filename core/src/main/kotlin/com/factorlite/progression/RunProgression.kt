package com.factorlite.progression

import kotlin.math.max

class RunProgression {
    var level: Int = 1
        private set

    var xp: Int = 0
        private set

    var xpToNext: Int = 12
        private set

    val weapons = mutableListOf<WeaponInstance>()
    val passives = mutableListOf<PassiveInstance>()

    // Слоты на релиз: 2+2
    val maxWeapons = 2
    val maxPassives = 2

    fun reset() {
        level = 1
        xp = 0
        xpToNext = 12
        weapons.clear()
        passives.clear()
    }

    fun addXp(amount: Int): Boolean {
        if (amount <= 0) return false
        xp += amount
        var leveled = false
        while (xp >= xpToNext) {
            xp -= xpToNext
            level += 1
            xpToNext = nextXpGoal(level)
            leveled = true
        }
        return leveled
    }

    fun makeUpgradeChoices(): List<UpgradeOption> {
        return UpgradeDirector.makeChoices(this)
    }

    fun applyUpgrade(option: UpgradeOption) {
        when (option) {
            is UpgradeOption.AddWeapon -> {
                if (weapons.size < maxWeapons && weapons.none { it.kind == option.weaponKind }) {
                    weapons += WeaponInstance(option.weaponKind, level = 1)
                }
            }
            is UpgradeOption.UpgradeWeapon -> {
                weapons.firstOrNull { it.kind == option.weaponKind }?.let { it.level += 1 }
            }

            is UpgradeOption.AddPassive -> {
                if (passives.size < maxPassives && passives.none { it.kind == option.passiveKind }) {
                    passives += PassiveInstance(option.passiveKind, level = 1)
                }
            }

            is UpgradeOption.UpgradePassive -> {
                passives.firstOrNull { it.kind == option.passiveKind }?.let { it.level += 1 }
            }

            is UpgradeOption.WeaponMod -> {
                applyWeaponMod(option.weaponKind, option.mod)
            }
        }
    }

    private fun applyWeaponMod(kind: WeaponKind, mod: WeaponModKind) {
        val w = weapons.firstOrNull { it.kind == kind } ?: return
        when (mod) {
            WeaponModKind.EXTRA -> w.extraLevel += 1
            WeaponModKind.RICOCHET -> w.ricochetLevel += 1
            WeaponModKind.PIERCE -> w.pierceLevel += 1
        }
    }

    fun getMoveSpeedMultiplier(): Float {
        val lvl = passives.firstOrNull { it.kind == PassiveKind.MOVE_SPEED }?.level ?: 0
        return 1f + lvl * 0.06f
    }

    fun getDamageMultiplier(): Float {
        val lvl = passives.firstOrNull { it.kind == PassiveKind.DAMAGE }?.level ?: 0
        return 1f + lvl * 0.12f
    }

    fun getFireRateMultiplier(): Float {
        val lvl = passives.firstOrNull { it.kind == PassiveKind.FIRE_RATE }?.level ?: 0
        return 1f + lvl * 0.10f
    }

    fun getCritChance(): Float {
        val lvl = passives.firstOrNull { it.kind == PassiveKind.CRIT_CHANCE }?.level ?: 0
        return max(0f, 0.02f * lvl) // 2% за уровень
    }

    fun getCritDamageMultiplier(): Float {
        val lvl = passives.firstOrNull { it.kind == PassiveKind.CRIT_DAMAGE }?.level ?: 0
        return 1.5f + lvl * 0.15f
    }

    fun getMagnetRadiusPx(): Float {
        // Радиус притяжения/подбора XP в world-space (у нас ~px).
        // База достаточная, чтобы “чувствовалось”, дальше — ощутимый рост.
        val lvl = passives.firstOrNull { it.kind == PassiveKind.MAGNET }?.level ?: 0
        return 140f + lvl * 40f
    }

    private fun nextXpGoal(level: Int): Int {
        // Очень простая кривая: растёт плавно
        return 10 + (level * 3)
    }
}

