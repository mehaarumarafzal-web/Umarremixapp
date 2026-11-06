package com.umar.musicplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("umar", Context.MODE_PRIVATE)
    }

    fun favourites(): MutableSet<Long> {
        val s = HashSet<Long>()
        JSONArray(sp.getString("fav", "[]")).let { a -> for (i in 0 until a.length()) s.add(a.getLong(i)) }
        return s
    }
    fun isFav(id: Long) = favourites().contains(id)
    fun toggleFav(id: Long) {
        val s = favourites(); if (!s.remove(id)) s.add(id)
        sp.edit().putString("fav", JSONArray(s.toList()).toString()).apply()
    }

    fun playlists(): MutableList<Playlist> {
        val list = ArrayList<Playlist>()
        JSONArray(sp.getString("pls", "[]")).let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val ids = ArrayList<Long>()
                o.getJSONArray("ids").let { ia -> for (j in 0 until ia.length()) ids.add(ia.getLong(j)) }
                list.add(Playlist(o.getLong("id"), o.getString("name"), ids))
            }
        }
        return list
    }
    private fun savePls(list: List<Playlist>) {
        val a = JSONArray()
        list.forEach { p ->
            val o = JSONObject(); o.put("id", p.id); o.put("name", p.name)
            val ia = JSONArray(); p.songIds.forEach { ia.put(it) }; o.put("ids", ia); a.put(o)
        }
        sp.edit().putString("pls", a.toString()).apply()
    }
    fun addPlaylist(name: String): Playlist {
        val list = playlists(); val p = Playlist(System.currentTimeMillis(), name)
        list.add(p); savePls(list); return p
    }
    fun renamePlaylist(id: Long, name: String) {
        val list = playlists(); list.find { it.id == id }?.let { it.name = name }; savePls(list)
    }
    fun deletePlaylist(id: Long) {
        val list = playlists(); list.removeAll { it.id == id }; savePls(list)
    }
    fun addToPlaylist(id: Long, songId: Long) {
        val list = playlists(); list.find { it.id == id }?.let { if (!it.songIds.contains(songId)) it.songIds.add(songId) }; savePls(list)
    }
    fun removeFromPlaylist(id: Long, songId: Long) {
        val list = playlists(); list.find { it.id == id }?.let { it.songIds.remove(songId) }; savePls(list)
    }

    fun library(): List<Song>? {
        val raw = sp.getString("lib", null) ?: return null
        val out = ArrayList<Song>()
        try {
            JSONArray(raw).let { a -> for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Song(o.getLong("id"), o.getString("t"), o.getString("a"), o.getLong("d")))
            } }
        } catch (_: Exception) { return null }
        return out
    }
    fun saveLibrary(list: List<Song>) {
        val a = JSONArray()
        list.forEach { val o = JSONObject(); o.put("id", it.id); o.put("t", it.title); o.put("a", it.artist); o.put("d", it.duration); a.put(o) }
        sp.edit().putString("lib", a.toString()).apply()
    }

    fun bgPath(): String? = sp.getString("bg", null)
    fun setBgPath(p: String?) = sp.edit().putString("bg", p).apply()

    fun globalCover(): String? = sp.getString("gcov", null)
    fun setGlobalCover(p: String?) = sp.edit().putString("gcov", p).apply()
    fun globalZoom(): Float = sp.getFloat("gz", 1f)
    fun setGlobalZoom(z: Float) = sp.edit().putFloat("gz", z).apply()

    fun zoomFor(id: Long): Float = sp.getFloat("z_$id", 1f)
    fun setZoom(id: Long, z: Float) = sp.edit().putFloat("z_$id", z).apply()

    fun rewFwdSize(): Int = sp.getInt("rfsize2", 72)
    fun setRewFwdSize(v: Int) = sp.edit().putInt("rfsize2", v).apply()

    fun trimStartFor(id: Long): Long = sp.getLong("ts_$id", 0L)
    fun trimEndFor(id: Long): Long = sp.getLong("te_$id", 0L)
    fun setTrim(id: Long, startMs: Long, endMs: Long) = sp.edit().putLong("ts_$id", startMs).putLong("te_$id", endMs).apply()

    fun voiceMode(): Int = sp.getInt("vmode", 0)
    fun setVoiceMode(m: Int) = sp.edit().putInt("vmode", m).apply()

    fun coverFor(id: Long): String? = sp.getString("cov_$id", null)
    fun setCover(id: Long, p: String?) = sp.edit().putString("cov_$id", p).apply()
    fun titleFor(id: Long, def: String): String = sp.getString("tit_$id", null) ?: def
    fun setTitle(id: Long, t: String) = sp.edit().putString("tit_$id", t).apply()
}