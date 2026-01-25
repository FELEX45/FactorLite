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
import com.factorlite.loot.ItemOption
import com.factorlite.progression.GlobalBonusOption
import com.factorlite.progression.label
import com.factorlite.progression.UpgradeOption
import com.factorlite.progression.UpgradeRarity

class RunUiSystem {
    private val cards = Array(3) { Rectangle() }
    private val skipRect = Rectangle()
    private val tmp = Vector2()

    fun layoutCards(uiViewport: ScreenViewport) {
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
            cards[i].set(padX, y - cardH, cardW, cardH)
        }

        // Кнопка "Пропустить" под карточками
        val skipH = 56f
        skipRect.set(padX, (topY - 3 * (cardH + gap)) - skipH, cardW, skipH)
    }

    fun pollKeyPick(runState: RunState): Int? {
        if (runState != RunState.LEVEL_UP && runState != RunState.CHEST_OPEN && runState != RunState.SHRINE_OPEN) return null
        return when {
            Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1) -> 0
            Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_2) -> 1
            Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_3) -> 2
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
                if (state != RunState.LEVEL_UP && state != RunState.CHEST_OPEN && state != RunState.SHRINE_OPEN) return false

                val count = getOptionCount()
                if (count <= 0) return false

                tmp.set(screenX.toFloat(), screenY.toFloat())
                uiViewport.unproject(tmp)
                for (i in 0 until minOf(3, count)) {
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

    fun drawLevelUpOverlay(
        batch: SpriteBatch,
        shapes: ShapeRenderer,
        font: BitmapFont,
        uiViewport: ScreenViewport,
        camera: Camera,
        pendingChoices: List<UpgradeOption>,
    ) {
        font.color = Color.WHITE
        font.draw(batch, "LEVEL UP! Выбери улучшение:", 16f, uiViewport.worldHeight - 92f)

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
            font.draw(batch, "${i + 1}. $rar", r.x + 14f, r.y + r.height - 16f)
            opt.levelLabel?.let { lvl ->
                font.draw(batch, lvl, r.x + r.width - 90f, r.y + r.height - 16f)
            }

            // Строка 2: название предмета/пассивки
            font.color = Color.WHITE
            font.draw(batch, opt.title, r.x + 14f, r.y + r.height - 38f)

            // Строка 3: "до -> после"
            font.color = Color.LIGHT_GRAY
            font.draw(batch, opt.description, r.x + 14f, r.y + r.height - 58f)
        }
    }

    fun drawChestOverlay(
        batch: SpriteBatch,
        shapes: ShapeRenderer,
        font: BitmapFont,
        uiViewport: ScreenViewport,
        camera: Camera,
        pendingChoices: List<ItemOption>,
    ) {
        font.color = Color.WHITE
        font.draw(batch, "СУНДУК! Выбери предмет:", 16f, uiViewport.worldHeight - 116f)

        batch.end()
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.45f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)
        shapes.color = Color(0.12f, 0.12f, 0.16f, 0.92f)
        for (i in 0 until minOf(3, pendingChoices.size)) {
            val r = cards[i]
            shapes.rect(r.x, r.y, r.width, r.height)
        }
        shapes.end()
        batch.begin()

        for (i in 0 until minOf(3, pendingChoices.size)) {
            val opt = pendingChoices[i]
            val r = cards[i]
            font.color = Color.WHITE
            font.draw(batch, "${i + 1}. ${opt.title}", r.x + 14f, r.y + r.height - 16f)
            font.color = Color.LIGHT_GRAY
            font.draw(batch, opt.description, r.x + 14f, r.y + r.height - 40f)
        }
        font.color = Color.WHITE
        font.draw(batch, "(Тап по карточке / 1-2-3)", 16f, uiViewport.worldHeight - 140f)
    }

    fun drawShrineOverlay(
        batch: SpriteBatch,
        shapes: ShapeRenderer,
        font: BitmapFont,
        uiViewport: ScreenViewport,
        camera: Camera,
        pendingChoices: List<GlobalBonusOption>,
    ) {
        font.color = Color.WHITE
        font.draw(batch, "СВЯТЫНЯ! Выбери глобальный бонус:", 16f, uiViewport.worldHeight - 116f)

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
            font.draw(batch, "${i + 1}. ${opt.rarity.label}", r.x + 14f, r.y + r.height - 16f)
            font.color = Color.WHITE
            font.draw(batch, opt.title, r.x + 14f, r.y + r.height - 38f)
            font.color = Color.LIGHT_GRAY
            font.draw(batch, opt.description, r.x + 14f, r.y + r.height - 58f)
        }
        font.color = Color.WHITE
        font.draw(batch, "Пропустить (Space/Esc)", skipRect.x + 14f, skipRect.y + skipRect.height - 18f)
    }
}

