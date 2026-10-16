package com.primevidvault.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Animated launch screen: the new Aurora logo pops in with an overshoot,
 * the brand name rises in behind it, then the screen fades to MainActivity.
 * Share-to-app (SEND intents) still opens MainActivity directly, so the
 * TikTok share flow stays instant and never sees this screen.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        findViewById<ImageView>(R.id.splashLogo).startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.splash_pop)
        )
        val textAnim = AnimationUtils.loadAnimation(this, R.anim.splash_text)
        findViewById<TextView>(R.id.splashName).startAnimation(textAnim)
        findViewById<TextView>(R.id.splashTag).startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.splash_text)
        )

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 1500)
    }
}
