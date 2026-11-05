package com.umar.musicplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class TonearmView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null, d: Int = 0) : View(ctx, attrs, d) {

    private var playing = false
    private var progress = 0f
    private var current = REST
    private var target = REST
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val h = Handler(Looper.getMainLooper())

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()
    private fun playAngle(p: Float) = 96f + 16f * p
    private companion object { const val REST = 78f }

    fun setPlaying(v: Boolean) {
        playing = v
        target = if (v) playAngle(progress) else REST
        glide()
    }
    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        if (playing) { target = playAngle(progress); glide() }
        invalidate()
    }

    private val tick = object : Runnable {
        override fun run() {
            val d = target - current
            if (abs(d) > 0.08f) {
                current += d * 0.16f
                invalidate()
                h.postDelayed(this, 16)
            } else {
                current = target
                invalidate()
            }
        }
    }
    private fun glide() { h.removeCallbacks(tick); h.post(tick) }
    override fun onDetachedFromWindow() { h.removeCallbacks(tick); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - dp(10f)
        val pivotX = cx + r * 0.82f
        val pivotY = cy - r * 0.98f
        val len = r * 1.45f
        val a = rad(current)
        val bendA = a - rad(9f)
        val elbowX = pivotX + cos(bendA) * len * 0.55f
        val elbowY = pivotY + sin(bendA) * len * 0.55f
        val tipX = pivotX + cos(a) * len
        val tipY = pivotY + sin(a) * len
        val headX = tipX + cos(a + rad(16f)) * len * 0.10f
        val headY = tipY + sin(a + rad(16f)) * len * 0.10f

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xFFFFFFFF.toInt()
        paint.strokeWidth = dp(4f)
        canvas.drawLine(pivotX, pivotY, elbowX, elbowY, paint)
        canvas.drawLine(elbowX, elbowY, tipX, tipY, paint)
        paint.strokeWidth = dp(11f)
        canvas.drawLine(tipX, tipY, headX, headY, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(pivotX, pivotY, dp(10f), paint)
        paint.color = 0xFFDDDDDD.toInt()
        canvas.drawCircle(pivotX, pivotY, dp(4.5f), paint)
    }
}