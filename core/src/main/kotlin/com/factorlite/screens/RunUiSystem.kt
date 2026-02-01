package com.factorlite.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.factorlite.game.RunState
import com.factorlite.progression.GlobalBonusOption
import com.factorlite.progression.CharacterKind
import com.factorlite.progression.Gender
import com.factorlite.progression.InnateKind
import com.factorlite.progression.label
import com.factorlite.progression.uiName
import com.factorlite.progression.playerSpriteKey
import com.factorlite.gfx.Sprites
import com.factorlite.progression.UpgradeOption
import com.factorlite.progression.UpgradeRarity

class RunUiSystem {
    private val cards = Array(7) { Rectangle() }
    private val skipRect = Rectangle()
    private val tmp = Vector2()

    fun layoutCards(uiViewport: ScreenViewport, optionCount: Int, withSkip: Boolean, uiScale: Float = 1f) {
        val w = uiViewport.worldWidth
        val h = uiViewport.worldHeight
        if (w <= 0f || h <= 0f) return

        val s = uiScale.coerceIn(1.0f, 2.0f)
        val padX = 16f * s
        val cardW = (w - padX * 2f)
        val cardH = 76f * s
        val topY = h - 130f * s
        val gap = 10f * s

        val n = minOf(cards.size, optionCount.coerceAtLeast(0))
        for (i in 0 until n) {
            val y = topY - i * (cardH + gap)
            cards[i].set(padX, y - cardH, cardW, cardH)
        }
        // Остальные очищаем (на всякий)
        for (i in n until cards.size) {
            cards[i].set(0f, 0f, 0f, 0f)
        }

        // Кнопка "Пропустить" под карточками
        if (withSkip) {
            val skipH = 56f * s
            skipRect.set(padX, (topY - n * (cardH + gap)) - skipH, cardW, skipH)
        } else {
            skipRect.set(0f, 0f, 0f, 0f)
        }
    }

    fun pollKeyPick(runState: RunState): Int? {
        return when (runState) {
            RunState.DIFFICULTY_SELECT -> when {
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1) -> 0
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_2) -> 1
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_3) -> 2
                else -> null
            }
            RunState.LEVEL_UP, RunState.SHRINE_OPEN -> when {
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1) -> 0
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_2) -> 1
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_3) -> 2
                else -> null
            }
            RunState.CHARACTER_SELECT -> when {
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1) -> 0
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_2) -> 1
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_3) -> 2
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_4) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_4) -> 3
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_5) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_5) -> 4
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_6) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_6) -> 5
                Gdx.input.isKeyJustPressed(Input.Keys.NUM_7) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_7) -> 6
                else -> null
            }
            else -> null
        }
    }

    fun pollSkip(runState: RunState): Boolean {
        if (runState != RunState.SHRINE_OPEN) return false
        return Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
    }

    fun createTapInput(
        uiViewport: ScreenViewport,
        getRunState: () -> RunState,
        getOptionCount: () -> Int,
        onPick: (idx: Int) -> Unit,
    ): InputAdapter {
        return object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val state = getRunState()
                if (state != RunState.LEVEL_UP && state != RunState.SHRINE_OPEN && state != RunState.CHARACTER_SELECT && state != RunState.DIFFICULTY_SELECT) return false

                val count = getOptionCount()
                if (count <= 0) return false

                tmp.set(screenX.toFloat(), screenY.toFloat())
                uiViewport.unproject(tmp)
                for (i in 0 until minOf(cards.size, count)) {
                    if (cards[i].contains(tmp.x, tmp.y)) {
                        onPick(i)
                        return true
                    }
                }

                if (state == RunState.SHRINE_OPEN && skipRect.contains(tmp.x, tmp.y)) {
                    onPick(3) // skip
                    return true
                }
                return false
            }
        }
    }

    private fun innateLabel(k: InnateKind): String = when (k) {
        InnateKind.SLOW_ENEMIES -> "Врождёнка: замедление врагов"
        InnateKind.DOT_SPEED -> "Врождёнка: усиление ДОТ"
        InnateKind.MOVE_SPEED -> "Врождёнка: скорость бега"
        InnateKind.AREA_SIZE -> "Врождёнка: размер области"
        InnateKind.DODGE_CHANCE -> "Врождёнка: уклонение"
        InnateKind.PHYSICAL_DAMAGE -> "Врождёнка: физ. урон"
        InnateKind.MAGIC_DAMAGE -> "Врождёнка: маг. урон"
    }

    private fun genderLabel(g: Gender): String = when (g) {
        Gender.M -> "м"
        Gender.F -> "ж"
    }

    fun drawCharacterSelectOverlay(
        batch: SpriteBatch,
        shapes: ShapeRenderer,
        font: BitmapFont,
        uiViewport: ScreenViewport,
        camera: Camera,
        chars: List<CharacterKind>,
        uiScale: Float = 1f,
    ) {
        val s = uiScale.coerceIn(1.0f, 2.0f)
        font.color = Color.WHITE
        font.draw(batch, "ВЫБОР ПЕРСОНАЖА", 16f * s, uiViewport.worldHeight - 92f * s)
        font.color = Color.LIGHT_GRAY
        font.draw(batch, "Нажми 1-7 или тапни по карточке", 16f * s, uiViewport.worldHeight - 112f * s)

        batch.end()
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)

        for (i in 0 until minOf(cards.size, chars.size)) {
            shapes.color = Color(0.12f, 0.12f, 0.16f, 0.92f)
            val r = cards[i]
            shapes.rect(r.x, r.y, r.width, r.height)
        }
        shapes.end()

        batch.begin()
        for (i in 0 until minOf(cards.size, chars.size)) {
            val c = chars[i]
            val r = cards[i]
            // Иконка персонажа слева (если спрайта нет — плейсхолдер)
            val icon = Sprites.get(c.playerSpriteKey) { Sprites.circleTexture(64, Color(0.85f, 0.85f, 0.90f, 1f)) }
            val iconPad = 10f * s
            val iconSz = 56f * s
            batch.draw(icon, r.x + iconPad, r.y + iconPad, iconSz, iconSz)
            font.color = Color.WHITE
            val textX = r.x + 76f * s
            font.draw(batch, "${i + 1}. ${c.uiName} (${genderLabel(c.gender)})", textX, r.y + r.height - 16f * s)
            font.color = Color.WHITE
            font.draw(batch, "Старт: ${c.startWeapon.uiName}", textX, r.y + r.height - 38f * s)
            font.color = Color.LIGHT_GRAY
            font.draw(batch, innateLabel(c.innate), textX, r.y + r.height - 58f * s)
        }
    }

    fun drawDifficultySelectOverlay(
        batch: SpriteBatch,
        shapes: ShapeRenderer,
        font: BitmapFont,
        uiViewport: ScreenViewport,
        camera: Camera,
        options: List<Pair<String, String>>,
        uiScale: Float = 1f,
    ) {
        val s = uiScale.coerceIn(1.0f, 2.0f)
        font.color = Color.WHITE
        font.draw(batch, "ВЫБОР СЛОЖНОСТИ", 16f * s, uiViewport.worldHeight - 92f * s)
        font.color = Color.LIGHT_GRAY
        font.draw(batch, "Нажми 1-3 или тапни по карточке", 16f * s, uiViewport.worldHeight - 112f * s)

        batch.end()
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)

        for (i in 0 until minOf(3, options.size)) {
            shapes.color = Color(0.12f, 0.12f, 0.16f, 0.92f)
            val r = cards[i]
            shapes.rect(r.x, r.y, r.width, r.height)
        }
        shapes.end()

        batch.begin()
        for (i in 0 until minOf(3, options.size)) {
            val (title, desc) = options[i]
            val r = cards[i]
            font.color = Color.WHITE
            font.draw(batch, "${i + 1}. $title", r.x + 14f * s, r.y + r.height - 16f * s)
            font.color = Color.LIGHT_GRAY
            font.draw(batch, desc, r.x + 14f * s, r.y + r.height - 42f * s)
        }
    }

    fun drawLevelUpOverlay(
        batch: SpriteBatch,
        shapes: ShapeRenderer,
        font: BitmapFont,
        uiViewport: ScreenViewport,
        camera: Camera,
        pendingChoices: List<UpgradeOption>,
        uiScale: Float = 1f,
    ) {
        val s = uiScale.coerceIn(1.0f, 2.0f)
        font.color = Color.WHITE
        font.draw(batch, "LEVEL UP! Выбери улучшение:", 16f * s, uiViewport.worldHeight - 92f * s)

        batch.end()
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)
        for (i in 0 until minOf(3, pendingChoices.size)) {
            val opt = pendingChoices[i]
            shapes.color = when (opt.rarity) {
                UpgradeRarity.COMMON -> Color(0.10f, 0.45f, 0.22f, 0.92f)
                UpgradeRarity.RARE -> Color(0.42f, 0.12f, 0.55f, 0.92f)
                UpgradeRarity.EPIC -> Color(0.18f, 0.30f, 0.75f, 0.92f)
                UpgradeRarity.LEGENDARY -> Color(0.75f, 0.55f, 0.10f, 0.92f)
                null -> Color(0.12f, 0.12f, 0.16f, 0.92f)
            }
            val r = cards[i]
            shapes.rect(r.x, r.y, r.width, r.height)
        }
        shapes.end()

        batch.begin()
        font.color = Color.WHITE
        for (i in 0 until minOf(3, pendingChoices.size)) {
            val opt = pendingChoices[i]
            val r = cards[i]
            // Строка 1: редкость слева, LVL справа
            font.color = Color.WHITE
            val rar = opt.rarity?.label ?: ""
            font.draw(batch, "${i + 1}. $rar", r.x + 14f * s, r.y + r.height - 16f * s)
            opt.levelLabel?.let { lvl ->
                font.draw(batch, lvl, r.x + r.width - 90f * s, r.y + r.height - 16f * s)
            }

            // Строка 2: название предмета/пассивки
            font.color = Color.WHITE
            font.draw(batch, opt.title, r.x + 14f * s, r.y + r.height - 38f * s)

            // Строка 3: "до -> после"
            font.color = Color.LIGHT_GRAY
            font.draw(batch, opt.description, r.x + 14f * s, r.y + r.height - 58f * s)
        }
    }

    fun drawShrineOverlay(
        batch: SpriteBatch,
        shapes: ShapeRenderer,
        font: BitmapFont,
        uiViewport: ScreenViewport,
        camera: Camera,
        pendingChoices: List<GlobalBonusOption>,
        uiScale: Float = 1f,
    ) {
        val s = uiScale.coerceIn(1.0f, 2.0f)
        font.color = Color.WHITE
        font.draw(batch, "СВЯТЫНЯ! Выбери глобальный бонус:", 16f * s, uiViewport.worldHeight - 116f * s)

        batch.end()
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)

        for (i in 0 until minOf(3, pendingChoices.size)) {
            val opt = pendingChoices[i]
            shapes.color = when (opt.rarity) {
                com.factorlite.progression.BonusRarity.COMMON -> Color(0.10f, 0.45f, 0.22f, 0.92f)
                com.factorlite.progression.BonusRarity.RARE -> Color(0.42f, 0.12f, 0.55f, 0.92f)
                com.factorlite.progression.BonusRarity.EPIC -> Color(0.75f, 0.55f, 0.10f, 0.92f)
            }
            val r = cards[i]
            shapes.rect(r.x, r.y, r.width, r.height)
        }

        // Skip
        shapes.color = Color(0.35f, 0.07f, 0.07f, 0.92f)
        shapes.rect(skipRect.x, skipRect.y, skipRect.width, skipRect.height)
        shapes.end()
        batch.begin()

        for (i in 0 until minOf(3, pendingChoices.size)) {
            val opt = pendingChoices[i]
            val r = cards[i]
            font.color = Color.WHITE
            font.draw(batch, "${i + 1}. ${opt.rarity.label}", r.x + 14f * s, r.y + r.height - 16f * s)
            font.color = Color.WHITE
            font.draw(batch, opt.title, r.x + 14f * s, r.y + r.height - 38f * s)
            font.color = Color.LIGHT_GRAY
            font.draw(batch, opt.description, r.x + 14f * s, r.y + r.height - 58f * s)
        }
        font.color = Color.WHITE
        font.draw(batch, "Пропустить (Space/Esc)", skipRect.x + 14f * s, skipRect.y + skipRect.height - 18f * s)
    }
}

