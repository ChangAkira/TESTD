package com.example.testd.tasks

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.testd.R

class ImagePreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val imageView = findViewById<ImageView>(R.id.ivPreviewImage)
        val url = intent.getStringExtra(EXTRA_IMAGE_URL).orEmpty()
        if (url.isNotBlank()) {
            Glide.with(this).load(url).into(imageView)
        }

        imageView.setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_IMAGE_URL = "extra_image_url"
    }
}
