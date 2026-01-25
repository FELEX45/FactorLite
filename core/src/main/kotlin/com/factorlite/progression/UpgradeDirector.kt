package com.factorlite.progression

import com.badlogic.gdx.math.MathUtils

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
            return listOf(UpgradeOption.UpgradeWeapon(p.weapons.firstOrNull()?.kind ?: WeaponKind.BLASTER))
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
            out += Weighted(
                option = UpgradeOption.UpgradeWeapon(w.kind),
                weight = 5.0f,
                key = "upWeapon:${w.kind}",
                isRelevant = true,
            )

            // Моды оружия (вес поменьше, чем общий уровень)
            if (w.extraLevel < 2) out += Weighted(UpgradeOption.WeaponMod(w.kind, WeaponModKind.EXTRA), 2.2f, "mod:${w.kind}:EXTRA", true)
            if (w.ricochetLevel < 2) out += Weighted(UpgradeOption.WeaponMod(w.kind, WeaponModKind.RICOCHET), 2.0f, "mod:${w.kind}:RICOCHET", true)
            if (w.pierceLevel < 2) out += Weighted(UpgradeOption.WeaponMod(w.kind, WeaponModKind.PIERCE), 2.0f, "mod:${w.kind}:PIERCE", true)
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
            out += Weighted(
                option = UpgradeOption.UpgradePassive(ps.kind),
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

