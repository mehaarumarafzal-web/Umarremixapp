package com.umar.musicplayer

import android.content.Context
import android.provider.MediaStore

object SongScanner {
    fun scan(context: Context): List<Song> {
        val out = ArrayList<Song>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                proj,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iT = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iA = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iD = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val t = c.getString(iT) ?: ""
                    if (t.isNotBlank()) {
                        out.add(Song(c.getLong(iId), t, c.getString(iA) ?: "", c.getLong(iD)))
                    }
                }
            }
        } catch (_: Exception) {}
        return out
    }
}