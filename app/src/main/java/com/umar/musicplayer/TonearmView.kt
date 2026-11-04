package com.umar.musicplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class TonearmView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null, d: Int = 0) : View(ctx, attrs, d) {
    private var playing = false
    private var progress = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(v: Float) = v * resources.displayMetrics.density
    fun setPlaying(v: Boolean) { playing = v; invalidate() }
    fun setProgress(p: Float) { progress = p.coerceIn(0f, 1f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = Math.min(width, height) / 2f - dp(10f)
        val pivotX = cx + r * 0.78f; val pivotY = cy - r * 0.92f
        val startA = Math.toRadians(78.0); val endA = Math.toRadians(118.0)
        val a = startA + (endA - startA) * progress
        var tipX = pivotX + cos(a) * r * 1.18f
        var tipY = pivotY + sin(a) * r * 1.18f
        if (!playing) tipY -= dp(26f)
        paint.color = 0xFFFFFFFF.toInt(); paint.strokeWidth = dp(3f); paint.style = Paint.Style.STROKE
        canvas.drawLine(pivotX, pivotY, tipX, tipY, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(pivotX, pivotY, dp(7f), paint)
        canvas.drawCircle(tipX, tipY, dp(9f), paint)
    }
}