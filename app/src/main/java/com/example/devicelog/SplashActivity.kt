package com.example.devicelog

import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.logo)
        logo.scaleX = 0.5f
        logo.scaleY = 0.5f
        logo.alpha = 0f

        logo.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(1000)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .start()
    }
}

