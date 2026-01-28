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
import com.factorlite.game.PlayerDamageSystem
import com.factorlite.game.ShrineSystem
import com.factorlite.input.FloatingJoystick
import com.factorlite.loot.ItemInstance
import com.factorlite.loot.ItemOption
import com.factorlite.loot.ItemTriggerSystem
import com.factorlite.screens.RunUiSystem
import com.factorlite.progression.GlobalBonusOption
import com.factorlite.progression.RunProgression
import com.factorlite.progression.UpgradeOption
import com.factorlite.progression.WeaponKind
import com.factorlite.progression.uiName
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class GameScreen : ScreenAdapter() {
    private val runDurationSec = 5f * 60f
    private var bossSpawned = false

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
    private lateinit var uiTapInput: InputAdapter
    private val uiSystem = RunUiSystem()

    private val playerPos = Vector2(0f, 0f)
    private val playerVel = Vector2(0f, 0f)

    private val basePlayerSpeed = 260f
    private val playerRadius = 14f
    private val playerDamage = PlayerDamageSystem(maxHp = 100f)
    private var runTime = 0f
    private var runState: RunState = RunState.RUNNING

    private val progression = RunProgression()
    private var pendingChoices: List<UpgradeOption> = emptyList()
    private val tmpVec = Vector2()

    private var pendingChestChoices: List<ItemOption> = emptyList()
    private var pendingShrineChoices: List<GlobalBonusOption> = emptyList()
    private val items = ArrayList<ItemInstance>()
    private val loot = LootSystem()
    private val combat = CombatSystem()
    private val enemySystem = EnemySystem()
    private val targetingSystem = TargetingSystem()
    private val shrineSystem = ShrineSystem()

    private val itemSystem = ItemTriggerSystem()

    private val enemies = ArrayList<Enemy>()

    // retargetTimer перенесён в TargetingSystem

    private val spawnDirector = SpawnDirector()

    // Простая арена (пока)
    private val arenaHalfW = 900f
    private val arenaHalfH = 500f

    override fun show() {
        inputMux.clear()
        uiTapInput = uiSystem.createTapInput(
            uiViewport = uiViewport,
            getRunState = { runState },
            getOptionCount = {
                when (runState) {
                    RunState.LEVEL_UP -> pendingChoices.size
                    RunState.CHEST_OPEN -> pendingChestChoices.size
                    RunState.SHRINE_OPEN -> pendingShrineChoices.size
                    else -> 0
                }
            },
            onPick = { idx ->
                when (runState) {
                    RunState.LEVEL_UP -> applyLevelUpChoice(idx)
                    RunState.CHEST_OPEN -> applyChestChoice(idx)
                    RunState.SHRINE_OPEN -> {
                        if (idx == 3) {
                            pendingShrineChoices = emptyList()
                            runState = RunState.RUNNING
                        } else {
                            applyShrineChoice(idx)
                        }
                    }
                    else -> Unit
                }
            },
        )
        inputMux.addProcessor(uiTapInput)
        inputMux.addProcessor(joystick)
        Gdx.input.inputProcessor = inputMux
        rebuildFont()
        resetRun()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        uiViewport.update(width, height, true)
        rebuildFont()
        uiSystem.layoutCards(uiViewport)
    }

    override fun render(delta: Float) {
        when (runState) {
            RunState.RUNNING -> update(delta)
            RunState.LEVEL_UP -> handleLevelUpInput()
            RunState.CHEST_OPEN -> handleChestInput()
            RunState.SHRINE_OPEN -> handleShrineInput()
            RunState.VICTORY -> {
                if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.R) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
                    resetRun()
                }
            }
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
        val invulnT = (playerDamage.invuln / 0.6f).coerceIn(0f, 1f)
        val a = if (playerDamage.invuln > 0f) 0.35f + 0.65f * (1f - Interpolation.fade.apply(invulnT)) else 1f
        shapes.color = Color(1f, 1f, 1f, a)
        shapes.circle(playerPos.x, playerPos.y, playerRadius, 20)

        // Враги
        for (e in enemies) {
            shapes.color =
                when {
                    e.isBoss -> Color(1f, 0.55f, 0.15f, 1f)
                    e.isElite -> Color(0.95f, 0.25f, 0.95f, 1f)
                    else -> Color(0.95f, 0.25f, 0.25f, 1f)
                }
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

        // Святыни (зоны зарядки)
        for (s in shrineSystem.shrines) {
            shapes.color = Color(0.25f, 0.95f, 0.45f, 0.28f)
            shapes.circle(s.pos.x, s.pos.y, s.radius, 26)
            val p = (s.progressSec / s.requiredSec).coerceIn(0f, 1f)
            shapes.color = Color(0.25f, 0.95f, 0.45f, 0.75f)
            shapes.circle(s.pos.x, s.pos.y, 6f + 12f * p, 18)
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
        itemSystem.update(delta)
        playerDamage.update(delta)

        // 5 минут -> босс уровня (останавливаем обычные волны)
        if (!bossSpawned && runTime >= runDurationSec) {
            startBossFight()
        }

        // joystick.direction сейчас в screen-space, Y у libGDX для touch идёт сверху вниз
        // Поэтому инвертируем Y.
        val playerSpeed = basePlayerSpeed * progression.getMoveSpeedMultiplier()
        playerVel.set(joystick.direction.x, -joystick.direction.y).scl(playerSpeed)
        playerPos.mulAdd(playerVel, delta)

        // Ограничение арены
        playerPos.x = MathUtils.clamp(playerPos.x, -arenaHalfW + playerRadius, arenaHalfW - playerRadius)
        playerPos.y = MathUtils.clamp(playerPos.y, -arenaHalfH + playerRadius, arenaHalfH - playerRadius)

        if (!bossSpawned) {
            updateSpawns(delta)
        }
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
        if (!bossSpawned) {
            loot.updateChestSpawns(delta, arenaHalfW, arenaHalfH, playerPos)
        }
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
            uiSystem.layoutCards(uiViewport)
        }

        // Камера следует за игроком
        camera.position.set(playerPos.x, playerPos.y, 0f)
        camera.update()

        // Радиус таргетинга растёт от "дальности" у оружия (как апгрейд паттерна)
        val extraRange = progression.weapons.maxOfOrNull { it.rangeLevel } ?: 0
        targetingSystem.range = 520f + extraRange * 35f
        targetingSystem.update(delta, playerPos, enemies)

        // Святыни: выключаем на фазе босса
        if (!bossSpawned) {
            shrineSystem.update(delta = delta, runTime = runTime, playerPos = playerPos)?.let { choices ->
                pendingShrineChoices = choices
                runState = RunState.SHRINE_OPEN
                uiSystem.layoutCards(uiViewport)
                return
            }
        }
        updateAttacking(delta)
        updateProjectiles(delta)
        cleanupDead()

        if (leveledUp && runState == RunState.RUNNING) {
            pendingChoices = progression.makeUpgradeChoices()
            runState = RunState.LEVEL_UP
            uiSystem.layoutCards(uiViewport)
        }
    }

    private fun resetRun() {
        runState = RunState.RUNNING
        runTime = 0f
        bossSpawned = false
        playerDamage.reset()
        playerPos.set(0f, 0f)
        playerVel.set(0f, 0f)

        progression.reset()
        progression.weapons += com.factorlite.progression.WeaponInstance(WeaponKind.BLASTER, level = 1)
        pendingChoices = emptyList()
        pendingChestChoices = emptyList()
        pendingShrineChoices = emptyList()
        items.clear()
        itemSystem.reset()
        loot.reset()
        shrineSystem.reset()

        enemies.clear()
        combat.reset()
        targetingSystem.reset()
        spawnDirector.reset(initialDelay = 0.2f)
        uiSystem.layoutCards(uiViewport)

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
        val pFast = (runTime / 120f).coerceIn(0f, 0.40f)
        val pTank = (runTime / 150f).coerceIn(0f, 0.28f)
        val pRanged = (runTime / 180f).coerceIn(0f, 0.28f)
        val roll = MathUtils.random()

        // Базовая сложность растёт по времени (даже без святынь)
        val timeMul = 1f + (runTime / runDurationSec).coerceIn(0f, 1f) * 0.70f // к 5:00 ~x1.70
        val diff = progression.getDifficultyMultiplier() * timeMul
        val rewardMul = progression.getRewardMultiplier() * (1f + (timeMul - 1f) * 0.35f)

        val baseEnemy = when {
            roll < pTank ->
                Enemy(
                    Vector2(x, y),
                    hp = 70f * diff,
                    maxHp = 70f * diff,
                    speed = 80f * (1f + (diff - 1f) * 0.15f),
                    contactDamage = 12f * diff,
                    radius = 18f,
                    xpReward = (3 * rewardMul).toInt().coerceAtLeast(1),
                    goldReward = (3 * rewardMul).toInt().coerceAtLeast(1),
                    kind = EnemyKind.TANK,
                )
            roll < pTank + pFast ->
                Enemy(
                    Vector2(x, y),
                    hp = 28f * diff,
                    maxHp = 28f * diff,
                    speed = 150f * (1f + (diff - 1f) * 0.15f),
                    contactDamage = 8f * diff,
                    radius = 13f,
                    xpReward = (2 * rewardMul).toInt().coerceAtLeast(1),
                    goldReward = (2 * rewardMul).toInt().coerceAtLeast(1),
                    kind = EnemyKind.FAST,
                )
            roll < pTank + pFast + pRanged ->
                Enemy(
                    Vector2(x, y),
                    hp = 34f * diff,
                    maxHp = 34f * diff,
                    speed = 92f * (1f + (diff - 1f) * 0.15f),
                    contactDamage = 8f * diff,
                    radius = 14f,
                    xpReward = (2 * rewardMul).toInt().coerceAtLeast(1),
                    goldReward = (2 * rewardMul).toInt().coerceAtLeast(1),
                    kind = EnemyKind.RANGED,
                    shootCooldown = MathUtils.random(0.2f, 1.0f),
                )
            else ->
                Enemy(
                    Vector2(x, y),
                    hp = 40f * diff,
                    maxHp = 40f * diff,
                    speed = 105f * (1f + (diff - 1f) * 0.15f),
                    contactDamage = 10f * diff,
                    radius = 15f,
                    xpReward = (1 * rewardMul).toInt().coerceAtLeast(1),
                    goldReward = (1 * rewardMul).toInt().coerceAtLeast(1),
                    kind = EnemyKind.NORMAL,
                )
        }

        // Элитка: редкий “пик” — сильнее и гарантирует сундук.
        val pEliteBase = (0.006f + (runTime / 240f) * 0.004f).coerceAtMost(0.02f) // ~0.6% -> 2%
        val pElite = (pEliteBase * progression.getEliteFrequencyMultiplier()).coerceAtMost(0.08f)
        val makeElite = MathUtils.random() < pElite
        val enemy = if (!makeElite) baseEnemy else {
            baseEnemy.copy(
                hp = baseEnemy.hp * 2.6f,
                maxHp = baseEnemy.hp * 2.6f,
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
            canDamagePlayer = playerDamage.canTakeDamage(),
            onHitPlayer = { dmg ->
                val res = playerDamage.applyHit(dmg, itemSystem)
                if (res.died) {
                    runState = RunState.GAME_OVER
                }
                !res.blocked
            },
        )
    }

    private fun applyContactDamage() {
        if (!playerDamage.canTakeDamage()) return
        for (e in enemies) {
            if (e.hp <= 0f) continue
            val dx = e.pos.x - playerPos.x
            val dy = e.pos.y - playerPos.y
            val r = e.radius + playerRadius
            if (dx * dx + dy * dy <= r * r) {
                val res = playerDamage.applyHit(e.contactDamage, itemSystem)
                if (res.died) {
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

        val speed = 780f + w.projectileSpeedLevel * 30f
        val baseDamage = 10f + w.damageLevel * 2.2f
        val extra = w.extraLevel
        val count = 1 + extra
        val spread = 0.10f * (0.88f.pow(w.accuracyLevel.toFloat()))
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

        val baseCd = 0.35f / (1f + 0.08f * w.cooldownLevel)
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

        val shots = (1 + w.extraLevel).coerceIn(1, 7)
        val candidates = pickNearestEnemies(shots, range = 560f)
        if (candidates.isEmpty()) return

        val speed = 820f + w.projectileSpeedLevel * 32f
        val baseDamage = 7.5f + w.damageLevel * 1.6f

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

        val baseCd = 0.65f / (1f + 0.08f * w.cooldownLevel)
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
        val range = 110f + w.rangeLevel * 8f + w.pierceLevel * 18f
        val hits = (1 + w.extraLevel).coerceIn(1, 8)
        val candidates = pickNearestEnemies(hits, range = range)
        if (candidates.isEmpty()) return

        val baseDamage = 14f + w.damageLevel * 3.0f
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

        val baseCd = 0.85f / (1f + 0.08f * w.cooldownLevel)
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
        var bossDied = false
        for (e in enemies) {
            if (e.hp <= 0f) {
                if (e.isBoss) bossDied = true
                loot.onEnemyKilled(
                    pos = e.pos,
                    xpReward = e.xpReward,
                    goldReward = e.goldReward,
                    isElite = e.isElite,
                )

                // Бургер — хил на убийство
                val heal = itemSystem.rollBurgerHeal()
                if (heal > 0f) playerDamage.heal(heal)
            }
        }
        enemies.removeAll { it.hp <= 0f }
        if (bossDied) {
            runState = RunState.VICTORY
        }
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
            "HP: ${playerDamage.hp.toInt()}   Lvl: ${progression.level}  XP: ${progression.xp}/${progression.xpToNext}   Gold: ${loot.gold}   Time: ${runTime.toInt()}s   Enemies: ${enemies.size}",
            16f,
            uiViewport.worldHeight - 16f,
        )

        // Слоты (2 оружия + 2 пассивки)
        val line1 = buildString {
            append("Оружие: ")
            if (progression.weapons.isEmpty()) append("-")
            for ((i, w) in progression.weapons.withIndex()) {
                if (i > 0) append(" | ")
                append(
                    "${w.kind.uiName} " +
                        "D${w.damageLevel} F${w.cooldownLevel} S${w.projectileSpeedLevel} A${w.accuracyLevel} R${w.rangeLevel} " +
                        "E${w.extraLevel} Rc${w.ricochetLevel} P${w.pierceLevel}",
                )
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

        // Миникарта (справа сверху)
        drawMiniMap()

        // Босс-бар (если жив)
        enemies.firstOrNull { it.isBoss }?.let { boss ->
            drawBossBar(boss)
        }

        when (runState) {
            RunState.GAME_OVER -> {
            font.color = Color(1f, 0.4f, 0.4f, 1f)
            font.draw(batch, "GAME OVER", uiViewport.worldWidth / 2f - 60f, uiViewport.worldHeight / 2f + 10f)
            font.color = Color.WHITE
            font.draw(batch, "Press R / Space to restart", uiViewport.worldWidth / 2f - 110f, uiViewport.worldHeight / 2f - 16f)
            }
            RunState.VICTORY -> {
                font.color = Color(0.4f, 1f, 0.4f, 1f)
                font.draw(batch, "VICTORY!", uiViewport.worldWidth / 2f - 60f, uiViewport.worldHeight / 2f + 10f)
                font.color = Color.WHITE
                font.draw(batch, "Press R / Space to restart", uiViewport.worldWidth / 2f - 110f, uiViewport.worldHeight / 2f - 16f)
            }
            RunState.LEVEL_UP -> uiSystem.drawLevelUpOverlay(batch, shapes, font, uiViewport, uiCamera, pendingChoices)
            RunState.CHEST_OPEN -> uiSystem.drawChestOverlay(batch, shapes, font, uiViewport, uiCamera, pendingChestChoices)
            RunState.SHRINE_OPEN -> uiSystem.drawShrineOverlay(batch, shapes, font, uiViewport, uiCamera, pendingShrineChoices)
            else -> Unit
        }
        batch.end()
    }

    private fun drawMiniMap() {
        // batch уже в begin()
        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        val uiW = uiViewport.worldWidth
        val uiH = uiViewport.worldHeight
        val radius = 68f
        val cx = uiW - radius - 16f
        val cy = uiH - radius - 16f

        // фон
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.circle(cx, cy, radius + 2f, 36)
        shapes.color = Color(0.10f, 0.12f, 0.14f, 0.85f)
        shapes.circle(cx, cy, radius, 36)

        val worldRange = 650f
        fun plot(wx: Float, wy: Float, r: Float, col: Color) {
            val dx = (wx - playerPos.x) / worldRange
            val dy = (wy - playerPos.y) / worldRange
            val len2 = dx * dx + dy * dy
            val s = if (len2 > 1f) 1f / sqrt(len2) else 1f
            val px = cx + dx * radius * s
            val py = cy + dy * radius * s
            shapes.color = col
            shapes.circle(px, py, r, 10)
        }

        for (e in enemies) {
            plot(
                e.pos.x,
                e.pos.y,
                if (e.isBoss) 3.0f else 2.2f,
                when {
                    e.isBoss -> Color(1f, 0.55f, 0.15f, 1f)
                    e.isElite -> Color(0.95f, 0.25f, 0.95f, 1f)
                    else -> Color(0.95f, 0.25f, 0.25f, 1f)
                },
            )
        }
        for (c in loot.chests) plot(c.pos.x, c.pos.y, 2.6f, Color(0.25f, 0.75f, 1f, 1f))
        for (s in shrineSystem.shrines) plot(s.pos.x, s.pos.y, 2.6f, Color(0.25f, 0.95f, 0.45f, 1f))

        shapes.color = Color.WHITE
        shapes.circle(cx, cy, 2.8f, 12)

        shapes.end()
        batch.begin()
    }

    private fun drawBossBar(boss: Enemy) {
        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        val w = uiViewport.worldWidth
        val x = w * 0.18f
        val y = uiViewport.worldHeight - 78f
        val bw = w * 0.64f
        val bh = 14f
        val t = (boss.hp / boss.maxHp).coerceIn(0f, 1f)

        shapes.color = Color(0f, 0f, 0f, 0.55f)
        shapes.rect(x - 2f, y - 2f, bw + 4f, bh + 4f)
        shapes.color = Color(0.75f, 0.12f, 0.12f, 0.95f)
        shapes.rect(x, y, bw * t, bh)
        shapes.color = Color(0.15f, 0.15f, 0.15f, 0.85f)
        shapes.rect(x + bw * t, y, bw * (1f - t), bh)

        shapes.end()
        batch.begin()

        font.color = Color.WHITE
        font.draw(batch, "BOSS", x, y + 32f)
        font.draw(batch, "${boss.hp.toInt()} / ${boss.maxHp.toInt()}", x + bw - 120f, y + 32f)
    }

    private fun startBossFight() {
        bossSpawned = true

        // Убираем "шум" и замораживаем экономику (сундуки/святыни уже перестали обновляться)
        enemies.removeAll { it.hp <= 0f }
        enemies.removeAll { it.isBoss } // на всякий
        combat.enemyProjectiles.clear()

        // Чуть чистим сундуки, чтобы не было "ковра" в фазе босса
        loot.chests.removeAll { !it.isElite }

        val diff = progression.getDifficultyMultiplier()
        val bossHp = 5200f * diff
        enemies.add(
            Enemy(
                pos = Vector2(playerPos.x + 360f, playerPos.y),
                hp = bossHp,
                maxHp = bossHp,
                speed = 78f,
                contactDamage = 22f * diff,
                radius = 26f,
                xpReward = 0,
                goldReward = 0,
                kind = EnemyKind.TANK,
                isElite = false,
                isBoss = true,
            ),
        )
    }

    private fun handleLevelUpInput() {
        // Пока простой UX: 1/2/3 на Desktop.
        val idx = uiSystem.pollKeyPick(runState) ?: return
        if (idx >= pendingChoices.size) return
        applyLevelUpChoice(idx)
    }

    private fun handleChestInput() {
        // Для Desktop отладки: 1/2/3
        val idx = uiSystem.pollKeyPick(runState) ?: return
        if (idx >= pendingChestChoices.size) return
        applyChestChoice(idx)
    }

    private fun handleShrineInput() {
        if (uiSystem.pollSkip(runState)) {
            pendingShrineChoices = emptyList()
            runState = RunState.RUNNING
            return
        }
        val idx = uiSystem.pollKeyPick(runState) ?: return
        if (idx >= pendingShrineChoices.size) return
        applyShrineChoice(idx)
    }


    private fun applyLevelUpChoice(idx: Int) {
        if (idx < 0 || idx >= pendingChoices.size) return
        progression.applyUpgrade(pendingChoices[idx])
        pendingChoices = emptyList()
        runState = RunState.RUNNING
    }


    // Отрисовка оверлеев/карточек вынесена в RunUiSystem

    private fun applyChestChoice(idx: Int) {
        if (idx < 0 || idx >= pendingChestChoices.size) return
        val chosen = pendingChestChoices[idx].item
        items.add(chosen)
        itemSystem.addItem(chosen)
        pendingChestChoices = emptyList()
        runState = RunState.RUNNING
    }

    private fun applyShrineChoice(idx: Int) {
        if (idx < 0 || idx >= pendingShrineChoices.size) return
        progression.applyGlobalBonus(pendingShrineChoices[idx])
        pendingShrineChoices = emptyList()
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

        // Продуктовый подход: не зависим от системных путей (Windows/Android),
        // всегда грузим из ассетов. Общая папка `assets/` подключена в Gradle
        // для Desktop и Android, так что Gdx.files.internal(...) работает везде одинаково.
        val ttf = Gdx.files.internal("fonts/Roboto-Regular.ttf")
        if (!ttf.exists()) {
            Gdx.app?.error(
                "Font",
                "Missing assets font: fonts/Roboto-Regular.ttf. " +
                    "Add it under repo root assets/fonts/Roboto-Regular.ttf. Falling back to BitmapFont().",
            )
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

