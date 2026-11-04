package com.umar.musicplayer

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class SongAdapter(
    private val numbered: Boolean,
    private val onSongClick: (Song, Int) -> Unit,
    private val onMoreClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.Holder>() {

    private var fullList: List<Song> = emptyList()
    private var shownList: MutableList<Song> = mutableListOf()

    private val palette = listOf(
        intArrayOf(0xFFFF6B9D.toInt(), 0xFFC44FE2.toInt()),
        intArrayOf(0xFFFF8A5B.toInt(), 0xFFFF3D6E.toInt()),
        intArrayOf(0xFF7C4DFF.toInt(), 0xFF448AFF.toInt()),
        intArrayOf(0xFF00C9A7.toInt(), 0xFF1B998B.toInt()),
        intArrayOf(0xFFFFD200.toInt(), 0xFFFF6D00.toInt()),
        intArrayOf(0xFF43E97B.toInt(), 0xFF38F9D7.toInt())
    )

    fun submit(list: List<Song>) { fullList = list; shownList = list.toMutableList(); notifyDataSetChanged() }
    fun filter(q: String) {
        val s = q.trim().lowercase()
        shownList = if (s.isEmpty()) fullList.toMutableList()
        else fullList.filter { Store.titleFor(it.id, it.title).lowercase().contains(s) || it.artist.lowercase().contains(s) }.toMutableList()
        notifyDataSetChanged()
    }
    fun shuffle() { Collections.shuffle(shownList); notifyDataSetChanged() }
    fun refresh() { notifyDataSetChanged() }
    fun shownSnapshot(): List<Song> = shownList.toList()

    override fun getItemCount() = shownList.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return Holder(v)
    }
    override fun onBindViewHolder(h: Holder, position: Int) {
        val ctx = h.itemView.context
        val song = shownList[position]
        h.title.text = Store.titleFor(song.id, song.title)
        h.artist.text = song.artist.ifBlank { ctx.getString(R.string.unknown_artist) }
        h.num.visibility = if (numbered) View.VISIBLE else View.GONE
        if (numbered) h.num.text = (position + 1).toString()
        h.thumb.background = makeGradient(ctx, song.title.hashCode())
        h.itemView.setOnClickListener { onSongClick(song, position) }
        h.more.setOnClickListener { onMoreClick(song) }
    }
    private fun makeGradient(ctx: Context, seed: Int): GradientDrawable {
        val c = palette[Math.floorMod(seed, palette.size)]
        val d = GradientDrawable(GradientDrawable.Orientation.TOP_LEFT_TO_BOTTOM_RIGHT, c)
        d.cornerRadius = 14f * ctx.resources.displayMetrics.density
        return d
    }
    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val num: TextView = v.findViewById(R.id.num)
        val thumb: View = v.findViewById(R.id.thumb)
        val title: TextView = v.findViewById(R.id.title)
        val artist: TextView = v.findViewById(R.id.artist)
        val more: ImageView = v.findViewById(R.id.more)
    }
}