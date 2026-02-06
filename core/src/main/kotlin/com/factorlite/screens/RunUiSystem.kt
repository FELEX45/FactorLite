package com.factorlite.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.Align
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
import com.factorlite.progression.BonusRarity
import com.factorlite.progression.UpgradeOption
import com.factorlite.progression.UpgradeRarity
import kotlin.math.min
import kotlin.math.max

class RunUiSystem {
    private val cards = Array(7) { Rectangle() }
    private val skipRect = Rectangle()
    private val tmp = Vector2()
    private val glyph = GlyphLayout()

    // Единая палитра редкостей (чтобы "Rare" и т.п. везде выглядело одинаково).
    // Конвенция: Common=зелёный, Rare=синий, Epic=фиолетовый, Legendary=золотой.
    private fun cardColorFor(r: UpgradeRarity?): Color = when (r) {
        UpgradeRarity.COMMON -> Color(0.10f, 0.45f, 0.22f, 0.92f)
        UpgradeRarity.RARE -> Color(0.18f, 0.30f, 0.75f, 0.92f)
        UpgradeRarity.EPIC -> Color(0.42f, 0.12f, 0.55f, 0.92f)
        UpgradeRarity.LEGENDARY -> Color(0.75f, 0.55f, 0.10f, 0.92f)
        null -> Color(0.12f, 0.12f, 0.16f, 0.92f)
    }

    private fun cardColorFor(r: BonusRarity): Color = when (r) {
        BonusRarity.COMMON -> Color(0.10f, 0.45f, 0.22f, 0.92f)
        BonusRarity.RARE -> Color(0.18f, 0.30f, 0.75f, 0.92f)
        BonusRarity.EPIC -> Color(0.42f, 0.12f, 0.55f, 0.92f)
    }

    fun layoutCards(uiViewport: ScreenViewport, optionCount: Int, withSkip: Boolean, uiScale: Float = 1f) {
        val w = uiViewport.worldWidth
        val h = uiViewport.worldHeight
        if (w <= 0f || h <= 0f) return

        val s = uiScale.coerceIn(0.9f, 3.5f)
        val padX = 16f * s
        val cardW = (w - padX * 2f)
        val gap = 10f * s

        val n = minOf(cards.size, optionCount.coerceAtLeast(0))

        // Заголовок/подсказка сверху занимает место; оставляем “шапку”.
        val headerH = 140f * s
        val topY = h - headerH

        // Доступная высота под карточки (учитываем нижний паддинг и кнопку skip).
        val bottomPad = 16f * s
        val skipReserve = if (withSkip) (56f * s + gap) else 0f
        val available = (topY - bottomPad - skipReserve).coerceAtLeast(180f * s)

        // Для 3-карточных экранов (сложность/level-up/святыня) — большие карточки под описание.
        // Для длинных списков (персонажи 7+) — подгоняем высоту так, чтобы всё влезало в экран.
        val cardH = when {
            n <= 0 -> 0f
            optionCount <= 3 -> {
                val per = (available - gap * (n - 1)) / n.toFloat()
                per.coerceIn(140f * s, 320f * s)
            }
            else -> {
                val per = (available - gap * (n - 1)) / n.toFloat()
                per.coerceIn(58f * s, 120f * s)
            }
        }
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

    private fun innateLabelShort(k: InnateKind): String = when (k) {
        InnateKind.SLOW_ENEMIES -> "Врожд: замедление"
        InnateKind.DOT_SPEED -> "Врожд: ДОТ+"
        InnateKind.MOVE_SPEED -> "Врожд: скорость"
        InnateKind.AREA_SIZE -> "Врожд: область"
        InnateKind.DODGE_CHANCE -> "Врожд: уклонение"
        InnateKind.PHYSICAL_DAMAGE -> "Врожд: физ. урон"
        InnateKind.MAGIC_DAMAGE -> "Врожд: маг. урон"
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
        val s = uiScale.coerceIn(0.9f, 3.5f)
        val sc = Rectangle()
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

            // Клипуем содержимое карточки, чтобы текст/иконка не вылезали в соседние карточки.
            ScissorStack.calculateScissors(camera, batch.transformMatrix, r, sc)
            batch.flush()
            ScissorStack.pushScissors(sc)
            try {
                val baseScaleX = font.data.scaleX
                val baseScaleY = font.data.scaleY

                val iconPad = 10f * s
                val icon = Sprites.get(c.playerSpriteKey) { Sprites.circleTexture(64, Color(0.85f, 0.85f, 0.90f, 1f)) }
                val iconSz = min(56f * s, (r.height - iconPad * 2f).coerceAtLeast(24f * s))
                batch.draw(icon, r.x + iconPad, r.y + (r.height - iconSz) / 2f, iconSz, iconSz)

                val textX = r.x + iconPad + iconSz + 12f * s
                val padTop = 14f * s
                val padBottom = 10f * s

                val maxW = (r.x + r.width - textX - 10f * s).coerceAtLeast(80f * s)
                val yTop = r.y + r.height - padTop
                val yBottom = r.y + padBottom
                val gap = 6f * s

                // 1) Заголовок (top-aligned через GlyphLayout — иначе baseline приводит к наложениям).
                font.data.setScale(0.86f, 0.86f)
                font.color = Color.WHITE
                glyph.setText(font, "${i + 1}. ${c.uiName}", Color.WHITE, maxW, Align.left, false)
                val titleTop = yTop
                font.draw(batch, glyph, textX, titleTop)

                // 2) Описание (уменьшаем до тех пор, пока не влезет по высоте).
                val descTop = titleTop - glyph.height - gap
                val availH = (descTop - yBottom).coerceAtLeast(0f)
                var detailScale = 0.68f
                val desc = "Старт: ${c.startWeapon.uiName}\n${innateLabelShort(c.innate)}"
                while (detailScale > 0.44f) {
                    font.data.setScale(detailScale, detailScale)
                    glyph.setText(font, desc, Color.LIGHT_GRAY, maxW, Align.left, true)
                    if (glyph.height <= availH + 0.5f) break
                    detailScale *= 0.90f
                }
                font.color = Color.LIGHT_GRAY
                font.draw(batch, glyph, textX, descTop)

                font.data.setScale(baseScaleX, baseScaleY)
            } finally {
                batch.flush()
                ScissorStack.popScissors()
            }
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
        val s = uiScale.coerceIn(0.9f, 3.5f)
        val gl = GlyphLayout()
        val sc = Rectangle()
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

            // Клипуем содержимое карточки, чтобы текст никогда не рисовался поверх соседних карточек.
            ScissorStack.calculateScissors(camera, batch.transformMatrix, r, sc)
            batch.flush()
            ScissorStack.pushScissors(sc)
            try {
                val baseScaleX = font.data.scaleX
                val baseScaleY = font.data.scaleY

                // Заголовок — нормальный размер
                font.data.setScale(0.92f, 0.92f)
                font.color = Color.WHITE
                font.draw(batch, "${i + 1}. $title", r.x + 14f * s, r.y + r.height - 16f * s)

                // Описание — чуть меньше, чтобы помещалось в карточку при больших экранах
                font.data.setScale(0.68f, 0.68f)
                font.color = Color.LIGHT_GRAY
                gl.setText(font, desc, Color.LIGHT_GRAY, r.width - 28f * s, Align.left, true)
                font.draw(batch, gl, r.x + 14f * s, r.y + r.height - 44f * s)

                font.data.setScale(baseScaleX, baseScaleY)
            } finally {
                batch.flush()
                ScissorStack.popScissors()
            }
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
        val s = uiScale.coerceIn(0.9f, 3.5f)
        font.color = Color.WHITE
        font.draw(batch, "LEVEL UP! Выбери улучшение:", 16f * s, uiViewport.worldHeight - 92f * s)

        batch.end()
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)
        for (i in 0 until minOf(3, pendingChoices.size)) {
            val opt = pendingChoices[i]
            shapes.color = cardColorFor(opt.rarity)
            val r = cards[i]
            shapes.rect(r.x, r.y, r.width, r.height)
        }
        shapes.end()

        batch.begin()
        val sc = Rectangle()
        for (i in 0 until minOf(3, pendingChoices.size)) {
            val opt = pendingChoices[i]
            val r = cards[i]

            ScissorStack.calculateScissors(camera, batch.transformMatrix, r, sc)
            batch.flush()
            ScissorStack.pushScissors(sc)
            try {
                val baseScaleX = font.data.scaleX
                val baseScaleY = font.data.scaleY

                val padX = 14f * s
                val padTop = 14f * s
                val padBottom = 10f * s
                val maxW = (r.width - padX * 2f).coerceAtLeast(80f * s)
                val yTop = r.y + r.height - padTop
                val yBottom = r.y + padBottom
                val gap = 6f * s

                // Линия 1: редкость слева, LVL справа (top-aligned)
                font.data.setScale(0.78f, 0.78f)
                val rar = opt.rarity?.label ?: ""
                glyph.setText(font, "${i + 1}. $rar", Color.WHITE, maxW, Align.left, false)
                font.color = Color.WHITE
                font.draw(batch, glyph, r.x + padX, yTop)

                opt.levelLabel?.let { lvl ->
                    glyph.setText(font, lvl, Color.WHITE, maxW, Align.left, false)
                    val xLvl = r.x + r.width - padX - glyph.width
                    font.draw(batch, glyph, xLvl, yTop)
                }

                // Линия 2: название (чуть крупнее)
                val y2 = yTop - glyph.height - gap
                font.data.setScale(0.88f, 0.88f)
                glyph.setText(font, opt.title, Color.WHITE, maxW, Align.left, true)
                font.draw(batch, glyph, r.x + padX, y2)

                // Линия 3: описание (уменьшаем пока не влезет)
                val y3 = y2 - glyph.height - gap
                val availH = (y3 - yBottom).coerceAtLeast(0f)
                var ds = 0.68f
                while (ds > 0.44f) {
                    font.data.setScale(ds, ds)
                    glyph.setText(font, opt.description, Color.LIGHT_GRAY, maxW, Align.left, true)
                    if (glyph.height <= availH + 0.5f) break
                    ds *= 0.90f
                }
                font.color = Color.LIGHT_GRAY
                font.draw(batch, glyph, r.x + padX, y3)

                font.data.setScale(baseScaleX, baseScaleY)
            } finally {
                batch.flush()
                ScissorStack.popScissors()
            }
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
        val s = uiScale.coerceIn(0.9f, 3.5f)
        font.color = Color.WHITE
        font.draw(batch, "СВЯТЫНЯ! Выбери глобальный бонус:", 16f * s, uiViewport.worldHeight - 116f * s)

        batch.end()
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)

        for (i in 0 until minOf(3, pendingChoices.size)) {
            val opt = pendingChoices[i]
            shapes.color = cardColorFor(opt.rarity)
            val r = cards[i]
            shapes.rect(r.x, r.y, r.width, r.height)
        }

        // Skip
        shapes.color = Color(0.35f, 0.07f, 0.07f, 0.92f)
        shapes.rect(skipRect.x, skipRect.y, skipRect.width, skipRect.height)
        shapes.end()
        batch.begin()

        val sc = Rectangle()
        for (i in 0 until minOf(3, pendingChoices.size)) {
            val opt = pendingChoices[i]
            val r = cards[i]

            ScissorStack.calculateScissors(camera, batch.transformMatrix, r, sc)
            batch.flush()
            ScissorStack.pushScissors(sc)
            try {
                val baseScaleX = font.data.scaleX
                val baseScaleY = font.data.scaleY

                val padX = 14f * s
                val padTop = 14f * s
                val padBottom = 10f * s
                val maxW = (r.width - padX * 2f).coerceAtLeast(80f * s)
                val yTop = r.y + r.height - padTop
                val yBottom = r.y + padBottom
                val gap = 6f * s

                // Линия 1: редкость
                font.data.setScale(0.78f, 0.78f)
                glyph.setText(font, "${i + 1}. ${opt.rarity.label}", Color.WHITE, maxW, Align.left, false)
                font.color = Color.WHITE
                font.draw(batch, glyph, r.x + padX, yTop)

                // Линия 2: title
                val y2 = yTop - glyph.height - gap
                font.data.setScale(0.88f, 0.88f)
                glyph.setText(font, opt.title, Color.WHITE, maxW, Align.left, true)
                font.draw(batch, glyph, r.x + padX, y2)

                // Линия 3: description
                val y3 = y2 - glyph.height - gap
                val availH = (y3 - yBottom).coerceAtLeast(0f)
                var ds = 0.68f
                while (ds > 0.44f) {
                    font.data.setScale(ds, ds)
                    glyph.setText(font, opt.description, Color.LIGHT_GRAY, maxW, Align.left, true)
                    if (glyph.height <= availH + 0.5f) break
                    ds *= 0.90f
                }
                font.color = Color.LIGHT_GRAY
                font.draw(batch, glyph, r.x + padX, y3)

                font.data.setScale(baseScaleX, baseScaleY)
            } finally {
                batch.flush()
                ScissorStack.popScissors()
            }
        }
        font.color = Color.WHITE
        font.draw(batch, "Пропустить (Space/Esc)", skipRect.x + 14f * s, skipRect.y + skipRect.height - 18f * s)
    }
}

