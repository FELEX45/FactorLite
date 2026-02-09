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
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.factorlite.FactorLiteGame
import com.factorlite.loot.ItemKind
import com.factorlite.loot.Rarity
import com.factorlite.loot.fixedRarity
import com.factorlite.loot.makeItemOption
import com.factorlite.loot.uiName as lootUiName
import com.factorlite.progression.CharacterKind
import com.factorlite.progression.WeaponKind
import com.factorlite.progression.isMagic
import com.factorlite.progression.uiName
import com.factorlite.util.CrashLog
import com.factorlite.audio.Audio

class InfoScreen(private val game: FactorLiteGame) : ScreenAdapter() {
    private enum class Tab { CHARACTERS, WEAPONS, ITEMS }

    private val uiCamera = OrthographicCamera()
    private val uiViewport = ScreenViewport(uiCamera)

    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val glyph = GlyphLayout()
    private var font: BitmapFont = BitmapFont()

    private var uiScale = 1f
    private var tab: Tab = Tab.CHARACTERS

    private val tabChars = Rectangle()
    private val tabWeapons = Rectangle()
    private val tabItems = Rectangle()
    private val btnBack = Rectangle()
    private val contentRect = Rectangle()

    private val scissor = Rectangle()
    private var scrollY = 0f
    private var maxScroll = 0f
    private var dragging = false
    private var lastDragScreenY = 0
    private var fatalErrorText: String? = null

    private val input = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            try {
                val ui = unproject(screenX, screenY)
                if (btnBack.contains(ui)) {
                    Audio.uiClick()
                    game.setScreen(MainMenuScreen(game))
                    return true
                }
                when {
                    tabChars.contains(ui) -> { Audio.uiClick(); tab = Tab.CHARACTERS; resetScroll(); return true }
                    tabWeapons.contains(ui) -> { Audio.uiClick(); tab = Tab.WEAPONS; resetScroll(); return true }
                    tabItems.contains(ui) -> { Audio.uiClick(); tab = Tab.ITEMS; resetScroll(); return true }
                }
                if (contentRect.contains(ui)) {
                    dragging = true
                    lastDragScreenY = screenY
                    return true
                }
                return false
            } catch (t: Throwable) {
                CrashLog.write("InfoScreen.touchDown", t)
                fatalErrorText = CrashLog.toText(t)
                return true
            }
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (!dragging) return false
            // Свайп вверх (screenY уменьшается) -> scrollY растёт -> контент уезжает вверх -> видно, что ниже.
            val dy = (lastDragScreenY - screenY).toFloat()
            lastDragScreenY = screenY
            scrollY = (scrollY + dy).coerceIn(0f, maxScroll)
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            dragging = false
            return false
        }

        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            val step = 56f * uiScale
            scrollY = (scrollY + amountY * step).coerceIn(0f, maxScroll)
            return true
        }
    }

    override fun show() {
        // Важно: setScreen(...) вызывается до первого resize(), поэтому viewport может быть ещё 0x0.
        uiViewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        recalcUiScale()
        rebuildFont()
        layout()
        rebuildMaxScroll()
        Gdx.input.inputProcessor = input
    }

    override fun hide() {
        if (Gdx.input.inputProcessor === input) {
            Gdx.input.inputProcessor = null
        }
    }

    override fun resize(width: Int, height: Int) {
        uiViewport.update(width, height, true)
        recalcUiScale()
        rebuildFont()
        layout()
        rebuildMaxScroll()
    }

    private fun recalcUiScale() {
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
            val size = (uiViewport.screenHeight * 0.022f * uiScale).toInt().coerceIn(14, 84)
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

        val pad = 14f * s
        val tabH = 56f * s
        val tabW = ((w - pad * 2f) / 3f).coerceAtLeast(120f * s)
        val topY = h - pad - tabH
        tabChars.set(pad, topY, tabW, tabH)
        tabWeapons.set(pad + tabW, topY, tabW, tabH)
        tabItems.set(pad + tabW * 2f, topY, tabW, tabH)

        btnBack.set(pad, pad, 160f * s, 54f * s)

        val contentTop = topY - 12f * s
        contentRect.set(pad, btnBack.y + btnBack.height + 12f * s, w - pad * 2f, contentTop - (btnBack.y + btnBack.height + 12f * s))
    }

    private fun unproject(screenX: Int, screenY: Int): Vector2 {
        return uiViewport.unproject(Vector2(screenX.toFloat(), screenY.toFloat()))
    }

    private fun resetScroll() {
        scrollY = 0f
        rebuildMaxScroll()
    }

    private fun rebuildMaxScroll() {
        // Приблизительная оценка высоты контента через GlyphLayout-wrap по ширине.
        val s = uiScale.coerceIn(0.75f, 2.0f)
        val padX = 14f * s
        val padY = 12f * s
        val wrapW = (contentRect.width - padX * 2f).coerceAtLeast(80f)
        val text = buildContentText()
        val baseX = font.data.scaleX
        val baseY = font.data.scaleY
        font.data.setScale(baseX * 0.92f, baseY * 0.92f)
        glyph.setText(font, text, Color.WHITE, wrapW, Align.left, true)
        font.data.setScale(baseX, baseY)

        val contentH = glyph.height
        val visibleH = (contentRect.height - padY * 2f).coerceAtLeast(0f)
        maxScroll = (contentH - visibleH).coerceAtLeast(0f)
        scrollY = scrollY.coerceIn(0f, maxScroll)
    }

    private fun buildContentText(): String {
        return when (tab) {
            Tab.CHARACTERS -> buildString {
                append("Персонажи:\n\n")
                for (c in CharacterKind.entries) {
                    append("- ").append(c.uiName).append('\n')
                    append("  Стартовое оружие: ").append(c.startWeapon.uiName).append('\n')
                    append("  Врождёнка: ").append(c.innate.uiName).append(" (").append((c.innatePerLevel * 100f).toInt()).append("% за уровень)\n")
                    append("  ").append(c.fullDescription).append("\n\n")
                }
            }

            Tab.WEAPONS -> buildString {
                append("Оружие:\n\n")
                for (w in WeaponKind.entries) {
                    append("- ").append(w.uiName)
                    append(if (w.isMagic) " (магическое)\n" else " (физическое)\n")
                    append("  ").append(w.fullDescription).append('\n')
                    append("  Возможные улучшения:\n")
                    for (line in w.possibleUpgradesLines) {
                        append("  - ").append(line).append('\n')
                    }
                    append('\n')
                }
            }

            Tab.ITEMS -> buildString {
                append("Предметы из сундуков:\n\n")
                for (k in ItemKind.entries) {
                    append("- ").append(k.lootUiName).append('\n')
                    val opt = makeItemOption(com.factorlite.loot.ItemInstance(k, k.fixedRarity))
                    append("  Редкость: ").append(k.fixedRarity.lootUiName).append('\n')
                    append("  ").append(opt.description).append('\n')
                    append('\n')
                }
            }
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

        // затемнение фона
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.62f)
        shapes.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)

        // tabs
        fun drawTab(r: Rectangle, isActive: Boolean, label: String) {
            shapes.color = if (isActive) Color(0.18f, 0.30f, 0.75f, 0.88f) else Color(0.12f, 0.12f, 0.16f, 0.88f)
            shapes.rect(r.x, r.y, r.width, r.height)
            shapes.color = Color(0f, 0f, 0f, 0.22f)
            shapes.rect(r.x, r.y, r.width, 2f * uiScale)
            shapes.rect(r.x, r.y + r.height - 2f * uiScale, r.width, 2f * uiScale)
        }
        drawTab(tabChars, tab == Tab.CHARACTERS, "Персонажи")
        drawTab(tabWeapons, tab == Tab.WEAPONS, "Оружие")
        drawTab(tabItems, tab == Tab.ITEMS, "Предметы")

        // content panel
        shapes.color = Color(0.10f, 0.10f, 0.12f, 0.92f)
        shapes.rect(contentRect.x, contentRect.y, contentRect.width, contentRect.height)

        // back
        shapes.color = Color(0.35f, 0.07f, 0.07f, 0.92f)
        shapes.rect(btnBack.x, btnBack.y, btnBack.width, btnBack.height)
        shapes.end()

        batch.begin()
        val s = uiScale.coerceIn(0.75f, 2.0f)

        fun labelCentered(r: Rectangle, text: String) {
            glyph.setText(font, text, Color.WHITE, r.width, Align.center, false)
            val y = r.y + r.height / 2f + glyph.height / 2f
            font.color = Color.WHITE
            font.draw(batch, glyph, r.x, y)
        }
        labelCentered(tabChars, "Персонажи")
        labelCentered(tabWeapons, "Оружие")
        labelCentered(tabItems, "Предметы")
        labelCentered(btnBack, "Назад")

        // content (clipped + scroll)
        val padX = 14f * s
        val padY = 12f * s
        val wrapW = (contentRect.width - padX * 2f).coerceAtLeast(80f)
        val text = buildContentText()

        val baseX = font.data.scaleX
        val baseY = font.data.scaleY
        font.data.setScale(baseX * 0.92f, baseY * 0.92f)
        glyph.setText(font, text, Color.WHITE, wrapW, Align.left, true)

        ScissorStack.calculateScissors(uiCamera, batch.transformMatrix, contentRect, scissor)
        batch.flush()
        ScissorStack.pushScissors(scissor)
        try {
            font.color = Color.WHITE
            // Рисуем от верхнего края панели, а scrollY сдвигает контент вверх (чтобы читать "что ниже").
            val startY = contentRect.y + contentRect.height - padY + scrollY
            font.draw(batch, glyph, contentRect.x + padX, startY)
        } finally {
            batch.flush()
            ScissorStack.popScissors()
        }
        font.data.setScale(baseX, baseY)

        // hint
        font.color = Color(1f, 1f, 1f, 0.55f)
        // Не рисуем подсказку "впритык" к кнопке "Назад", иначе на некоторых Android-экранах
        // (особенно с большой плотностью пикселей) она визуально наезжает/обрезается.
        val hint = if (Gdx.app.type == com.badlogic.gdx.Application.ApplicationType.Android) {
            "Свайп — скролл"
        } else {
            "Колёсико мыши / свайп — скролл"
        }
        glyph.setText(font, hint)
        font.draw(batch, glyph, contentRect.x, btnBack.y + btnBack.height + 10f * s + glyph.height)

        batch.end()
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        font.dispose()
    }
}

// --- Текстовые расширения для "Информации" ---

private val CharacterKind.fullDescription: String
    get() = when (this) {
        CharacterKind.FROZKA -> "Контроль и выживание: врождёнка замедляет врагов сильнее с уровнем."
        CharacterKind.FARZER -> "Поджоги и ДОТ: врождёнка ускоряет срабатывание эффектов урона во времени."
        CharacterKind.JOE -> "Стрелок: врождёнка даёт скорость передвижения для кайта и позиционки."
        CharacterKind.TRAPPER -> "Зона контроля: врождёнка увеличивает размер области (ловушки/облака)."
        CharacterKind.NIKUMI -> "Дуэлянт: врождёнка увеличивает шанс уклонения."
        CharacterKind.SAPKA -> "Ассасин: врождёнка усиливает физический урон."
        CharacterKind.ZHIDJ -> "Маг: врождёнка усиливает магический урон."
    }

private val WeaponKind.fullDescription: String
    get() = when (this) {
        WeaponKind.FROSTSTAFF -> "Автоматически стреляет в ближайшего врага. Замедляет по попаданию."
        WeaponKind.FIRESTAFF -> "Автоматически стреляет в ближайшего врага. Накладывает горение."
        WeaponKind.REVOLVER -> "Стреляет очередью по направлению движения/прицела. Хорош для чистого DPS."
        WeaponKind.POISON_TRAP -> "Ставит ловушку под цель: после взвода появляется ядовитое облако по области."
        WeaponKind.KATANA -> "Ближняя атака по нескольким ближайшим целям в радиусе."
        WeaponKind.DAGGER -> "Ближняя атака с шансом кровотечения (ДОТ) по попаданию."
        WeaponKind.POISON_AURA -> "Постоянная аура вокруг героя, наносит урон всем врагам в радиусе."
    }

private val WeaponKind.possibleUpgradesLines: List<String>
    get() = when (this) {
        WeaponKind.FROSTSTAFF, WeaponKind.FIRESTAFF -> listOf(
            "Урон",
            "Скорость атаки",
            "Скорость снарядов",
            "Точность",
            "Дальность",
            "Моды: доп. выстрел / рикошет / пробивание",
        )
        WeaponKind.REVOLVER -> listOf(
            "Урон",
            "Скорость атаки",
            "Скорость снарядов",
            "Дальность",
            "Моды: доп. выстрел / рикошет / пробивание",
        )
        WeaponKind.POISON_TRAP, WeaponKind.POISON_AURA -> listOf(
            "Урон",
            "Скорость атаки",
            "Дальность (для удобства позиционки/радиуса подбора цели)",
            "Моды: нет (для урона по области не показываем “лишние” моды)",
        )
        WeaponKind.KATANA, WeaponKind.DAGGER -> listOf(
            "Урон",
            "Скорость атаки",
            "Дальность",
            "Моды: доп. цель / цепочка / пробивание (в зависимости от оружия)",
        )
    }

