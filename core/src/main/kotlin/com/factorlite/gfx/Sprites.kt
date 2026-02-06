package com.factorlite.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.math.MathUtils
import kotlin.math.sqrt

/**
 * Минимальный реестр спрайтов:
 * - пытается загрузить `assets/textures/<name>.png`
 * - если файла нет — создаёт плейсхолдер через Pixmap
 *
 * Это позволяет постепенно подкидывать реальные спрайты без падений/правок кода.
 */
object Sprites : Disposable {
    private data class Entry(val tex: Texture, val fromFile: Boolean)
    private val cache = HashMap<String, Entry>()

    fun get(name: String, fallback: () -> Texture): Texture {
        val f = findTextureFile(name)
        val exists = f != null && f.exists()

        val cached = cache[name]
        if (cached != null) {
            // Дев‑удобство: если раньше файла не было и мы закэшировали плейсхолдер,
            // а потом художник/ты добавили текстуру — автоматически подменяем без перезапуска.
            if (exists && !cached.fromFile) {
                cached.tex.dispose()
                val tex = Texture(f).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
                cache[name] = Entry(tex = tex, fromFile = true)
                return tex
            }
            return cached.tex
        }

        return if (exists) {
            val tex = Texture(f).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
            cache[name] = Entry(tex = tex, fromFile = true)
            tex
        } else {
            val tex = fallback()
            cache[name] = Entry(tex = tex, fromFile = false)
            tex
        }
    }

    private fun findTextureFile(name: String): FileHandle? {
        // Поддерживаем PNG и JPG/JPEG (часто арты приходят в jpg на этапе прототипа).
        val exts = arrayOf("png", "jpg", "jpeg")
        for (ext in exts) {
            val f = Gdx.files.internal("textures/$name.$ext")
            if (f.exists()) return f
        }
        return null
    }

    fun circleTexture(size: Int, color: Color): Texture {
        val s = size.coerceIn(8, 256)
        val pm = Pixmap(s, s, Pixmap.Format.RGBA8888)
        pm.setBlending(Pixmap.Blending.None)
        pm.setColor(0f, 0f, 0f, 0f)
        pm.fill()
        pm.setBlending(Pixmap.Blending.SourceOver)
        pm.setColor(color)
        pm.fillCircle(s / 2, s / 2, s / 2 - 1)
        val t = Texture(pm)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pm.dispose()
        return t
    }

    fun solidTexture(size: Int, color: Color): Texture {
        val s = size.coerceIn(2, 256)
        val pm = Pixmap(s, s, Pixmap.Format.RGBA8888)
        pm.setBlending(Pixmap.Blending.None)
        pm.setColor(color)
        pm.fill()
        val t = Texture(pm)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pm.dispose()
        return t
    }

    /**
     * Мягкое "облако": радиальный градиент + несколько полупрозрачных пятен.
     * Хорошо подходит как плейсхолдер для токсичного облака/ауры, когда PNG ещё нет.
     */
    fun softCloudTexture(size: Int, base: Color, spotCount: Int = 9, seed: Long = 1337L): Texture {
        val s = size.coerceIn(32, 256)
        val pm = Pixmap(s, s, Pixmap.Format.RGBA8888)
        pm.setBlending(Pixmap.Blending.None)
        pm.setColor(0f, 0f, 0f, 0f)
        pm.fill()
        pm.setBlending(Pixmap.Blending.SourceOver)

        // Радиальный градиент: центр плотнее, края мягче.
        val cx = (s - 1) * 0.5f
        val cy = (s - 1) * 0.5f
        val maxR = (s * 0.5f) * 0.98f
        for (y in 0 until s) {
            for (x in 0 until s) {
                val dx = x - cx
                val dy = y - cy
                val r = sqrt(dx * dx + dy * dy)
                val t = (1f - (r / maxR)).coerceIn(0f, 1f)
                // Профиль плотности: мягко, без резкого круга.
                val a = (t * t * 0.70f).coerceIn(0f, 1f) * base.a
                if (a <= 0.001f) continue
                pm.setColor(base.r, base.g, base.b, a)
                pm.drawPixel(x, y)
            }
        }

        // Пятна: добавляют "дымность" без шума на каждом пикселе.
        MathUtils.random.setSeed(seed)
        for (i in 0 until spotCount) {
            val rx = MathUtils.random(s * 0.25f, s * 0.75f)
            val ry = MathUtils.random(s * 0.25f, s * 0.75f)
            val rr = MathUtils.random(s * 0.08f, s * 0.18f)
            val a = MathUtils.random(0.08f, 0.22f) * base.a
            pm.setColor(base.r, base.g, base.b, a)
            pm.fillCircle(rx.toInt(), ry.toInt(), rr.toInt())
        }

        val t = Texture(pm)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pm.dispose()
        return t
    }

    fun drawCentered(batch: SpriteBatch, tex: Texture, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
        // Важно: SpriteBatch.color возвращает *внутренний* Color по ссылке.
        // Нельзя сохранять его как `prev = batch.color` и потом менять batch.setColor(),
        // иначе prev тоже изменится и восстановление "сломается" (что давало эффект затемнения экрана).
        val c = batch.color
        val pr = c.r
        val pg = c.g
        val pb = c.b
        val pa = c.a
        if (alpha != 1f) batch.setColor(pr, pg, pb, pa * alpha)
        batch.draw(tex, x - w / 2f, y - h / 2f, w, h)
        if (alpha != 1f) batch.setColor(pr, pg, pb, pa)
    }

    override fun dispose() {
        for (e in cache.values) e.tex.dispose()
        cache.clear()
    }
}

