package com.umar.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class CoverPreviewView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null, d: Int = 0) : View(ctx, attrs, d) {
    private var bmp: Bitmap? = null
    private var zoom = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setBitmap(b: Bitmap?) { bmp = b; invalidate() }
    fun setZoom(z: Float) { zoom = z.coerceIn(0.5f, 3f); invalidate() }
    fun zoom() = zoom

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - 4f
        paint.style = Paint.Style.FILL; paint.color = 0xFF121212.toInt()
        c.drawCircle(cx, cy, r, paint)
        val b = bmp
        if (b != null) {
            val sq = ImageUtils.prepareWithZoom(b, zoom)
            val d = (r * 2).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(sq, d, d, true)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r * 0.86f, p)
        }
        paint.shader = null
        paint.style = Paint.Style.STROKE; paint.color = 0xFF3A3A3A.toInt(); paint.strokeWidth = 8f
        c.drawCircle(cx, cy, r, paint)
    }
}