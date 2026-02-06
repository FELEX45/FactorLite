package com.factorlite

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Game
import com.factorlite.content.Balance
import com.factorlite.gfx.Sprites
import com.factorlite.screens.MainMenuScreen
import com.factorlite.util.CrashLog

/**
 * Точка входа для логики игры.
 */
class FactorLiteGame : Game() {
    override fun create() {
        Thread.setDefaultUncaughtExceptionHandler { _, t ->
            CrashLog.write("Uncaught", t)
        }
        Balance.loadOrKeepDefaults()
        setScreen(MainMenuScreen(this))
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        Gdx.app.log("FactorLite", "resize: ${width}x$height")
    }

    override fun dispose() {
        super.dispose()
        Sprites.dispose()
    }
}

