package com.umar.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    fun savePicked(ctx: Context, uri: Uri): String? = try {
        val f = File(ctx.filesDir, "img_${System.currentTimeMillis()}.dat")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(f).use { out -> input.copyTo(out) }
        }
        f.absolutePath
    } catch (_: Exception) { null }

    fun loadSharp(path: String, w: Int, h: Int): Bitmap? = decodeFile(path, w, h)
    fun loadBlurred(path: String, w: Int, h: Int): Bitmap? {
        val s = decodeFile(path, (w / 6).coerceAtLeast(1), (h / 6).coerceAtLeast(1)) ?: return null
        return Bitmap.createScaledBitmap(s, w, h, true)
    }
    fun defaultSharp(ctx: Context, w: Int, h: Int): Bitmap? = decodeRes(ctx, w, h)
    fun defaultBlurred(ctx: Context, w: Int, h: Int): Bitmap? {
        val s = decodeRes(ctx, (w / 6).coerceAtLeast(1), (h / 6).coerceAtLeast(1)) ?: return null
        return Bitmap.createScaledBitmap(s, w, h, true)
    }

    fun centerCropSquare(b: Bitmap): Bitmap {
        val s = minOf(b.width, b.height)
        if (s == b.width && s == b.height) return b
        val x = (b.width - s) / 2; val y = (b.height - s) / 2
        return Bitmap.createBitmap(b, x, y, s, s)
    }

    /** mode: 0 = poora fit (letterbox), 1 = center fill (crop), 2 = zoom 1.5x */
    fun prepareSquare(b: Bitmap, mode: Int): Bitmap = when (mode) {
        0 -> letterboxSquare(b)
        2 -> zoomCrop(centerCropSquare(b), 1.5f)
        else -> centerCropSquare(b)
    }

    private fun zoomCrop(c: Bitmap, z: Float): Bitmap {
        val s = (c.width / z).toInt().coerceAtLeast(1)
        val off = (c.width - s) / 2
        return Bitmap.createBitmap(c, off, off, s, s)
    }

    private fun letterboxSquare(b: Bitmap): Bitmap {
        val s = maxOf(b.width, b.height)
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.drawColor(0xFF000000.toInt())
        cv.drawBitmap(b, (s - b.width) / 2f, (s - b.height) / 2f, null)
        return out
    }

    private fun decodeFile(path: String, rw: Int, rh: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (o.outWidth / s > rw * 2 && o.outHeight / s > rh * 2) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (_: Exception) { null }

    private fun decodeRes(ctx: Context, rw: Int, rh: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(ctx.resources, R.drawable.home_bg, o)
        var s = 1
        while (o.outWidth / s > rw * 2 && o.outHeight / s > rh * 2) s *= 2
        BitmapFactory.decodeResource(ctx.resources, R.drawable.home_bg, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (_: Exception) { null }

    fun embeddedArt(ctx: Context, songId: Long): Bitmap? = try {
        val uri = android.content.ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        val r = MediaMetadataRetriever(); r.setDataSource(ctx, uri)
        val b = r.embeddedPicture; r.release()
        if (b != null) BitmapFactory.decodeByteArray(b, 0, b.size) else null
    } catch (_: Exception) { null }
}