package com.factorlite.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable

/**
 * Минимальный реестр спрайтов:
 * - пытается загрузить `assets/textures/<name>.png`
 * - если файла нет — создаёт плейсхолдер через Pixmap
 *
 * Это позволяет постепенно подкидывать реальные спрайты без падений/правок кода.
 */
object Sprites : Disposable {
    private val cache = HashMap<String, Texture>()

    fun get(name: String, fallback: () -> Texture): Texture {
        return cache.getOrPut(name) {
            val f = Gdx.files.internal("textures/$name.png")
            if (f.exists()) {
                Texture(f).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
            } else {
                fallback()
            }
        }
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

    fun drawCentered(batch: SpriteBatch, tex: Texture, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
        val prev = batch.color
        if (alpha != 1f) batch.setColor(prev.r, prev.g, prev.b, alpha)
        batch.draw(tex, x - w / 2f, y - h / 2f, w, h)
        if (alpha != 1f) batch.color = prev
    }

    override fun dispose() {
        for (t in cache.values) t.dispose()
        cache.clear()
    }
}

