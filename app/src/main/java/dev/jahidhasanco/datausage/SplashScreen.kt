package dev.jahidhasanco.datausage

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.alauddin.datausagen.databinding.ActivitySplashScreenBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashScreen : AppCompatActivity() {

    private lateinit var binding: ActivitySplashScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // lifecycleScope cancels on destroy, so a rotation or an early back press
        // cannot fire the navigation into a dead Activity.
        lifecycleScope.launch {
            delay(SPLASH_DELAY_MS)
            startActivity(Intent(this@SplashScreen, MainActivity::class.java))
            finish()
        }
    }

    private companion object {
        const val SPLASH_DELAY_MS = 1400L
    }
}
