package com.umar.musicplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

@UnstableApi
class FavouritesActivity : AppCompatActivity() {

    private lateinit var adapter: SongAdapter
    private var lastCoverId = -1L

    private val coverPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && lastCoverId > 0) ImageUtils.savePicked(this, uri)?.let { p ->
            Store.setCover(lastCoverId, p)
            SongMenu.openCoverEditor(this, p, lastCoverId, false) { adapter.refresh() }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.init(this); Library.ensureLoaded(this); PlayerCtl.bind(this)
        setContentView(R.layout.activity_favourites)
        adapter = SongAdapter(false,
            { _, pos ->
                val list = favSongs()
                if (list.isNotEmpty()) {
                    PlayerCtl.playSongs(list, pos, false)
                    startActivity(Intent(this, PlayerActivity::class.java))
                }
            },
            { song -> SongMenu.show(this, song, false, { id -> lastCoverId = id; coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, { refresh() }) }
        )
        val rv = findViewById<RecyclerView>(R.id.recycler); rv.layoutManager = LinearLayoutManager(this); rv.adapter = adapter
        findViewById<AppCompatImageButton>(R.id.back).setOnClickListener { finish() }
        refresh()
    }
    override fun onResume() { super.onResume(); refresh() }

    private fun favSongs(): List<Song> {
        val favs = Store.favourites()
        return Library.songs.filter { favs.contains(it.id) }
    }
    private fun refresh() {
        val list = favSongs()
        findViewById<android.widget.TextView>(R.id.favTitle).text = "${list.size} songs"
        adapter.submit(list)
    }
}