package com.hd.emojibattery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    private val appPrefs by lazy { getSharedPreferences(if (BuildConfig.UI_TEST_MODE) "emoji_battery_preview" else "emoji_battery", MODE_PRIVATE) }
    private var accessibilityDialog: AlertDialog? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.activity_root)
        bottomNav = findViewById(R.id.app_bottom_nav)
        if (BuildConfig.UI_TEST_MODE) {
            findViewById<View>(R.id.nav_home).setOnLongClickListener { showPreviewRoutes(); true }
        }
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
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: androidx.fragment.app.FragmentManager, f: Fragment) {
                updateChrome()
                updateEnableControls()
            }
        }, false)
        if (savedInstanceState == null) {
            ScreenRouter.open(this, R.id.main_container, intent.getStringExtra("target_screen") ?: ScreenIds.SPLASH, false)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val screen = (supportFragmentManager.findFragmentById(R.id.main_container) as? BaseFeatureFragment<*>)?.getScreenId()
                if (screen in setOf(ScreenIds.PET, ScreenIds.PET_STATE_2, ScreenIds.PET_STATE_3)) {
                    openScreen(ScreenIds.HOME, false)
                    return
                }
                if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
                else AlertDialog.Builder(this@MainActivity).setTitle("Exit Emoji Battery?")
                    .setMessage("Would you like to close the app?")
                    .setNegativeButton("Keep browsing", null).setPositiveButton("Exit") { _, _ -> finish() }.show()
            }
        })
        AppContentRepository.get(this).load(this) { _, updated -> if (updated) refreshVisibleScreen() }
        updateChrome()
        if (!BuildConfig.UI_TEST_MODE && Build.VERSION.SDK_INT >= 33 && !appPrefs.getBoolean("notification_prompt_shown", false)) {
            appPrefs.edit().putBoolean("notification_prompt_shown", true).apply()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("target_screen")?.let { openScreen(it, false) }
    }

    fun openScreen(id: String, addToBackStack: Boolean, args: Bundle? = null) {
        val destination = if (id == ScreenIds.INTRO_1 && !(BuildConfig.UI_TEST_MODE && intent.getStringExtra("target_screen") == id) && !getSharedPreferences("emoji_battery", MODE_PRIVATE).getBoolean("language_selected", false)) ScreenIds.LANGUAGE else id
        if (ScreenRouter.isMainTab(destination)) supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        ScreenRouter.open(this, R.id.main_container, destination, args, addToBackStack && !ScreenRouter.isMainTab(destination))
    }

    private fun selectTab(id: String) {
        if (supportFragmentManager.findFragmentById(R.id.main_container)?.tag == id) return
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        ScreenRouter.open(this, R.id.main_container, id, false)
    }

    private fun updateChrome() {
        val fragment = supportFragmentManager.findFragmentById(R.id.main_container)
        val screen = (fragment as? BaseFeatureFragment<*>)?.getScreenId()
        val immersiveArt = fragment is com.hd.emojibattery.ui.onboarding.IntroFragment<*> || fragment is com.hd.emojibattery.ui.onboarding.PaywallFragment
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (immersiveArt) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
        bottomNav.visibility = if (screen != null && ScreenRouter.isMainTab(screen)) View.VISIBLE else View.GONE
        if (screen != null) {
            val selected = when (screen) { ScreenIds.CUSTOMIZE -> 1; ScreenIds.SETTINGS -> 2; else -> 0 }
            for (index in 0 until (bottomNav as android.view.ViewGroup).childCount) {
                val cell = (bottomNav as android.view.ViewGroup).getChildAt(index) as android.view.ViewGroup
                val color = if (index == selected) 0xff8b69fb.toInt() else 0xff999999.toInt()
                (cell.getChildAt(0) as android.widget.ImageView).imageTintList = android.content.res.ColorStateList.valueOf(color)
                (cell.getChildAt(1) as android.widget.TextView).setTextColor(color)
            }
        }
    }

    private fun refreshVisibleScreen() {
        (supportFragmentManager.findFragmentById(R.id.main_container) as? BaseFeatureFragment<*>)?.refreshContent()
    }

    fun enableChargingMonitor() {
        if (BuildConfig.UI_TEST_MODE) {
            getSharedPreferences("emoji_battery_preview", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
            return
        }
        startMonitor()
    }

    fun hasAcceptedAccessibilityUsage(): Boolean = appPrefs.getBoolean("accessibility_usage_accepted", false)


    fun requestEmojiEnable(
        onAccepted: (() -> Unit)? = null
    ) {
        if (hasAcceptedAccessibilityUsage()) {
            if (!BuildConfig.UI_TEST_MODE && !com.hd.emojibattery.runtime.FeatureApply.permission(this)) {
                updateEnableControls()
                return
            }
            enableChargingMonitor()
            updateEnableControls()
            onAccepted?.invoke()
            return
        }

        updateEnableControls()

        if (accessibilityDialog?.isShowing == true) return

        val content = layoutInflater.inflate(
            R.layout.dialog_accessibility_usage,
            null
        )

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .create()

        accessibilityDialog = dialog

        val next = content.findViewById<android.widget.Button>(
            R.id.accessibility_next
        )

        val agree = content.findViewById<android.widget.CheckBox>(
            R.id.accessibility_agree
        )

        next.isEnabled = false
        next.alpha = 0.4f

        agree.setOnCheckedChangeListener { _, checked ->
            next.isEnabled = checked
            next.alpha = if (checked) 1f else 0.4f
        }

        content.findViewById<View>(
            R.id.accessibility_close
        ).setOnClickListener {
            dialog.dismiss()
        }

        next.setOnClickListener {
            appPrefs.edit()
                .putBoolean("accessibility_usage_accepted", true)
                .apply()

            dialog.dismiss()
            // Consent is separate from Android's actual overlay/accessibility grants.
            requestEmojiEnable(onAccepted)
        }

        dialog.setOnDismissListener {
            accessibilityDialog = null
            updateEnableControls()
        }

        dialog.show()

        dialog.window?.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels -
                    40 * resources.displayMetrics.density)
                .toInt()
                .coerceAtMost(
                    (420 * resources.displayMetrics.density).toInt()
                ),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }


    override fun onResume() {
        super.onResume()
        com.hd.emojibattery.runtime.RestrictedApps.foreground(this, packageName)
        updateEnableControls()
    }

    fun updateEnableControls() {
        val root = supportFragmentManager.findFragmentById(R.id.main_container)?.view ?: return
        val screen = (supportFragmentManager.findFragmentById(R.id.main_container) as? BaseFeatureFragment<*>)?.getScreenId()
        val accepted = hasAcceptedAccessibilityUsage() && (BuildConfig.UI_TEST_MODE ||
            (android.provider.Settings.canDrawOverlays(this) && GestureAccessibilityService.active != null))
        listOf(R.id.customize_enable, R.id.settings_enable, R.id.pet_enable).forEach { id ->
            val relevant = when (id) {
                R.id.customize_enable -> screen !in setOf(ScreenIds.BATTERY_TROLL, ScreenIds.BATTERY_TROLL_SETUP, ScreenIds.TROLL_SETUP_2, ScreenIds.TROLL_PERCENT, ScreenIds.TROLL_EXIT)
                R.id.pet_enable -> screen in setOf(ScreenIds.PET, ScreenIds.PET_STATE_2, ScreenIds.PET_STATE_3)
                else -> true
            }
            root.findViewById<View>(id)?.let { button -> (button.parent as View).visibility = if (relevant && !accepted) View.VISIBLE else View.GONE }
        }
        root.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.home_enable)?.apply {
            setOnCheckedChangeListener(null)
            isChecked = accepted && appPrefs.getBoolean("enabled", false)
            setOnCheckedChangeListener { _, checked -> if (checked) requestEmojiEnable() else { disableChargingMonitor(); updateEnableControls() } }
        }
    }

    override fun onDestroy() {
        accessibilityDialog?.dismiss()
        super.onDestroy()
    }

    fun disableChargingMonitor() {
        if (BuildConfig.UI_TEST_MODE) {
            getSharedPreferences("emoji_battery_preview", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            return
        }
        getSharedPreferences("emoji_battery", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
        stopService(Intent(this, BatteryMonitorService::class.java))
    }

    private fun startMonitor() {
        getSharedPreferences("emoji_battery", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
        ContextCompat.startForegroundService(this, Intent(this, BatteryMonitorService::class.java))
    }

    private fun showPreviewRoutes() {
        val routes = ScreenIds::class.java.fields.filter { it.type == String::class.java }
            .map { it.name to (it.get(null) as String) }
            .filterNot { it.first.startsWith("SPLASH") }.sortedBy { it.first }
        AlertDialog.Builder(this).setTitle("Preview flows")
            .setItems(routes.map { "${it.first} · ${it.second}" }.toTypedArray()) { _, index ->
                openScreen(routes[index].second, true)
            }.setNegativeButton("Close", null).show()
    }

}
