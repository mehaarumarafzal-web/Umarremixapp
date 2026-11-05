package com.umar.musicplayer

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var preview: ImageView

    private val bgPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ImageUtils.savePicked(this, uri)?.let {
            Store.setBgPath(it); loadPreview(); Toast.makeText(this, "Background set ho gaya", Toast.LENGTH_SHORT).show()
        }
    }

    private val coverPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ImageUtils.savePicked(this, uri)?.let {
            Store.setGlobalCover(it); Toast.makeText(this, "Sab songs par cover lag gaya", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Store.init(this); PlayerCtl.bind(this)
        setContentView(R.layout.activity_settings)
        preview = findViewById(R.id.bgPreview)
        findViewById<AppCompatImageButton>(R.id.back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnChoose).setOnClickListener { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        findViewById<Button>(R.id.btnReset).setOnClickListener { Store.setBgPath(null); loadPreview(); Toast.makeText(this, "Default background wapas", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.btnAllCover).setOnClickListener { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        findViewById<Button>(R.id.btnResetCovers).setOnClickListener { Store.setGlobalCover(null); Toast.makeText(this, "Covers reset ho gaye", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.btnRewSize).setOnClickListener { pickSize() }
        loadPreview()
    }

    private fun pickSize() {
        val opts = arrayOf("Small", "Medium", "Large", "Extra")
        val vals = intArrayOf(56, 64, 72, 80)
        AlertDialog.Builder(this).setTitle("Rewind / Forward button size")
            .setItems(opts) { _, i -> Store.setRewFwdSize(vals[i]); Toast.makeText(this, "Size: ${opts[i]}", Toast.LENGTH_SHORT).show() }
            .show()
    }

    private fun loadPreview() {
        preview.post {
            val w = preview.width; val h = preview.height
            if (w == 0 || h == 0) return@post
            val path = Store.bgPath()
            val bmp = if (path != null) ImageUtils.loadSharp(path, w, h) else ImageUtils.defaultSharp(this, w, h)
            if (bmp != null) preview.setImageBitmap(bmp) else preview.setImageDrawable(null)
        }
    }
}