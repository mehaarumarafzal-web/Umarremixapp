package com.umar.musicplayer

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

@UnstableApi
class PlaylistActivity : AppCompatActivity() {

    private var pid = -1L
    private lateinit var adapter: SongAdapter
    private var lastCoverId = -1L

    private val coverPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && lastCoverId > 0) ImageUtils.savePicked(this, uri)?.let { Store.setCover(lastCoverId, it); adapter.refresh() }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.init(this); Library.ensureLoaded(this); PlayerCtl.bind(this)
        pid = intent.getLongExtra("pid", -1L)
        setContentView(R.layout.activity_playlist)
        adapter = SongAdapter(false,
            { _, pos ->
                val list = songs()
                if (list.isNotEmpty()) {
                    PlayerCtl.playSongs(list, pos, false)
                    startActivity(Intent(this, PlayerActivity::class.java))
                }
            },
            { song -> SongMenu.show(this, song, false, { id -> lastCoverId = id; coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, { refresh() }) }
        )
        val rv = findViewById<RecyclerView>(R.id.recycler); rv.layoutManager = LinearLayoutManager(this); rv.adapter = adapter
        findViewById<AppCompatImageButton>(R.id.back).setOnClickListener { finish() }
        findViewById<AppCompatImageButton>(R.id.plMore).setOnClickListener { showMenu() }
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun songs(): List<Song> {
        val p = Store.playlists().find { it.id == pid } ?: return emptyList()
        return p.songIds.mapNotNull { Library.byId(it) }
    }
    private fun refresh() {
        val p = Store.playlists().find { it.id == pid }
        findViewById<TextView>(R.id.plTitle).text = p?.name ?: ""
        adapter.submit(songs())
    }
    private fun showMenu() {
        val opts = arrayOf(getString(R.string.add_songs), getString(R.string.rename), getString(R.string.delete))
        AlertDialog.Builder(this).setItems(opts) { _, i ->
            when (i) {
                0 -> addSongs()
                1 -> rename()
                2 -> { Store.deletePlaylist(pid); finish() }
            }
        }.show()
    }
    private fun addSongs() {
        val p = Store.playlists().find { it.id == pid } ?: return
        val pool = Library.songs.filter { !p.songIds.contains(it.id) }
        if (pool.isEmpty()) { Toast.makeText(this, R.string.no_songs, Toast.LENGTH_SHORT).show(); return }
        val names = pool.map { Store.titleFor(it.id, it.title) }.toTypedArray()
        val picked = BooleanArray(pool.size)
        AlertDialog.Builder(this).setTitle(R.string.add_songs)
            .setMultiChoiceItems(names, picked) { _, which, checked -> picked[which] = checked }
            .setPositiveButton(R.string.save) { _, _ ->
                pool.forEachIndexed { idx, sg -> if (picked[idx]) Store.addToPlaylist(pid, sg.id) }
                refresh()
            }.setNegativeButton(R.string.cancel, null).show()
    }
    private fun rename() {
        val cur = findViewById<TextView>(R.id.plTitle)
        val e = android.widget.EditText(this); e.setText(cur.text)
        AlertDialog.Builder(this).setTitle(R.string.rename).setView(e)
            .setPositiveButton(R.string.save) { _, _ -> Store.renamePlaylist(pid, e.text.toString().ifBlank { cur.text.toString() }); refresh() }
            .setNegativeButton(R.string.cancel, null).show()
    }
}