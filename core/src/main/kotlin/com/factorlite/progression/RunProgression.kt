package com.factorlite.progression

import com.factorlite.content.Balance
import kotlin.math.max
import kotlin.math.pow

class RunProgression {
    var character: CharacterKind = CharacterKind.FROZKA
        private set

    /**
     * Врождёнка прокачивается автоматически каждый раз при получении уровня.
     * Храним отдельно, чтобы не путать с уровнем игрока.
     */
    var innateLevel: Int = 0
        private set

    var level: Int = 1
        private set

    var xp: Int = 0
        private set

    var xpToNext: Int = 12
        private set

    val weapons = mutableListOf<WeaponInstance>()
    val rings = mutableListOf<RingInstance>()

    // Глобальные бонусы (из святынь), мультипликативные/аддитивные, не занимают слоты.
    private var eliteFrequencyBonus: Float = 0f      // +% к шансу элит
    private var globalCritChanceBonus: Float = 0f     // +% к critChance
    private var globalDamageBonus: Float = 0f         // +% к урону
    private var globalFireRateBonus: Float = 0f       // +% к скорости атаки
    private var globalPickupRadiusBonus: Float = 0f   // +% к радиусу подбора (магнит)
    private var globalDifficultyBonus: Float = 0f     // +% к сложности (риск/награда)

    // Слоты на релиз: 2+2
    val maxWeapons = 2
    val maxRings = 2

    init {
        // Подтянем кривую из конфига сразу (Balance уже грузится при старте игры).
        xpToNext = nextXpGoal(level)
    }

    fun reset() {
        level = 1
        xp = 0
        xpToNext = nextXpGoal(level)
        weapons.clear()
        rings.clear()
        innateLevel = 0
        eliteFrequencyBonus = 0f
        globalCritChanceBonus = 0f
        globalDamageBonus = 0f
        globalFireRateBonus = 0f
        globalPickupRadiusBonus = 0f
        globalDifficultyBonus = 0f
    }

    fun setCharacter(kind: CharacterKind) {
        character = kind
    }

    fun addXp(amount: Int): Boolean {
        if (amount <= 0) return false
        // Кольцо разума: множитель опыта.
        val scaled = (amount.toFloat() * getXpGainMultiplier()).toInt().coerceAtLeast(1)
        xp += scaled
        var leveled = false
        while (xp >= xpToNext) {
            xp -= xpToNext
            level += 1
            innateLevel += 1
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

            is UpgradeOption.AddRing -> {
                if (rings.size < maxRings && rings.none { it.kind == option.ringKind }) {
                    rings += RingInstance(option.ringKind, level = 1)
                }
            }

            is UpgradeOption.UpgradeRing -> {
                rings.firstOrNull { it.kind == option.ringKind }?.let { it.level += option.steps }
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
        val rb = Balance.cfg.rings
        val lvl = rings.firstOrNull { it.kind == RingKind.SPEED }?.level ?: 0
        val ringMul = 1f + lvl * rb.speedPerLevel
        val innateMul = 1f + getInnateMoveSpeedBonus()
        return ringMul * innateMul
    }

    fun getDamageMultiplier(): Float {
        val rb = Balance.cfg.rings
        val lvl = rings.firstOrNull { it.kind == RingKind.DAMAGE }?.level ?: 0
        return (1f + lvl * rb.damagePerLevel) * (1f + globalDamageBonus)
    }

    fun getDamageMultiplierForWeapon(kind: WeaponKind): Float {
        val base = getDamageMultiplier()
        val v = getInnateValue()
        val typeBonus = when (character.innate) {
            InnateKind.MAGIC_DAMAGE -> if (kind.isMagic) v else 0f
            InnateKind.PHYSICAL_DAMAGE -> if (!kind.isMagic) v else 0f
            else -> 0f
        }
        return base * (1f + typeBonus)
    }

    fun getFireRateMultiplier(): Float {
        val rb = Balance.cfg.rings
        val lvl = rings.firstOrNull { it.kind == RingKind.QUICK_HAND }?.level ?: 0
        return (1f + lvl * rb.quickHandPerLevel) * (1f + globalFireRateBonus)
    }

    fun getCritChance(): Float {
        val rb = Balance.cfg.rings
        val lvl = rings.firstOrNull { it.kind == RingKind.LUCKY }?.level ?: 0
        return (max(0f, rb.critChancePerLevel * lvl) + globalCritChanceBonus).coerceAtMost(rb.critChanceCap)
    }

    fun getCritDamageMultiplier(): Float {
        // По дизайну: базовый крит-урон x2.
        return 2.0f
    }

    fun getMagnetRadiusPx(): Float {
        val rb = Balance.cfg.rings
        val lvl = rings.firstOrNull { it.kind == RingKind.MAGNET }?.level ?: 0
        val mul = 1f + lvl * rb.magnetPerLevel
        return rb.magnetBase * mul * (1f + globalPickupRadiusBonus)
    }

    private fun nextXpGoal(level: Int): Int {
        val pb = Balance.cfg.progression
        val l = (level - 1).coerceAtLeast(0).toFloat()
        val need = pb.xpToNextBase + pb.xpToNextPerLevel * l.pow(pb.xpToNextPow.coerceAtLeast(0.05f))
        return need.toInt().coerceAtLeast(8)
    }

    fun getDodgeChance(): Float {
        val rb = Balance.cfg.rings
        val lvl = rings.firstOrNull { it.kind == RingKind.WIND }?.level ?: 0
        return (getInnateDodgeChanceBonus() + lvl * rb.dodgePerLevel).coerceIn(0f, rb.dodgeChanceCap)
    }

    fun getLifeStealPct(): Float {
        val rb = Balance.cfg.rings
        val lvl = rings.firstOrNull { it.kind == RingKind.VAMPIRE }?.level ?: 0
        return (lvl * rb.lifestealPerLevel).coerceIn(0f, rb.lifestealCap)
    }

    fun getMaxHpMultiplier(): Float {
        val rb = Balance.cfg.rings
        val lvl = rings.firstOrNull { it.kind == RingKind.VITALITY }?.level ?: 0
        return (1f + lvl * rb.maxHpPerLevel).coerceIn(0.5f, rb.maxHpMulCap)
    }

    fun getXpGainMultiplier(): Float {
        val rb = Balance.cfg.rings
        val lvl = rings.firstOrNull { it.kind == RingKind.MIND }?.level ?: 0
        return (1f + lvl * rb.xpGainPerLevel).coerceIn(0.2f, rb.xpGainMulCap)
    }

    fun getInnateValue(): Float = (innateLevel.coerceAtLeast(0) * character.innatePerLevel).coerceAtLeast(0f)

    fun getInnateMoveSpeedBonus(): Float {
        if (character.innate != InnateKind.MOVE_SPEED) return 0f
        return getInnateValue().coerceAtMost(0.45f)
    }

    // Заготовки под будущие системы (пока не используются).
    fun getInnateSlowBonus(): Float =
        if (character.innate == InnateKind.SLOW_ENEMIES) getInnateValue().coerceAtMost(0.45f) else 0f

    fun getInnateDotSpeedBonus(): Float =
        if (character.innate == InnateKind.DOT_SPEED) getInnateValue().coerceAtMost(0.80f) else 0f

    fun getInnateAreaSizeBonus(): Float =
        if (character.innate == InnateKind.AREA_SIZE) getInnateValue().coerceAtMost(0.90f) else 0f

    fun getInnateDodgeChanceBonus(): Float =
        if (character.innate == InnateKind.DODGE_CHANCE) getInnateValue().coerceAtMost(0.35f) else 0f
}

