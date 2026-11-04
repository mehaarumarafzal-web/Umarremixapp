package com.umar.musicplayer

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long
)

data class Playlist(
    val id: Long,
    var name: String,
    val songIds: MutableList<Long> = mutableListOf()
)