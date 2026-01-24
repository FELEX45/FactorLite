package com.factorlite.input

import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Vector2

class FloatingJoystick(
    private val deadzonePx: Float = 10f,
    private val radiusPx: Float = 90f,
) : InputAdapter() {
    private var activePointer = -1

    private val originPx = Vector2()
    private val currentPx = Vector2()

    /** Нормализованное направление движения (x,y) в диапазоне [-1..1]. */
    val direction = Vector2()

    /** Удобно для рендера: центр и текущая точка. */
    val isActive: Boolean get() = activePointer != -1
    val originForRenderPx: Vector2 get() = originPx
    val currentForRenderPx: Vector2 get() = currentPx

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (activePointer != -1) return false
        activePointer = pointer
        originPx.set(screenX.toFloat(), screenY.toFloat())
        currentPx.set(originPx)
        direction.set(0f, 0f)
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (pointer != activePointer) return false
        currentPx.set(screenX.toFloat(), screenY.toFloat())
        updateDirection()
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer != activePointer) return false
        activePointer = -1
        direction.set(0f, 0f)
        return true
    }

    private fun updateDirection() {
        val dx = currentPx.x - originPx.x
        val dy = currentPx.y - originPx.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len < deadzonePx) {
            direction.set(0f, 0f)
            return
        }
        val clamped = kotlin.math.min(len, radiusPx)
        direction.set(dx / len, dy / len).scl(clamped / radiusPx)
    }
}

