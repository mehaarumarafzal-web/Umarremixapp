package com.umar.musicplayer

import android.content.Context

object Library {
    var songs: List<Song> = emptyList()
        private set

    /** Pehli baar scan + save; agli baar seedha cached list (koi dobara scan nahi). */
    fun ensureLoaded(ctx: Context) {
        Store.init(ctx)
        val cached = Store.library()
        songs = if (cached != null && cached.isNotEmpty()) cached
        else {
            val s = SongScanner.scan(ctx)
            Store.saveLibrary(s)
            s
        }
    }

    fun byId(id: Long): Song? = songs.find { it.id == id }
    fun titles(ids: List<Long>): List<Song> = ids.mapNotNull { byId(it) }
}