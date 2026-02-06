package com.factorlite.progression

/**
 * Персонажи (пока без UI выбора).
 *
 * Важно: некоторые врождёнки (замедление/ДОТ/уклонение/размер области) ещё не реализованы в боёвке,
 * поэтому сейчас они прокачиваются "за кадром" как числа, но не влияют на геймплей.
 */
enum class Gender {
    M, F
}

enum class InnateKind {
    SLOW_ENEMIES,      // замедление противников
    DOT_SPEED,         // скорость срабатывания ДОТОК
    MOVE_SPEED,        // скорость передвижения
    AREA_SIZE,         // размер области (ловушки/снаряды)
    DODGE_CHANCE,      // шанс уклониться
    PHYSICAL_DAMAGE,   // + физический урон
    MAGIC_DAMAGE,      // + магический урон
}

val InnateKind.uiName: String
    get() = when (this) {
        InnateKind.SLOW_ENEMIES -> "Замедление врагов"
        InnateKind.DOT_SPEED -> "Скорость ДОТ"
        InnateKind.MOVE_SPEED -> "Скорость передвижения"
        InnateKind.AREA_SIZE -> "Размер области"
        InnateKind.DODGE_CHANCE -> "Шанс уклонения"
        InnateKind.PHYSICAL_DAMAGE -> "Физический урон"
        InnateKind.MAGIC_DAMAGE -> "Магический урон"
    }

enum class CharacterKind(
    val uiName: String,
    val gender: Gender,
    val startWeapon: WeaponKind,
    val innate: InnateKind,
    /**
     * Сколько добавляется за каждый уровень персонажа (авто-ап).
     * Например 0.02 == +2% за уровень.
     */
    val innatePerLevel: Float,
) {
    // По docs/персы.txt. Второе оружие НЕ фиксируем — игрок выбирает его на уровне.
    FROZKA(
        uiName = "Фрозка",
        gender = Gender.F,
        startWeapon = WeaponKind.FROSTSTAFF,
        innate = InnateKind.SLOW_ENEMIES,
        innatePerLevel = 0.02f,
    ),
    FARZER(
        uiName = "Фарзер",
        gender = Gender.M,
        startWeapon = WeaponKind.FIRESTAFF,
        innate = InnateKind.DOT_SPEED,
        innatePerLevel = 0.02f,
    ),
    JOE(
        uiName = "Джо",
        gender = Gender.M,
        startWeapon = WeaponKind.REVOLVER,
        innate = InnateKind.MOVE_SPEED,
        innatePerLevel = 0.02f,
    ),
    TRAPPER(
        uiName = "Трапер",
        gender = Gender.M,
        startWeapon = WeaponKind.POISON_TRAP,
        innate = InnateKind.AREA_SIZE,
        innatePerLevel = 0.02f,
    ),
    NIKUMI(
        uiName = "Никуми",
        gender = Gender.F,
        startWeapon = WeaponKind.KATANA,
        innate = InnateKind.DODGE_CHANCE,
        innatePerLevel = 0.015f,
    ),
    SAPKA(
        uiName = "Сапка",
        gender = Gender.F,
        startWeapon = WeaponKind.DAGGER,
        innate = InnateKind.PHYSICAL_DAMAGE,
        innatePerLevel = 0.015f,
    ),
    ZHIDJ(
        uiName = "Жидж",
        gender = Gender.M,
        startWeapon = WeaponKind.POISON_AURA,
        innate = InnateKind.MAGIC_DAMAGE,
        innatePerLevel = 0.015f,
    ),
}

/**
 * Ключ текстуры персонажа: `assets/textures/<key>.png`
 * Пример: `player_frozka.png`
 */
val CharacterKind.playerSpriteKey: String
    get() = "player_${name.lowercase()}"

val WeaponKind.isMagic: Boolean
    get() = when (this) {
        WeaponKind.FROSTSTAFF,
        WeaponKind.FIRESTAFF,
        WeaponKind.POISON_TRAP,
        WeaponKind.POISON_AURA,
        -> true

        WeaponKind.REVOLVER,
        WeaponKind.KATANA,
        WeaponKind.DAGGER,
        -> false
    }

