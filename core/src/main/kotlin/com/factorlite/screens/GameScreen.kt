package com.factorlite.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.factorlite.input.FloatingJoystick
import kotlin.math.max

class GameScreen : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(960f, 540f, camera)

    private val shapes = ShapeRenderer()

    private val uiCamera = OrthographicCamera()
    private val uiViewport = ScreenViewport(uiCamera)
    private val batch = SpriteBatch()
    private val font = BitmapFont()

    private val joystick = FloatingJoystick(
        deadzonePx = 12f,
        radiusPx = 110f,
    )

    private val playerPos = Vector2(0f, 0f)
    private val playerVel = Vector2(0f, 0f)

    private val playerSpeed = 260f
    private val playerRadius = 14f
    private var playerHp = 100f
    private var playerInvuln = 0f
    private var isGameOver = false
    private var runTime = 0f

    private data class Enemy(
        val pos: Vector2,
        var hp: Float,
        val speed: Float,
        val contactDamage: Float,
        val radius: Float = 14f,
    )

    private data class Projectile(
        val pos: Vector2,
        val vel: Vector2,
        val damage: Float,
        val radius: Float = 4f,
    )

    private val enemies = ArrayList<Enemy>()
    private val projectiles = ArrayList<Projectile>()

    private var target: Enemy? = null
    private var retargetTimer = 0f

    private var attackCooldown = 0f
    private var spawnTimer = 0f

    // Простая арена (пока)
    private val arenaHalfW = 900f
    private val arenaHalfH = 500f

    override fun show() {
        Gdx.input.inputProcessor = joystick
        resetRun()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        uiViewport.update(width, height, true)
    }

    override fun render(delta: Float) {
        if (!isGameOver) {
            update(delta)
        } else {
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.R) || Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
                resetRun()
            }
        }

        Gdx.gl.glClearColor(0.07f, 0.08f, 0.10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        shapes.projectionMatrix = camera.combined

        // Мир
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.DARK_GRAY
        shapes.rect(-arenaHalfW, -arenaHalfH, arenaHalfW * 2f, arenaHalfH * 2f)
        shapes.end()

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Игрок мигает при неуязвимости
        val invulnT = (playerInvuln / 0.6f).coerceIn(0f, 1f)
        val a = if (playerInvuln > 0f) 0.35f + 0.65f * (1f - Interpolation.fade.apply(invulnT)) else 1f
        shapes.color = Color(1f, 1f, 1f, a)
        shapes.circle(playerPos.x, playerPos.y, playerRadius, 20)

        // Враги
        shapes.color = Color(0.95f, 0.25f, 0.25f, 1f)
        for (e in enemies) {
            shapes.circle(e.pos.x, e.pos.y, e.radius, 18)
        }

        // Снаряды
        shapes.color = Color(1f, 0.9f, 0.2f, 1f)
        for (p in projectiles) {
            shapes.circle(p.pos.x, p.pos.y, p.radius, 12)
        }
        shapes.end()

        // Подсветка цели
        target?.let { t ->
            shapes.begin(ShapeRenderer.ShapeType.Line)
            shapes.color = Color(0.3f, 1f, 0.3f, 1f)
            shapes.circle(t.pos.x, t.pos.y, t.radius + 6f, 24)
            shapes.line(playerPos.x, playerPos.y, t.pos.x, t.pos.y)
            shapes.end()
        }

        // Джойстик (в screen-space)
        drawJoystickOverlay()

        // HUD (screen-space)
        drawHud()
    }

    private fun update(delta: Float) {
        runTime += delta
        playerInvuln = max(0f, playerInvuln - delta)

        // joystick.direction сейчас в screen-space, Y у libGDX для touch идёт сверху вниз
        // Поэтому инвертируем Y.
        playerVel.set(joystick.direction.x, -joystick.direction.y).scl(playerSpeed)
        playerPos.mulAdd(playerVel, delta)

        // Ограничение арены
        playerPos.x = MathUtils.clamp(playerPos.x, -arenaHalfW + playerRadius, arenaHalfW - playerRadius)
        playerPos.y = MathUtils.clamp(playerPos.y, -arenaHalfH + playerRadius, arenaHalfH - playerRadius)

        updateSpawns(delta)
        updateEnemies(delta)
        applyContactDamage()

        // Камера следует за игроком
        camera.position.set(playerPos.x, playerPos.y, 0f)
        camera.update()

        updateTargeting(delta)
        updateAttacking(delta)
        updateProjectiles(delta)
        cleanupDead()
    }

    private fun resetRun() {
        isGameOver = false
        runTime = 0f
        playerHp = 100f
        playerInvuln = 0f
        playerPos.set(0f, 0f)
        playerVel.set(0f, 0f)

        enemies.clear()
        projectiles.clear()
        target = null
        retargetTimer = 0f
        attackCooldown = 0f
        spawnTimer = 0f

        // Стартовая “дыра”, чтобы сразу было что стрелять (через 0.2с спавнится первый враг)
        spawnTimer = 0.2f
    }

    private fun updateSpawns(delta: Float) {
        // Темп растёт со временем: интервал от 1.2 сек к ~0.25 сек
        val minInterval = 0.25f
        val maxInterval = 1.2f
        val t = (runTime / 90f).coerceIn(0f, 1f)
        val interval = MathUtils.lerp(maxInterval, minInterval, t)

        spawnTimer -= delta
        while (spawnTimer <= 0f) {
            spawnTimer += interval
            spawnEnemy()
        }
    }

    private fun spawnEnemy() {
        // Спавнимся по краям арены (слегка за пределом)
        val pad = 40f
        val side = MathUtils.random(3)
        val x: Float
        val y: Float
        when (side) {
            0 -> { // left
                x = -arenaHalfW - pad
                y = MathUtils.random(-arenaHalfH, arenaHalfH)
            }
            1 -> { // right
                x = arenaHalfW + pad
                y = MathUtils.random(-arenaHalfH, arenaHalfH)
            }
            2 -> { // bottom
                x = MathUtils.random(-arenaHalfW, arenaHalfW)
                y = -arenaHalfH - pad
            }
            else -> { // top
                x = MathUtils.random(-arenaHalfW, arenaHalfW)
                y = arenaHalfH + pad
            }
        }

        // Тип врага: чем больше runTime, тем чаще быстрые/танки
        val pFast = (runTime / 120f).coerceIn(0f, 0.35f)
        val pTank = (runTime / 150f).coerceIn(0f, 0.25f)
        val roll = MathUtils.random()

        val enemy = when {
            roll < pTank -> Enemy(Vector2(x, y), hp = 70f, speed = 80f, contactDamage = 12f, radius = 18f) // tank
            roll < pTank + pFast -> Enemy(Vector2(x, y), hp = 28f, speed = 150f, contactDamage = 8f, radius = 13f) // fast
            else -> Enemy(Vector2(x, y), hp = 40f, speed = 105f, contactDamage = 10f, radius = 15f) // normal
        }
        enemies.add(enemy)
    }

    private fun updateEnemies(delta: Float) {
        for (e in enemies) {
            if (e.hp <= 0f) continue
            val dir = Vector2(playerPos.x - e.pos.x, playerPos.y - e.pos.y)
            if (!dir.isZero(0.0001f)) {
                dir.nor().scl(e.speed)
                e.pos.mulAdd(dir, delta)
            }
        }
    }

    private fun applyContactDamage() {
        if (playerInvuln > 0f) return
        for (e in enemies) {
            if (e.hp <= 0f) continue
            val dx = e.pos.x - playerPos.x
            val dy = e.pos.y - playerPos.y
            val r = e.radius + playerRadius
            if (dx * dx + dy * dy <= r * r) {
                playerHp -= e.contactDamage
                playerInvuln = 0.6f
                if (playerHp <= 0f) {
                    playerHp = 0f
                    isGameOver = true
                }
                return
            }
        }
    }

    private fun updateTargeting(delta: Float) {
        val range = 520f
        val range2 = range * range

        // Если текущая цель ещё валидна — держим её, пока не убежала далеко.
        target?.let { t ->
            val dx = t.pos.x - playerPos.x
            val dy = t.pos.y - playerPos.y
            val d2 = dx * dx + dy * dy
            if (t.hp <= 0f || d2 > range2 * 1.1f * 1.1f) {
                target = null
            }
        }

        retargetTimer -= delta
        if (retargetTimer > 0f && target != null) return
        retargetTimer = 0.12f

        var best: Enemy? = target
        var bestD2 = Float.POSITIVE_INFINITY
        target?.let { t ->
            val dx = t.pos.x - playerPos.x
            val dy = t.pos.y - playerPos.y
            bestD2 = dx * dx + dy * dy
        }

        for (e in enemies) {
            if (e.hp <= 0f) continue
            val dx = e.pos.x - playerPos.x
            val dy = e.pos.y - playerPos.y
            val d2 = dx * dx + dy * dy
            if (d2 > range2) continue

            // “Гистерезис”: переключаемся только если новая цель заметно ближе
            val shouldSwitch = best == null || d2 < bestD2 * 0.8f
            if (shouldSwitch) {
                best = e
                bestD2 = d2
            }
        }

        target = best
    }

    private fun updateAttacking(delta: Float) {
        attackCooldown = max(0f, attackCooldown - delta)
        val t = target ?: return
        if (attackCooldown > 0f) return

        // Базовая авто-атака (пуля в цель)
        val dir = Vector2(t.pos.x - playerPos.x, t.pos.y - playerPos.y)
        if (dir.isZero(0.0001f)) return
        dir.nor()

        val speed = 780f
        val dmg = 10f
        val p = Projectile(
            pos = Vector2(playerPos.x, playerPos.y),
            vel = dir.scl(speed),
            damage = dmg,
        )
        projectiles.add(p)

        attackCooldown = 0.35f
    }

    private fun updateProjectiles(delta: Float) {
        // Движение и попадания
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.pos.mulAdd(p.vel, delta)

            // Выход за арену — удаляем
            if (p.pos.x < -arenaHalfW - 50f || p.pos.x > arenaHalfW + 50f || p.pos.y < -arenaHalfH - 50f || p.pos.y > arenaHalfH + 50f) {
                it.remove()
                continue
            }

            // Попадание по врагу
            for (e in enemies) {
                if (e.hp <= 0f) continue
                val dx = e.pos.x - p.pos.x
                val dy = e.pos.y - p.pos.y
                val r = e.radius + p.radius
                if (dx * dx + dy * dy <= r * r) {
                    e.hp -= p.damage
                    it.remove()
                    break
                }
            }
        }
    }

    private fun cleanupDead() {
        enemies.removeAll { it.hp <= 0f }
        if (target != null && target?.hp ?: 0f <= 0f) target = null
    }

    private fun drawJoystickOverlay() {
        if (!joystick.isActive) return

        // Рисуем в world-space через unproject (достаточно для MVP).
        shapes.projectionMatrix = camera.combined
        val originScreen = joystick.originForRenderPx
        val currentScreen = joystick.currentForRenderPx

        val originWorld = viewport.unproject(Vector2(originScreen.x, originScreen.y))
        val currentWorld = viewport.unproject(Vector2(currentScreen.x, currentScreen.y))

        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color(1f, 1f, 1f, 0.35f)
        shapes.circle(originWorld.x, originWorld.y, 40f, 24)
        shapes.line(originWorld.x, originWorld.y, currentWorld.x, currentWorld.y)
        shapes.end()
    }

    private fun drawHud() {
        uiViewport.apply()
        batch.projectionMatrix = uiCamera.combined

        batch.begin()
        font.color = Color.WHITE
        font.draw(batch, "HP: ${playerHp.toInt()}   Time: ${runTime.toInt()}s   Enemies: ${enemies.size}", 16f, uiViewport.worldHeight - 16f)
        if (isGameOver) {
            font.color = Color(1f, 0.4f, 0.4f, 1f)
            font.draw(batch, "GAME OVER", uiViewport.worldWidth / 2f - 60f, uiViewport.worldHeight / 2f + 10f)
            font.color = Color.WHITE
            font.draw(batch, "Press R / Space to restart", uiViewport.worldWidth / 2f - 110f, uiViewport.worldHeight / 2f - 16f)
        }
        batch.end()
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }
}

