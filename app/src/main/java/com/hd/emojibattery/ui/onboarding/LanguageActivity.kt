package com.hd.emojibattery.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.hd.emojibattery.R
import com.hd.emojibattery.ads.BatteryAds
import com.hd.emojibattery.runtime.LanguagePreference
import com.hd.emojibattery.ui.navigation.ScreenIds
import com.hd.emojibattery.ui.navigation.ScreenRouter

/** Owns Language independently; MainActivity remains underneath for the result. */
class LanguageActivity : AppCompatActivity() {
    val batteryAds by lazy { BatteryAds(this) }
    val fromOnboarding get() = intent.getBooleanExtra("from_onboarding", true)

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguagePreference.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.app_bottom_nav).visibility = View.GONE
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_root)) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, 0)
            insets
        }
        val dock = findViewById<View>(R.id.battery_bottom_dock)
        dock.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            findViewById<View>(R.id.battery_content_root).setPadding(0, 0, 0, dock.height)
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: androidx.fragment.app.FragmentManager, f: androidx.fragment.app.Fragment, v: View, state: Bundle?) {
                batteryAds.onScreen(f, ScreenIds.LANGUAGE)
            }
        }, false)
        batteryAds.initialize { }
        if (savedInstanceState == null) ScreenRouter.open(this, R.id.main_container, ScreenIds.LANGUAGE, false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fromOnboarding) {
                    androidx.appcompat.app.AlertDialog.Builder(this@LanguageActivity)
                        .setTitle("Exit Emoji Battery?")
                        .setMessage("Would you like to close the app?")
                        .setNegativeButton("Keep browsing", null)
                        .setPositiveButton("Exit") { _, _ ->
                            setResult(RESULT_CANCELED, Intent().putExtra("exit_onboarding", true))
                            finish()
                        }.show()
                } else finish()
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            show(WindowInsetsCompat.Type.statusBars())
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    fun completeSelection() {
        LanguagePreference.apply(this)
        setResult(RESULT_OK, Intent().putExtra("go_to_intro", fromOnboarding))
        finish()
    }

    override fun onDestroy() {
        batteryAds.close()
        super.onDestroy()
    }
}
