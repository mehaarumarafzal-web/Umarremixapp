package com.umar.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: SongAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var search: EditText
    private lateinit var bg: ImageView
    private lateinit var miniRoot: View
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: ImageButton
    private lateinit var miniThumb: ImageView
    private lateinit var favCount: TextView
    private lateinit var plCount: TextView
    private var lastCoverId = -1L
    private val artJob = Handler(Looper.getMainLooper())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadAndShow()
            else Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
        }

    private val coverPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null && lastCoverId > 0)
                ImageUtils.savePicked(this, uri)?.let { p ->
                    Store.setCover(lastCoverId, p)
                    SongMenu.askCoverMode(this) { adapter.refresh() }
                }
        }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.init(this)
        setContentView(R.layout.activity_main)

        bg = findViewById(R.id.bg)
        search = findViewById(R.id.search)
        recycler = findViewById(R.id.recycler)
        favCount = findViewById(R.id.favCount)
        plCount = findViewById(R.id.plCount)
        miniRoot = findViewById(R.id.miniRoot)
        miniTitle = findViewById(R.id.miniTitle)
        miniArtist = findViewById(R.id.miniArtist)
        miniPlay = findViewById(R.id.miniPlay)
        miniThumb = findViewById(R.id.miniThumb)

        adapter = SongAdapter(
            numbered = false,
            onSongClick = { _, pos -> playFromList(pos) },
            onMoreClick = { song ->
                SongMenu.show(this, song, false,
                    { id -> lastCoverId = id; coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    { adapter.refresh(); updateCounts() })
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<ImageButton>(R.id.gear).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.favCard).setOnClickListener {
            startActivity(Intent(this, FavouritesActivity::class.java))
        }
        findViewById<View>(R.id.plCard).setOnClickListener { showPlaylistsChooser() }
        findViewById<LinearLayout>(R.id.shuffleRow).setOnClickListener {
            Library.ensureLoaded(this)
            if (Library.songs.isEmpty()) return@setOnClickListener
            PlayerCtl.playSongs(Library.songs, 0, true)
            startActivity(Intent(this, PlayerActivity::class.java))
        }
        search.addTextChangedListener { adapter.filter(it?.toString().orEmpty()) }

        miniRoot.setOnClickListener { startActivity(Intent(this, PlayerActivity::class.java)) }
        miniPlay.setOnClickListener { PlayerCtl.toggle() }
        findViewById<ImageButton>(R.id.miniNext).setOnClickListener { PlayerCtl.next() }

        PlayerCtl.bind(this)
        PlayerCtl.onChange = { updateMini(); adapter.refresh() }

        loadHomeBg()
        ensurePermissionThenLoad()
    }

    override fun onResume() {
        super.onResume()
        Library.ensureLoaded(this)
        adapter.submit(Library.songs)
        updateCounts()
        updateMini()
        loadHomeBg()
    }

    override fun onDestroy() { PlayerCtl.unbind(); super.onDestroy() }

    private fun ensurePermissionThenLoad() {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) loadAndShow()
        else permissionLauncher.launch(perm)
    }

    private fun loadAndShow() {
        Thread {
            Library.ensureLoaded(this)
            runOnUiThread { adapter.submit(Library.songs); updateCounts() }
        }.start()
    }

    private fun playFromList(pos: Int) {
        val list = adapter.shownSnapshot()
        if (list.isEmpty()) return
        PlayerCtl.playSongs(list, pos, false)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun showPlaylistsChooser() {
        val pls = Store.playlists()
        if (pls.isEmpty()) { Toast.makeText(this, R.string.empty_playlist, Toast.LENGTH_SHORT).show(); return }
        val names = pls.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.playlists).setItems(names) { _, i ->
            val intent = Intent(this, PlaylistActivity::class.java)
            intent.putExtra("pid", pls[i].id)
            startActivity(intent)
        }.show()
    }

    private fun updateCounts() {
        val favs = Store.favourites().count { Library.byId(it) != null }
        favCount.text = "$favs songs"
        plCount.text = "${Store.playlists().size} playlists"
    }

    private fun updateMini() {
        val song = PlayerCtl.currentSong()
        if (song == null) { miniRoot.visibility = View.GONE; return }
        miniRoot.visibility = View.VISIBLE
        miniTitle.text = Store.titleFor(song.id, song.title)
        miniArtist.text = song.artist.ifBlank { getString(R.string.unknown_artist) }
        miniPlay.setImageResource(if (PlayerCtl.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play)
        val id = song.id
        artJob.post {
            val custom = Store.coverFor(id) ?: Store.globalCover()
            val bmp = if (custom != null) ImageUtils.loadSharp(custom, 96, 96)?.let { ImageUtils.centerCropSquare(it) } else ImageUtils.embeddedArt(this, id)
            if (bmp != null) miniThumb.setImageBitmap(bmp)
        }
    }

    private fun loadHomeBg() {
        bg.post {
            val w = bg.width; val h = bg.height
            if (w == 0 || h == 0) return@post
            val path = Store.bgPath()
            val bmp = if (path != null) ImageUtils.loadSharp(path, w, h) else ImageUtils.defaultSharp(this, w, h)
            if (bmp != null) bg.setImageBitmap(bmp)
        }
    }
}