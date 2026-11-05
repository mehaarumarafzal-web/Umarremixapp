package com.umar.musicplayer

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

@UnstableApi
object PlayerCtl {
    private var controller: MediaController? = null
    private var bound = false
    var onChange: (() -> Unit)? = null
    private val sleepHandler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null
    var sleepMinutes = 0
    private val trimH = Handler(Looper.getMainLooper())

    fun bind(ctx: Context) {
        if (bound) return
        bound = true
        val app = ctx.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val fut = MediaController.Builder(app, token).buildAsync()
        fut.addListener({
            try {
                controller = fut.get()
                controller?.let { attach(it) }
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(app))
    }

    private fun attach(c: MediaController) {
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { onChange?.invoke() }
            override fun onIsPlayingChanged(isPlaying: Boolean) { onChange?.invoke() }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                onChange?.invoke()
                val s = currentSong()
                if (s != null) { val st = Store.trimStartFor(s.id); if (st > 0) p()?.seekTo(st) }
            }
            override fun onPlaybackStateChanged(state: Int) { onChange?.invoke() }
        })
        trimH.removeCallbacks(trimTick)
        trimH.postDelayed(trimTick, 700)
        onChange?.invoke()
    }

    private val trimTick = object : Runnable {
        override fun run() {
            enforceEnd()
            trimH.postDelayed(this, 700)
        }
    }

    private fun enforceEnd() {
        val s = currentSong() ?: return
        val en = Store.trimEndFor(s.id)
        if (en > 0 && isPlaying() && position() >= en) next()
    }

    fun unbind() {
        trimH.removeCallbacks(trimTick)
        controller?.release(); controller = null; bound = false
    }

    private fun p() = controller
    private fun uri(id: Long) = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    private fun item(s: Song) = MediaItem.Builder()
        .setMediaId(s.id.toString()).setUri(uri(s.id))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).build())
        .build()

    fun playSongs(songs: List<Song>, start: Int, shuffle: Boolean) {
        val pl = p() ?: return
        if (songs.isEmpty()) return
        val idx = start.coerceIn(0, songs.size - 1)
        pl.setMediaItems(songs.map { item(it) }, idx, 0)
        pl.shuffleModeEnabled = shuffle
        pl.prepare(); pl.play()
        val st = Store.trimStartFor(songs[idx].id)
        if (st > 0) pl.seekTo(st)
    }
    fun toggle() { val pl = p() ?: return; if (pl.isPlaying) pl.pause() else pl.play() }
    fun next() { p()?.seekToNextMediaItem() }
    fun prev() { val pl = p() ?: return; if (pl.currentPosition > 3000) pl.seekTo(0) else pl.seekToPreviousMediaItem() }
    fun skip(d: Long) { val pl = p() ?: return; pl.seekTo((pl.currentPosition + d).coerceIn(0, pl.duration.coerceAtLeast(0))) }
    fun seekTo(pos: Long) { p()?.seekTo(pos) }
    fun isPlaying() = p()?.isPlaying == true
    fun position() = p()?.currentPosition ?: 0L
    fun duration() = (p()?.duration ?: 0L).coerceAtLeast(0L)
    fun progress(): Float { val d = duration(); return if (d <= 0) 0f else position().toFloat() / d }
    fun currentIndex() = p()?.currentMediaItemIndex ?: 0
    fun queueSize() = p()?.mediaItemCount ?: 0
    fun titleAt(i: Int): String = try { p()?.getMediaItemAt(i)?.mediaMetadata?.title?.toString() ?: "" } catch (_: Exception) { "" }
    fun currentSong(): Song? {
        val pl = p() ?: return null
        val id = pl.currentMediaItem?.mediaId?.toLongOrNull() ?: return null
        return Library.byId(id)
    }
    fun cycleRepeat() {
        val pl = p() ?: return
        pl.repeatMode = when (pl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        onChange?.invoke()
    }
    fun repeatMode() = p()?.repeatMode ?: Player.REPEAT_MODE_OFF
    fun setShuffle(on: Boolean) { p()?.shuffleModeEnabled = on }
    fun isShuffle() = p()?.shuffleModeEnabled == true
    fun setSpeed(f: Float) { p()?.playbackParameters = PlaybackParameters(f) }
    fun speed(): Float = p()?.playbackParameters?.speed ?: 1f
    fun removeFromQueue(index: Int) { val pl = p() ?: return; if (index in 0 until pl.mediaItemCount) pl.removeMediaItems(index, index + 1) }
    fun setSleep(min: Int) {
        sleepRunnable?.let { sleepHandler.removeCallbacks(it) }
        sleepMinutes = min
        if (min > 0) { val r = Runnable { p()?.pause() }; sleepRunnable = r; sleepHandler.postDelayed(r, min * 60_000L) }
    }
}