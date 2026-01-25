package com.factorlite.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.InputAdapter
import com.factorlite.game.RunState
import com.factorlite.game.LootSystem
import com.factorlite.game.SpawnDirector
import com.factorlite.game.CombatSystem
import com.factorlite.game.Enemy
import com.factorlite.game.EnemyKind
import com.factorlite.game.EnemySystem
import com.factorlite.game.TargetingSystem
import com.factorlite.input.FloatingJoystick
import com.factorlite.loot.ItemInstance
import com.factorlite.loot.ItemOption
import com.factorlite.loot.ItemTriggerSystem
import com.factorlite.progression.RunProgression
import com.factorlite.progression.UpgradeOption
import com.factorlite.progression.WeaponKind
import com.factorlite.progression.uiName
import kotlin.math.max
import kotlin.math.sqrt

class GameScreen : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(960f, 540f, camera)

    private val shapes = ShapeRenderer()

    private val uiCamera = OrthographicCamera()
    private val uiViewport = ScreenViewport(uiCamera)
    private val batch = SpriteBatch()
    private var font: BitmapFont = BitmapFont()

    private val joystick = FloatingJoystick(
        deadzonePx = 12f,
        radiusPx = 110f,
    )
    private val inputMux = InputMultiplexer()
    private val levelUpInput = LevelUpInput()

    private val playerPos = Vector2(0f, 0f)
    private val playerVel = Vector2(0f, 0f)

    private val basePlayerSpeed = 260f
    private val playerRadius = 14f
    private var playerHp = 100f
    private var playerInvuln = 0f
    private var runTime = 0f
    private var runState: RunState = RunState.RUNNING

    private val progression = RunProgression()
    private var pendingChoices: List<UpgradeOption> = emptyList()
    private val levelUpCards = Array(3) { Rectangle() }
    private val tmpVec = Vector2()

    private var pendingChestChoices: List<ItemOption> = emptyList()
    private val items = ArrayList<ItemInstance>()
    private val loot = LootSystem()
    private val combat = CombatSystem()
    private val enemySystem = EnemySystem()
    private val targetingSystem = TargetingSystem()

    private val itemSystem = ItemTriggerSystem()

    private val enemies = ArrayList<Enemy>()

    // retargetTimer перенесён в TargetingSystem

    private val spawnDirector = SpawnDirector()

    // Простая арена (пока)
    private val arenaHalfW = 900f
    private val arenaHalfH = 500f

    override fun show() {
        inputMux.clear()
        inputMux.addProcessor(levelUpInput)
        inputMux.addProcessor(joystick)
        Gdx.input.inputProcessor = inputMux
        rebuildFont()
        resetRun()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        uiViewport.update(width, height, true)
        rebuildFont()
        layoutLevelUpCards()
    }

    override fun render(delta: Float) {
        when (runState) {
            RunState.RUNNING -> update(delta)
            RunState.LEVEL_UP -> handleLevelUpInput()
            RunState.CHEST_OPEN -> handleChestInput()
            RunState.GAME_OVER -> {
                if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.R) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
                    resetRun()
                }
            }
        }

        Gdx.gl.glClearColor(0.07f, 0.08f, 0.10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        shapes.projectionMatrix = camera.combined

        // Мир
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.DARK_GRAY
        shapes.rect(-arenaHalfW, -arenaHalfH, arenaHalfW * 2f, arenaHalfH * 2f)
        shapes.end()

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Игрок мигает при неуязвимости
        val invulnT = (playerInvuln / 0.6f).coerceIn(0f, 1f)
        val a = if (playerInvuln > 0f) 0.35f + 0.65f * (1f - Interpolation.fade.apply(invulnT)) else 1f
        shapes.color = Color(1f, 1f, 1f, a)
        shapes.circle(playerPos.x, playerPos.y, playerRadius, 20)

        // Враги
        for (e in enemies) {
            shapes.color = if (e.isElite) Color(0.95f, 0.25f, 0.95f, 1f) else Color(0.95f, 0.25f, 0.25f, 1f)
            shapes.circle(e.pos.x, e.pos.y, e.radius, 18)
        }

        // XP-сферы
        shapes.color = Color(0.35f, 0.95f, 0.95f, 1f)
        for (o in loot.xpOrbs) {
            shapes.circle(o.pos.x, o.pos.y, 6.5f, 14)
        }

        // Монетки (gold)
        shapes.color = Color(1f, 0.85f, 0.25f, 1f)
        for (o in loot.goldOrbs) {
            shapes.circle(o.pos.x, o.pos.y, 5.5f, 14)
        }

        // Сундуки
        for (c in loot.chests) {
            shapes.color = if (c.isElite) Color(1f, 0.85f, 0.25f, 1f) else Color(0.25f, 0.75f, 1f, 1f)
            shapes.circle(c.pos.x, c.pos.y, c.radius, 18)
        }

        // Токсичное облако (визуал)
        if (itemSystem.toxicActive()) {
            shapes.color = Color(0.3f, 1f, 0.3f, 0.25f)
            shapes.circle(playerPos.x, playerPos.y, itemSystem.toxicRadius(), 36)
        }

        // Снаряды
        shapes.color = Color(1f, 0.9f, 0.2f, 1f)
        for (p in combat.projectiles) {
            shapes.circle(p.pos.x, p.pos.y, p.radius, 12)
        }

        // Вражеские снаряды
        shapes.color = Color(0.95f, 0.55f, 1f, 1f)
        for (p in combat.enemyProjectiles) {
            shapes.circle(p.pos.x, p.pos.y, p.radius, 12)
        }
        shapes.end()

        // Молнии (визуал, короткий)
        if (itemSystem.lightningFx.isNotEmpty()) {
            shapes.begin(ShapeRenderer.ShapeType.Line)
            shapes.color = Color(0.7f, 0.9f, 1f, 0.85f)
            for (fx in itemSystem.lightningFx) {
                shapes.line(fx.a.x, fx.a.y, fx.b.x, fx.b.y)
            }
            shapes.end()
        }

        // Подсветка цели
        targetingSystem.target?.let { t ->
            shapes.begin(ShapeRenderer.ShapeType.Line)
            shapes.color = Color(0.3f, 1f, 0.3f, 1f)
            shapes.circle(t.pos.x, t.pos.y, t.radius + 6f, 24)
            shapes.line(playerPos.x, playerPos.y, t.pos.x, t.pos.y)
            shapes.end()
        }

        // Джойстик (в screen-space)
        if (runState == RunState.RUNNING) drawJoystickOverlay()

        // HUD (screen-space)
        drawHud()
    }

    private fun update(delta: Float) {
        runTime += delta
        playerInvuln = max(0f, playerInvuln - delta)
        itemSystem.update(delta)

        // joystick.direction сейчас в screen-space, Y у libGDX для touch идёт сверху вниз
        // Поэтому инвертируем Y.
        val playerSpeed = basePlayerSpeed * progression.getMoveSpeedMultiplier()
        playerVel.set(joystick.direction.x, -joystick.direction.y).scl(playerSpeed)
        playerPos.mulAdd(playerVel, delta)

        // Ограничение арены
        playerPos.x = MathUtils.clamp(playerPos.x, -arenaHalfW + playerRadius, arenaHalfW - playerRadius)
        playerPos.y = MathUtils.clamp(playerPos.y, -arenaHalfH + playerRadius, arenaHalfH - playerRadius)

        updateSpawns(delta)
        enemySystem.updateEnemies(
            delta = delta,
            runTime = runTime,
            playerPos = playerPos,
            enemies = enemies,
            spawnEnemyShot = { pos, dir, damage, speed, radius ->
                combat.spawnEnemyProjectile(
                    CombatSystem.EnemyProjectile(
                        pos = pos,
                        vel = dir.scl(speed),
                        damage = damage,
                        radius = radius,
                    ),
                )
            },
        )
        loot.updateChestSpawns(delta, arenaHalfW, arenaHalfH, playerPos)
        val leveledUp = loot.updateXpOrbs(delta, playerPos, progression)
        loot.updateGoldOrbs(delta, playerPos, progression)
        itemSystem.applyToxicDamage(
            delta = delta,
            playerPos = playerPos,
            enemies = enemies,
            isAlive = { it.hp > 0f },
            getPos = { it.pos },
            damageEnemy = { e, dmg -> e.hp -= dmg },
        )
        applyContactDamage()
        updateEnemyProjectiles(delta)
        loot.tryOpenChestByProximity(playerPos)?.let { res ->
            pendingChestChoices = res.choices
            runState = RunState.CHEST_OPEN
            layoutLevelUpCards()
        }

        // Камера следует за игроком
        camera.position.set(playerPos.x, playerPos.y, 0f)
        camera.update()

        targetingSystem.update(delta, playerPos, enemies)
        updateAttacking(delta)
        updateProjectiles(delta)
        cleanupDead()

        if (leveledUp && runState == RunState.RUNNING) {
            pendingChoices = progression.makeUpgradeChoices()
            runState = RunState.LEVEL_UP
            layoutLevelUpCards()
        }
    }

    private fun resetRun() {
        runState = RunState.RUNNING
        runTime = 0f
        playerHp = 100f
        playerInvuln = 0f
        playerPos.set(0f, 0f)
        playerVel.set(0f, 0f)

        progression.reset()
        progression.weapons += com.factorlite.progression.WeaponInstance(WeaponKind.BLASTER, level = 1)
        pendingChoices = emptyList()
        pendingChestChoices = emptyList()
        items.clear()
        itemSystem.reset()
        loot.reset()

        enemies.clear()
        combat.reset()
        targetingSystem.reset()
        spawnDirector.reset(initialDelay = 0.2f)

        // Стартовая “дыра”, чтобы сразу было что стрелять (через 0.2с спавнится первый враг)
    }

    private fun updateSpawns(delta: Float) {
        val spawns = spawnDirector.update(delta = delta, runTime = runTime, aliveEnemies = enemies.size)
        repeat(spawns) {
            spawnEnemy()
        }
    }

    private fun spawnEnemy() {
        // Спавнимся по краям арены (слегка за пределом)
        val pad = 40f
        val side = MathUtils.random(3)
        val x: Float
        val y: Float
        when (side) {
            0 -> { // left
                x = -arenaHalfW - pad
                y = MathUtils.random(-arenaHalfH, arenaHalfH)
            }
            1 -> { // right
                x = arenaHalfW + pad
                y = MathUtils.random(-arenaHalfH, arenaHalfH)
            }
            2 -> { // bottom
                x = MathUtils.random(-arenaHalfW, arenaHalfW)
                y = -arenaHalfH - pad
            }
            else -> { // top
                x = MathUtils.random(-arenaHalfW, arenaHalfW)
                y = arenaHalfH + pad
            }
        }

        // Тип врага: чем больше runTime, тем чаще быстрые/танки/дальники
        val pFast = (runTime / 120f).coerceIn(0f, 0.30f)
        val pTank = (runTime / 150f).coerceIn(0f, 0.22f)
        val pRanged = (runTime / 180f).coerceIn(0f, 0.22f)
        val roll = MathUtils.random()

        val baseEnemy = when {
            roll < pTank ->
                Enemy(
                    Vector2(x, y),
                    hp = 70f,
                    speed = 80f,
                    contactDamage = 12f,
                    radius = 18f,
                    xpReward = 3,
                    goldReward = 3,
                    kind = EnemyKind.TANK,
                )
            roll < pTank + pFast ->
                Enemy(
                    Vector2(x, y),
                    hp = 28f,
                    speed = 150f,
                    contactDamage = 8f,
                    radius = 13f,
                    xpReward = 2,
                    goldReward = 2,
                    kind = EnemyKind.FAST,
                )
            roll < pTank + pFast + pRanged ->
                Enemy(
                    Vector2(x, y),
                    hp = 34f,
                    speed = 92f,
                    contactDamage = 8f,
                    radius = 14f,
                    xpReward = 2,
                    goldReward = 2,
                    kind = EnemyKind.RANGED,
                    shootCooldown = MathUtils.random(0.2f, 1.0f),
                )
            else ->
                Enemy(
                    Vector2(x, y),
                    hp = 40f,
                    speed = 105f,
                    contactDamage = 10f,
                    radius = 15f,
                    xpReward = 1,
                    goldReward = 1,
                    kind = EnemyKind.NORMAL,
                )
        }

        // Элитка: редкий “пик” — сильнее и гарантирует сундук.
        val pElite = (0.006f + (runTime / 240f) * 0.004f).coerceAtMost(0.02f) // ~0.6% -> 2%
        val makeElite = MathUtils.random() < pElite
        val enemy = if (!makeElite) baseEnemy else {
            baseEnemy.copy(
                hp = baseEnemy.hp * 2.6f,
                speed = baseEnemy.speed * 1.05f,
                contactDamage = baseEnemy.contactDamage * 1.35f,
                radius = baseEnemy.radius * 1.15f,
                xpReward = baseEnemy.xpReward * 5,
                goldReward = baseEnemy.goldReward * 6,
                isElite = true,
                // чуть меньше “раскидываем” дальника, чтобы элитка чаще дралась рядом
                shootCooldown = if (baseEnemy.kind == EnemyKind.RANGED) MathUtils.random(0.25f, 0.9f) else baseEnemy.shootCooldown,
            )
        }

        enemies.add(enemy)
    }

    // updateEnemies/fireEnemyShot вынесены в EnemySystem

    private fun updateEnemyProjectiles(delta: Float) {
        combat.updateEnemyProjectiles(
            delta = delta,
            arenaHalfW = arenaHalfW,
            arenaHalfH = arenaHalfH,
            playerPos = playerPos,
            playerRadius = playerRadius,
            canDamagePlayer = playerInvuln <= 0f,
            onHitPlayer = { dmg ->
                if (itemSystem.onPlayerHit().blocked) return@updateEnemyProjectiles false
                playerHp -= dmg
                playerInvuln = 0.6f
                if (playerHp <= 0f) {
                    playerHp = 0f
                    runState = RunState.GAME_OVER
                }
                true
            },
        )
    }

    private fun applyContactDamage() {
        if (playerInvuln > 0f) return
        for (e in enemies) {
            if (e.hp <= 0f) continue
            val dx = e.pos.x - playerPos.x
            val dy = e.pos.y - playerPos.y
            val r = e.radius + playerRadius
            if (dx * dx + dy * dy <= r * r) {
                // Предметы (щит/токсики) — через общую систему триггеров.
                if (itemSystem.onPlayerHit().blocked) return

                playerHp -= e.contactDamage
                playerInvuln = 0.6f

                if (playerHp <= 0f) {
                    playerHp = 0f
                    runState = RunState.GAME_OVER
                }
                return
            }
        }
    }

    // updateTargeting вынесен в TargetingSystem

    private fun updateAttacking(delta: Float) {
        val fireRateMul = progression.getFireRateMultiplier()
        val dmgMul = progression.getDamageMultiplier()
        val critChance = progression.getCritChance()
        val critMul = progression.getCritDamageMultiplier()

        // Каждый слот оружия атакует независимо
        for (w in progression.weapons) {
            w.cooldown = max(0f, w.cooldown - delta)
            if (w.cooldown > 0f) continue

            when (w.kind) {
                WeaponKind.BLASTER -> {
                    val t = targetingSystem.target ?: continue
                    fireBlaster(w, t, dmgMul, critChance, critMul, fireRateMul)
                }

                WeaponKind.REVOLVER -> {
                    fireRevolver(w, dmgMul, critChance, critMul, fireRateMul)
                }

                WeaponKind.SWORD -> {
                    swingSword(w, dmgMul, critChance, critMul, fireRateMul)
                }
            }
        }
    }

    private fun fireBlaster(
        w: com.factorlite.progression.WeaponInstance,
        t: Enemy,
        dmgMul: Float,
        critChance: Float,
        critMul: Float,
        fireRateMul: Float,
    ) {
        val dir = Vector2(t.pos.x - playerPos.x, t.pos.y - playerPos.y)
        if (dir.isZero(0.0001f)) return
        dir.nor()

        val speed = 780f
        val baseDamage = 10f + (w.level - 1) * 2.2f
        val extra = w.extraLevel
        val count = 1 + extra
        val spread = 0.10f
        for (i in 0 until count) {
            val angle = (i - (count - 1) / 2f) * spread
            val shotDir = Vector2(dir).rotateRad(angle)
            var dmg = baseDamage * dmgMul
            if (MathUtils.random() < critChance) dmg *= critMul
            combat.spawnProjectile(
                CombatSystem.Projectile(
                    pos = Vector2(playerPos.x, playerPos.y),
                    vel = shotDir.scl(speed),
                    damage = dmg,
                    source = WeaponKind.BLASTER,
                    pierceLeft = w.pierceLevel,
                    ricochetLeft = w.ricochetLevel,
                ),
            )
        }

        val baseCd = 0.35f
        w.cooldown = baseCd / fireRateMul
    }

    private fun fireRevolver(
        w: com.factorlite.progression.WeaponInstance,
        dmgMul: Float,
        critChance: Float,
        critMul: Float,
        fireRateMul: Float,
    ) {
        // Стреляет несколькими пулями по ближайшим целям
        if (enemies.isEmpty()) return

        val shots = ((1 + (w.level / 2)) + w.extraLevel).coerceIn(1, 7)
        val candidates = pickNearestEnemies(shots, range = 560f)
        if (candidates.isEmpty()) return

        val speed = 820f
        val baseDamage = 7.5f + (w.level - 1) * 1.6f

        for (e in candidates) {
            val dir = Vector2(e.pos.x - playerPos.x, e.pos.y - playerPos.y)
            if (dir.isZero(0.0001f)) continue
            dir.nor()
            var dmg = baseDamage * dmgMul
            if (MathUtils.random() < critChance) dmg *= critMul
            combat.spawnProjectile(
                CombatSystem.Projectile(
                    pos = Vector2(playerPos.x, playerPos.y),
                    vel = dir.scl(speed),
                    damage = dmg,
                    source = WeaponKind.REVOLVER,
                    pierceLeft = w.pierceLevel,
                    ricochetLeft = w.ricochetLevel,
                ),
            )
        }

        val baseCd = 0.65f
        w.cooldown = baseCd / fireRateMul
    }

    private fun swingSword(
        w: com.factorlite.progression.WeaponInstance,
        dmgMul: Float,
        critChance: Float,
        critMul: Float,
        fireRateMul: Float,
    ) {
        // Ближняя атака по нескольким ближайшим в радиусе
        val range = 110f + w.pierceLevel * 18f
        val hits = ((1 + (w.level / 2)) + w.extraLevel).coerceIn(1, 8)
        val candidates = pickNearestEnemies(hits, range = range)
        if (candidates.isEmpty()) return

        val baseDamage = 14f + (w.level - 1) * 3.0f
        for (e in candidates) {
            var dmg = baseDamage * dmgMul
            if (MathUtils.random() < critChance) dmg *= critMul
            e.hp -= dmg
            itemSystem.onEnemyHit(
                hit = e,
                playerPos = playerPos,
                isAlive = { it.hp > 0f },
                getPos = { it.pos },
                damageEnemy = { en, d -> en.hp -= d },
                findNearest = ::findNearestEnemyFromPointExcludingVisited,
            )
        }

        val baseCd = 0.85f
        w.cooldown = baseCd / fireRateMul
    }

    private fun pickNearestEnemies(maxCount: Int, range: Float): List<Enemy> {
        val range2 = range * range
        // Простой топ-K без сортировки всего списка
        val best = ArrayList<Enemy>(maxCount)
        val bestD2 = FloatArray(maxCount) { Float.POSITIVE_INFINITY }

        for (e in enemies) {
            if (e.hp <= 0f) continue
            val dx = e.pos.x - playerPos.x
            val dy = e.pos.y - playerPos.y
            val d2 = dx * dx + dy * dy
            if (d2 > range2) continue

            // вставка в массив лучших
            var idx = -1
            for (i in 0 until maxCount) {
                if (d2 < bestD2[i]) {
                    idx = i
                    break
                }
            }
            if (idx == -1) continue

            // сдвиг
            if (best.size < maxCount) {
                best.add(e)
            }
            for (j in minOf(best.size - 1, maxCount - 1) downTo idx + 1) {
                bestD2[j] = bestD2[j - 1]
                best[j] = best[j - 1]
            }
            bestD2[idx] = d2
            best[idx] = e
        }

        // Уберём возможные null-слоты (на случай maxCount>найдено)
        return best.distinct().take(maxCount)
    }

    private fun updateProjectiles(delta: Float) {
        combat.updatePlayerProjectiles(
            delta = delta,
            arenaHalfW = arenaHalfW,
            arenaHalfH = arenaHalfH,
            enemies = enemies,
            isAlive = { it.hp > 0f },
            getPos = { it.pos },
            getRadius = { it.radius },
            damageEnemy = { e, dmg -> e.hp -= dmg },
            onEnemyHit = { e ->
                itemSystem.onEnemyHit(
                    hit = e,
                    playerPos = playerPos,
                    isAlive = { it.hp > 0f },
                    getPos = { it.pos },
                    damageEnemy = { en, d -> en.hp -= d },
                    findNearest = ::findNearestEnemyFromPointExcludingVisited,
                )
            },
            findNearestEnemyExcluding = { exclude, fromX, fromY, maxRange2 ->
                findNearestEnemyFromPointExcludingVisited(fromX, fromY, setOf(System.identityHashCode(exclude)), maxRange2)
            },
        )
    }

    private fun findNearestEnemyExcluding(exclude: Enemy, maxRange: Float): Enemy? {
        val maxR2 = maxRange * maxRange
        var best: Enemy? = null
        var bestD2 = Float.POSITIVE_INFINITY
        for (e in enemies) {
            if (e === exclude) continue
            if (e.hp <= 0f) continue
            val dx = e.pos.x - exclude.pos.x
            val dy = e.pos.y - exclude.pos.y
            val d2 = dx * dx + dy * dy
            if (d2 > maxR2) continue
            if (d2 < bestD2) {
                bestD2 = d2
                best = e
            }
        }
        return best
    }

    private fun cleanupDead() {
        // Дроп лута за убийства + награды
        for (e in enemies) {
            if (e.hp <= 0f) {
                loot.onEnemyKilled(
                    pos = e.pos,
                    xpReward = e.xpReward,
                    goldReward = e.goldReward,
                    isElite = e.isElite,
                )

                // Бургер — хил на убийство
                val heal = itemSystem.rollBurgerHeal()
                if (heal > 0f) playerHp = (playerHp + heal).coerceAtMost(100f)
            }
        }
        enemies.removeAll { it.hp <= 0f }
        // Цель чистится внутри TargetingSystem на апдейте
    }

    private fun findNearestEnemyFromPointExcludingVisited(
        x: Float,
        y: Float,
        visitedIds: Set<Int>,
        maxRange2: Float,
    ): Enemy? {
        var best: Enemy? = null
        var bestD2 = Float.POSITIVE_INFINITY
        for (e in enemies) {
            if (e.hp <= 0f) continue
            if (visitedIds.contains(System.identityHashCode(e))) continue
            val dx = e.pos.x - x
            val dy = e.pos.y - y
            val d2 = dx * dx + dy * dy
            if (d2 > maxRange2) continue
            if (d2 < bestD2) {
                bestD2 = d2
                best = e
            }
        }
        return best
    }

    private fun drawJoystickOverlay() {
        if (!joystick.isActive) return

        // Рисуем в world-space через unproject (достаточно для MVP).
        shapes.projectionMatrix = camera.combined
        val originScreen = joystick.originForRenderPx
        val currentScreen = joystick.currentForRenderPx

        val originWorld = viewport.unproject(Vector2(originScreen.x, originScreen.y))
        val currentWorld = viewport.unproject(Vector2(currentScreen.x, currentScreen.y))

        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color(1f, 1f, 1f, 0.35f)
        shapes.circle(originWorld.x, originWorld.y, 40f, 24)
        shapes.line(originWorld.x, originWorld.y, currentWorld.x, currentWorld.y)
        shapes.end()
    }

    private fun drawHud() {
        uiViewport.apply()
        batch.projectionMatrix = uiCamera.combined

        batch.begin()
        font.color = Color.WHITE
        font.draw(
            batch,
            "HP: ${playerHp.toInt()}   Lvl: ${progression.level}  XP: ${progression.xp}/${progression.xpToNext}   Gold: ${loot.gold}   Time: ${runTime.toInt()}s   Enemies: ${enemies.size}",
            16f,
            uiViewport.worldHeight - 16f,
        )

        // Слоты (2 оружия + 2 пассивки)
        val line1 = buildString {
            append("Оружие: ")
            if (progression.weapons.isEmpty()) append("-")
            for ((i, w) in progression.weapons.withIndex()) {
                if (i > 0) append(" | ")
                append("${w.kind.uiName} Lv${w.level} E${w.extraLevel} R${w.ricochetLevel} P${w.pierceLevel}")
            }
        }
        val line2 = buildString {
            append("Пассивки: ")
            if (progression.passives.isEmpty()) append("-")
            for ((i, p) in progression.passives.withIndex()) {
                if (i > 0) append(" | ")
                append("${p.kind.uiName} Lv${p.level}")
            }
        }
        font.draw(batch, line1, 16f, uiViewport.worldHeight - 40f)
        font.draw(batch, line2, 16f, uiViewport.worldHeight - 64f)

        val line3 = buildString {
            append("Предметы: ")
            if (itemSystem.items.isEmpty()) append("-")
            for ((i, it) in itemSystem.items.withIndex()) {
                if (i > 0) append(" | ")
                append(it.kind.name)
            }
        }
        font.draw(batch, line3, 16f, uiViewport.worldHeight - 88f)

        when (runState) {
            RunState.GAME_OVER -> {
            font.color = Color(1f, 0.4f, 0.4f, 1f)
            font.draw(batch, "GAME OVER", uiViewport.worldWidth / 2f - 60f, uiViewport.worldHeight / 2f + 10f)
            font.color = Color.WHITE
            font.draw(batch, "Press R / Space to restart", uiViewport.worldWidth / 2f - 110f, uiViewport.worldHeight / 2f - 16f)
            }
            RunState.LEVEL_UP -> drawLevelUpOverlay(batch, uiCamera)
            RunState.CHEST_OPEN -> drawChestOverlay(batch)
            else -> Unit
        }
        batch.end()
    }

    private fun drawLevelUpOverlay(batch: SpriteBatch, camera: Camera) {
        font.color = Color.WHITE
        font.draw(batch, "LEVEL UP! Выбери улучшение:", 16f, uiViewport.worldHeight - 92f)

        // Карточки рисуем примитивно (прямоугольник + текст)
        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)
        shapes.color = Color(0.12f, 0.12f, 0.16f, 0.92f)
        for (i in 0 until minOf(3, pendingChoices.size)) {
            val r = levelUpCards[i]
            shapes.rect(r.x, r.y, r.width, r.height)
        }
        shapes.end()

        batch.begin()
        font.color = Color.WHITE
        for (i in 0 until minOf(3, pendingChoices.size)) {
            val opt = pendingChoices[i]
            val r = levelUpCards[i]
            font.draw(batch, "${i + 1}. ${opt.title}", r.x + 14f, r.y + r.height - 16f)
            font.color = Color.LIGHT_GRAY
            font.draw(batch, opt.description, r.x + 14f, r.y + r.height - 40f)
            font.color = Color.WHITE
        }
    }

    private fun handleLevelUpInput() {
        // Пока простой UX: 1/2/3 на Desktop.
        val idx = when {
            Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_1) -> 0
            Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_2) -> 1
            Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_3) -> 2
            else -> -1
        }
        if (idx < 0 || idx >= pendingChoices.size) return
        applyLevelUpChoice(idx)
    }

    private fun handleChestInput() {
        // Для Desktop отладки: 1/2/3
        val idx = when {
            Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_1) -> 0
            Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_2) -> 1
            Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUMPAD_3) -> 2
            else -> -1
        }
        if (idx < 0 || idx >= pendingChestChoices.size) return
        applyChestChoice(idx)
    }


    private fun applyLevelUpChoice(idx: Int) {
        if (idx < 0 || idx >= pendingChoices.size) return
        progression.applyUpgrade(pendingChoices[idx])
        pendingChoices = emptyList()
        runState = RunState.RUNNING
    }


    private fun layoutLevelUpCards() {
        // world units = screen px (ScreenViewport), поэтому считаем как UI-пиксели
        val w = uiViewport.worldWidth
        val h = uiViewport.worldHeight
        if (w <= 0f || h <= 0f) return

        val padX = 16f
        val cardW = (w - padX * 2f)
        val cardH = 76f
        val topY = h - 130f
        val gap = 10f

        for (i in 0..2) {
            val y = topY - i * (cardH + gap)
            levelUpCards[i].set(padX, y - cardH, cardW, cardH)
        }
    }

    private inner class LevelUpInput : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (runState != RunState.LEVEL_UP && runState != RunState.CHEST_OPEN) return false
            val count = when (runState) {
                RunState.LEVEL_UP -> pendingChoices.size
                RunState.CHEST_OPEN -> pendingChestChoices.size
                else -> 0
            }
            if (count <= 0) return false

            // Переводим координаты тапа в ui-world
            tmpVec.set(screenX.toFloat(), screenY.toFloat())
            uiViewport.unproject(tmpVec)

            for (i in 0 until minOf(3, count)) {
                if (levelUpCards[i].contains(tmpVec.x, tmpVec.y)) {
                    when (runState) {
                        RunState.LEVEL_UP -> applyLevelUpChoice(i)
                        RunState.CHEST_OPEN -> applyChestChoice(i)
                        else -> Unit
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun drawChestOverlay(batch: SpriteBatch) {
        font.color = Color.WHITE
        font.draw(batch, "СУНДУК! Выбери предмет:", 16f, uiViewport.worldHeight - 116f)

        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)
        shapes.color = Color(0.12f, 0.12f, 0.16f, 0.92f)
        for (i in 0 until minOf(3, pendingChestChoices.size)) {
            val r = levelUpCards[i]
            shapes.rect(r.x, r.y, r.width, r.height)
        }
        shapes.end()
        batch.begin()

        for (i in 0 until minOf(3, pendingChestChoices.size)) {
            val opt = pendingChestChoices[i]
            val r = levelUpCards[i]
            font.color = Color.WHITE
            font.draw(batch, "${i + 1}. ${opt.title}", r.x + 14f, r.y + r.height - 16f)
            font.color = Color.LIGHT_GRAY
            font.draw(batch, opt.description, r.x + 14f, r.y + r.height - 40f)
        }
        font.color = Color.WHITE
        font.draw(batch, "(Тап по карточке / 1-2-3)", 16f, uiViewport.worldHeight - 140f)
    }

    private fun applyChestChoice(idx: Int) {
        if (idx < 0 || idx >= pendingChestChoices.size) return
        val chosen = pendingChestChoices[idx].item
        items.add(chosen)
        itemSystem.addItem(chosen)
        pendingChestChoices = emptyList()
        runState = RunState.RUNNING
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }

    private fun rebuildFont() {
        // Перегенерация шрифта под текущее разрешение (простая версия).
        // Для релиза можно сделать кэширование и обновлять реже.
        font.dispose()

        // 1) Предпочтительно положить TTF в проект (например, в будущую папку assets/fonts)
        // и грузить через Gdx.files.internal("fonts/Roboto-Regular.ttf").
        // 2) На Windows для разработки можно fallback на системный Arial.
        val ttf = when {
            Gdx.files.internal("fonts/Roboto-Regular.ttf").exists() -> Gdx.files.internal("fonts/Roboto-Regular.ttf")
            Gdx.files.absolute("C:/Windows/Fonts/arial.ttf").exists() -> Gdx.files.absolute("C:/Windows/Fonts/arial.ttf")
            else -> null
        }

        if (ttf == null) {
            // Фоллбек: будет без кириллицы, но хотя бы не упадём.
            font = BitmapFont()
            return
        }

        val gen = FreeTypeFontGenerator(ttf)
        try {
            val size = (uiViewport.screenHeight * 0.028f).toInt().coerceIn(14, 28)
            val param = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                this.size = size
                color = Color.WHITE
                // Кириллица + базовый латинский набор
                characters = FreeTypeFontGenerator.DEFAULT_CHARS + "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя№—«»"
            }
            font = gen.generateFont(param)
        } finally {
            gen.dispose()
        }
    }
}

