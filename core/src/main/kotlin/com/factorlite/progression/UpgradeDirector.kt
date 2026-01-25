package com.factorlite.progression

import com.badlogic.gdx.math.MathUtils
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
            val k = p.weapons.firstOrNull()?.kind ?: WeaponKind.BLASTER
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

        val hasWeaponSlot = p.weapons.size < p.maxWeapons
        val hasPassiveSlot = p.passives.size < p.maxPassives

        // --- Weapons ---
        if (hasWeaponSlot) {
            for (k in WeaponKind.entries) {
                if (p.weapons.any { it.kind == k }) continue
                out += Weighted(
                    option = UpgradeOption.AddWeapon(k),
                    weight = 1.2f,
                    key = "addWeapon:$k",
                    isRelevant = true,
                )
            }
        }

        for (w in p.weapons) {
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
            when (w.kind) {
                WeaponKind.BLASTER -> {
                    addUp(UpgradeOption.WeaponUpgradeKind.DAMAGE, 3.0f, cap = 10)
                    addUp(UpgradeOption.WeaponUpgradeKind.FIRE_RATE, 3.0f, cap = 10)
                    addUp(UpgradeOption.WeaponUpgradeKind.ACCURACY, 2.2f, cap = 8)
                    addUp(UpgradeOption.WeaponUpgradeKind.PROJECTILE_SPEED, 2.0f, cap = 8)
                    addUp(UpgradeOption.WeaponUpgradeKind.RANGE, 1.6f, cap = 6)
                }
                WeaponKind.REVOLVER -> {
                    addUp(UpgradeOption.WeaponUpgradeKind.DAMAGE, 3.0f, cap = 10)
                    addUp(UpgradeOption.WeaponUpgradeKind.FIRE_RATE, 3.0f, cap = 10)
                    addUp(UpgradeOption.WeaponUpgradeKind.ACCURACY, 2.0f, cap = 8)
                    addUp(UpgradeOption.WeaponUpgradeKind.PROJECTILE_SPEED, 2.0f, cap = 8)
                    addUp(UpgradeOption.WeaponUpgradeKind.RANGE, 1.6f, cap = 6)
                }
                WeaponKind.SWORD -> {
                    addUp(UpgradeOption.WeaponUpgradeKind.DAMAGE, 3.2f, cap = 10)
                    addUp(UpgradeOption.WeaponUpgradeKind.FIRE_RATE, 2.6f, cap = 10)
                    addUp(UpgradeOption.WeaponUpgradeKind.RANGE, 2.2f, cap = 10)
                }
            }

            // Моды оружия (вес поменьше, чем общий уровень)
            if (w.extraLevel < 2) {
                val rarity = rollRarity()
                out += Weighted(
                    UpgradeOption.WeaponMod(
                        weaponKind = w.kind,
                        mod = WeaponModKind.EXTRA,
                        rarity = rarity,
                        levelLabel = "LVL ${w.extraLevel + 1}",
                        statLine = when (w.kind) {
                            WeaponKind.SWORD -> "Цели: ${1 + w.extraLevel} → ${2 + w.extraLevel}"
                            else -> "Снаряды: ${1 + w.extraLevel} → ${2 + w.extraLevel}"
                        },
                    ),
                    3.2f,
                    "mod:${w.kind}:EXTRA",
                    true,
                )
            }
            if (w.ricochetLevel < 2) {
                val rarity = rollRarity()
                out += Weighted(
                    UpgradeOption.WeaponMod(
                        weaponKind = w.kind,
                        mod = WeaponModKind.RICOCHET,
                        rarity = rarity,
                        levelLabel = "LVL ${w.ricochetLevel + 1}",
                        statLine = if (w.kind == WeaponKind.SWORD) {
                            "Цепь: ${w.ricochetLevel} → ${w.ricochetLevel + 1}"
                        } else {
                            "Рикошет: ${w.ricochetLevel} → ${w.ricochetLevel + 1}"
                        },
                    ),
                    2.8f,
                    "mod:${w.kind}:RICOCHET",
                    true,
                )
            }
            if (w.pierceLevel < 2) {
                val rarity = rollRarity()
                out += Weighted(
                    UpgradeOption.WeaponMod(
                        weaponKind = w.kind,
                        mod = WeaponModKind.PIERCE,
                        rarity = rarity,
                        levelLabel = "LVL ${w.pierceLevel + 1}",
                        statLine = if (w.kind == WeaponKind.SWORD) {
                            "Дальность: ${w.pierceLevel} → ${w.pierceLevel + 1}"
                        } else {
                            "Пробивание: ${w.pierceLevel} → ${w.pierceLevel + 1}"
                        },
                    ),
                    2.8f,
                    "mod:${w.kind}:PIERCE",
                    true,
                )
            }
        }

        // --- Passives ---
        if (hasPassiveSlot) {
            for (k in PassiveKind.entries) {
                if (p.passives.any { it.kind == k }) continue
                out += Weighted(
                    option = UpgradeOption.AddPassive(k),
                    weight = 1.1f,
                    key = "addPassive:$k",
                    isRelevant = true,
                )
            }
        }

        for (ps in p.passives) {
            val rarity = rollRarity()
            val steps = rarity.steps
            val levelLabel = "LVL ${ps.level + steps}"
            val statLine = passivePreviewLine(p, ps.kind, ps.level, steps)
            out += Weighted(
                option = UpgradeOption.UpgradePassive(ps.kind, rarity, steps, levelLabel, statLine),
                weight = 4.0f,
                key = "upPassive:${ps.kind}",
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
        return when (w.kind) {
            WeaponKind.BLASTER -> when (up) {
                UpgradeOption.WeaponUpgradeKind.DAMAGE -> {
                    val a = 10f + w.damageLevel * 2.2f
                    val b = 10f + (w.damageLevel + steps) * 2.2f
                    "Урон: ${fmt(a)} → ${fmt(b)}"
                }
                UpgradeOption.WeaponUpgradeKind.FIRE_RATE -> {
                    val a = 0.35f / (1f + 0.08f * w.cooldownLevel)
                    val b = 0.35f / (1f + 0.08f * (w.cooldownLevel + steps))
                    "КД: ${fmt(a)}s → ${fmt(b)}s"
                }
                UpgradeOption.WeaponUpgradeKind.PROJECTILE_SPEED -> {
                    val a = 780f + w.projectileSpeedLevel * 30f
                    val b = 780f + (w.projectileSpeedLevel + steps) * 30f
                    "Скорость: ${fmt(a)} → ${fmt(b)}"
                }
                UpgradeOption.WeaponUpgradeKind.ACCURACY -> {
                    val base = 0.10f
                    val a = (0.88f.pow(w.accuracyLevel.toFloat()))
                    val b = (0.88f.pow((w.accuracyLevel + steps).toFloat()))
                    val ap = (a * 100f).toInt()
                    val bp = (b * 100f).toInt()
                    "Разброс: ${ap}% → ${bp}%"
                }
                UpgradeOption.WeaponUpgradeKind.RANGE -> {
                    val a = 520f + w.rangeLevel * 35f
                    val b = 520f + (w.rangeLevel + steps) * 35f
                    "Дальность: ${fmt(a)} → ${fmt(b)}"
                }
            }
            WeaponKind.REVOLVER -> when (up) {
                UpgradeOption.WeaponUpgradeKind.DAMAGE -> {
                    val a = 7.5f + w.damageLevel * 1.6f
                    val b = 7.5f + (w.damageLevel + steps) * 1.6f
                    "Урон: ${fmt(a)} → ${fmt(b)}"
                }
                UpgradeOption.WeaponUpgradeKind.FIRE_RATE -> {
                    val a = 0.65f / (1f + 0.08f * w.cooldownLevel)
                    val b = 0.65f / (1f + 0.08f * (w.cooldownLevel + steps))
                    "КД: ${fmt(a)}s → ${fmt(b)}s"
                }
                UpgradeOption.WeaponUpgradeKind.PROJECTILE_SPEED -> {
                    val a = 820f + w.projectileSpeedLevel * 32f
                    val b = 820f + (w.projectileSpeedLevel + steps) * 32f
                    "Скорость: ${fmt(a)} → ${fmt(b)}"
                }
                UpgradeOption.WeaponUpgradeKind.ACCURACY -> {
                    val a = (0.90f.pow(w.accuracyLevel.toFloat()))
                    val b = (0.90f.pow((w.accuracyLevel + steps).toFloat()))
                    val ap = (a * 100f).toInt()
                    val bp = (b * 100f).toInt()
                    "Разброс: ${ap}% → ${bp}%"
                }
                UpgradeOption.WeaponUpgradeKind.RANGE -> {
                    val a = 520f + w.rangeLevel * 35f
                    val b = 520f + (w.rangeLevel + steps) * 35f
                    "Дальность: ${fmt(a)} → ${fmt(b)}"
                }
            }
            WeaponKind.SWORD -> when (up) {
                UpgradeOption.WeaponUpgradeKind.DAMAGE -> {
                    val a = 14f + w.damageLevel * 3.0f
                    val b = 14f + (w.damageLevel + steps) * 3.0f
                    "Урон: ${fmt(a)} → ${fmt(b)}"
                }
                UpgradeOption.WeaponUpgradeKind.FIRE_RATE -> {
                    val a = 0.85f / (1f + 0.08f * w.cooldownLevel)
                    val b = 0.85f / (1f + 0.08f * (w.cooldownLevel + steps))
                    "КД: ${fmt(a)}s → ${fmt(b)}s"
                }
                UpgradeOption.WeaponUpgradeKind.RANGE -> {
                    val a = 110f + w.rangeLevel * 8f
                    val b = 110f + (w.rangeLevel + steps) * 8f
                    "Радиус: ${fmt(a)} → ${fmt(b)}"
                }
                else -> "—"
            }
        }
    }

    private fun passivePreviewLine(p: RunProgression, kind: PassiveKind, currentLevel: Int, steps: Int): String {
        fun pct(x: Float): Int = (x * 100f).toInt()
        return when (kind) {
            PassiveKind.DAMAGE -> {
                val cur = p.getDamageMultiplier()
                val a = 1f + currentLevel * 0.12f
                val g = cur / max(0.0001f, a)
                val next = (1f + (currentLevel + steps) * 0.12f) * g
                "Урон: ${pct(cur - 1f)}% → ${pct(next - 1f)}%"
            }
            PassiveKind.FIRE_RATE -> {
                val cur = p.getFireRateMultiplier()
                val a = 1f + currentLevel * 0.10f
                val g = cur / max(0.0001f, a)
                val next = (1f + (currentLevel + steps) * 0.10f) * g
                "Скорость атаки: ${pct(cur - 1f)}% → ${pct(next - 1f)}%"
            }
            PassiveKind.MOVE_SPEED -> {
                val cur = 1f + currentLevel * 0.06f
                val next = 1f + (currentLevel + steps) * 0.06f
                "Скорость: ${pct(cur - 1f)}% → ${pct(next - 1f)}%"
            }
            PassiveKind.CRIT_CHANCE -> {
                val curTotal = p.getCritChance()
                val g = (curTotal - 0.02f * currentLevel).coerceAtLeast(0f)
                val next = (0.02f * (currentLevel + steps) + g).coerceAtMost(0.85f)
                "Шанс крита: ${pct(curTotal)}% → ${pct(next)}%"
            }
            PassiveKind.CRIT_DAMAGE -> {
                val cur = 1.5f + currentLevel * 0.15f
                val next = 1.5f + (currentLevel + steps) * 0.15f
                "Крит. урон: ${String.format("%.2f", cur)}x → ${String.format("%.2f", next)}x"
            }
            PassiveKind.MAGNET -> {
                val cur = p.getMagnetRadiusPx()
                val base = 140f + currentLevel * 40f
                val g = cur / max(0.0001f, base)
                val next = (140f + (currentLevel + steps) * 40f) * g
                "Радиус: ${cur.toInt()} → ${next.toInt()}"
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

