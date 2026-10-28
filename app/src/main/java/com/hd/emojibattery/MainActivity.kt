package com.hd.emojibattery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.hd.emojibattery.data.AppContentRepository
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.navigation.ScreenIds
import com.hd.emojibattery.ui.navigation.ScreenRouter

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNav: View
    private var permissionRequestPending = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && permissionRequestPending) startMonitor() else if (permissionRequestPending) showNotificationPermissionHelp()
        permissionRequestPending = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.activity_root)
        bottomNav = findViewById(R.id.app_bottom_nav)
        bottomNav.visibility = View.GONE
        findViewById<View>(R.id.nav_home).setOnClickListener { selectTab(ScreenIds.HOME) }
        findViewById<View>(R.id.nav_customize).setOnClickListener { selectTab(ScreenIds.CUSTOMIZE) }
        findViewById<View>(R.id.nav_settings).setOnClickListener { selectTab(ScreenIds.SETTINGS) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        supportFragmentManager.addOnBackStackChangedListener(::updateChrome)
        if (savedInstanceState == null) ScreenRouter.open(this, R.id.main_container, intent.getStringExtra("target_screen") ?: ScreenIds.SPLASH, false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
                else AlertDialog.Builder(this@MainActivity).setTitle("Exit Emoji Battery?")
                    .setMessage("Would you like to close the app?")
                    .setNegativeButton("Keep browsing", null).setPositiveButton("Exit") { _, _ -> finish() }.show()
            }
        })
        AppContentRepository.get(this).load(this) { _, updated -> if (updated) refreshVisibleScreen() }
        updateChrome()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("target_screen")?.let { openScreen(it, false) }
    }

    fun openScreen(id: String, addToBackStack: Boolean, args: Bundle? = null) {
        ScreenRouter.open(this, R.id.main_container, id, args, addToBackStack)
        supportFragmentManager.executePendingTransactions()
        updateChrome()
    }

    private fun selectTab(id: String) {
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        ScreenRouter.open(this, R.id.main_container, id, false)
        supportFragmentManager.executePendingTransactions()
        updateChrome()
    }

    private fun updateChrome() {
        val fragment = supportFragmentManager.findFragmentById(R.id.main_container)
        val screen = (fragment as? BaseFeatureFragment<*>)?.getScreenId()
        bottomNav.visibility = if (screen != null && ScreenRouter.isMainTab(screen)) View.VISIBLE else View.GONE
        if (screen != null) {
            val selected = when (screen) { ScreenIds.CUSTOMIZE -> 1; ScreenIds.SETTINGS -> 2; else -> 0 }
            for (index in 0 until (bottomNav as android.view.ViewGroup).childCount) {
                val cell = (bottomNav as android.view.ViewGroup).getChildAt(index) as android.view.ViewGroup
                val color = if (index == selected) 0xff8b69fb.toInt() else 0xff999999.toInt()
                (cell.getChildAt(0) as android.widget.TextView).setTextColor(color)
                (cell.getChildAt(1) as android.widget.TextView).setTextColor(color)
            }
        }
    }

    private fun refreshVisibleScreen() {
        (supportFragmentManager.findFragmentById(R.id.main_container) as? BaseFeatureFragment<*>)?.refreshContent()
    }

    fun enableChargingMonitor() {
        getSharedPreferences("emoji_battery", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionRequestPending = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startMonitor()
    }

    fun disableChargingMonitor() {
        getSharedPreferences("emoji_battery", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
        stopService(Intent(this, BatteryMonitorService::class.java))
    }

    private fun startMonitor() {
        getSharedPreferences("emoji_battery", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
        ContextCompat.startForegroundService(this, Intent(this, BatteryMonitorService::class.java))
    }

    private fun showNotificationPermissionHelp() {
        getSharedPreferences("emoji_battery", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
        AlertDialog.Builder(this).setTitle("Notifications are off")
            .setMessage("Allow notifications to keep the charging monitor available.")
            .setPositiveButton("Open settings") { _, _ -> startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
            .setNegativeButton("Later", null).show()
    }
}
