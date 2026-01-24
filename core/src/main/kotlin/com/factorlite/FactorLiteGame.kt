package com.factorlite

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Game
import com.factorlite.screens.GameScreen

/**
 * Точка входа для логики игры.
 */
class FactorLiteGame : Game() {
    override fun create() {
        setScreen(GameScreen())
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        Gdx.app.log("FactorLite", "resize: ${width}x$height")
    }
}

