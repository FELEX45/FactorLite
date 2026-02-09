package com.factorlite.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.factorlite.FactorLiteGame
import com.factorlite.audio.Audio
import com.factorlite.audio.AudioSettings
import com.factorlite.audio.Bgm
import com.factorlite.gfx.Sprites
import com.factorlite.util.CrashLog

class MainMenuScreen(private val game: FactorLiteGame) : ScreenAdapter() {
    private val uiCamera = OrthographicCamera()
    private val uiViewport = ScreenViewport(uiCamera)

    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val glyph = GlyphLayout()

    private var font: BitmapFont = BitmapFont()
    private var uiScale = 1f

    private val btnPlay = Rectangle()
    private val btnInfo = Rectangle()
    private val btnExit = Rectangle()
    private val toggleSfx = Rectangle()
    private val toggleMusic = Rectangle()

    private var fatalErrorText: String? = null

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            try {
                // Screen coords -> UI coords
                val ui = uiViewport.unproject(com.badlogic.gdx.math.Vector2(screenX.toFloat(), screenY.toFloat()))

                when {
                    toggleSfx.contains(ui) -> {
                        val was = Audio.enabled
                        Audio.enabled = !Audio.enabled
                        AudioSettings.save()
                        if (!was && Audio.enabled) Audio.uiClick()
                        return true
                    }
                    toggleMusic.contains(ui) -> {
                        val was = Bgm.enabled
                        Bgm.enabled = !Bgm.enabled
                        Bgm.applySettings()
                        AudioSettings.save()
                        if (!was && Bgm.enabled) Audio.uiClick()
                        return true
                    }
                    btnPlay.contains(ui) -> {
                        Audio.uiClick()
                        game.setScreen(GameScreen(game))
                        return true
                    }
                    btnInfo.contains(ui) -> {
                        Audio.uiClick()
                        game.setScreen(InfoScreen(game))
                        return true
                    }
                    btnExit.contains(ui) -> {
                        Audio.uiClick()
                        Gdx.app.exit()
                        return true
                    }
                }
                return false
            } catch (t: Throwable) {
                CrashLog.write("MainMenuScreen.touchDown", t)
                fatalErrorText = CrashLog.toText(t)
                return true
            }
        }
    }

    override fun show() {
        // Важно: setScreen(...) вызывается до первого resize(), поэтому viewport может быть ещё 0x0.
        uiViewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        recalcUiScale()
        rebuildFont()
        layout()
        Gdx.input.inputProcessor = input
    }

    override fun hide() {
        // Чтобы старый Screen не продолжал принимать клики.
        if (Gdx.input.inputProcessor === input) {
            Gdx.input.inputProcessor = null
        }
    }

    override fun resize(width: Int, height: Int) {
        uiViewport.update(width, height, true)
        recalcUiScale()
        rebuildFont()
        layout()
    }

    private fun recalcUiScale() {
        // То же “ощущение”, что и в GameScreen: базируемся на высоте.
        val h = uiViewport.screenHeight.coerceAtLeast(1)
        uiScale = (h / 1920f).coerceIn(0.65f, 1.35f)
    }

    private fun rebuildFont() {
        font.dispose()
        val preferred = Gdx.files.internal("fonts/Roboto-Regular.ttf")
        val ttf = if (preferred.exists()) preferred else null
        if (ttf == null) {
            font = BitmapFont()
            return
        }

        val gen = FreeTypeFontGenerator(ttf)
        try {
            val size = (uiViewport.screenHeight * 0.026f * uiScale).toInt().coerceIn(16, 96)
            val param = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                this.size = size
                color = Color.WHITE
                characters = FreeTypeFontGenerator.DEFAULT_CHARS +
                    "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя№—«»→"
            }
            font = gen.generateFont(param)
        } finally {
            gen.dispose()
        }
    }

    private fun layout() {
        val w = uiViewport.worldWidth
        val h = uiViewport.worldHeight
        val s = uiScale.coerceIn(0.75f, 2.0f)

        val btnW = (w * 0.62f).coerceIn(260f * s, 520f * s)
        val btnH = (78f * s).coerceIn(56f * s, 92f * s)
        val gap = 18f * s
        val cx = w / 2f

        val firstY = h * 0.55f
        btnPlay.set(cx - btnW / 2f, firstY, btnW, btnH)
        btnInfo.set(cx - btnW / 2f, firstY - (btnH + gap), btnW, btnH)
        btnExit.set(cx - btnW / 2f, firstY - 2f * (btnH + gap), btnW, btnH)

        // Тумблеры звука/музыки (под кнопками).
        // На узких экранах подписи "Звук: ВКЛ" и "Музыка: ВКЛ" могут не влезать в половинки
        // и визуально "наезжать" друг на друга, потому что текст рисуется без клиппинга.
        // Поэтому делаем тумблеры в одну колонку на всю ширину кнопок.
        val tw = btnW
        val minH = 52f * s
        val textH = font.lineHeight * 1.35f
        val th = maxOf(minH, textH).coerceIn(44f * s, 92f * s)
        val ty1 = btnExit.y - (th + 14f * s)
        toggleSfx.set(cx - tw / 2f, ty1, tw, th)
        val ty2 = ty1 - (th + 10f * s)
        toggleMusic.set(cx - tw / 2f, ty2, tw, th)

        // Страховка для низких экранов: не даём нижнему тумблеру залезать на подпись версии снизу.
        // (внутри render() версия рисуется примерно от 12*s)
        val minBottomY = 12f * s + th + 8f * s
        if (toggleMusic.y < minBottomY) {
            val dy = (minBottomY - toggleMusic.y)
            toggleSfx.y += dy
            toggleMusic.y += dy
        }
    }

    override fun render(delta: Float) {
        fatalErrorText?.let { err ->
            uiViewport.apply()
            batch.projectionMatrix = uiCamera.combined
            batch.begin()
            font.color = Color(1f, 0.55f, 0.55f, 1f)
            font.draw(batch, "CRASH (см. crash.log)", 16f, uiViewport.worldHeight - 18f)
            font.color = Color.WHITE
            font.draw(batch, err, 16f, uiViewport.worldHeight - 44f)
            font.color = Color.LIGHT_GRAY
            font.draw(batch, "Пути логов:\n${CrashLog.pathsHint()}", 16f, uiViewport.worldHeight - 240f)
            batch.end()
            return
        }

        uiViewport.apply()
        batch.projectionMatrix = uiCamera.combined
        shapes.projectionMatrix = uiCamera.combined

        // Фон: берём easy-world текстуру как "обои" меню, но сильно приглушаем.
        batch.begin()
        val bg = Sprites.get("world_easy") { Sprites.solidTexture(4, Color(0.12f, 0.14f, 0.16f, 1f)) }
        val c = batch.color
        val pr = c.r; val pg = c.g; val pb = c.b; val pa = c.a
        batch.setColor(0.55f, 0.58f, 0.62f, 1f)
        batch.draw(bg, 0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)
        batch.setColor(pr, pg, pb, pa)
        batch.end()

        // Панели + кнопки
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        fun drawButton(r: Rectangle, col: Color) {
            shapes.color = col
            shapes.rect(r.x, r.y, r.width, r.height)
            shapes.color = Color(0f, 0f, 0f, 0.22f)
            shapes.rect(r.x, r.y, r.width, 2f * uiScale)
            shapes.rect(r.x, r.y + r.height - 2f * uiScale, r.width, 2f * uiScale)
        }
        drawButton(btnPlay, Color(0.10f, 0.45f, 0.22f, 0.88f))
        drawButton(btnInfo, Color(0.12f, 0.12f, 0.16f, 0.88f))
        drawButton(btnExit, Color(0.35f, 0.07f, 0.07f, 0.88f))
        shapes.end()

        // Текст
        batch.begin()
        val w = uiViewport.worldWidth
        val h = uiViewport.worldHeight
        val s = uiScale.coerceIn(0.75f, 2.0f)

        // Заголовок (красивее: чуть крупнее + тень)
        val baseX = font.data.scaleX
        val baseY = font.data.scaleY
        font.data.setScale(baseX * 2.10f, baseY * 2.10f)
        val title = "FactorLite"
        // Важно: для точного центрирования не используем wrapWidth+Align.center,
        // потому что glyph.width может стать равным wrapWidth.
        glyph.setText(font, title)
        val tx = (w - glyph.width) / 2f
        val ty = h - 26f * s
        font.color = Color(0f, 0f, 0f, 0.55f)
        font.draw(batch, glyph, tx + 2f * s, ty - 2f * s)
        font.color = Color.WHITE
        font.draw(batch, glyph, tx, ty)
        font.data.setScale(baseX, baseY)

        fun labelCentered(r: Rectangle, text: String) {
            glyph.setText(font, text, Color.WHITE, r.width, Align.center, false)
            val x = r.x
            val y = r.y + r.height / 2f + glyph.height / 2f
            font.color = Color.WHITE
            font.draw(batch, glyph, x, y)
        }

        labelCentered(btnPlay, "Играть")
        labelCentered(btnInfo, "Информация")
        labelCentered(btnExit, "Выход")

        // Тумблеры
        fun drawToggle(r: Rectangle, label: String, on: Boolean) {
            batch.end()
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            shapes.color = if (on) Color(0.10f, 0.45f, 0.22f, 0.78f) else Color(0.12f, 0.12f, 0.16f, 0.78f)
            shapes.rect(r.x, r.y, r.width, r.height)
            shapes.end()
            batch.begin()

            glyph.setText(font, "$label: ${if (on) "ВКЛ" else "ВЫКЛ"}", Color.WHITE, r.width, Align.center, false)
            val y = r.y + r.height / 2f + glyph.height / 2f
            font.color = Color.WHITE
            font.draw(batch, glyph, r.x, y)
        }
        drawToggle(toggleSfx, "Звук", Audio.enabled)
        drawToggle(toggleMusic, "Музыка", Bgm.enabled)

        // Подпись снизу
        val version = "v0.2 демо"
        glyph.setText(font, version)
        font.color = Color(1f, 1f, 1f, 0.55f)
        // Рисуем по baseline, чтобы текст полностью был видим (не обрезался снизу).
        font.draw(batch, glyph, 14f * s, 12f * s + glyph.height)
        batch.end()
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        font.dispose()
    }
}

