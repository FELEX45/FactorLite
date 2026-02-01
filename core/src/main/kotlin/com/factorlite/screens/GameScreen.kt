package com.factorlite.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
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
import com.factorlite.loot.ItemTriggerSystem
import com.factorlite.loot.uiName
import com.factorlite.screens.RunUiSystem
import com.factorlite.content.Balance
import com.factorlite.gfx.Sprites
import com.factorlite.progression.GlobalBonusOption
import com.factorlite.progression.CharacterKind
import com.factorlite.progression.playerSpriteKey
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

    // Портретный "телефонный" формат. Можно переключить на 9:21.
    private enum class PhoneAspect(val hOverW: Float) { R9_16(16f / 9f), R9_21(21f / 9f) }
    private val phoneAspect: PhoneAspect = PhoneAspect.R9_16
    private val viewW = 540f
    private val viewH = viewW * phoneAspect.hOverW

    private val camera = OrthographicCamera()
    private val viewport = FitViewport(viewW, viewH, camera)

    private val shapes = ShapeRenderer()

    private val uiCamera = OrthographicCamera()
    private val uiViewport = ScreenViewport(uiCamera)
    private val batch = SpriteBatch()
    private var font: BitmapFont = BitmapFont()
    private val glyph = GlyphLayout()
    private var fatalErrorText: String? = null

    // Глобальный скейл интерфейса под телефон (HUD + карточки).
    // Если захочешь — вынесем в баланс/настройки.
    private val uiScale: Float = 1.45f

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
    private val baseMaxHp = 100f
    private val playerDamage = PlayerDamageSystem(maxHp = baseMaxHp)
    private var runTime = 0f
    private var runState: RunState = RunState.RUNNING

    private val progression = RunProgression()
    private var pendingChoices: List<UpgradeOption> = emptyList()
    private val tmpVec = Vector2()

    // Пока без UI выбора: дефолтный персонаж. Позже заменим на меню/сохранение.
    private var selectedCharacter: CharacterKind = CharacterKind.FROZKA
    private val characterChoices: List<CharacterKind> = CharacterKind.entries.toList()

    private enum class DifficultyKind(
        val uiName: String,
        val description: String,
        val enemyHpMul: Float,
        val enemyDamageMul: Float,
        val spawnIntervalMul: Float,
    ) {
        EASY("Лёгкая", "Меньше врагов и урона.", enemyHpMul = 0.85f, enemyDamageMul = 0.80f, spawnIntervalMul = 1.25f),
        MEDIUM("Средняя", "Чуть проще, чем сейчас.", enemyHpMul = 0.92f, enemyDamageMul = 0.90f, spawnIntervalMul = 1.10f),
        HARD("Сложная", "Текущая (как сейчас).", enemyHpMul = 1.00f, enemyDamageMul = 1.00f, spawnIntervalMul = 1.00f),
    }

    private var selectedDifficulty: DifficultyKind = DifficultyKind.HARD
    private val difficultyChoices: List<DifficultyKind> = DifficultyKind.entries.toList()

    // Пауза: кнопка в правом верхнем углу HUD
    private val pauseRect = Rectangle()
    private lateinit var pauseInput: InputAdapter

    private var pendingShrineChoices: List<GlobalBonusOption> = emptyList()
    private val items = ArrayList<ItemInstance>()
    private val loot = LootSystem()
    private val combat = CombatSystem()
    private val enemySystem = EnemySystem()
    private val targetingSystem = TargetingSystem()
    private val shrineSystem = ShrineSystem()

    private val itemSystem = ItemTriggerSystem()

    private val enemies = ArrayList<Enemy>()
    private var killedEnemies: Int = 0
    private data class PoisonTrapCloud(
        val pos: Vector2,
        var armTimer: Float,
        var cloudTimer: Float,
        val radius: Float,
        val dps: Float,
        val slowPct: Float,
    )
    private val poisonTraps: MutableList<PoisonTrapCloud> = ArrayList()

    // retargetTimer перенесён в TargetingSystem

    private val spawnDirector = SpawnDirector()

    // Простая арена (пока) — под портретное окно.
    private val arenaHalfW = 520f
    private val arenaHalfH = 920f

    override fun show() {
        inputMux.clear()
        uiTapInput = uiSystem.createTapInput(
            uiViewport = uiViewport,
            getRunState = { runState },
            getOptionCount = {
                when (runState) {
                    RunState.DIFFICULTY_SELECT -> difficultyChoices.size
                    RunState.CHARACTER_SELECT -> characterChoices.size
                    RunState.LEVEL_UP -> pendingChoices.size
                    RunState.SHRINE_OPEN -> pendingShrineChoices.size
                    else -> 0
                }
            },
            onPick = { idx ->
                when (runState) {
                    RunState.DIFFICULTY_SELECT -> applyDifficultyPick(idx)
                    RunState.CHARACTER_SELECT -> applyCharacterPick(idx)
                    RunState.LEVEL_UP -> applyLevelUpChoice(idx)
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

        pauseInput = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (runState != RunState.RUNNING && runState != RunState.PAUSED) return false
                tmpVec.set(screenX.toFloat(), screenY.toFloat())
                uiViewport.unproject(tmpVec)
                if (pauseRect.contains(tmpVec.x, tmpVec.y)) {
                    runState = if (runState == RunState.RUNNING) RunState.PAUSED else RunState.RUNNING
                    return true
                }
                return false
            }
        }

        inputMux.addProcessor(uiTapInput)
        inputMux.addProcessor(pauseInput)
        inputMux.addProcessor(joystick)
        Gdx.input.inputProcessor = inputMux
        rebuildFont()
        enterDifficultySelect()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        uiViewport.update(width, height, true)
        rebuildFont()
        val count = when (runState) {
            RunState.DIFFICULTY_SELECT -> difficultyChoices.size
            RunState.CHARACTER_SELECT -> characterChoices.size
            RunState.LEVEL_UP -> pendingChoices.size
            RunState.SHRINE_OPEN -> pendingShrineChoices.size
            else -> 0
        }
        uiSystem.layoutCards(uiViewport, optionCount = count, withSkip = (runState == RunState.SHRINE_OPEN), uiScale = uiScale)

        // Позиция кнопки паузы обновляется в drawHud() (зависит от высоты HUD),
        // но держим дефолт, чтобы клики работали даже до первой отрисовки.
        val w = uiViewport.worldWidth
        val h = uiViewport.worldHeight
        pauseRect.set(w - 16f - 44f, h - 16f - 44f, 44f, 44f)
    }

    override fun render(delta: Float) {
        // Если на телефоне/устройстве что-то падает — показываем ошибку на экране, а не вылетаем молча.
        fatalErrorText?.let { err ->
            Gdx.gl.glClearColor(0.05f, 0.05f, 0.06f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            uiViewport.apply()
            batch.projectionMatrix = uiCamera.combined
            batch.begin()
            font.color = Color(1f, 0.55f, 0.55f, 1f)
            font.draw(batch, "CRASH (не закрываем, чтобы увидеть причину)", 16f, uiViewport.worldHeight - 18f)
            font.color = Color.WHITE
            glyph.setText(font, err, Color.WHITE, uiViewport.worldWidth - 32f, Align.left, true)
            font.draw(batch, glyph, 16f, uiViewport.worldHeight - 52f)
            batch.end()
            return
        }

        try {
        // Быстрая пауза с клавиатуры (ПК)
        if (runState == RunState.RUNNING &&
            (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE))
        ) {
            runState = RunState.PAUSED
        }

        when (runState) {
            RunState.DIFFICULTY_SELECT -> handleDifficultySelectInput()
            RunState.CHARACTER_SELECT -> handleCharacterSelectInput()
            RunState.PAUSED -> handlePauseInput()
            RunState.RUNNING -> update(delta)
            RunState.LEVEL_UP -> handleLevelUpInput()
            RunState.SHRINE_OPEN -> handleShrineInput()
            RunState.VICTORY -> {
                if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.R) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
                    enterDifficultySelect()
                }
            }
            RunState.GAME_OVER -> {
                if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.R) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
                    enterDifficultySelect()
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

        // --- Спрайты мира ---
        batch.projectionMatrix = camera.combined
        batch.begin()

        val texPlayer = Sprites.get(progression.character.playerSpriteKey) {
            Sprites.get("player") { Sprites.circleTexture(64, Color.WHITE) }
        }
        val invulnT = (playerDamage.invuln / 0.6f).coerceIn(0f, 1f)
        val alpha = if (playerDamage.invuln > 0f) 0.35f + 0.65f * (1f - Interpolation.fade.apply(invulnT)) else 1f
        Sprites.drawCentered(batch, texPlayer, playerPos.x, playerPos.y, playerRadius * 2f, playerRadius * 2f, alpha = alpha)

        val texEnemyNormal = Sprites.get("enemy_normal") { Sprites.circleTexture(64, Color(0.95f, 0.25f, 0.25f, 1f)) }
        val texEnemyFast = Sprites.get("enemy_fast") { Sprites.circleTexture(64, Color(1f, 0.45f, 0.35f, 1f)) }
        val texEnemyTank = Sprites.get("enemy_tank") { Sprites.circleTexture(64, Color(0.85f, 0.15f, 0.15f, 1f)) }
        val texEnemyRanged = Sprites.get("enemy_ranged") { Sprites.circleTexture(64, Color(0.95f, 0.35f, 0.95f, 1f)) }
        val texEnemyElite = Sprites.get("enemy_elite") { Sprites.circleTexture(64, Color(0.95f, 0.25f, 0.95f, 1f)) }
        val texEnemyBoss = Sprites.get("enemy_boss") { Sprites.circleTexture(64, Color(1f, 0.55f, 0.15f, 1f)) }

        for (e in enemies) {
            val t = when {
                e.isBoss -> texEnemyBoss
                e.isElite -> texEnemyElite
                else -> when (e.kind) {
                    EnemyKind.NORMAL -> texEnemyNormal
                    EnemyKind.FAST -> texEnemyFast
                    EnemyKind.TANK -> texEnemyTank
                    EnemyKind.RANGED -> texEnemyRanged
                }
            }
            Sprites.drawCentered(batch, t, e.pos.x, e.pos.y, e.radius * 2f, e.radius * 2f)
        }

        val texXp = Sprites.get("orb_xp") { Sprites.circleTexture(32, Color(0.35f, 0.95f, 0.95f, 1f)) }
        for (o in loot.xpOrbs) Sprites.drawCentered(batch, texXp, o.pos.x, o.pos.y, 13f, 13f)

        val texGold = Sprites.get("orb_gold") { Sprites.circleTexture(32, Color(1f, 0.85f, 0.25f, 1f)) }
        for (o in loot.goldOrbs) Sprites.drawCentered(batch, texGold, o.pos.x, o.pos.y, 11f, 11f)

        val texChest = Sprites.get("chest") { Sprites.circleTexture(48, Color(0.25f, 0.75f, 1f, 1f)) }
        val texChestElite = Sprites.get("chest_elite") { Sprites.circleTexture(48, Color(1f, 0.85f, 0.25f, 1f)) }
        for (c in loot.chests) {
            Sprites.drawCentered(batch, if (c.isElite) texChestElite else texChest, c.pos.x, c.pos.y, c.radius * 2f, c.radius * 2f)
        }

        val texShrine = Sprites.get("shrine") { Sprites.circleTexture(64, Color(0.25f, 0.95f, 0.45f, 0.75f)) }
        for (s in shrineSystem.shrines) {
            // Иконка + лёгкая "пульсация" прогресса
            val p = (s.progressSec / s.requiredSec).coerceIn(0f, 1f)
            Sprites.drawCentered(batch, texShrine, s.pos.x, s.pos.y, 26f, 26f, alpha = 0.55f)
            Sprites.drawCentered(batch, texShrine, s.pos.x, s.pos.y, 10f + 14f * p, 10f + 14f * p, alpha = 0.75f)
        }

        val texCloud = Sprites.get("cloud_toxic") { Sprites.circleTexture(128, Color(0.3f, 1f, 0.3f, 0.25f)) }
        if (itemSystem.toxicActive()) {
            val r = itemSystem.toxicRadius()
            Sprites.drawCentered(batch, texCloud, playerPos.x, playerPos.y, r * 2f, r * 2f, alpha = 0.25f)
        }
        // Ловушки (иконка) / облако от ловушки
        val texTrap = Sprites.get("trap") { Sprites.circleTexture(64, Color(0.35f, 0.95f, 0.45f, 1f)) }
        for (t in poisonTraps) {
            if (t.armTimer > 0f) {
                Sprites.drawCentered(batch, texTrap, t.pos.x, t.pos.y, 18f, 18f, alpha = 0.85f)
            } else if (t.cloudTimer > 0f) {
                Sprites.drawCentered(batch, texCloud, t.pos.x, t.pos.y, t.radius * 2f, t.radius * 2f, alpha = 0.22f)
            }
        }

        val texProjP = Sprites.get("projectile_player") { Sprites.circleTexture(24, Color(1f, 0.9f, 0.2f, 1f)) }
        for (p in combat.projectiles) Sprites.drawCentered(batch, texProjP, p.pos.x, p.pos.y, p.radius * 2f, p.radius * 2f)

        val texProjE = Sprites.get("projectile_enemy") { Sprites.circleTexture(24, Color(0.95f, 0.55f, 1f, 1f)) }
        for (p in combat.enemyProjectiles) Sprites.drawCentered(batch, texProjE, p.pos.x, p.pos.y, p.radius * 2f, p.radius * 2f)

        batch.end()

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
        } catch (t: Throwable) {
            // Лог в logcat + текст на экране (чтобы можно было прислать скрин).
            Gdx.app?.error("FactorLite", "Fatal error in GameScreen.render()", t)
            val sb = StringBuilder()
            sb.append(t::class.java.name).append(": ").append(t.message ?: "(no message)").append('\n')
            val st = t.stackTrace
            val maxLines = 14
            for (i in 0 until minOf(maxLines, st.size)) {
                sb.append("  at ").append(st[i].toString()).append('\n')
            }
            if (st.size > maxLines) sb.append("  ... (${st.size - maxLines} more)\n")
            sb.append("\nСделай скрин этого текста и пришли мне.")
            fatalErrorText = sb.toString()
        }
    }

    private fun update(delta: Float) {
        runTime += delta
        itemSystem.update(delta)
        playerDamage.update(delta)
        // Кольцо жизненной силы: динамический max HP (обновляем каждый тик, чтобы работало сразу после выбора).
        playerDamage.setMaxHp(baseMaxHp * progression.getMaxHpMultiplier())

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
        // Difficulty multiplier for enemy combat (matches spawnEnemy() logic)
        val dc = Balance.cfg.spawning.enemy.difficulty
        val timeMul = 1f + (runTime / runDurationSec).coerceIn(0f, 1f) * dc.timeMulBonusAtEnd
        // enemyDiffMul влияет на дальний урон/темп врагов в EnemySystem, поэтому домножаем на выбранную сложность по урону.
        val enemyDiffMul = progression.getDifficultyMultiplier() * timeMul * selectedDifficulty.enemyDamageMul
        enemySystem.updateEnemies(
            delta = delta,
            runTime = runTime,
            difficultyMul = enemyDiffMul,
            // Врождёнка Фрозки: замедление врагов (движение + темп стрельбы дальников).
            enemySpeedMul = (1f - progression.getInnateSlowBonus()).coerceIn(0.35f, 1f),
            playerPos = playerPos,
            arenaHalfW = arenaHalfW,
            arenaHalfH = arenaHalfH,
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
        val easyBonus = if (selectedDifficulty == DifficultyKind.EASY) 2f else 1f
        val leveledUp = loot.updateXpOrbs(delta, playerPos, progression, xpMul = easyBonus)
        loot.updateGoldOrbs(delta, playerPos, progression, goldMul = easyBonus)
        updateEnemyStatusesAndDots(delta)
        updatePoisonTraps(delta)
        itemSystem.applyToxicDamage(
            delta = delta,
            // Врождёнка Фарзера: ускорение ДОТОК (по факту множитель урона в секунду).
            damageMul = (1f + progression.getInnateDotSpeedBonus()).coerceIn(0.2f, 3.0f),
            // Врождёнка Трапера: увеличение области (радиус облака/зон).
            radiusMul = (1f + progression.getInnateAreaSizeBonus()).coerceIn(0.4f, 3.0f),
            playerPos = playerPos,
            enemies = enemies,
            isAlive = { it.hp > 0f },
            getPos = { it.pos },
            damageEnemy = { e, dmg -> e.hp -= dmg },
        )
        applyContactDamage()
        updateEnemyProjectiles(delta)
        loot.tryOpenChestByProximity(playerPos)?.let { res ->
            // Сундук теперь сразу выдаёт рандомный предмет (без UI выбора)
            items.add(res.item)
            itemSystem.addItem(res.item)
        }

        // Камера следует за игроком
        camera.position.set(playerPos.x, playerPos.y, 0f)
        camera.update()

        // Радиус таргетинга растёт от "дальности" у оружия (как апгрейд паттерна)
        val extraRange = progression.weapons.maxOfOrNull { it.rangeLevel } ?: 0
        targetingSystem.range = Balance.cfg.targeting.baseRange + extraRange * Balance.cfg.targeting.rangePerLevel
        targetingSystem.update(delta, playerPos, enemies)

        // Святыни: выключаем на фазе босса
        if (!bossSpawned) {
            shrineSystem.update(delta = delta, runTime = runTime, playerPos = playerPos, arenaHalfW = arenaHalfW, arenaHalfH = arenaHalfH)?.let { choices ->
                pendingShrineChoices = choices
                runState = RunState.SHRINE_OPEN
                uiSystem.layoutCards(uiViewport, optionCount = pendingShrineChoices.size, withSkip = true, uiScale = uiScale)
                return
            }
        }
        updateAttacking(delta)
        updateProjectiles(delta)
        cleanupDead()

        if (leveledUp && runState == RunState.RUNNING) {
            pendingChoices = progression.makeUpgradeChoices()
            runState = RunState.LEVEL_UP
            uiSystem.layoutCards(uiViewport, optionCount = pendingChoices.size, withSkip = false, uiScale = uiScale)
        }
    }

    private fun updateEnemyStatusesAndDots(delta: Float) {
        val lifesteal = progression.getLifeStealPct()
        for (e in enemies) {
            if (e.hp <= 0f) continue

            // Slow
            if (e.slowTimer > 0f) {
                e.slowTimer = max(0f, e.slowTimer - delta)
                if (e.slowTimer <= 0f) e.slowMul = 1f
            } else {
                e.slowMul = 1f
            }

            // Burn / Bleed DOT
            var dps = 0f
            if (e.burnStacks > 0) {
                e.burnTimer = max(0f, e.burnTimer - delta)
                if (e.burnTimer <= 0f) {
                    e.burnStacks = 0
                    e.burnDpsPerStack = 0f
                } else {
                    dps += e.burnStacks * e.burnDpsPerStack
                }
            }
            if (e.bleedStacks > 0) {
                e.bleedTimer = max(0f, e.bleedTimer - delta)
                if (e.bleedTimer <= 0f) {
                    e.bleedStacks = 0
                    e.bleedDpsPerStack = 0f
                } else {
                    dps += e.bleedStacks * e.bleedDpsPerStack
                }
            }
            if (dps > 0f) {
                val dmg = dps * delta
                e.hp -= dmg
                if (lifesteal > 0f) playerDamage.heal(dmg * lifesteal)
            }
        }
    }

    private fun updatePoisonTraps(delta: Float) {
        if (poisonTraps.isEmpty()) return
        val lifesteal = progression.getLifeStealPct()
        val it = poisonTraps.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (t.armTimer > 0f) {
                t.armTimer = max(0f, t.armTimer - delta)
                continue
            }
            // Если ещё не активировали облако — ждём триггер по врагу
            if (t.cloudTimer <= 0f) {
                val r2 = t.radius * t.radius
                var triggered = false
                for (e in enemies) {
                    if (e.hp <= 0f) continue
                    val dx = e.pos.x - t.pos.x
                    val dy = e.pos.y - t.pos.y
                    if (dx * dx + dy * dy <= r2) {
                        triggered = true
                        break
                    }
                }
                if (triggered) {
                    t.cloudTimer = 3.2f
                }
                continue
            }

            // Облако активно
            t.cloudTimer = max(0f, t.cloudTimer - delta)
            val r2 = t.radius * t.radius
            val dmg = t.dps * delta
            for (e in enemies) {
                if (e.hp <= 0f) continue
                val dx = e.pos.x - t.pos.x
                val dy = e.pos.y - t.pos.y
                if (dx * dx + dy * dy <= r2) {
                    e.hp -= dmg
                    if (lifesteal > 0f) playerDamage.heal(dmg * lifesteal)
                    // Лёгкий слоу пока стоишь в облаке
                    if (t.slowPct > 0f) {
                        val mul = (1f - t.slowPct).coerceIn(0.35f, 1f)
                        e.slowMul = minOf(e.slowMul, mul)
                        e.slowTimer = max(e.slowTimer, 0.25f)
                    }
                }
            }
            if (t.cloudTimer <= 0f) it.remove()
        }
    }

    private fun enterCharacterSelect() {
        // Чистим состояние, чтобы на экране выбора не оставалось "хвостов" прошлого забега.
        runState = RunState.CHARACTER_SELECT
        runTime = 0f
        bossSpawned = false
        playerDamage.reset()
        playerPos.set(0f, 0f)
        playerVel.set(0f, 0f)

        progression.reset()
        progression.setCharacter(selectedCharacter)

        pendingChoices = emptyList()
        pendingShrineChoices = emptyList()
        items.clear()
        itemSystem.reset()
        loot.reset()
        shrineSystem.reset()

        enemies.clear()
        killedEnemies = 0
        combat.reset()
        targetingSystem.reset()
        spawnDirector.reset(initialDelay = Balance.cfg.spawning.spawnDirector.initialDelay)

        uiSystem.layoutCards(uiViewport, optionCount = characterChoices.size, withSkip = false, uiScale = uiScale)
    }

    private fun enterDifficultySelect() {
        runState = RunState.DIFFICULTY_SELECT
        pendingChoices = emptyList()
        pendingShrineChoices = emptyList()
        uiSystem.layoutCards(uiViewport, optionCount = difficultyChoices.size, withSkip = false, uiScale = uiScale)
    }

    private fun applyDifficultyPick(idx: Int) {
        if (idx < 0 || idx >= difficultyChoices.size) return
        selectedDifficulty = difficultyChoices[idx]
        enterCharacterSelect()
    }

    private fun handleDifficultySelectInput() {
        val idx = uiSystem.pollKeyPick(runState) ?: return
        applyDifficultyPick(idx)
    }

    private fun applyCharacterPick(idx: Int) {
        if (idx < 0 || idx >= characterChoices.size) return
        selectedCharacter = characterChoices[idx]
        resetRun()
    }

    private fun handleCharacterSelectInput() {
        val idx = uiSystem.pollKeyPick(runState) ?: return
        applyCharacterPick(idx)
    }

    private fun handlePauseInput() {
        // Space/Esc = продолжить (кнопка в HUD тоже работает)
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            runState = RunState.RUNNING
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
        progression.setCharacter(selectedCharacter)
        // Стартовое оружие персонажа
        progression.weapons += com.factorlite.progression.WeaponInstance(selectedCharacter.startWeapon, level = 1)
        pendingChoices = emptyList()
        pendingShrineChoices = emptyList()
        items.clear()
        itemSystem.reset()
        loot.reset()
        shrineSystem.reset()

        enemies.clear()
        killedEnemies = 0
        combat.reset()
        targetingSystem.reset()
        spawnDirector.reset(initialDelay = Balance.cfg.spawning.spawnDirector.initialDelay)
        uiSystem.layoutCards(uiViewport, optionCount = 3, withSkip = false, uiScale = uiScale)

        // Стартовая “дыра”, чтобы сразу было что стрелять (через 0.2с спавнится первый враг)
    }

    private fun updateSpawns(delta: Float) {
        // Важно: считать только ЖИВЫХ, иначе спавн “душится” из-за трупов до cleanupDead().
        val alive = enemies.count { it.hp > 0f }
        val spawns = spawnDirector.update(delta = delta, runTime = runTime, aliveEnemies = alive, intervalMul = selectedDifficulty.spawnIntervalMul)
        repeat(spawns) {
            spawnEnemy()
        }
    }

    private fun spawnEnemy() {
        val eb = Balance.cfg.spawning.enemy
        // Спавнимся по краям арены (слегка за пределом)
        val pad = eb.spawnPad
        val side = MathUtils.random(3)
        val x: Float
        val y: Float
        when (side) {
            0 -> { // left
                x = -arenaHalfW + pad
                y = MathUtils.random(-arenaHalfH, arenaHalfH)
            }
            1 -> { // right
                x = arenaHalfW - pad
                y = MathUtils.random(-arenaHalfH, arenaHalfH)
            }
            2 -> { // bottom
                x = MathUtils.random(-arenaHalfW, arenaHalfW)
                y = -arenaHalfH + pad
            }
            else -> { // top
                x = MathUtils.random(-arenaHalfW, arenaHalfW)
                y = arenaHalfH - pad
            }
        }

        // Тип врага: чем больше runTime, тем чаще быстрые/танки/дальники
        val tc = eb.typeChances
        val pFast = (runTime / tc.fastRampSeconds).coerceIn(0f, tc.fastCap)
        val pTank = (runTime / tc.tankRampSeconds).coerceIn(0f, tc.tankCap)
        val pRanged = (runTime / tc.rangedRampSeconds).coerceIn(0f, tc.rangedCap)
        val roll = MathUtils.random()

        // Базовая сложность растёт по времени (даже без святынь)
        val dc = eb.difficulty
        val timeMul = 1f + (runTime / runDurationSec).coerceIn(0f, 1f) * dc.timeMulBonusAtEnd
        // HP врагов: домножаем на выбранную сложность (Hard = 1.0 => как сейчас).
        val diff = progression.getDifficultyMultiplier() * timeMul * selectedDifficulty.enemyHpMul
        val rewardMul = progression.getRewardMultiplier() * (1f + (timeMul - 1f) * dc.rewardTimeFactor)

        fun scaledSpeed(base: Float): Float = base * (1f + (diff - 1f) * dc.speedDiffFactor)

        val baseStats = eb.base
        val baseEnemy = when {
            roll < pTank ->
                Enemy(
                    Vector2(x, y),
                    hp = baseStats.tank.hp * diff,
                    maxHp = baseStats.tank.hp * diff,
                    speed = scaledSpeed(baseStats.tank.speed),
                    contactDamage = baseStats.tank.contactDamage * diff * selectedDifficulty.enemyDamageMul,
                    radius = baseStats.tank.radius,
                    xpReward = (baseStats.tank.xp * rewardMul).toInt().coerceAtLeast(1),
                    goldReward = (baseStats.tank.gold * rewardMul).toInt().coerceAtLeast(1),
                    kind = EnemyKind.TANK,
                )
            roll < pTank + pFast ->
                Enemy(
                    Vector2(x, y),
                    hp = baseStats.fast.hp * diff,
                    maxHp = baseStats.fast.hp * diff,
                    speed = scaledSpeed(baseStats.fast.speed),
                    contactDamage = baseStats.fast.contactDamage * diff * selectedDifficulty.enemyDamageMul,
                    radius = baseStats.fast.radius,
                    xpReward = (baseStats.fast.xp * rewardMul).toInt().coerceAtLeast(1),
                    goldReward = (baseStats.fast.gold * rewardMul).toInt().coerceAtLeast(1),
                    kind = EnemyKind.FAST,
                )
            roll < pTank + pFast + pRanged ->
                Enemy(
                    Vector2(x, y),
                    hp = baseStats.ranged.hp * diff,
                    maxHp = baseStats.ranged.hp * diff,
                    speed = scaledSpeed(baseStats.ranged.speed),
                    contactDamage = baseStats.ranged.contactDamage * diff * selectedDifficulty.enemyDamageMul,
                    radius = baseStats.ranged.radius,
                    xpReward = (baseStats.ranged.xp * rewardMul).toInt().coerceAtLeast(1),
                    goldReward = (baseStats.ranged.gold * rewardMul).toInt().coerceAtLeast(1),
                    kind = EnemyKind.RANGED,
                    shootCooldown = MathUtils.random(baseStats.ranged.shootCooldownMin, baseStats.ranged.shootCooldownMax),
                )
            else ->
                Enemy(
                    Vector2(x, y),
                    hp = baseStats.normal.hp * diff,
                    maxHp = baseStats.normal.hp * diff,
                    speed = scaledSpeed(baseStats.normal.speed),
                    contactDamage = baseStats.normal.contactDamage * diff * selectedDifficulty.enemyDamageMul,
                    radius = baseStats.normal.radius,
                    xpReward = (baseStats.normal.xp * rewardMul).toInt().coerceAtLeast(1),
                    goldReward = (baseStats.normal.gold * rewardMul).toInt().coerceAtLeast(1),
                    kind = EnemyKind.NORMAL,
                )
        }

        // Элитка: редкий “пик” — сильнее и гарантирует сундук.
        val elite = eb.elite
        val pEliteBase =
            (elite.pBaseStart + (runTime / elite.rampSeconds) * elite.pBaseRampAdd).coerceAtMost(elite.pBaseCap)
        val pElite = (pEliteBase * progression.getEliteFrequencyMultiplier()).coerceAtMost(elite.pFinalCap)
        val makeElite = MathUtils.random() < pElite
        val enemy = if (!makeElite) baseEnemy else {
            baseEnemy.copy(
                hp = baseEnemy.hp * elite.hpMul,
                maxHp = baseEnemy.hp * elite.hpMul,
                speed = baseEnemy.speed * elite.speedMul,
                contactDamage = baseEnemy.contactDamage * elite.contactDamageMul,
                radius = baseEnemy.radius * elite.radiusMul,
                xpReward = baseEnemy.xpReward * elite.xpMul,
                goldReward = baseEnemy.goldReward * elite.goldMul,
                isElite = true,
                // чуть меньше “раскидываем” дальника, чтобы элитка чаще дралась рядом
                shootCooldown = if (baseEnemy.kind == EnemyKind.RANGED) {
                    MathUtils.random(elite.rangedShootCooldownMin, elite.rangedShootCooldownMax)
                } else {
                    baseEnemy.shootCooldown
                },
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
                val res = playerDamage.applyHit(dmg * selectedDifficulty.enemyDamageMul, itemSystem, dodgeChance = progression.getDodgeChance())
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
                val res = playerDamage.applyHit(e.contactDamage, itemSystem, dodgeChance = progression.getDodgeChance())
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
        val critChance = progression.getCritChance()
        val critMul = progression.getCritDamageMultiplier()

        // Каждый слот оружия атакует независимо
        for (w in progression.weapons) {
            w.cooldown = max(0f, w.cooldown - delta)
            if (w.cooldown > 0f) continue

            when (w.kind) {
                WeaponKind.FROSTSTAFF, WeaponKind.FIRESTAFF -> {
                    val t = targetingSystem.target ?: continue
                    val dmgMul = progression.getDamageMultiplierForWeapon(w.kind)
                    fireSingleTargetProjectile(w.kind, w, t, dmgMul, critChance, critMul, fireRateMul)
                }

                WeaponKind.REVOLVER -> {
                    val dmgMul = progression.getDamageMultiplierForWeapon(w.kind)
                    fireRevolver(w, dmgMul, critChance, critMul, fireRateMul)
                }

                WeaponKind.POISON_TRAP -> {
                    val t = targetingSystem.target ?: continue
                    val dmgMul = progression.getDamageMultiplierForWeapon(w.kind)
                    throwPoisonTrap(w, t, dmgMul, fireRateMul)
                }

                WeaponKind.POISON_AURA -> {
                    val dmgMul = progression.getDamageMultiplierForWeapon(w.kind)
                    tickPoisonAura(w, dmgMul, fireRateMul)
                }

                WeaponKind.KATANA, WeaponKind.DAGGER -> {
                    val dmgMul = progression.getDamageMultiplierForWeapon(w.kind)
                    swingMelee(w.kind, w, dmgMul, critChance, critMul, fireRateMul)
                }
            }
        }
    }

    private fun tickPoisonAura(
        w: com.factorlite.progression.WeaponInstance,
        dmgMul: Float,
        fireRateMul: Float,
    ) {
        val b = Balance.cfg.weapons.poisonAura
        val range = b.swingRangeBase + w.rangeLevel * b.swingRangePerLevel
        val r2 = range * range
        val baseDmg = b.baseDamage + w.damageLevel * b.damagePerLevel
        val dmg = baseDmg * dmgMul
        val slowPct = 0.10f

        val lifesteal = progression.getLifeStealPct()
        for (e in enemies) {
            if (e.hp <= 0f) continue
            val dx = e.pos.x - playerPos.x
            val dy = e.pos.y - playerPos.y
            if (dx * dx + dy * dy <= r2) {
                e.hp -= dmg
                if (lifesteal > 0f) playerDamage.heal(dmg * lifesteal)
                // лёгкий слоу пока в ауре
                val mul = (1f - slowPct).coerceIn(0.35f, 1f)
                e.slowMul = minOf(e.slowMul, mul)
                e.slowTimer = max(e.slowTimer, 0.25f)
            }
        }

        val baseCd = b.baseCooldownSec / (1f + b.cooldownLevelFactor * w.cooldownLevel)
        w.cooldown = baseCd / fireRateMul
    }

    private fun throwPoisonTrap(
        w: com.factorlite.progression.WeaponInstance,
        t: Enemy,
        dmgMul: Float,
        fireRateMul: Float,
    ) {
        val b = Balance.cfg.weapons.poisonTrap
        // Ставим ловушку прямо под ближайшего врага (триггер по наступанию).
        val areaMul = (1f + progression.getInnateAreaSizeBonus()).coerceIn(0.4f, 3.0f)
        val radius = (70f + w.rangeLevel * 10f) * areaMul
        val baseDps = (b.baseDamage + w.damageLevel * b.damagePerLevel) * dmgMul
        val slowPct = 0.12f
        poisonTraps.add(
            PoisonTrapCloud(
                pos = Vector2(t.pos.x, t.pos.y),
                armTimer = 0.20f,
                cloudTimer = 0f,
                radius = radius,
                dps = baseDps,
                slowPct = slowPct,
            ),
        )

        val baseCd = b.baseCooldownSec / (1f + b.cooldownLevelFactor * w.cooldownLevel)
        w.cooldown = baseCd / fireRateMul
    }

    private fun fireSingleTargetProjectile(
        kind: WeaponKind,
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

        val b = when (kind) {
            WeaponKind.FROSTSTAFF -> Balance.cfg.weapons.froststaff
            WeaponKind.FIRESTAFF -> Balance.cfg.weapons.firestaff
            WeaponKind.POISON_TRAP -> Balance.cfg.weapons.poisonTrap
            else -> Balance.cfg.weapons.froststaff
        }
        val speed = b.projectileSpeedBase + w.projectileSpeedLevel * b.projectileSpeedPerLevel
        val baseDamage = b.baseDamage + w.damageLevel * b.damagePerLevel
        val extra = w.extraLevel
        val count = 1 + extra
        val spread = b.spreadBaseRad * (b.accuracyPowBase.pow(w.accuracyLevel.toFloat()))
        for (i in 0 until count) {
            val angle = (i - (count - 1) / 2f) * spread
            val shotDir = Vector2(dir).rotateRad(angle)
            var dmg = baseDamage * dmgMul
            if (MathUtils.random() < critChance) dmg *= critMul
            val areaMul = (1f + progression.getInnateAreaSizeBonus()).coerceIn(0.4f, 3.0f)
            val baseRadius = if (kind == WeaponKind.POISON_TRAP) 5f else 4f
            combat.spawnProjectile(
                CombatSystem.Projectile(
                    pos = Vector2(playerPos.x, playerPos.y),
                    vel = shotDir.scl(speed),
                    damage = dmg,
                    radius = baseRadius * areaMul,
                    source = kind,
                    pierceLeft = w.pierceLevel,
                    ricochetLeft = w.ricochetLevel,
                ),
            )
        }

        val baseCd = b.baseCooldownSec / (1f + b.cooldownLevelFactor * w.cooldownLevel)
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

        val b = Balance.cfg.weapons.revolver
        val shots = (1 + w.extraLevel).coerceIn(1, 7)
        val candidates = pickNearestEnemies(shots, range = b.targetAcquireRange)
        if (candidates.isEmpty()) return

        val speed = b.projectileSpeedBase + w.projectileSpeedLevel * b.projectileSpeedPerLevel
        val baseDamage = b.baseDamage + w.damageLevel * b.damagePerLevel

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

        val baseCd = b.baseCooldownSec / (1f + b.cooldownLevelFactor * w.cooldownLevel)
        w.cooldown = baseCd / fireRateMul
    }

    private fun swingMelee(
        kind: WeaponKind,
        w: com.factorlite.progression.WeaponInstance,
        dmgMul: Float,
        critChance: Float,
        critMul: Float,
        fireRateMul: Float,
    ) {
        // Ближняя атака по нескольким ближайшим в радиусе
        val b = when (kind) {
            WeaponKind.KATANA -> Balance.cfg.weapons.katana
            WeaponKind.DAGGER -> Balance.cfg.weapons.dagger
            WeaponKind.POISON_AURA -> Balance.cfg.weapons.poisonAura
            else -> Balance.cfg.weapons.katana
        }
        val range = b.swingRangeBase + w.rangeLevel * b.swingRangePerLevel + w.pierceLevel * b.pierceRangePerLevel
        val hits = (1 + w.extraLevel).coerceIn(1, 8)
        val candidates = pickNearestEnemies(hits, range = range)
        if (candidates.isEmpty()) return

        val baseDamage = b.baseDamage + w.damageLevel * b.damagePerLevel
        for (e in candidates) {
            var dmg = baseDamage * dmgMul
            if (MathUtils.random() < critChance) dmg *= critMul
            e.hp -= dmg
            val lifesteal = progression.getLifeStealPct()
            if (lifesteal > 0f) playerDamage.heal(dmg * lifesteal)

            if (kind == WeaponKind.DAGGER) {
                // Кровотечение по шансy
                val proc = 0.28f
                if (MathUtils.random() < proc) {
                    val maxStacks = 5
                    val dur = 3.0f
                    e.bleedStacks = (e.bleedStacks + 1).coerceAtMost(maxStacks)
                    e.bleedTimer = dur
                    val perStack = (dmg * 0.22f).coerceAtLeast(0.4f)
                    e.bleedDpsPerStack = max(e.bleedDpsPerStack, perStack)
                }
            }
            itemSystem.onEnemyHit(
                hit = e,
                playerPos = playerPos,
                isAlive = { it.hp > 0f },
                getPos = { it.pos },
                damageEnemy = { en, d -> en.hp -= d },
                findNearest = ::findNearestEnemyFromPointExcludingVisited,
            )
        }

        val baseCd = b.baseCooldownSec / (1f + b.cooldownLevelFactor * w.cooldownLevel)
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
        val lifesteal = progression.getLifeStealPct()
        combat.updatePlayerProjectiles(
            delta = delta,
            arenaHalfW = arenaHalfW,
            arenaHalfH = arenaHalfH,
            enemies = enemies,
            isAlive = { it.hp > 0f },
            getPos = { it.pos },
            getRadius = { it.radius },
            damageEnemy = { e, dmg ->
                e.hp -= dmg
                if (lifesteal > 0f) playerDamage.heal(dmg * lifesteal)
            },
            onEnemyHit = { e, source, dmg ->
                applyWeaponOnHit(source = source, enemy = e, hitDamage = dmg)
                itemSystem.onEnemyHit(
                    hit = e,
                    playerPos = playerPos,
                    isAlive = { it.hp > 0f },
                    getPos = { it.pos },
                    damageEnemy = { en, d ->
                        en.hp -= d
                        if (lifesteal > 0f) playerDamage.heal(d * lifesteal)
                    },
                    findNearest = ::findNearestEnemyFromPointExcludingVisited,
                )
            },
            findNearestEnemyExcluding = { exclude, fromX, fromY, maxRange2 ->
                findNearestEnemyFromPointExcludingVisited(fromX, fromY, setOf(System.identityHashCode(exclude)), maxRange2)
            },
        )
    }

    private fun applyWeaponOnHit(source: WeaponKind, enemy: Enemy, hitDamage: Float) {
        if (enemy.hp <= 0f) return
        when (source) {
            WeaponKind.FROSTSTAFF -> {
                // Слоу по попаданию
                val slowPct = 0.22f
                val slowSec = 1.20f
                val mul = (1f - slowPct).coerceIn(0.35f, 1f)
                enemy.slowMul = minOf(enemy.slowMul, mul)
                enemy.slowTimer = max(enemy.slowTimer, slowSec)
            }
            WeaponKind.FIRESTAFF -> {
                // Горение стакается
                val maxStacks = 6
                val dur = 2.8f
                enemy.burnStacks = (enemy.burnStacks + 1).coerceAtMost(maxStacks)
                enemy.burnTimer = dur
                val perStack = (hitDamage * 0.35f).coerceAtLeast(0.5f)
                enemy.burnDpsPerStack = max(enemy.burnDpsPerStack, perStack)
            }
            else -> Unit
        }
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
                killedEnemies += 1
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
        val s = uiScale.coerceIn(1.0f, 2.0f)
        val padX = 16f * s
        val top = uiViewport.worldHeight - 16f * s
        val maxW = (uiViewport.worldWidth - padX * 2f).coerceAtLeast(100f)
        val gap = 8f * s

        fun drawWrapped(text: String, x: Float, yTop: Float): Float {
            // Возвращает новую Y-координату (верх следующего блока), уже со смещением вниз.
            glyph.setText(font, text, Color.WHITE, maxW, Align.left, true)
            font.draw(batch, glyph, x, yTop)
            return yTop - glyph.height - gap
        }

        var y = top
        y = drawWrapped(
            "HP: ${playerDamage.hp.toInt()}   LVL: ${progression.level}   GOLD: ${loot.gold}   Time: ${runTime.toInt()}s   Kills: $killedEnemies",
            padX,
            y,
        )

        val lineWeapon = buildString {
            append("Оружие: ")
            if (progression.weapons.isEmpty()) append("-")
            for ((i, w) in progression.weapons.withIndex()) {
                if (i > 0) append(" | ")
                val up = (w.level - 1).coerceAtLeast(0)
                append("${w.kind.uiName} Lv$up")
            }
        }
        y = drawWrapped(lineWeapon, padX, y)

        val lineRings = buildString {
            append("Кольца: ")
            if (progression.rings.isEmpty()) append("-")
            for ((i, p) in progression.rings.withIndex()) {
                if (i > 0) append(" | ")
                val up = (p.level - 1).coerceAtLeast(0)
                append("${p.kind.uiName} Lv$up")
            }
        }
        y = drawWrapped(lineRings, padX, y)

        val lineItems = buildString {
            append("Предметы: ")
            if (itemSystem.items.isEmpty()) {
                append("-")
            } else {
                val counts = LinkedHashMap<com.factorlite.loot.ItemKind, Int>()
                for (it in itemSystem.items) {
                    counts[it.kind] = (counts[it.kind] ?: 0) + 1
                }
                var i = 0
                for ((k, n) in counts) {
                    if (i++ > 0) append(" | ")
                    append("${k.uiName} x$n")
                }
            }
        }
        y = drawWrapped(lineItems, padX, y)

        // Кнопка паузы (справа сверху, но под HUD-текстом)
        val hudUsed = (top - y).coerceAtLeast(0f)
        val pauseSize = 44f * s
        pauseRect.set(
            uiViewport.worldWidth - 16f * s - pauseSize,
            (uiViewport.worldHeight - 16f * s - pauseSize - hudUsed).coerceAtLeast(16f * s),
            pauseSize,
            pauseSize,
        )
        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(pauseRect.x, pauseRect.y, pauseRect.width, pauseRect.height)
        shapes.end()
        batch.begin()
        font.color = Color.WHITE
        val pauseLabel = if (runState == RunState.PAUSED) ">" else "||"
        font.draw(batch, pauseLabel, pauseRect.x + 14f * s, pauseRect.y + 30f * s)

        // Миникарта (справа сверху)
        drawMiniMap(topOffset = hudUsed + 8f)

        // Босс-бар (если жив)
        enemies.firstOrNull { it.isBoss }?.let { boss ->
            drawBossBar(boss, topOffset = hudUsed + 8f)
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
            RunState.LEVEL_UP -> uiSystem.drawLevelUpOverlay(batch, shapes, font, uiViewport, uiCamera, pendingChoices, uiScale = uiScale)
            RunState.SHRINE_OPEN -> uiSystem.drawShrineOverlay(batch, shapes, font, uiViewport, uiCamera, pendingShrineChoices, uiScale = uiScale)
            RunState.CHARACTER_SELECT -> uiSystem.drawCharacterSelectOverlay(batch, shapes, font, uiViewport, uiCamera, characterChoices, uiScale = uiScale)
            RunState.DIFFICULTY_SELECT -> uiSystem.drawDifficultySelectOverlay(
                batch,
                shapes,
                font,
                uiViewport,
                uiCamera,
                difficultyChoices.map { it.uiName to it.description },
                uiScale = uiScale,
            )
            RunState.PAUSED -> {
                font.color = Color.WHITE
                font.draw(batch, "PAUSE", uiViewport.worldWidth / 2f - 40f, uiViewport.worldHeight / 2f + 10f)
                font.color = Color.LIGHT_GRAY
                font.draw(batch, "Tap > / Space / Esc", uiViewport.worldWidth / 2f - 90f, uiViewport.worldHeight / 2f - 16f)
            }
            else -> Unit
        }
        batch.end()
    }

    private fun drawMiniMap(topOffset: Float = 0f) {
        // batch уже в begin()
        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        val uiW = uiViewport.worldWidth
        val uiH = uiViewport.worldHeight
        // Если HUD занял много места, уменьшаем миникарту и сдвигаем ниже.
        val radius = (68f - topOffset * 0.25f).coerceIn(42f, 68f)
        val cx = uiW - radius - 16f
        val cy = uiH - radius - 16f - topOffset

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

    private fun drawBossBar(boss: Enemy, topOffset: Float = 0f) {
        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        val w = uiViewport.worldWidth
        val x = w * 0.18f
        val y = (uiViewport.worldHeight - 78f - topOffset).coerceAtLeast(24f)
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
        val bossCfg = Balance.cfg.spawning.enemy.boss
        if (bossCfg.cleanupChestsKeepEliteOnly) {
            loot.chests.removeAll { !it.isElite }
        }

        val diff = progression.getDifficultyMultiplier()
        val bossHp = bossCfg.hp * diff
        enemies.add(
            Enemy(
                pos = Vector2(playerPos.x + bossCfg.spawnOffsetX, playerPos.y),
                hp = bossHp,
                maxHp = bossHp,
                speed = bossCfg.speed,
                contactDamage = bossCfg.contactDamage * diff,
                radius = bossCfg.radius,
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
        val preferred = Gdx.files.internal("fonts/Roboto-Regular.ttf")
        val ttf = if (preferred.exists()) {
            preferred
        } else {
            // Фоллбек: если имя отличается, но шрифт положили в assets/fonts — найдём любой ttf/otf.
            val dir = Gdx.files.internal("fonts")
            if (dir.exists() && dir.isDirectory) {
                dir.list()
                    .firstOrNull { it.extension().equals("ttf", ignoreCase = true) || it.extension().equals("otf", ignoreCase = true) }
            } else {
                null
            }
        }

        if (ttf == null || !ttf.exists()) {
            Gdx.app?.error(
                "Font",
                "Missing assets font under fonts/. " +
                    "Put Roboto-Regular.ttf into assets/fonts/ (or run tools/download_fonts.ps1). Falling back to BitmapFont().",
            )
            // Фоллбек: будет без кириллицы, но хотя бы не упадём.
            font = BitmapFont()
            return
        }

        val gen = FreeTypeFontGenerator(ttf)
        try {
            val size = (uiViewport.screenHeight * 0.028f * uiScale).toInt().coerceIn(16, 42)
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

