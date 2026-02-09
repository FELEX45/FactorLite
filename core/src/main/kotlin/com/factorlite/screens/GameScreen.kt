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
import com.badlogic.gdx.Preferences
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
import com.factorlite.FactorLiteGame
import com.factorlite.audio.Audio
import com.factorlite.audio.AudioSettings
import com.factorlite.audio.Bgm
import com.factorlite.progression.GlobalBonusOption
import com.factorlite.progression.CharacterKind
import com.factorlite.progression.playerSpriteKey
import com.factorlite.progression.RunProgression
import com.factorlite.progression.UpgradeOption
import com.factorlite.progression.WeaponKind
import com.factorlite.progression.uiName
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Locale

class GameScreen(private val game: FactorLiteGame) : ScreenAdapter() {
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

    // Базовый скейл интерфейса (на “референсном” разрешении 540x960).
    // Итоговый скейл пересчитывается от реального разрешения: см. recalcUiScale().
    private val baseUiScale: Float = 1.45f
    private var uiScale: Float = baseUiScale

    private lateinit var joystick: FloatingJoystick
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

    private val prefs: Preferences by lazy { Gdx.app.getPreferences("factorlite_prefs") }
    private val prefKeyDifficulty = "difficulty"
    private val prefKeyCharacter = "character"

    private enum class DifficultyKind(
        val uiName: String,
        val description: String,
        val biomeName: String,
        val worldTextureKey: String,
        val enemyHpMul: Float,
        val enemyDamageMul: Float,
        val spawnIntervalMul: Float,
    ) {
        EASY(
            "Лёгкая",
            "Проще пережить старт и разогнаться.",
            biomeName = "Лесная полянка",
            worldTextureKey = "bg_forest",
            enemyHpMul = 0.85f,
            enemyDamageMul = 0.80f,
            spawnIntervalMul = 1.25f,
        ),
        MEDIUM(
            "Средняя",
            "Чуть проще, чем сейчас.",
            biomeName = "Пустыня",
            worldTextureKey = "bg_desert",
            enemyHpMul = 0.92f,
            enemyDamageMul = 0.90f,
            spawnIntervalMul = 1.10f,
        ),
        HARD(
            "Сложная",
            "Текущая (как сейчас).",
            biomeName = "Зимний биом",
            worldTextureKey = "bg_winter",
            enemyHpMul = 1.00f,
            enemyDamageMul = 1.00f,
            spawnIntervalMul = 1.00f,
        ),
    }

    private var selectedDifficulty: DifficultyKind = DifficultyKind.HARD
    private val difficultyChoices: List<DifficultyKind> = DifficultyKind.entries.toList()

    // Пауза: кнопка в правом верхнем углу HUD
    private val pauseRect = Rectangle()
    private lateinit var pauseInput: InputAdapter
    private val pauseContinueRect = Rectangle()
    private val pauseMenuRect = Rectangle()
    private val pauseSfxRect = Rectangle()
    private val pauseMusicRect = Rectangle()

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

    // --- Визуальные FX ближней атаки (катана/кинжал) ---
    private data class MeleeSlashFx(
        var x: Float,
        var y: Float,
        var rotDeg: Float,
        var timeLeft: Float,
        var duration: Float,
        var length: Float,
        var width: Float,
        var r: Float,
        var g: Float,
        var b: Float,
    )

    private val meleeFx: MutableList<MeleeSlashFx> = ArrayList()

    // --- Вылетающие цифры урона ---
    private data class DamageNumberFx(
        var x: Float,
        var y: Float,
        val value: Int,
        var timeLeft: Float,
        val duration: Float,
        val r: Float,
        val g: Float,
        val b: Float,
    )

    private val damageNumbers: MutableList<DamageNumberFx> = ArrayList()

    private fun spawnDamageNumber(x: Float, y: Float, dmg: Float, crit: Boolean = false) {
        val r = if (crit) 1.0f else 1.0f
        val g = if (crit) 0.86f else 1.0f
        val b = if (crit) 0.25f else 1.0f
        spawnDamageNumberTint(x, y, dmg, r, g, b)
    }

    private fun spawnDamageNumberTint(x: Float, y: Float, dmg: Float, r: Float, g: Float, b: Float) {
        val v = dmg.toInt().coerceAtLeast(1)
        val ox = MathUtils.random(-6f, 6f)
        val oy = MathUtils.random(0f, 10f)
        damageNumbers.add(
            DamageNumberFx(
                x = x + ox,
                y = y + oy,
                value = v,
                timeLeft = 0.65f,
                duration = 0.65f,
                r = r,
                g = g,
                b = b,
            ),
        )
        // Не даём разрастаться бесконечно (на всякий).
        if (damageNumbers.size > 90) {
            damageNumbers.subList(0, 30).clear()
        }
    }

    private fun accDotDamageNumber(enemy: Enemy, dmg: Float, r: Float, g: Float, b: Float) {
        if (dmg <= 0.001f) return
        val key = System.identityHashCode(enemy)
        val st = dotNum.getOrPut(key) {
            DotNumState(
                acc = 0f,
                timer = 0f,
                x = enemy.pos.x,
                y = enemy.pos.y,
                radius = enemy.radius,
                r = r,
                g = g,
                b = b,
            )
        }
        st.acc += dmg
        st.x = enemy.pos.x
        st.y = enemy.pos.y
        st.radius = enemy.radius
        st.r = r
        st.g = g
        st.b = b

        // Для DOT не спамим каждый тик: показываем раз в ~0.25s суммарный урон.
        if (st.timer <= 0f) st.timer = 0.25f
    }

    private fun updateDotDamageNumbers(delta: Float) {
        if (dotNum.isEmpty()) return
        val it = dotNum.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val st = e.value
            st.timer -= delta
            if (st.timer > 0f) continue

            if (st.acc > 0.001f) {
                // DOT: показываем редко и зелёным (яд)
                spawnDamageNumberTint(st.x, st.y + st.radius + 8f, st.acc, st.r, st.g, st.b)
                st.acc = 0f
            } else {
                it.remove()
            }
        }
    }

    // Простая арена (пока) — под портретное окно.
    private val arenaHalfW = 520f
    private val arenaHalfH = 920f

    // SFX throttles
    private var killSfxCooldown = 0f
    private var playerHitSfxCooldown = 0f

    private data class DotNumState(
        var acc: Float,
        var timer: Float,
        var x: Float,
        var y: Float,
        var radius: Float,
        var r: Float,
        var g: Float,
        var b: Float,
    )
    private val dotNum: MutableMap<Int, DotNumState> = HashMap()

    override fun show() {
        // setScreen(...) может произойти до первого resize(), поэтому обновим вьюпорты вручную.
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        uiViewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
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
                if (runState != RunState.RUNNING &&
                    runState != RunState.PAUSED &&
                    runState != RunState.VICTORY &&
                    runState != RunState.GAME_OVER
                ) {
                    return false
                }
                tmpVec.set(screenX.toFloat(), screenY.toFloat())
                uiViewport.unproject(tmpVec)

                if (runState == RunState.PAUSED || runState == RunState.VICTORY || runState == RunState.GAME_OVER) {
                    layoutPauseMenu()
                    if (pauseContinueRect.contains(tmpVec.x, tmpVec.y)) {
                        if (runState == RunState.PAUSED) {
                            Audio.uiClick()
                            Audio.resume()
                            runState = RunState.RUNNING
                        } else {
                            Audio.uiClick()
                            // Победа/поражение: рестарт за того же персонажа/сложность
                            resetRun()
                        }
                        return true
                    }
                    if (pauseMenuRect.contains(tmpVec.x, tmpVec.y)) {
                        Audio.uiClick()
                        // Безопасно переключаем экран после обработки ввода.
                        Gdx.app.postRunnable {
                            game.setScreen(MainMenuScreen(game))
                        }
                        return true
                    }
                    if (pauseSfxRect.contains(tmpVec.x, tmpVec.y)) {
                        val was = Audio.enabled
                        Audio.enabled = !Audio.enabled
                        AudioSettings.save()
                        if (!was && Audio.enabled) Audio.uiClick()
                        return true
                    }
                    if (pauseMusicRect.contains(tmpVec.x, tmpVec.y)) {
                        val was = Bgm.enabled
                        Bgm.enabled = !Bgm.enabled
                        Bgm.applySettings()
                        AudioSettings.save()
                        if (!was && Bgm.enabled) Audio.uiClick()
                        return true
                    }
                }

                if (pauseRect.contains(tmpVec.x, tmpVec.y)) {
                    runState = if (runState == RunState.RUNNING) {
                        Audio.pause()
                        RunState.PAUSED
                    } else {
                        Audio.resume()
                        RunState.RUNNING
                    }
                    return true
                }
                return false
            }
        }

        recalcUiScale()
        joystick = FloatingJoystick(
            deadzonePx = 12f * uiScale,
            radiusPx = 110f * uiScale,
        )

        inputMux.addProcessor(uiTapInput)
        inputMux.addProcessor(pauseInput)
        inputMux.addProcessor(joystick)
        Gdx.input.inputProcessor = inputMux
        rebuildFont()
        loadSelectionsFromPrefs()
        enterDifficultySelect()
    }

    override fun hide() {
        // Чтобы старый Screen не продолжал принимать клики.
        if (Gdx.input.inputProcessor === inputMux) {
            Gdx.input.inputProcessor = null
        }
    }

    private fun layoutPauseMenu() {
        val s = uiScale.coerceIn(0.9f, 3.5f)
        val w = uiViewport.worldWidth
        val h = uiViewport.worldHeight
        val bw = (w * 0.64f).coerceIn(240f * s, 520f * s)
        val bh = (76f * s).coerceIn(54f * s, 92f * s)
        val gap = 16f * s
        val cx = w / 2f
        val topY = h / 2f + (bh + gap) * 0.5f
        pauseContinueRect.set(cx - bw / 2f, topY, bw, bh)
        pauseMenuRect.set(cx - bw / 2f, topY - (bh + gap), bw, bh)

        val th = (52f * s).coerceIn(40f * s, 64f * s)
        val tw = (bw * 0.49f).coerceAtLeast(200f * s)
        val ty = pauseMenuRect.y - (th + 14f * s)
        pauseSfxRect.set(cx - tw - 10f * s, ty, tw, th)
        pauseMusicRect.set(cx + 10f * s, ty, tw, th)
    }

    private fun drawPauseMenuOverlay() {
        layoutPauseMenu()
        // затемнение + кнопки
        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.55f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)

        fun btn(r: Rectangle, col: Color) {
            shapes.color = col
            shapes.rect(r.x, r.y, r.width, r.height)
            shapes.color = Color(0f, 0f, 0f, 0.22f)
            shapes.rect(r.x, r.y, r.width, 2f * uiScale)
            shapes.rect(r.x, r.y + r.height - 2f * uiScale, r.width, 2f * uiScale)
        }
        btn(pauseContinueRect, Color(0.10f, 0.45f, 0.22f, 0.92f))
        btn(pauseMenuRect, Color(0.12f, 0.12f, 0.16f, 0.92f))
        btn(pauseSfxRect, if (Audio.enabled) Color(0.10f, 0.45f, 0.22f, 0.78f) else Color(0.12f, 0.12f, 0.16f, 0.78f))
        btn(pauseMusicRect, if (Bgm.enabled) Color(0.10f, 0.45f, 0.22f, 0.78f) else Color(0.12f, 0.12f, 0.16f, 0.78f))
        shapes.end()

        batch.begin()
        // текст
        val baseX = font.data.scaleX
        val baseY = font.data.scaleY
        // чуть крупнее для меню
        font.data.setScale(baseX * 1.05f, baseY * 1.05f)
        font.color = Color.WHITE
        glyph.setText(font, "ПАУЗА")
        font.draw(batch, glyph, (uiViewport.worldWidth - glyph.width) / 2f, pauseContinueRect.y + pauseContinueRect.height + 40f * uiScale)

        fun labelCentered(r: Rectangle, text: String) {
            glyph.setText(font, text, Color.WHITE, r.width, Align.center, false)
            val y = r.y + r.height / 2f + glyph.height / 2f
            font.draw(batch, glyph, r.x, y)
        }
        labelCentered(pauseContinueRect, "Продолжить")
        labelCentered(pauseMenuRect, "В главное меню")
        labelCentered(pauseSfxRect, "Звук: ${if (Audio.enabled) "ВКЛ" else "ВЫКЛ"}")
        labelCentered(pauseMusicRect, "Музыка: ${if (Bgm.enabled) "ВКЛ" else "ВЫКЛ"}")
        font.data.setScale(baseX, baseY)
    }

    private fun drawEndRunMenuOverlay(title: String, titleColor: Color) {
        layoutPauseMenu()
        // затемнение + кнопки
        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.60f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)

        fun btn(r: Rectangle, col: Color) {
            shapes.color = col
            shapes.rect(r.x, r.y, r.width, r.height)
            shapes.color = Color(0f, 0f, 0f, 0.22f)
            shapes.rect(r.x, r.y, r.width, 2f * uiScale)
            shapes.rect(r.x, r.y + r.height - 2f * uiScale, r.width, 2f * uiScale)
        }
        btn(pauseContinueRect, Color(0.10f, 0.45f, 0.22f, 0.92f))
        btn(pauseMenuRect, Color(0.12f, 0.12f, 0.16f, 0.92f))
        btn(pauseSfxRect, if (Audio.enabled) Color(0.10f, 0.45f, 0.22f, 0.78f) else Color(0.12f, 0.12f, 0.16f, 0.78f))
        btn(pauseMusicRect, if (Bgm.enabled) Color(0.10f, 0.45f, 0.22f, 0.78f) else Color(0.12f, 0.12f, 0.16f, 0.78f))
        shapes.end()

        batch.begin()

        val baseX = font.data.scaleX
        val baseY = font.data.scaleY
        font.data.setScale(baseX * 1.10f, baseY * 1.10f)
        glyph.setText(font, title)
        font.color = titleColor
        font.draw(batch, glyph, (uiViewport.worldWidth - glyph.width) / 2f, pauseContinueRect.y + pauseContinueRect.height + 40f * uiScale)

        fun labelCentered(r: Rectangle, text: String) {
            glyph.setText(font, text, Color.WHITE, r.width, Align.center, false)
            val y = r.y + r.height / 2f + glyph.height / 2f
            font.color = Color.WHITE
            font.draw(batch, glyph, r.x, y)
        }
        labelCentered(pauseContinueRect, "Начать ещё раз")
        labelCentered(pauseMenuRect, "В главное меню")
        labelCentered(pauseSfxRect, "Звук: ${if (Audio.enabled) "ВКЛ" else "ВЫКЛ"}")
        labelCentered(pauseMusicRect, "Музыка: ${if (Bgm.enabled) "ВКЛ" else "ВЫКЛ"}")

        font.data.setScale(baseX, baseY)
    }

    private fun recalcUiScale() {
        // ScreenViewport работает в пикселях: чем выше разрешение, тем “мельче” выглядит UI.
        // Приводим UI к референсному размеру относительно игровой вьюхи (viewW/viewH).
        val sx = Gdx.graphics.width / viewW
        val sy = Gdx.graphics.height / viewH
        val s = min(sx, sy)
        uiScale = (baseUiScale * s).coerceIn(0.9f, 3.5f)
    }

    private fun loadSelectionsFromPrefs() {
        val rawD = prefs.getString(prefKeyDifficulty, "")
        val rawC = prefs.getString(prefKeyCharacter, "")

        DifficultyKind.entries.firstOrNull { it.name == rawD }?.let { selectedDifficulty = it }
        CharacterKind.entries.firstOrNull { it.name == rawC }?.let { selectedCharacter = it }

        // Чтобы в меню (до resetRun) уже рисовался правильный спрайт персонажа
        progression.setCharacter(selectedCharacter)
    }

    private fun saveSelectionsToPrefs() {
        prefs.putString(prefKeyDifficulty, selectedDifficulty.name)
        prefs.putString(prefKeyCharacter, selectedCharacter.name)
        prefs.flush()
    }

    private fun DifficultyKind.uiCardDesc(): String {
        fun f(x: Float) = String.format(Locale.US, "%.2f", x)
        val reward = if (this == DifficultyKind.EASY) "Бонус: x2 опыт и золото.\n" else ""
        val biome = "Биом: $biomeName.\n"
        val stats = "Враги: HP x${f(enemyHpMul)}, урон x${f(enemyDamageMul)}. Спавн x${f(spawnIntervalMul)}."
        return reward + biome + description + "\n" + stats
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        uiViewport.update(width, height, true)
        recalcUiScale()
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
                handleEndRunInput()
            }
            RunState.GAME_OVER -> {
                handleEndRunInput()
            }
        }

        Gdx.gl.glClearColor(0.07f, 0.08f, 0.10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        shapes.projectionMatrix = camera.combined

        // Фон мира (по сложности). Если текстуры нет — будет плейсхолдер.
        batch.projectionMatrix = camera.combined
        batch.begin()
        val worldBg = Sprites.get(selectedDifficulty.worldTextureKey) {
            when (selectedDifficulty) {
                DifficultyKind.EASY -> Sprites.solidTexture(32, Color(0.18f, 0.32f, 0.18f, 1f))   // зелёный
                DifficultyKind.MEDIUM -> Sprites.solidTexture(32, Color(0.55f, 0.45f, 0.22f, 1f)) // песок
                DifficultyKind.HARD -> Sprites.solidTexture(32, Color(0.72f, 0.80f, 0.86f, 1f))   // снег/лёд
            }
        }
        // Приглушаем фон, чтобы он не "спорил" с врагами/игроком (меньше контраста/насыщенности).
        // Важно: обязательно восстанавливаем batch.color, иначе повлияет на весь остальной рендер.
        val c = batch.color
        val pr = c.r
        val pg = c.g
        val pb = c.b
        val pa = c.a
        val tint = when (selectedDifficulty) {
            DifficultyKind.EASY -> Color(0.62f, 0.66f, 0.62f, 1f)
            DifficultyKind.MEDIUM -> Color(0.68f, 0.66f, 0.60f, 1f)
            DifficultyKind.HARD -> Color(0.78f, 0.80f, 0.86f, 1f)
        }
        batch.setColor(tint.r, tint.g, tint.b, pa)
        batch.draw(worldBg, -arenaHalfW, -arenaHalfH, arenaHalfW * 2f, arenaHalfH * 2f)
        // Лёгкая "дымка" поверх фона для дополнительного снижения визуального шума.
        val fog = Sprites.get("bg_fog") { Sprites.solidTexture(2, Color.WHITE) }
        batch.setColor(0f, 0f, 0f, 0.14f)
        batch.draw(fog, -arenaHalfW, -arenaHalfH, arenaHalfW * 2f, arenaHalfH * 2f)
        batch.setColor(pr, pg, pb, pa)
        batch.end()

        // Контур арены поверх фона
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.DARK_GRAY
        shapes.rect(-arenaHalfW, -arenaHalfH, arenaHalfW * 2f, arenaHalfH * 2f)
        shapes.end()

        // --- Спрайты мира ---
        batch.begin()

        // Визуальный масштаб мировых спрайтов (не влияет на хитбоксы/баланс).
        // Просили сделать персонажа/врагов/сундуки/шрайны крупнее относительно окна.
        val worldSpriteScale = 2.0f

        val texPlayer = Sprites.get(progression.character.playerSpriteKey) {
            Sprites.get("player") { Sprites.circleTexture(64, Color.WHITE) }
        }
        val invulnT = (playerDamage.invuln / 0.6f).coerceIn(0f, 1f)
        val alpha = if (playerDamage.invuln > 0f) 0.35f + 0.65f * (1f - Interpolation.fade.apply(invulnT)) else 1f
        Sprites.drawCentered(
            batch,
            texPlayer,
            playerPos.x,
            playerPos.y,
            playerRadius * 2f * worldSpriteScale,
            playerRadius * 2f * worldSpriteScale,
            alpha = alpha,
        )

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
            Sprites.drawCentered(
                batch,
                t,
                e.pos.x,
                e.pos.y,
                e.radius * 2f * worldSpriteScale,
                e.radius * 2f * worldSpriteScale,
            )
        }

        // FX ближней атаки (рисуем поверх врагов для читаемости)
        if (meleeFx.isNotEmpty()) {
            val texSlash = Sprites.get("fx_slash") { Sprites.solidTexture(2, Color.WHITE) }
            val tw = texSlash.width
            val th = texSlash.height
            val c2 = batch.color
            val pr2 = c2.r
            val pg2 = c2.g
            val pb2 = c2.b
            val pa2 = c2.a
            for (fx in meleeFx) {
                val t = (fx.timeLeft / fx.duration).coerceIn(0f, 1f)
                val a = (0.10f + 0.90f * t) * 0.85f
                batch.setColor(fx.r, fx.g, fx.b, a)
                val wFx = fx.length * worldSpriteScale
                val hFx = fx.width * worldSpriteScale
                batch.draw(
                    texSlash,
                    fx.x - wFx / 2f,
                    fx.y - hFx / 2f,
                    wFx / 2f,
                    hFx / 2f,
                    wFx,
                    hFx,
                    1f,
                    1f,
                    fx.rotDeg,
                    0,
                    0,
                    tw,
                    th,
                    false,
                    false,
                )
            }
            batch.setColor(pr2, pg2, pb2, pa2)
        }

        // Цифры урона (поверх мира)
        if (damageNumbers.isNotEmpty()) {
            val baseX = font.data.scaleX
            val baseY = font.data.scaleY
            font.data.setScale(baseX * 0.55f, baseY * 0.55f)
            for (fx in damageNumbers) {
                val t = (fx.timeLeft / fx.duration).coerceIn(0f, 1f)
                val a = (t * t).coerceIn(0f, 1f)
                font.setColor(fx.r, fx.g, fx.b, a)
                val text = fx.value.toString()
                glyph.setText(font, text)
                font.draw(batch, glyph, fx.x - glyph.width * 0.5f, fx.y)
            }
            font.data.setScale(baseX, baseY)
            font.color = Color.WHITE
        }

        val texXp = Sprites.get("orb_xp") { Sprites.circleTexture(32, Color(0.35f, 0.95f, 0.95f, 1f)) }
        for (o in loot.xpOrbs) Sprites.drawCentered(batch, texXp, o.pos.x, o.pos.y, 13f, 13f)

        val texGold = Sprites.get("orb_gold") { Sprites.circleTexture(32, Color(1f, 0.85f, 0.25f, 1f)) }
        for (o in loot.goldOrbs) Sprites.drawCentered(batch, texGold, o.pos.x, o.pos.y, 11f, 11f)

        val texChest = Sprites.get("chest") { Sprites.circleTexture(48, Color(0.25f, 0.75f, 1f, 1f)) }
        val texChestElite = Sprites.get("chest_elite") { Sprites.circleTexture(48, Color(1f, 0.85f, 0.25f, 1f)) }
        for (c in loot.chests) {
            Sprites.drawCentered(
                batch,
                if (c.isElite) texChestElite else texChest,
                c.pos.x,
                c.pos.y,
                c.radius * 2f * worldSpriteScale,
                c.radius * 2f * worldSpriteScale,
            )
        }

        val texShrine = Sprites.get("shrine") { Sprites.circleTexture(64, Color(0.25f, 0.95f, 0.45f, 0.75f)) }
        for (s in shrineSystem.shrines) {
            // Иконка + лёгкая "пульсация" прогресса
            val p = (s.progressSec / s.requiredSec).coerceIn(0f, 1f)
            Sprites.drawCentered(batch, texShrine, s.pos.x, s.pos.y, 26f * worldSpriteScale, 26f * worldSpriteScale, alpha = 0.55f)
            Sprites.drawCentered(
                batch,
                texShrine,
                s.pos.x,
                s.pos.y,
                (10f + 14f * p) * worldSpriteScale,
                (10f + 14f * p) * worldSpriteScale,
                alpha = 0.75f,
            )
        }

        val texCloud = Sprites.get("cloud_toxic") {
            Sprites.softCloudTexture(
                size = 192,
                base = Color(0.25f, 1.00f, 0.35f, 0.85f),
                spotCount = 11,
                seed = 20260206L,
            )
        }

        // Оружие "Облако яда": визуальная аура вокруг игрока (раньше её не рисовали, из-за этого казалось, что текстуры нет).
        progression.weapons.firstOrNull { it.kind == WeaponKind.POISON_AURA }?.let { wAura ->
            val b = Balance.cfg.weapons.poisonAura
            val range = b.swingRangeBase + wAura.rangeLevel * b.swingRangePerLevel
            // лёгкая пульсация, чтобы эффект читался
            val pulse = 1f + 0.05f * sin(runTime * 2.6f)
            val alphaAura = 0.34f
            Sprites.drawCentered(
                batch,
                texCloud,
                playerPos.x,
                playerPos.y,
                range * 2f * pulse * worldSpriteScale,
                range * 2f * pulse * worldSpriteScale,
                alpha = alphaAura,
            )
        }

        if (itemSystem.toxicActive()) {
            val r = itemSystem.toxicRadius()
            // Визуальный масштаб эффектов (просили "ещё ловушка/облако").
            Sprites.drawCentered(batch, texCloud, playerPos.x, playerPos.y, r * 2f * worldSpriteScale, r * 2f * worldSpriteScale, alpha = 0.25f)
        }
        // Ловушки (иконка) / облако от ловушки
        val texTrap = Sprites.get("trap") { Sprites.circleTexture(64, Color(0.35f, 0.95f, 0.45f, 1f)) }
        for (t in poisonTraps) {
            if (t.armTimer > 0f) {
                Sprites.drawCentered(batch, texTrap, t.pos.x, t.pos.y, 18f * worldSpriteScale, 18f * worldSpriteScale, alpha = 0.90f)
            } else if (t.cloudTimer > 0f) {
                Sprites.drawCentered(batch, texCloud, t.pos.x, t.pos.y, t.radius * 2f * worldSpriteScale, t.radius * 2f * worldSpriteScale, alpha = 0.22f)
                // Иконку ловушки оставляем поверх облака для читаемости.
                Sprites.drawCentered(batch, texTrap, t.pos.x, t.pos.y, 14f * worldSpriteScale, 14f * worldSpriteScale, alpha = 0.70f)
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
        // Предметы могут добавлять XP (например "Книга")
        var leveledFromItems = false
        itemSystem.update(delta) { gainedXp ->
            if (progression.addXp(gainedXp)) leveledFromItems = true
        }
        playerDamage.update(delta)
        killSfxCooldown = max(0f, killSfxCooldown - delta)
        playerHitSfxCooldown = max(0f, playerHitSfxCooldown - delta)
        updateDotDamageNumbers(delta)
        // FX ближней атаки
        if (meleeFx.isNotEmpty()) {
            val it = meleeFx.iterator()
            while (it.hasNext()) {
                val fx = it.next()
                fx.timeLeft -= delta
                if (fx.timeLeft <= 0f) it.remove()
            }
        }
        // FX цифр урона
        if (damageNumbers.isNotEmpty()) {
            val it = damageNumbers.iterator()
            while (it.hasNext()) {
                val fx = it.next()
                fx.timeLeft -= delta
                // лёгкий "взлёт"
                fx.y += 42f * delta
                if (fx.timeLeft <= 0f) it.remove()
            }
        }
        // Динамический max HP: кольца + предмет "Сердце" (+20 за штуку).
        playerDamage.setMaxHp(baseMaxHp * progression.getMaxHpMultiplier() + itemSystem.getMaxHpFlatBonus())

        // 5 минут -> босс уровня (останавливаем обычные волны)
        if (!bossSpawned && runTime >= runDurationSec) {
            startBossFight()
        }

        // joystick.direction сейчас в screen-space, Y у libGDX для touch идёт сверху вниз
        // Поэтому инвертируем Y.
        val playerSpeed = basePlayerSpeed * progression.getMoveSpeedMultiplier() * itemSystem.getMoveSpeedMultiplier()
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
        loot.updateGoldOrbs(delta, playerPos, progression, goldMul = easyBonus * itemSystem.getGoldGainMultiplier())
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
            damageEnemy = { e, dmg ->
                e.hp -= dmg
                accDotDamageNumber(e, dmg, r = 0.35f, g = 0.95f, b = 0.45f)
            },
        )
        applyContactDamage()
        updateEnemyProjectiles(delta)
        loot.tryOpenChestByProximity(playerPos, luckBonus = itemSystem.getLuckBonus())?.let { res ->
            // Сундук теперь сразу выдаёт рандомный предмет (без UI выбора)
            items.add(res.item)
            itemSystem.addItem(res.item)
            Audio.chestOpen()
            if (res.item.kind == com.factorlite.loot.ItemKind.HEART) {
                playerDamage.heal(20f)
            }
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

        if ((leveledUp || leveledFromItems) && runState == RunState.RUNNING) {
            pendingChoices = progression.makeUpgradeChoices()
            runState = RunState.LEVEL_UP
            uiSystem.layoutCards(uiViewport, optionCount = pendingChoices.size, withSkip = false, uiScale = uiScale)
            Audio.levelUp()
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
                    accDotDamageNumber(e, dmg, r = 0.35f, g = 0.95f, b = 0.45f)
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
        saveSelectionsToPrefs()
        enterCharacterSelect()
    }

    private fun handleDifficultySelectInput() {
        val idx = uiSystem.pollKeyPick(runState) ?: return
        applyDifficultyPick(idx)
    }

    private fun applyCharacterPick(idx: Int) {
        if (idx < 0 || idx >= characterChoices.size) return
        selectedCharacter = characterChoices[idx]
        saveSelectionsToPrefs()
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

    private fun handleEndRunInput() {
        // Рестарт (за того же персонажа и на той же сложности, без возврата в выбор)
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.R) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            resetRun()
        }
        // Esc -> главное меню
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            Gdx.app.postRunnable {
                game.setScreen(MainMenuScreen(game))
            }
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
                if (!res.blocked && playerHitSfxCooldown <= 0f) {
                    Audio.playerHit()
                    playerHitSfxCooldown = 0.10f
                }
                if (res.died) {
                    runState = RunState.GAME_OVER
                    Audio.gameOver()
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
                if (!res.blocked && playerHitSfxCooldown <= 0f) {
                    Audio.playerHit()
                    playerHitSfxCooldown = 0.10f
                }
                if (res.died) {
                    runState = RunState.GAME_OVER
                    Audio.gameOver()
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
                accDotDamageNumber(e, dmg, r = 0.35f, g = 0.95f, b = 0.45f)
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
                armTimer = 0.55f,
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

        // Визуал удара: простой "слэш" в направлении первой цели.
        run {
            val t = candidates.first()
            val dx = t.pos.x - playerPos.x
            val dy = t.pos.y - playerPos.y
            val len = sqrt(dx * dx + dy * dy)
            val nx = if (len > 0.001f) dx / len else 1f
            val ny = if (len > 0.001f) dy / len else 0f
            val angle = (atan2(ny, nx) * 57.29578f) // rad->deg
            val off = 18f
            val fxLen = (28f + range * 0.10f).coerceIn(34f, 62f)
            val fxW = if (kind == WeaponKind.DAGGER) 7f else 9f
            val col = if (kind == WeaponKind.DAGGER) floatArrayOf(1.0f, 0.35f, 0.35f) else floatArrayOf(0.85f, 0.92f, 1.0f)
            meleeFx.add(
                MeleeSlashFx(
                    x = playerPos.x + nx * off,
                    y = playerPos.y + ny * off,
                    rotDeg = angle,
                    timeLeft = 0.12f,
                    duration = 0.12f,
                    length = fxLen,
                    width = fxW,
                    r = col[0],
                    g = col[1],
                    b = col[2],
                ),
            )
        }

        val baseDamage = b.baseDamage + w.damageLevel * b.damagePerLevel
        for (e in candidates) {
            var dmg = baseDamage * dmgMul
            val crit = MathUtils.random() < critChance
            if (crit) dmg *= critMul
            e.hp -= dmg
            spawnDamageNumber(e.pos.x, e.pos.y + e.radius + 10f, dmg, crit = crit)
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
                spawnDamageNumber(e.pos.x, e.pos.y + e.radius + 8f, dmg, crit = false)
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
                        spawnDamageNumber(en.pos.x, en.pos.y + en.radius + 8f, d, crit = false)
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
                if (!e.isBoss && killSfxCooldown <= 0f) {
                    Audio.kill()
                    killSfxCooldown = 0.05f
                }
                loot.onEnemyKilled(
                    pos = e.pos,
                    xpReward = e.xpReward,
                    goldReward = e.goldReward,
                    isElite = e.isElite,
                )

                // Бургер — хил на убийство
                val heal = itemSystem.rollBurgerHeal()
                if (heal > 0f) playerDamage.heal(heal)

                // Клык волка — шанс хила на убийство
                val heal2 = itemSystem.rollWolfFangHeal()
                if (heal2 > 0f) playerDamage.heal(heal2)
            }
        }
        enemies.removeAll { it.hp <= 0f }
        if (bossDied) {
            runState = RunState.VICTORY
            Audio.victory()
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
        val s = uiScale.coerceIn(0.9f, 3.5f)
        val padX = 16f * s
        val top = uiViewport.worldHeight - 16f * s
        val w = uiViewport.worldWidth
        val h = uiViewport.worldHeight
        val gap = 6f * s
        val colW = (w - padX * 2f).coerceAtLeast(120f * s)

        // HUD просили "в 2 раза меньше" — уменьшаем масштаб только внутри HUD-блока.
        val baseScaleX = font.data.scaleX
        val baseScaleY = font.data.scaleY
        val hudFontScale = 0.5f
        font.data.setScale(baseScaleX * hudFontScale, baseScaleY * hudFontScale)

        fun drawWrapped(text: String, x: Float, yTop: Float, wrapW: Float): Float {
            // Возвращает новую Y-координату (верх следующего блока), уже со смещением вниз.
            glyph.setText(font, text, Color.WHITE, wrapW, Align.left, true)
            font.draw(batch, glyph, x, yTop)
            return yTop - glyph.height - gap
        }

        // --- Левый верх: HP / LVL / XP ---
        var y = top
        y = drawWrapped("HP: ${playerDamage.hp.toInt()}", padX, y, colW)
        y = drawWrapped("LVL: ${progression.level}", padX, y, colW)
        y = drawWrapped("XP: ${progression.xp}/${progression.xpToNext}", padX, y, colW)
        y = drawWrapped("GOLD: ${loot.gold}", padX, y, colW)

        val lineWeapon = buildString {
            append("Оружие: ")
            if (progression.weapons.isEmpty()) append("-")
            for ((i, w) in progression.weapons.withIndex()) {
                if (i > 0) append(" | ")
                val up = (w.level - 1).coerceAtLeast(0)
                append("${w.kind.uiName} Lv$up")
            }
        }
        // --- Оружие / кольца / предметы (вернули обратно в левую колонку) ---
        y = drawWrapped(lineWeapon, padX, y, colW)

        val lineRings = buildString {
            append("Кольца: ")
            if (progression.rings.isEmpty()) append("-")
            for ((i, p) in progression.rings.withIndex()) {
                if (i > 0) append(" | ")
                val up = (p.level - 1).coerceAtLeast(0)
                append("${p.kind.uiName} Lv$up")
            }
        }
        y = drawWrapped(lineRings, padX, y, colW)

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
        y = drawWrapped(lineItems, padX, y, colW)

        // --- Нижний правый угол: Time + Kills в одной строке через пробел ---
        val timeKills = "Time: ${runTime.toInt()}s  Kills: $killedEnemies"
        glyph.setText(font, timeKills, Color.WHITE, w, Align.left, false)
        font.draw(batch, glyph, w - padX - glyph.width, padX + glyph.height)

        // Кнопка паузы (строго справа сверху)
        val hudUsed = (top - y).coerceAtLeast(0f) // оставляем для boss-bar
        val pauseSize = 44f * s
        pauseRect.set(
            uiViewport.worldWidth - 16f * s - pauseSize,
            uiViewport.worldHeight - 16f * s - pauseSize,
            pauseSize,
            pauseSize,
        )
        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Без чёрного фона: только две вертикальные палочки.
        val barW = (pauseRect.width * 0.12f).coerceAtLeast(2f * s)
        val barH = pauseRect.height * 0.58f
        val barY = pauseRect.y + (pauseRect.height - barH) * 0.5f
        val leftX = pauseRect.x + pauseRect.width * 0.36f - barW * 0.5f
        val rightX = pauseRect.x + pauseRect.width * 0.64f - barW * 0.5f
        shapes.color = Color(1f, 1f, 1f, 0.92f)
        shapes.rect(leftX, barY, barW, barH)
        shapes.rect(rightX, barY, barW, barH)
        shapes.end()
        batch.begin()
        font.color = Color.WHITE
        // Текст на кнопке больше не рисуем.

        // Миникарта (снизу слева, уменьшили — без x2)
        drawMiniMap(uiScale = s, bottomLeft = true, scaleMul = 1.0f)

        // Босс-бар (если жив)
        enemies.firstOrNull { it.isBoss }?.let { boss ->
            drawBossBar(boss, topOffset = hudUsed + 8f)
        }

        when (runState) {
            RunState.GAME_OVER -> {
                drawEndRunMenuOverlay(title = "КОНЕЦ ЗАБЕГА", titleColor = Color(1f, 0.4f, 0.4f, 1f))
            }
            RunState.VICTORY -> {
                drawEndRunMenuOverlay(title = "ПОБЕДА!", titleColor = Color(0.4f, 1f, 0.4f, 1f))
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
                difficultyChoices.map { it.uiName to it.uiCardDesc() },
                uiScale = uiScale,
            )
            RunState.PAUSED -> {
                drawPauseMenuOverlay()
            }
            else -> Unit
        }

        // Восстанавливаем масштаб шрифта после HUD-рендера (оверлеи/меню используют другой масштаб).
        font.data.setScale(baseScaleX, baseScaleY)
        batch.end()
    }

    private fun drawMiniMap(uiScale: Float = 1f, bottomLeft: Boolean = false, scaleMul: Float = 1f, topOffset: Float = 0f) {
        // batch уже в begin()
        batch.end()
        shapes.projectionMatrix = uiCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        val uiW = uiViewport.worldWidth
        val uiH = uiViewport.worldHeight
        val s = uiScale.coerceIn(0.9f, 3.5f)
        // Просили: увеличить миникарту в 2 раза и поставить в угол снизу слева.
        val baseRadius = 68f * s * scaleMul
        val radius = baseRadius.coerceIn(56f * s, 140f * s)
        val cx = if (bottomLeft) radius + 16f * s else uiW - radius - 16f * s
        val cy = if (bottomLeft) radius + 16f * s else uiH - radius - 16f * s - topOffset

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
            // Важно:
            // - `ScreenViewport` уже в пикселях, поэтому `screenHeight` сам растёт вместе с разрешением.
            // - `uiScale` мы тоже пересчитываем от разрешения.
            // Если умножать ещё и на `uiScale`, получится ДВОЙНОЕ масштабирование (и текст разъедется по UI).
            // Чуть меньше базовый размер: иначе на Desktop-окнах под Android (1080x1920) HUD/карточки становятся гигантскими.
            val size = (uiViewport.screenHeight * 0.022f * baseUiScale).toInt().coerceIn(14, 90)
            val param = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                this.size = size
                color = Color.WHITE
                // Кириллица + базовый латинский набор
                // + "→" для строк апгрейдов вида "0 → 1" (иначе рисуется квадратик).
                characters = FreeTypeFontGenerator.DEFAULT_CHARS + "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя№—«»→"
            }
            font = gen.generateFont(param)
        } finally {
            gen.dispose()
        }
    }
}

