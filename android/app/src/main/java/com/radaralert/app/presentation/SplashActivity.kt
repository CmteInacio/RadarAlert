package com.radaralert.app.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.radaralert.app.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoView = VideoView(this)
        setContentView(videoView)

        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.splash}")
        videoView.setVideoURI(videoUri)
        videoView.setOnCompletionListener { goToMain() }
        videoView.setOnErrorListener { _, _, _ -> goToMain(); true }
        videoView.start()
    }

    private fun goToMain() {
        startActivity(Intent(this, SpeedDisplayActivity::class.java))
        finish()
    }
}
