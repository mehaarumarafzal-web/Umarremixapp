package com.umar.musicplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.material.bottomsheet.BottomSheetDialog

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private lateinit var bg: ImageView
    private lateinit var disc: VinylDiscView
    private lateinit var arm: TonearmView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var star: ImageButton
    private lateinit var seek: SeekBar
    private lateinit var cur: TextView
    private lateinit var tot: TextView
    private lateinit var playBtn: ImageButton
    private lateinit var repBtn: ImageButton
    private var lastId = -1L
    private val tick = Handler(Looper.getMainLooper())
    private val artJob = Handler(Looper.getMainLooper())

    private val coverPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && lastId > 0) ImageUtils.savePicked(this, uri)?.let { p ->
            Store.setCover(lastId, p)
            SongMenu.openCoverEditor(this, p, lastId, false) { loadArt(lastId) }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.init(this); Library.ensureLoaded(this); PlayerCtl.bind(this)
        setContentView(R.layout.activity_player)
        bg = findViewById(R.id.bg); disc = findViewById(R.id.disc); arm = findViewById(R.id.arm)
        title = findViewById(R.id.title); artist = findViewById(R.id.artist); star = findViewById(R.id.star)
        seek = findViewById(R.id.seek); cur = findViewById(R.id.cur); tot = findViewById(R.id.tot)
        playBtn = findViewById(R.id.play); repBtn = findViewById(R.id.repeat)

        applyRewFwdSize()
        loadBg()
        findViewById<ImageButton>(R.id.expand).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.theme).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<ImageButton>(R.id.menu).setOnClickListener { showPlayerMenu() }
        findViewById<TextView>(R.id.fwdBtn).setOnClickListener { PlayerCtl.skip(10_000); softRefresh() }
        findViewById<TextView>(R.id.rewBtn).setOnClickListener { PlayerCtl.skip(-10_000); softRefresh() }
        playBtn.setOnClickListener { PlayerCtl.toggle() }
        findViewById<ImageButton>(R.id.next).setOnClickListener { PlayerCtl.next() }
        findViewById<ImageButton>(R.id.prev).setOnClickListener { PlayerCtl.prev() }
        repBtn.setOnClickListener { PlayerCtl.cycleRepeat() }
        findViewById<ImageButton>(R.id.queue).setOnClickListener { showQueue() }
        star.setOnClickListener { PlayerCtl.currentSong()?.let { Store.toggleFav(it.id); refresh() } }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) cur.text = fmt(p.toLong()) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { PlayerCtl.seekTo(seek.progress.toLong()); softRefresh() }
        })
        PlayerCtl.onChange = { refresh() }
        refresh()
    }

    override fun onResume() { super.onResume(); tick.postDelayed(pump, 400); loadBg(); applyRewFwdSize() }
    override fun onPause() { super.onPause(); tick.removeCallbacks(pump) }
    override fun onDestroy() { super.onDestroy() }

    private val pump = object : Runnable {
        override fun run() { softRefresh(); tick.postDelayed(this, 500) }
    }

    private fun applyRewFwdSize() {
        val dpv = Store.rewFwdSize()
        val px = dp(dpv)
        for (id in intArrayOf(R.id.rewBtn, R.id.fwdBtn)) {
            val v = findViewById<TextView>(id)
            v.textSize = dpv * 0.25f
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.width = px; lp.height = px; v.layoutParams = lp
        }
    }

    private fun loadBg() {
        bg.post {
            val w = bg.width; val h = bg.height
            if (w == 0 || h == 0) return@post
            val path = Store.bgPath()
            val bmp = if (path != null) ImageUtils.loadBlurred(path, w, h) else ImageUtils.defaultBlurred(this, w, h)
            if (bmp != null) bg.setImageBitmap(bmp)
        }
    }

    private fun refresh() {
        val song = PlayerCtl.currentSong()
        title.text = if (song != null) Store.titleFor(song.id, song.title) else getString(R.string.empty_queue)
        artist.text = song?.artist?.ifBlank { getString(R.string.unknown_artist) } ?: ""
        val playing = PlayerCtl.isPlaying()
        playBtn.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        disc.setPlaying(playing); arm.setPlaying(playing)
        repBtn.setImageResource(if (PlayerCtl.repeatMode() == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
        repBtn.alpha = if (PlayerCtl.repeatMode() == Player.REPEAT_MODE_OFF) 0.5f else 1f
        if (song != null) {
            star.setImageResource(if (Store.isFav(song.id)) R.drawable.ic_star_filled else R.drawable.ic_star)
            if (song.id != lastId) { lastId = song.id; loadArt(song.id) }
        }
        softRefresh()
    }

    private fun softRefresh() {
        val pos = PlayerCtl.position(); val dur = PlayerCtl.duration()
        seek.max = dur.toInt().coerceAtLeast(1)
        if (!seek.isPressed) seek.progress = pos.toInt()
        cur.text = fmt(pos); tot.text = fmt(dur)
        arm.setProgress(PlayerCtl.progress())
    }

    private fun loadArt(id: Long) {
        artJob.post {
            val own = Store.coverFor(id)
            val custom = own ?: Store.globalCover()
            val z = if (own != null) Store.zoomFor(id) else Store.globalZoom()
            val bmp = if (custom != null)
                ImageUtils.loadSharp(custom, 512, 512)?.let { ImageUtils.prepareWithZoom(it, z) }
            else
                ImageUtils.embeddedArt(this, id)?.let { ImageUtils.prepareWithZoom(it, 0.98f) }
            disc.setArt(bmp)
        }
    }

    private fun showPlayerMenu() {
        val d = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_player_menu, null)
        v.findViewById<TextView>(R.id.pSpeed).setOnClickListener { d.dismiss(); SongMenu.pickSpeed(this) }
        v.findViewById<TextView>(R.id.pSleep).setOnClickListener { d.dismiss(); SongMenu.pickSleep(this) }
        v.findViewById<TextView>(R.id.pCover).setOnClickListener { d.dismiss(); PlayerCtl.currentSong()?.let { lastId = it.id; coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
        v.findViewById<TextView>(R.id.pShare).setOnClickListener { d.dismiss(); PlayerCtl.currentSong()?.let { shareIt(it.id) } }
        d.setContentView(v); d.show()
    }

    private fun shareIt(id: Long) {
        val u = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, u), getString(R.string.share)))
    }

    private fun showQueue() {
        val d = BottomSheetDialog(this)
        val scroll = android.widget.ScrollView(this)
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setPadding(dp(10), dp(10), dp(10), dp(10))
        val n = PlayerCtl.queueSize(); val c = PlayerCtl.currentIndex()
        if (n == 0) { val t = TextView(this); t.text = getString(R.string.empty_queue); t.setTextColor(0xFF9E9E9E.toInt()); col.addView(t) }
        for (i in 0 until n) {
            val tv = TextView(this); tv.text = PlayerCtl.titleAt(i).ifBlank { "Song ${i + 1}" }
            tv.setTextColor(if (i == c) 0xFF7C4DFF.toInt() else 0xFFFFFFFF.toInt()); tv.textSize = 16f
            tv.setPadding(dp(12), dp(14), dp(12), dp(14)); tv.setBackgroundResource(R.drawable.bg_pill)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = dp(6); tv.layoutParams = lp
            col.addView(tv)
        }
        scroll.addView(col); d.setContentView(scroll); d.show()
    }

    private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}