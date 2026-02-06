package com.factorlite.content

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json

/**
 * Баланс-конфиг, который грузится из `assets/data/balance.json`.
 *
 * Важно: дефолты совпадают с текущими “зашитыми” цифрами, чтобы поведение
 * не менялось, если файл отсутствует/битый.
 */
object Balance {
    @JvmStatic
    var cfg: BalanceConfig = BalanceConfig()
        private set

    @JvmStatic
    fun loadOrKeepDefaults() {
        try {
            val f = Gdx.files.internal("data/balance.json")
            if (!f.exists()) {
                Gdx.app?.log("Balance", "No data/balance.json found; using defaults.")
                return
            }
            val json = Json().apply {
                // если в json появятся новые поля — старые версии не должны падать
                setIgnoreUnknownFields(true)
            }
            cfg = json.fromJson(BalanceConfig::class.java, f)
            Gdx.app?.log("Balance", "Loaded data/balance.json")
        } catch (t: Throwable) {
            Gdx.app?.error("Balance", "Failed to load data/balance.json; using defaults.", t)
            cfg = BalanceConfig()
        }
    }
}

data class BalanceConfig(
    val targeting: TargetingBalance = TargetingBalance(),
    val weapons: WeaponsBalance = WeaponsBalance(),
    val rings: RingsBalance = RingsBalance(),
    val progression: ProgressionBalance = ProgressionBalance(),
    val cards: UpgradeCardsBalance = UpgradeCardsBalance(),
    val spawning: SpawningBalance = SpawningBalance(),
    val combat: CombatBalance = CombatBalance(),
    val shrines: ShrinesBalance = ShrinesBalance(),
)

data class TargetingBalance(
    val baseRange: Float = 520f,
    val rangePerLevel: Float = 35f,
)

data class WeaponsBalance(
    val froststaff: ProjectileWeaponBalance = ProjectileWeaponBalance(
        baseDamage = 8f,
        damagePerLevel = 1.8f,
        baseCooldownSec = 0.42f,
        cooldownLevelFactor = 0.08f,
        projectileSpeedBase = 720f,
        projectileSpeedPerLevel = 28f,
        spreadBaseRad = 0.10f,
        accuracyPowBase = 0.90f,
        targetAcquireRange = 520f,
    ),
    val firestaff: ProjectileWeaponBalance = ProjectileWeaponBalance(
        baseDamage = 6f,
        damagePerLevel = 1.4f,
        baseCooldownSec = 0.40f,
        cooldownLevelFactor = 0.08f,
        projectileSpeedBase = 720f,
        projectileSpeedPerLevel = 28f,
        spreadBaseRad = 0.10f,
        accuracyPowBase = 0.90f,
        targetAcquireRange = 520f,
    ),
    val revolver: ProjectileWeaponBalance = ProjectileWeaponBalance(
        baseDamage = 7.5f,
        damagePerLevel = 1.6f,
        baseCooldownSec = 0.65f,
        cooldownLevelFactor = 0.08f,
        projectileSpeedBase = 820f,
        projectileSpeedPerLevel = 32f,
        spreadBaseRad = 0f,
        accuracyPowBase = 0.90f,
        targetAcquireRange = 560f,
    ),
    val poisonTrap: ProjectileWeaponBalance = ProjectileWeaponBalance(
        baseDamage = 3.0f,
        damagePerLevel = 1.0f,
        baseCooldownSec = 1.10f,
        cooldownLevelFactor = 0.08f,
        projectileSpeedBase = 600f,
        projectileSpeedPerLevel = 20f,
        spreadBaseRad = 0f,
        accuracyPowBase = 0.92f,
        targetAcquireRange = 420f,
    ),
    val katana: SwordBalance = SwordBalance(
        baseDamage = 14f,
        damagePerLevel = 2.0f,
        baseCooldownSec = 0.55f,
        cooldownLevelFactor = 0.08f,
        swingRangeBase = 120f,
        swingRangePerLevel = 10f,
        pierceRangePerLevel = 18f,
    ),
    val dagger: SwordBalance = SwordBalance(
        baseDamage = 9f,
        damagePerLevel = 1.4f,
        baseCooldownSec = 0.28f,
        cooldownLevelFactor = 0.08f,
        swingRangeBase = 95f,
        swingRangePerLevel = 8f,
        pierceRangePerLevel = 14f,
    ),
    val poisonAura: SwordBalance = SwordBalance(
        baseDamage = 2f,
        damagePerLevel = 0.6f,
        baseCooldownSec = 0.10f,
        cooldownLevelFactor = 0.08f,
        swingRangeBase = 70f,
        swingRangePerLevel = 10f,
        pierceRangePerLevel = 0f,
    ),
)

data class ProjectileWeaponBalance(
    // Важно для libGDX Json: нужен no-arg ctor -> все поля с дефолтами.
    val baseDamage: Float = 10f,
    val damagePerLevel: Float = 2.2f,
    val baseCooldownSec: Float = 0.35f,
    val cooldownLevelFactor: Float = 0.08f,
    val projectileSpeedBase: Float = 780f,
    val projectileSpeedPerLevel: Float = 30f,
    val spreadBaseRad: Float = 0f,
    val accuracyPowBase: Float = 0.90f,
    val targetAcquireRange: Float = 560f,
)

data class SwordBalance(
    val baseDamage: Float = 14f,
    val damagePerLevel: Float = 3.0f,
    val baseCooldownSec: Float = 0.85f,
    val cooldownLevelFactor: Float = 0.08f,
    val swingRangeBase: Float = 110f,
    val swingRangePerLevel: Float = 8f,
    val pierceRangePerLevel: Float = 18f,
)

data class RingsBalance(
    // Кольцо скорости
    val speedPerLevel: Float = 0.06f,
    // Кольцо ветра
    val dodgePerLevel: Float = 0.02f,
    val dodgeChanceCap: Float = 0.80f,
    // Кольцо вампира
    val lifestealPerLevel: Float = 0.005f, // 0.5% за уровень
    val lifestealCap: Float = 0.08f,
    // Кольцо везучести
    val critChancePerLevel: Float = 0.02f,
    val critChanceCap: Float = 0.85f,
    // Кольцо жизненной силы
    val maxHpPerLevel: Float = 0.08f,
    val maxHpMulCap: Float = 2.5f,
    // Кольцо урона
    val damagePerLevel: Float = 0.12f,
    // Кольцо быстрой руки
    val quickHandPerLevel: Float = 0.10f,
    // Магнитное кольцо
    val magnetBase: Float = 140f,
    val magnetPerLevel: Float = 0.22f, // +22% за уровень
    // Кольцо разума
    val xpGainPerLevel: Float = 0.08f,
    val xpGainMulCap: Float = 3.0f,
)

data class ProgressionBalance(
    // XP -> уровень: xpToNext = base + perLevel * (level-1)^pow
    // Чем выше pow — тем быстрее разгон “стоимости” уровней.
    val xpToNextBase: Float = 10f,
    val xpToNextPerLevel: Float = 3f,
    val xpToNextPow: Float = 1.0f,
)

data class UpgradeCardsBalance(
    val addWeaponWeight: Float = 1.2f,
    val addRingWeight: Float = 1.1f,
    val ringUpgradeWeight: Float = 4.0f,
    // Универсальные веса/капы карточек оружия (одни и те же для всех новых оружий).
    // Для мили accuracy/projectileSpeed игнорируются (caps можно оставить 0).
    val weapon: WeaponCardsBalance = WeaponCardsBalance(
        damageWeight = 2.2f, damageCap = 10,
        fireRateWeight = 2.2f, fireRateCap = 10,
        accuracyWeight = 1.6f, accuracyCap = 8,
        projectileSpeedWeight = 1.6f, projectileSpeedCap = 8,
        rangeWeight = 1.4f, rangeCap = 10,
    ),
    val modExtraWeight: Float = 3.2f,
    val modExtraCap: Int = 2,
    val modRicochetWeight: Float = 2.8f,
    val modRicochetCap: Int = 2,
    val modPierceWeight: Float = 2.8f,
    val modPierceCap: Int = 2,
)

data class WeaponCardsBalance(
    val damageWeight: Float = 3.0f,
    val damageCap: Int = 10,
    val fireRateWeight: Float = 3.0f,
    val fireRateCap: Int = 10,
    val projectileSpeedWeight: Float = 2.0f,
    val projectileSpeedCap: Int = 8,
    val accuracyWeight: Float = 2.0f,
    val accuracyCap: Int = 8,
    val rangeWeight: Float = 1.6f,
    val rangeCap: Int = 6,
)

data class SpawningBalance(
    val spawnDirector: SpawnDirectorBalance = SpawnDirectorBalance(),
    val enemy: EnemySpawningBalance = EnemySpawningBalance(),
)

data class SpawnDirectorBalance(
    val initialDelay: Float = 0.2f,
    val maxSpawnsPerFrame: Int = 10,
    val minInterval: Float = 0.14f,
    val maxInterval: Float = 0.95f,
    val rampSeconds: Float = 160f,
    val rampPow: Float = 1.0f,
    // Доп. кривая “мягкий старт”: interval умножается на startSoftMul и плавно уходит к 1.0 за startSoftSeconds.
    val startSoftSeconds: Float = 35f,
    val startSoftMul: Float = 1.35f,
    // Спайк плотности перед боссом: interval умножается на endSpikeMul (меньше -> чаще спавн) и плавно за endSpikeRampSeconds.
    val endSpikeStartSec: Float = 240f,
    val endSpikeRampSeconds: Float = 35f,
    val endSpikeMul: Float = 0.75f,
    val crowdDiv: Float = 160f,
    val crowdMulCap: Float = 3.8f,
)

data class EnemySpawningBalance(
    val spawnPad: Float = 40f,
    val typeChances: EnemyTypeChances = EnemyTypeChances(),
    val difficulty: EnemyDifficultyBalance = EnemyDifficultyBalance(),
    val base: EnemyBaseBalance = EnemyBaseBalance(),
    val rangedCombat: RangedCombatBalance = RangedCombatBalance(),
    val elite: EliteBalance = EliteBalance(),
    val boss: BossBalance = BossBalance(),
)

data class EnemyTypeChances(
    val fastRampSeconds: Float = 120f,
    val fastCap: Float = 0.40f,
    val tankRampSeconds: Float = 150f,
    val tankCap: Float = 0.28f,
    val rangedRampSeconds: Float = 180f,
    val rangedCap: Float = 0.28f,
)

data class EnemyDifficultyBalance(
    val timeMulBonusAtEnd: Float = 0.70f,
    val speedDiffFactor: Float = 0.15f,
    val rewardTimeFactor: Float = 0.35f,
)

data class EnemyBaseBalance(
    val normal: EnemyStats = EnemyStats(hp = 40f, speed = 105f, contactDamage = 10f, radius = 15f, xp = 1, gold = 1),
    val fast: EnemyStats = EnemyStats(hp = 28f, speed = 150f, contactDamage = 8f, radius = 13f, xp = 2, gold = 2),
    val tank: EnemyStats = EnemyStats(hp = 70f, speed = 80f, contactDamage = 12f, radius = 18f, xp = 3, gold = 3),
    val ranged: RangedEnemyStats = RangedEnemyStats(
        hp = 34f,
        speed = 92f,
        contactDamage = 8f,
        radius = 14f,
        xp = 2,
        gold = 2,
        shootCooldownMin = 0.2f,
        shootCooldownMax = 1.0f,
    ),
)

data class EnemyStats(
    // Важно для libGDX Json: нужен no-arg ctor -> все поля с дефолтами.
    val hp: Float = 40f,
    val speed: Float = 105f,
    val contactDamage: Float = 10f,
    val radius: Float = 15f,
    val xp: Int = 1,
    val gold: Int = 1,
)

data class RangedEnemyStats(
    // Важно для libGDX Json: нужен no-arg ctor -> все поля с дефолтами.
    val hp: Float = 34f,
    val speed: Float = 92f,
    val contactDamage: Float = 8f,
    val radius: Float = 14f,
    val xp: Int = 2,
    val gold: Int = 2,
    val shootCooldownMin: Float = 0.2f,
    val shootCooldownMax: Float = 1.0f,
)

data class RangedCombatBalance(
    // Дальники: держат дистанцию в пределах, где могут стрелять.
    // Если shootRange уменьшить, важно уменьшать и desiredMaxDist, иначе дальник будет стоять "слишком далеко" и не стрелять.
    val desiredMinDist: Float = 120f,
    val desiredMaxDist: Float = 240f,
    val shootRange: Float = 260f,
    val projectileSpeed: Float = 420f,
    val projectileRadius: Float = 5f,
    val baseDamage: Float = 9f,
    val scaleDamageWithDifficulty: Boolean = false,
    val baseCooldownSec: Float = 1.35f,
    val cooldownTimeRampSeconds: Float = 160f,
    val cooldownMulCap: Float = 1.7f,
    val eliteExtraShotChance: Float = 0.12f,
    val eliteCooldownMul: Float = 0.85f,
)

data class EliteBalance(
    val pBaseStart: Float = 0.006f,
    val pBaseRampAdd: Float = 0.004f,
    val rampSeconds: Float = 240f,
    val pBaseCap: Float = 0.02f,
    val pFinalCap: Float = 0.08f,
    val hpMul: Float = 2.6f,
    val speedMul: Float = 1.05f,
    val contactDamageMul: Float = 1.35f,
    val radiusMul: Float = 1.15f,
    val xpMul: Int = 5,
    val goldMul: Int = 6,
    val rangedShootCooldownMin: Float = 0.25f,
    val rangedShootCooldownMax: Float = 0.9f,
)

data class BossBalance(
    val spawnOffsetX: Float = 360f,
    val hp: Float = 5200f,
    val speed: Float = 78f,
    val contactDamage: Float = 22f,
    val radius: Float = 26f,
    val cleanupChestsKeepEliteOnly: Boolean = true,
)

data class CombatBalance(
    val projectiles: ProjectileBalance = ProjectileBalance(),
)

data class ProjectileBalance(
    val playerArenaMargin: Float = 50f,
    val enemyArenaMargin: Float = 80f,
    val ricochetMaxAcquireRange: Float = 700f,
    val ricochetMinSpeed: Float = 500f,
)

data class ShrinesBalance(
    val maxOnMap: Int = 2,
    val firstSpawnAt: Float = 35f,
    val nextIntervalMin: Float = 55f,
    val nextIntervalMax: Float = 85f,
    val spawnDistMin: Float = 260f,
    val spawnDistMax: Float = 420f,
    val edgeMargin: Float = 70f,
)
