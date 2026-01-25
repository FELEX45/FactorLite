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

    // Глобальные бонусы (из святынь), мультипликативные/аддитивные, не занимают слоты.
    private var eliteFrequencyBonus: Float = 0f      // +% к шансу элит
    private var globalCritChanceBonus: Float = 0f     // +% к critChance
    private var globalDamageBonus: Float = 0f         // +% к урону
    private var globalFireRateBonus: Float = 0f       // +% к скорости атаки
    private var globalPickupRadiusBonus: Float = 0f   // +% к радиусу подбора (магнит)
    private var globalDifficultyBonus: Float = 0f     // +% к сложности (риск/награда)

    // Слоты на релиз: 2+2
    val maxWeapons = 2
    val maxPassives = 2

    fun reset() {
        level = 1
        xp = 0
        xpToNext = 12
        weapons.clear()
        passives.clear()
        eliteFrequencyBonus = 0f
        globalCritChanceBonus = 0f
        globalDamageBonus = 0f
        globalFireRateBonus = 0f
        globalPickupRadiusBonus = 0f
        globalDifficultyBonus = 0f
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
            is UpgradeOption.WeaponUpgrade -> {
                applyWeaponUpgrade(option.weaponKind, option.upgrade, option.steps)
            }

            is UpgradeOption.AddPassive -> {
                if (passives.size < maxPassives && passives.none { it.kind == option.passiveKind }) {
                    passives += PassiveInstance(option.passiveKind, level = 1)
                }
            }

            is UpgradeOption.UpgradePassive -> {
                passives.firstOrNull { it.kind == option.passiveKind }?.let { it.level += option.steps }
            }

            is UpgradeOption.WeaponMod -> {
                applyWeaponMod(option.weaponKind, option.mod)
            }
        }
    }

    fun applyGlobalBonus(option: GlobalBonusOption) {
        when (option.kind) {
            GlobalBonusKind.ELITE_FREQUENCY -> eliteFrequencyBonus += option.amount
            GlobalBonusKind.DIFFICULTY -> globalDifficultyBonus += option.amount
            GlobalBonusKind.CRIT_CHANCE -> globalCritChanceBonus += option.amount
            GlobalBonusKind.DAMAGE -> globalDamageBonus += option.amount
            GlobalBonusKind.FIRE_RATE -> globalFireRateBonus += option.amount
            GlobalBonusKind.PICKUP_RADIUS -> globalPickupRadiusBonus += option.amount
        }
    }

    fun getEliteFrequencyMultiplier(): Float = 1f + eliteFrequencyBonus

    fun getDifficultyMultiplier(): Float = 1f + globalDifficultyBonus

    /**
     * Награды чуть растут от сложности, чтобы было зачем её брать.
     * (Мягко: +difficulty * 0.75)
     */
    fun getRewardMultiplier(): Float = 1f + globalDifficultyBonus * 0.75f

    private fun applyWeaponUpgrade(kind: WeaponKind, up: UpgradeOption.WeaponUpgradeKind, steps: Int) {
        val w = weapons.firstOrNull { it.kind == kind } ?: return
        when (up) {
            UpgradeOption.WeaponUpgradeKind.DAMAGE -> w.damageLevel += steps
            UpgradeOption.WeaponUpgradeKind.FIRE_RATE -> w.cooldownLevel += steps
            UpgradeOption.WeaponUpgradeKind.PROJECTILE_SPEED -> w.projectileSpeedLevel += steps
            UpgradeOption.WeaponUpgradeKind.ACCURACY -> w.accuracyLevel += steps
            UpgradeOption.WeaponUpgradeKind.RANGE -> w.rangeLevel += steps
        }
        // Внутренний “ранг” (для отладки). В UI больше не используем.
        w.level += steps
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
        return (1f + lvl * 0.12f) * (1f + globalDamageBonus)
    }

    fun getFireRateMultiplier(): Float {
        val lvl = passives.firstOrNull { it.kind == PassiveKind.FIRE_RATE }?.level ?: 0
        return (1f + lvl * 0.10f) * (1f + globalFireRateBonus)
    }

    fun getCritChance(): Float {
        val lvl = passives.firstOrNull { it.kind == PassiveKind.CRIT_CHANCE }?.level ?: 0
        return (max(0f, 0.02f * lvl) + globalCritChanceBonus).coerceAtMost(0.85f)
    }

    fun getCritDamageMultiplier(): Float {
        val lvl = passives.firstOrNull { it.kind == PassiveKind.CRIT_DAMAGE }?.level ?: 0
        return 1.5f + lvl * 0.15f
    }

    fun getMagnetRadiusPx(): Float {
        // Радиус притяжения/подбора XP в world-space (у нас ~px).
        // База достаточная, чтобы “чувствовалось”, дальше — ощутимый рост.
        val lvl = passives.firstOrNull { it.kind == PassiveKind.MAGNET }?.level ?: 0
        val base = 140f + lvl * 40f
        return base * (1f + globalPickupRadiusBonus)
    }

    private fun nextXpGoal(level: Int): Int {
        // Очень простая кривая: растёт плавно
        return 10 + (level * 3)
    }
}

