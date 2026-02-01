package com.factorlite.progression

import com.badlogic.gdx.math.MathUtils
import com.factorlite.content.Balance
import kotlin.math.max
import kotlin.math.pow

/**
 * Генератор 3 карточек апгрейда.
 * Цели:
 * - не давать 3 мусорных опции
 * - не дублировать одинаковые карточки
 * - чуть “направлять” билд: апгрейды текущего чаще, новые — когда есть слот
 */
object UpgradeDirector {
    private data class Weighted(
        val option: UpgradeOption,
        val weight: Float,
        val key: String,
        val isRelevant: Boolean,
    )

    fun makeChoices(p: RunProgression): List<UpgradeOption> {
        val cands = buildCandidates(p)
        if (cands.isEmpty()) {
            // Фоллбек: хоть что-то
            val k = p.weapons.firstOrNull()?.kind ?: WeaponKind.FROSTSTAFF
            val rarity = UpgradeRarity.COMMON
            val steps = rarity.steps
            val w = p.weapons.firstOrNull { it.kind == k } ?: WeaponInstance(k, level = 1)
            val statLine = weaponPreviewLine(p, w, UpgradeOption.WeaponUpgradeKind.DAMAGE, steps)
            return listOf(UpgradeOption.WeaponUpgrade(k, UpgradeOption.WeaponUpgradeKind.DAMAGE, rarity, steps, "LVL ${w.level + steps}", statLine))
        }

        val picked = sampleWithoutReplacement(cands, count = 3)

        // Гарантия: хотя бы 1 релевантная карточка
        if (picked.none { it.isRelevant }) {
            val forced = cands.firstOrNull { it.isRelevant } ?: cands.first()
            if (picked.isNotEmpty()) picked[picked.lastIndex] = forced
        }

        return picked.map { it.option }
    }

    private fun buildCandidates(p: RunProgression): MutableList<Weighted> {
        val out = ArrayList<Weighted>(64)
        val cb = Balance.cfg.cards

        val hasWeaponSlot = p.weapons.size < p.maxWeapons
        val hasRingSlot = p.rings.size < p.maxRings

        // --- Weapons ---
        if (hasWeaponSlot) {
            // Игрок сам выбирает второе оружие на уровне: предлагаем все оружия, которых ещё нет.
            for (k in WeaponKind.entries) {
                if (p.weapons.any { it.kind == k }) continue
                out += Weighted(
                    option = UpgradeOption.AddWeapon(k),
                    weight = cb.addWeaponWeight,
                    key = "addWeapon:$k",
                    isRelevant = true,
                )
            }
        }

        for (w in p.weapons) {
            val wc = cb.weapon
            // Вместо "Улучшить оружие" выдаём конкретные апгрейды паттерна.
            fun addUp(up: UpgradeOption.WeaponUpgradeKind, weight: Float, cap: Int) {
                val current = when (up) {
                    UpgradeOption.WeaponUpgradeKind.DAMAGE -> w.damageLevel
                    UpgradeOption.WeaponUpgradeKind.FIRE_RATE -> w.cooldownLevel
                    UpgradeOption.WeaponUpgradeKind.PROJECTILE_SPEED -> w.projectileSpeedLevel
                    UpgradeOption.WeaponUpgradeKind.ACCURACY -> w.accuracyLevel
                    UpgradeOption.WeaponUpgradeKind.RANGE -> w.rangeLevel
                }
                if (current >= cap) return
                val rarity = rollRarity()
                val steps = rarity.steps
                val levelLabel = "LVL ${w.level + steps}"
                val statLine = weaponPreviewLine(p, w, up, steps)
                out += Weighted(
                    option = UpgradeOption.WeaponUpgrade(w.kind, up, rarity, steps, levelLabel, statLine),
                    weight = weight,
                    key = "wup:${w.kind}:$up",
                    isRelevant = true,
                )
            }

            // Базовые апгрейды паттерна
            addUp(UpgradeOption.WeaponUpgradeKind.DAMAGE, wc.damageWeight, cap = wc.damageCap)
            addUp(UpgradeOption.WeaponUpgradeKind.FIRE_RATE, wc.fireRateWeight, cap = wc.fireRateCap)
            addUp(UpgradeOption.WeaponUpgradeKind.ACCURACY, wc.accuracyWeight, cap = wc.accuracyCap)
            addUp(UpgradeOption.WeaponUpgradeKind.PROJECTILE_SPEED, wc.projectileSpeedWeight, cap = wc.projectileSpeedCap)
            addUp(UpgradeOption.WeaponUpgradeKind.RANGE, wc.rangeWeight, cap = wc.rangeCap)

            // Моды оружия (вес поменьше, чем общий уровень)
            if (w.extraLevel < cb.modExtraCap) {
                val rarity = rollRarity()
                out += Weighted(
                    UpgradeOption.WeaponMod(
                        weaponKind = w.kind,
                        mod = WeaponModKind.EXTRA,
                        rarity = rarity,
                        levelLabel = "LVL ${w.extraLevel + 1}",
                        statLine = when (w.kind) {
                            WeaponKind.KATANA, WeaponKind.DAGGER, WeaponKind.POISON_AURA -> "Цели: ${1 + w.extraLevel} → ${2 + w.extraLevel}"
                            else -> "Снаряды: ${1 + w.extraLevel} → ${2 + w.extraLevel}"
                        },
                    ),
                    cb.modExtraWeight,
                    "mod:${w.kind}:EXTRA",
                    true,
                )
            }
            if (w.ricochetLevel < cb.modRicochetCap) {
                val rarity = rollRarity()
                out += Weighted(
                    UpgradeOption.WeaponMod(
                        weaponKind = w.kind,
                        mod = WeaponModKind.RICOCHET,
                        rarity = rarity,
                        levelLabel = "LVL ${w.ricochetLevel + 1}",
                        statLine = if (w.kind == WeaponKind.KATANA || w.kind == WeaponKind.DAGGER || w.kind == WeaponKind.POISON_AURA) {
                            "Цепь: ${w.ricochetLevel} → ${w.ricochetLevel + 1}"
                        } else {
                            "Рикошет: ${w.ricochetLevel} → ${w.ricochetLevel + 1}"
                        },
                    ),
                    cb.modRicochetWeight,
                    "mod:${w.kind}:RICOCHET",
                    true,
                )
            }
            if (w.pierceLevel < cb.modPierceCap) {
                val rarity = rollRarity()
                out += Weighted(
                    UpgradeOption.WeaponMod(
                        weaponKind = w.kind,
                        mod = WeaponModKind.PIERCE,
                        rarity = rarity,
                        levelLabel = "LVL ${w.pierceLevel + 1}",
                        statLine = if (w.kind == WeaponKind.KATANA || w.kind == WeaponKind.DAGGER || w.kind == WeaponKind.POISON_AURA) {
                            "Дальность: ${w.pierceLevel} → ${w.pierceLevel + 1}"
                        } else {
                            "Пробивание: ${w.pierceLevel} → ${w.pierceLevel + 1}"
                        },
                    ),
                    cb.modPierceWeight,
                    "mod:${w.kind}:PIERCE",
                    true,
                )
            }
        }

        // --- Rings ---
        if (hasRingSlot) {
            for (k in RingKind.entries) {
                if (p.rings.any { it.kind == k }) continue
                out += Weighted(
                    option = UpgradeOption.AddRing(k),
                    weight = cb.addRingWeight,
                    key = "addRing:$k",
                    isRelevant = true,
                )
            }
        }

        for (ps in p.rings) {
            val rarity = rollRarity()
            val steps = rarity.steps
            val levelLabel = "LVL ${ps.level + steps}"
            val statLine = ringPreviewLine(p, ps.kind, ps.level, steps)
            out += Weighted(
                option = UpgradeOption.UpgradeRing(ps.kind, rarity, steps, levelLabel, statLine),
                weight = cb.ringUpgradeWeight,
                key = "upRing:${ps.kind}",
                isRelevant = true,
            )
        }

        // Убираем дубликаты по key (на случай будущих расширений)
        val seen = HashSet<String>(out.size)
        val unique = ArrayList<Weighted>(out.size)
        for (w in out) {
            if (seen.add(w.key)) unique.add(w)
        }
        return unique
    }

    private fun rollRarity(): UpgradeRarity {
        val r = MathUtils.random()
        var acc = 0f
        for (rar in UpgradeRarity.entries) {
            acc += rar.weight
            if (r <= acc) return rar
        }
        return UpgradeRarity.COMMON
    }

    private fun weaponPreviewLine(p: RunProgression, w: WeaponInstance, up: UpgradeOption.WeaponUpgradeKind, steps: Int): String {
        fun fmt(v: Float): String = if (v >= 10f) v.toInt().toString() else String.format("%.2f", v)
        val melee = (w.kind == WeaponKind.KATANA || w.kind == WeaponKind.DAGGER || w.kind == WeaponKind.POISON_AURA)
        if (melee) {
            val wb = when (w.kind) {
                WeaponKind.KATANA -> Balance.cfg.weapons.katana
                WeaponKind.DAGGER -> Balance.cfg.weapons.dagger
                WeaponKind.POISON_AURA -> Balance.cfg.weapons.poisonAura
                else -> Balance.cfg.weapons.katana
            }
            return when (up) {
                UpgradeOption.WeaponUpgradeKind.DAMAGE -> {
                    val a = wb.baseDamage + w.damageLevel * wb.damagePerLevel
                    val b = wb.baseDamage + (w.damageLevel + steps) * wb.damagePerLevel
                    "Урон: ${fmt(a)} → ${fmt(b)}"
                }
                UpgradeOption.WeaponUpgradeKind.FIRE_RATE -> {
                    val a = wb.baseCooldownSec / (1f + wb.cooldownLevelFactor * w.cooldownLevel)
                    val b = wb.baseCooldownSec / (1f + wb.cooldownLevelFactor * (w.cooldownLevel + steps))
                    "КД: ${fmt(a)}s → ${fmt(b)}s"
                }
                UpgradeOption.WeaponUpgradeKind.RANGE -> {
                    val a = wb.swingRangeBase + w.rangeLevel * wb.swingRangePerLevel
                    val b = wb.swingRangeBase + (w.rangeLevel + steps) * wb.swingRangePerLevel
                    "Радиус: ${fmt(a)} → ${fmt(b)}"
                }
                else -> "—"
            }
        }

        val wb = when (w.kind) {
            WeaponKind.FROSTSTAFF -> Balance.cfg.weapons.froststaff
            WeaponKind.FIRESTAFF -> Balance.cfg.weapons.firestaff
            WeaponKind.REVOLVER -> Balance.cfg.weapons.revolver
            WeaponKind.POISON_TRAP -> Balance.cfg.weapons.poisonTrap
            else -> Balance.cfg.weapons.froststaff
        }
        return when (up) {
            UpgradeOption.WeaponUpgradeKind.DAMAGE -> {
                val a = wb.baseDamage + w.damageLevel * wb.damagePerLevel
                val b = wb.baseDamage + (w.damageLevel + steps) * wb.damagePerLevel
                "Урон: ${fmt(a)} → ${fmt(b)}"
            }
            UpgradeOption.WeaponUpgradeKind.FIRE_RATE -> {
                val a = wb.baseCooldownSec / (1f + wb.cooldownLevelFactor * w.cooldownLevel)
                val b = wb.baseCooldownSec / (1f + wb.cooldownLevelFactor * (w.cooldownLevel + steps))
                "КД: ${fmt(a)}s → ${fmt(b)}s"
            }
            UpgradeOption.WeaponUpgradeKind.PROJECTILE_SPEED -> {
                val a = wb.projectileSpeedBase + w.projectileSpeedLevel * wb.projectileSpeedPerLevel
                val b = wb.projectileSpeedBase + (w.projectileSpeedLevel + steps) * wb.projectileSpeedPerLevel
                "Скорость: ${fmt(a)} → ${fmt(b)}"
            }
            UpgradeOption.WeaponUpgradeKind.ACCURACY -> {
                val a = (wb.accuracyPowBase.pow(w.accuracyLevel.toFloat()))
                val b = (wb.accuracyPowBase.pow((w.accuracyLevel + steps).toFloat()))
                val ap = (a * 100f).toInt()
                val bp = (b * 100f).toInt()
                "Разброс: ${ap}% → ${bp}%"
            }
            UpgradeOption.WeaponUpgradeKind.RANGE -> {
                val tb = Balance.cfg.targeting
                val a = tb.baseRange + w.rangeLevel * tb.rangePerLevel
                val b = tb.baseRange + (w.rangeLevel + steps) * tb.rangePerLevel
                "Дальность: ${fmt(a)} → ${fmt(b)}"
            }
        }
    }

    private fun ringPreviewLine(p: RunProgression, kind: RingKind, currentLevel: Int, steps: Int): String {
        fun pct(x: Float): Int = (x * 100f).toInt()
        val rb = Balance.cfg.rings
        return when (kind) {
            RingKind.DAMAGE -> {
                val cur = p.getDamageMultiplier()
                val a = 1f + currentLevel * rb.damagePerLevel
                val g = cur / max(0.0001f, a)
                val next = (1f + (currentLevel + steps) * rb.damagePerLevel) * g
                "Урон: ${pct(cur - 1f)}% → ${pct(next - 1f)}%"
            }
            RingKind.QUICK_HAND -> {
                val cur = p.getFireRateMultiplier()
                val a = 1f + currentLevel * rb.quickHandPerLevel
                val g = cur / max(0.0001f, a)
                val next = (1f + (currentLevel + steps) * rb.quickHandPerLevel) * g
                "Скорость атаки: ${pct(cur - 1f)}% → ${pct(next - 1f)}%"
            }
            RingKind.SPEED -> {
                val cur = 1f + currentLevel * rb.speedPerLevel
                val next = 1f + (currentLevel + steps) * rb.speedPerLevel
                "Скорость: ${pct(cur - 1f)}% → ${pct(next - 1f)}%"
            }
            RingKind.LUCKY -> {
                val curTotal = p.getCritChance()
                val g = (curTotal - rb.critChancePerLevel * currentLevel).coerceAtLeast(0f)
                val next = (rb.critChancePerLevel * (currentLevel + steps) + g).coerceAtMost(rb.critChanceCap)
                "Шанс крита: ${pct(curTotal)}% → ${pct(next)}% (крит x2)"
            }
            RingKind.WIND -> {
                val cur = (p.getInnateDodgeChanceBonus() + currentLevel * rb.dodgePerLevel).coerceAtMost(rb.dodgeChanceCap)
                val next = (p.getInnateDodgeChanceBonus() + (currentLevel + steps) * rb.dodgePerLevel).coerceAtMost(rb.dodgeChanceCap)
                "Уклонение: ${pct(cur)}% → ${pct(next)}%"
            }
            RingKind.VAMPIRE -> {
                val cur = (currentLevel * rb.lifestealPerLevel).coerceAtMost(rb.lifestealCap)
                val next = ((currentLevel + steps) * rb.lifestealPerLevel).coerceAtMost(rb.lifestealCap)
                "Вампиризм: ${pct(cur)}% → ${pct(next)}%"
            }
            RingKind.VITALITY -> {
                val baseHp = 100f
                val curMul = (1f + currentLevel * rb.maxHpPerLevel).coerceAtMost(rb.maxHpMulCap)
                val nextMul = (1f + (currentLevel + steps) * rb.maxHpPerLevel).coerceAtMost(rb.maxHpMulCap)
                "HP: ${(baseHp * curMul).toInt()} → ${(baseHp * nextMul).toInt()}"
            }
            RingKind.MAGNET -> {
                val cur = p.getMagnetRadiusPx()
                val curMul = 1f + currentLevel * rb.magnetPerLevel
                val nextMul = 1f + (currentLevel + steps) * rb.magnetPerLevel
                "Радиус: ${cur.toInt()} → ${(rb.magnetBase * nextMul).toInt()}"
            }
            RingKind.MIND -> {
                val cur = (1f + currentLevel * rb.xpGainPerLevel).coerceAtMost(rb.xpGainMulCap)
                val next = (1f + (currentLevel + steps) * rb.xpGainPerLevel).coerceAtMost(rb.xpGainMulCap)
                "Опыт: ${pct(cur - 1f)}% → ${pct(next - 1f)}%"
            }
        }
    }

    private fun sampleWithoutReplacement(cands: List<Weighted>, count: Int): MutableList<Weighted> {
        val pool = cands.toMutableList()
        val result = ArrayList<Weighted>(count)

        repeat(count) {
            if (pool.isEmpty()) return@repeat
            val total = pool.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.0001f)
            var r = MathUtils.random() * total
            var idx = pool.lastIndex
            for (i in pool.indices) {
                r -= pool[i].weight
                if (r <= 0f) {
                    idx = i
                    break
                }
            }
            result += pool.removeAt(idx)
        }

        // Добиваем до count (на всякий)
        while (result.size < count && pool.isNotEmpty()) {
            result += pool.removeAt(0)
        }
        return result
    }
}

