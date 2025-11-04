package com.example.pajaroprueba

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.max
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), Runnable {

    private val threadLock = Object()
    private var running = false
    private var gameThread: Thread? = null

    private val holder: SurfaceHolder = holder
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Screen
    private var screenW = 1
    private var screenH = 1

    // Bird
    private var birdX = 200f
    private var birdY = 300f
    private var birdRadius = 30f
    private var velocity = 0f
    private val gravity = 0.9f
    private val flapStrength = -18f

    // Pipes
    private data class Pipe(var x: Float, val topHeight: Float, val gap: Float, var passed: Boolean = false)
    private val pipes = mutableListOf<Pipe>()
    private var pipeSpawnTimer = 0
    private val pipeInterval = 120 // frames
    private val pipeSpeed = 6f
    private var baseGap = 350f

    // Score & state
    private var score = 0
    private var gameOver = false

    // Paint presets
    private val bgColor = Color.rgb(135, 206, 235) // sky
    private val groundColor = Color.rgb(87, 59, 12)

    init {
        paint.textSize = 64f
        paint.typeface = Typeface.DEFAULT_BOLD
        isFocusable = true
    }

    override fun run() {
        var lastTime = System.currentTimeMillis()
        while (running) {
            if (!holder.surface.isValid) continue

            val now = System.currentTimeMillis()
            val delta = (now - lastTime).coerceAtMost(50)
            lastTime = now

            update()
            draw()

            // Basic frame limiter ~60fps
            try {
                Thread.sleep(16)
            } catch (e: InterruptedException) { }
        }
    }

    private fun update() {
        if (screenW <= 1 || screenH <= 1) return

        if (!gameOver) {
            // Bird physics
            velocity += gravity
            birdY += velocity

            // Pipes movement
            val it = pipes.iterator()
            while (it.hasNext()) {
                val p = it.next()
                p.x -= pipeSpeed
                // score when passed
                if (!p.passed && p.x + 100 < birdX) {
                    p.passed = true
                    score += 1
                }
                // remove off-screen
                if (p.x + 200 < 0) it.remove()
            }

            // Spawn new pipes occasionally
            pipeSpawnTimer++
            if (pipeSpawnTimer >= pipeInterval) {
                spawnPipe()
                pipeSpawnTimer = 0
            }

            // Collision detection with ground and ceiling
            if (birdY + birdRadius > screenH - 120) { // ground height 120
                birdY = (screenH - 120) - birdRadius
                gameOver = true
            }
            if (birdY - birdRadius < 0) {
                birdY = birdRadius
                velocity = 0f
            }

            // Collision with pipes (approx circle-rect)
            for (p in pipes) {
                val pipeWidth = 140f
                // top rect
                val topRect = RectF(p.x, 0f, p.x + pipeWidth, p.topHeight)
                val bottomRect = RectF(p.x, p.topHeight + p.gap, p.x + pipeWidth, screenH - 120f)
                if (circleRectCollision(birdX, birdY, birdRadius, topRect) ||
                    circleRectCollision(birdX, birdY, birdRadius, bottomRect)) {
                    gameOver = true
                }
            }
        } else {
            // gentle drop when game over
            if (birdY + birdRadius < screenH - 120) {
                velocity += gravity
                birdY += velocity
            }
        }
    }

    private fun spawnPipe() {
        val topMin = 100
        val topMax = (screenH - 120 - baseGap - 200).toInt().coerceAtLeast(150)
        val topH = Random.nextInt(topMin, max(topMin, topMax))
        val gap = baseGap + Random.nextInt(-80, 80)
        val startX = screenW + 200f
        pipes.add(Pipe(startX, topH.toFloat(), gap.toFloat()))
    }

    private fun circleRectCollision(cx: Float, cy: Float, r: Float, rect: RectF): Boolean {
        // Find closest point on the rect to the circle center
        val closestX = cx.coerceIn(rect.left, rect.right)
        val closestY = cy.coerceIn(rect.top, rect.bottom)
        val dx = cx - closestX
        val dy = cy - closestY
        return (dx*dx + dy*dy) <= r*r
    }

    private fun draw() {
        val canvas = holder.lockCanvas()
        if (canvas == null) return

        // Update screen dims first time
        screenW = canvas.width
        screenH = canvas.height

        // Background
        canvas.drawColor(bgColor)

        // Draw moving ground
        paint.color = groundColor
        val groundTop = screenH - 120f
        canvas.drawRect(0f, groundTop, screenW.toFloat(), screenH.toFloat(), paint)

        // Draw pipes
        paint.color = Color.rgb(34, 139, 34) // green
        for (p in pipes) {
            val pipeWidth = 140f
            val left = p.x
            val right = p.x + pipeWidth
            // top
            canvas.drawRect(left, 0f, right, p.topHeight, paint)
            // bottom
            canvas.drawRect(left, p.topHeight + p.gap, right, groundTop, paint)
        }

        // Draw bird (simple circle with eye)
        paint.color = Color.rgb(255, 200, 0)
        canvas.drawCircle(birdX, birdY, birdRadius, paint)
        // eye
        paint.color = Color.BLACK
        canvas.drawCircle(birdX + birdRadius/3, birdY - birdRadius/3, birdRadius/6, paint)

        // Score
        paint.color = Color.WHITE
        paint.textSize = 72f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("$score", screenW / 2f, 120f, paint)

        // Game over text
        if (gameOver) {
            paint.textSize = 64f
            paint.color = Color.RED
            canvas.drawText("¡Game Over!", screenW / 2f, screenH / 2f - 40f, paint)
            paint.textSize = 40f
            paint.color = Color.WHITE
            canvas.drawText("Toca para reiniciar", screenW / 2f, screenH / 2f + 20f, paint)
        }

        holder.unlockCanvasAndPost(canvas)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            if (!gameOver) {
                velocity = flapStrength
            } else {
                // reset game
                reset()
            }
        }
        return true
    }

    private fun reset() {
        birdX = 200f
        birdY = screenH / 2f
        velocity = 0f
        pipes.clear()
        pipeSpawnTimer = 0
        score = 0
        gameOver = false
    }

    fun pause() {
        running = false
        synchronized(threadLock) {
            try {
                gameThread?.join()
            } catch (e: InterruptedException) { }
        }
    }

    fun resume() {
        running = true
        gameThread = Thread(this)
        gameThread?.start()
    }
}
