package com.umar.musicplayer

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog

object SongMenu {
    fun show(ctx: Context, song: Song, inQueue: Boolean,
             onRequestCover: (Long) -> Unit, onChanged: () -> Unit) {
        val d = BottomSheetDialog(ctx)
        val v = View.inflate(ctx, R.layout.sheet_song_menu, null)
        fun row(id: Int, click: () -> Unit) = v.findViewById<TextView>(id).setOnClickListener { d.dismiss(); click() }
        row(R.id.mAdd) { pickPlaylist(ctx, song.id, onChanged) }
        row(R.id.mTrim) { openTrimDialog(ctx, song) }
        row(R.id.mSleep) { pickSleep(ctx) }
        row(R.id.mSpeed) { pickSpeed(ctx) }
        row(R.id.mEdit) { editTitle(ctx, song, onChanged) }
        row(R.id.mCover) { d.dismiss(); onRequestCover(song.id) }
        row(R.id.mShare) { shareSong(ctx, song.id) }
        v.findViewById<TextView>(R.id.mRemove).visibility = if (inQueue) View.VISIBLE else View.GONE
        row(R.id.mRemove) { PlayerCtl.removeFromQueue(PlayerCtl.currentIndex()); onChanged() }
        d.setContentView(v); d.show()
    }

    fun openTrimDialog(ctx: Context, song: Song) {
        val durSec = (song.duration / 1000).toInt().coerceAtLeast(1)
        val startCur = (Store.trimStartFor(song.id) / 1000).toInt().coerceIn(0, durSec)
        val endStored = (Store.trimEndFor(song.id) / 1000).toInt()
        val endCur = if (endStored <= 0) durSec else endStored.coerceIn(0, durSec)
        val dpx = (16 * ctx.resources.displayMetrics.density).toInt()

        val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL
        val pad = (24 * ctx.resources.displayMetrics.density).toInt()
        col.setPadding(pad, pad / 2, pad, pad / 2)

        val ls = TextView(ctx); ls.text = "Start: " + fmt(startCur); ls.setTextColor(0xFFFFFFFF.toInt()); ls.textSize = 15f
        val ss = SeekBar(ctx); ss.max = durSec; ss.progress = startCur
        val le = TextView(ctx); le.text = "End: " + fmt(endCur); le.setTextColor(0xFFFFFFFF.toInt()); le.textSize = 15f
        le.setPadding(0, dpx, 0, 0)
        val se = SeekBar(ctx); se.max = durSec; se.progress = endCur
        val reset = TextView(ctx); reset.text = "Reset (poora song)"; reset.setTextColor(0xFF9E9E9E.toInt())
        reset.setPadding(0, dpx, 0, 0)

        ss.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                ls.text = "Start: " + fmt(p)
                if (p > se.progress) se.progress = p
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        se.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                le.text = "End: " + fmt(p)
                if (p < ss.progress) ss.progress = p
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        reset.setOnClickListener {
            ss.progress = 0; se.progress = durSec
            ls.text = "Start: " + fmt(0); le.text = "End: " + fmt(durSec)
        }

        col.addView(ls); col.addView(ss); col.addView(le); col.addView(se); col.addView(reset)

        AlertDialog.Builder(ctx).setTitle("Start / End time")
            .setView(col)
            .setPositiveButton(R.string.save) { _, _ ->
                val sV = ss.progress * 1000L
                val eV = if (se.progress >= durSec) 0L else se.progress * 1000L
                Store.setTrim(song.id, sV, eV)
                Toast.makeText(ctx, "Range: " + fmt(ss.progress) + " → " + fmt(se.progress), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    private fun fmt(s: Int) = "%d:%02d".format(s / 60, s % 60)

    fun openCoverEditor(ctx: Context, path: String, songId: Long, global: Boolean, done: () -> Unit) {
        val bmp = ImageUtils.loadSharp(path, 512, 512)
        if (bmp == null) { done(); return }
        val v = View.inflate(ctx, R.layout.dialog_cover_editor, null)
        val preview = v.findViewById<CoverPreviewView>(R.id.preview)
        val seek = v.findViewById<SeekBar>(R.id.zoomSeek)
        val cur = if (global) Store.globalZoom() else Store.zoomFor(songId)
        preview.setBitmap(bmp); preview.setZoom(cur)
        seek.progress = zoomToProgress(cur)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) preview.setZoom(progressToZoom(p)) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        AlertDialog.Builder(ctx).setTitle("Cover set karo")
            .setView(v)
            .setPositiveButton(R.string.save) { _, _ ->
                val z = preview.zoom()
                if (global) Store.setGlobalZoom(z) else Store.setZoom(songId, z)
                done()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    private fun zoomToProgress(z: Float) = (((z.coerceIn(0.5f, 3f)) - 0.5f) / 2.5f * 100).toInt()
    private fun progressToZoom(p: Int) = 0.5f + (p / 100f) * 2.5f

    fun pickSleep(ctx: Context) {
        val opts = arrayOf("Off", "5 min", "10 min", "15 min", "30 min", "60 min")
        val vals = intArrayOf(0, 5, 10, 15, 30, 60)
        AlertDialog.Builder(ctx).setTitle(R.string.sleep_timer).setItems(opts) { _, i -> PlayerCtl.setSleep(vals[i]) }.show()
    }

    fun pickSpeed(ctx: Context) {
        val opts = arrayOf("0.5x", "0.75x", "Normal", "1.25x", "1.5x", "1.75x", "2x")
        val vals = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        AlertDialog.Builder(ctx).setTitle(R.string.playback_speed).setItems(opts) { _, i -> PlayerCtl.setSpeed(vals[i]) }.show()
    }

    private fun pickPlaylist(ctx: Context, songId: Long, done: () -> Unit) {
        val pls = Store.playlists()
        val names = pls.map { it.name }.toMutableList(); names.add(ctx.getString(R.string.new_playlist))
        AlertDialog.Builder(ctx).setTitle(R.string.add_to_playlist).setItems(names.toTypedArray()) { _, i ->
            if (i < pls.size) { Store.addToPlaylist(pls[i].id, songId); toast(ctx, R.string.song_added); done() }
            else newPlaylistDialog(ctx) { pid -> Store.addToPlaylist(pid, songId); toast(ctx, R.string.song_added); done() }
        }.show()
    }

    private fun newPlaylistDialog(ctx: Context, created: (Long) -> Unit) {
        val e = EditText(ctx); e.inputType = InputType.TYPE_CLASS_TEXT; e.hint = ctx.getString(R.string.playlist_name)
        AlertDialog.Builder(ctx).setTitle(R.string.new_playlist).setView(e)
            .setPositiveButton(R.string.create) { _, _ -> created(Store.addPlaylist(e.text.toString().ifBlank { "Playlist" }).id); toast(ctx, R.string.playlist_created) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun editTitle(ctx: Context, song: Song, done: () -> Unit) {
        val e = EditText(ctx); e.setText(Store.titleFor(song.id, song.title)); e.inputType = InputType.TYPE_CLASS_TEXT
        AlertDialog.Builder(ctx).setTitle(R.string.edit_song_info).setView(e)
            .setPositiveButton(R.string.save) { _, _ -> Store.setTitle(song.id, e.text.toString().ifBlank { song.title }); done() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun shareSong(ctx: Context, id: Long) {
        val u = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, u), ctx.getString(R.string.share)))
    }

    private fun toast(ctx: Context, res: Int) = Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()
}