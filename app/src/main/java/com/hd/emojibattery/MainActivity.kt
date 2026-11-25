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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.hd.emojibattery.data.AppContentRepository
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.navigation.ScreenIds
import com.hd.emojibattery.ui.navigation.ScreenRouter

class MainActivity : AppCompatActivity() {
    val batteryAds by lazy { com.hd.emojibattery.ads.BatteryAds(this) }
    private val resumeRouteEligible: () -> Boolean = {
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                supportFragmentManager.findFragmentById(R.id.main_container) is BaseFeatureFragment<*> &&
                pendingEnable == null && pendingOverlay == null && accessibilityDialog?.isShowing != true && batteryAds.canResumeAd()
            }
    private lateinit var bottomNav: View
    private val appPrefs by lazy { getSharedPreferences(if (BuildConfig.UI_TEST_MODE) "emoji_battery_preview" else "emoji_battery", MODE_PRIVATE) }
    private val enableStateListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("status_overlay_enabled", "gesture_enabled", "permissions_enabled_once")) {
            updateEnableControls()
        }
    }
    private var accessibilityDialog: AlertDialog? = null
    private var pendingEnable: (() -> Unit)? = null
    private var pendingEnableScreen: String? = null
    private var pendingEnablesBattery = true
    private var pendingOverlay: (() -> Unit)? = null
    private var pendingOverlayScreen: String? = null
    private val languagePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            if (result.data?.getBooleanExtra("go_to_intro", false) == true) {
                openScreenDirect(ScreenIds.INTRO_1, false)
            } else {
                val current = supportFragmentManager.findFragmentById(R.id.main_container)
                val screen = current?.arguments?.getString("screen_id") ?: ScreenIds.SETTINGS
                ScreenRouter.open(this, R.id.main_container, screen, Bundle(current?.arguments ?: Bundle()), false)
            }
        } else if (result.data?.getBooleanExtra("exit_onboarding", false) == true) {
            finish()
        }
    }

    fun launchLanguage() {
        languagePicker.launch(Intent(this, com.hd.emojibattery.ui.onboarding.LanguageActivity::class.java)
            .putExtra("from_onboarding", !getSharedPreferences("emoji_battery", MODE_PRIVATE).getBoolean("intro_complete", false)))
    }

    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finishOverlayPermission()
    }
    private val overlayNotificationSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            launchOverlayPermission()
        } else pendingOverlay = null
        updateEnableControls()
    }
    private val overlayNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchOverlayPermission() else {
            pendingOverlay = null
            updateEnableControls()
        }
    }
    private val enablePermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        continueEnable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.hd.emojibattery.runtime.LanguagePreference.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.activity_root)
        bottomNav = findViewById(R.id.app_bottom_nav)
        if (BuildConfig.UI_TEST_MODE) {
            findViewById<View>(R.id.nav_home).setOnLongClickListener { showPreviewRoutes(); true }
        }
        bottomNav.visibility = View.GONE
        findViewById<View>(R.id.battery_bottom_dock).addOnLayoutChangeListener { dock, _, _, _, _, _, _, _, _ ->
            val fragment = supportFragmentManager.findFragmentById(R.id.main_container)
            val screen = (fragment as? BaseFeatureFragment<*>)?.getScreenId()
                ?: fragment?.arguments?.getString("screen_id")
            val reserve = if (screen != null && screen != ScreenIds.INTRO_3 && !ScreenRouter.isMainTab(screen) && !ScreenRouter.isSticker(screen)) dock.height else 0
            findViewById<View>(R.id.battery_content_root).setPadding(0, 0, 0, reserve)
        }
        findViewById<View>(R.id.nav_home).setOnClickListener { selectTab(ScreenIds.HOME) }
        findViewById<View>(R.id.nav_pet).setOnClickListener { selectTab(ScreenIds.PET) }
        findViewById<View>(R.id.nav_customize).setOnClickListener { selectTab(ScreenIds.CUSTOMIZE) }
        findViewById<View>(R.id.nav_settings).setOnClickListener { selectTab(ScreenIds.SETTINGS) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safeInsets.left, safeInsets.top, safeInsets.right, 0)
            insets
        }
        supportFragmentManager.addOnBackStackChangedListener(::updateChrome)
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: androidx.fragment.app.FragmentManager, f: Fragment, v: View, state: Bundle?) {
                val screen = f.arguments?.getString("screen_id") ?: return
                batteryAds.onScreen(f, screen)
            }

            override fun onFragmentResumed(fm: androidx.fragment.app.FragmentManager, f: Fragment) {
                f.arguments?.getString("screen_id")?.let { screen ->
                    com.hd.emojibattery.analytics.ScreenAnalytics.track(this@MainActivity, screen, f.javaClass.simpleName)
                }
                updateChrome()
                updateEnableControls()
            }
        }, false)
        tpt.dev.monetization.ads.AdsManager.getInstance().resumeAdEligibility = resumeRouteEligible
        // Deep links/restoration do not pass through Splash.
        if (savedInstanceState != null || intent.hasExtra("target_screen")) batteryAds.initialize { }
        if (savedInstanceState == null) {
            ScreenRouter.open(this, R.id.main_container, intent.getStringExtra("target_screen") ?: ScreenIds.SPLASH, false)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!ScreenRouter.canNavigate(this@MainActivity) || batteryAds.busy) return
                val currentFragment = supportFragmentManager.findFragmentById(R.id.main_container)
                val screen = (currentFragment as? BaseFeatureFragment<*>)?.getScreenId()
                    ?: currentFragment?.arguments?.getString("screen_id")
                val introPages = listOf(ScreenIds.INTRO_1, ScreenIds.INTRO_2, ScreenIds.INTRO_3, ScreenIds.INTRO_5)
                val introIndex = introPages.indexOf(screen)
                if (introIndex > 0) {
                    openScreen(introPages[introIndex - 1], false)
                    return
                }
                if (introIndex == 0) {
                    showExitDialog()
                    return
                }
                if (screen in setOf(ScreenIds.PET, ScreenIds.PET_STATE_2, ScreenIds.PET_STATE_3)) {
                    openScreen(ScreenIds.HOME, false)
                    return
                }
                if (screen == ScreenIds.HOW_TO_USE) {
                    pendingEnable = null
                    pendingEnableScreen = null
                }
                if (supportFragmentManager.backStackEntryCount > 0) backWithAd(supportFragmentManager.findFragmentById(R.id.main_container)) { supportFragmentManager.findFragmentById(R.id.main_container)?.let { ScreenRouter.pop(this@MainActivity, it) } }
                else showExitDialog()
            }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.getSubscribedStateFlow().collect { premium ->
                    if (premium && supportFragmentManager.findFragmentById(R.id.main_container) is BaseFeatureFragment<*>) {
                        batteryAds.close()
                    }
                    refreshVisibleScreen()
                }
            }
        }
        AppContentRepository.get(this).load(this) { _, updated -> if (updated) refreshVisibleScreen() }
        updateChrome()
        BatteryMonitorService.running.observe(this) { updateEnableControls() }
        DecorationService.running.observe(this) { updateEnableControls() }
        GestureAccessibilityService.connected.observe(this) { connected ->
            updateEnableControls()
            if (connected && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                com.hd.emojibattery.runtime.RestrictedApps.foreground(this, packageName)
                if (pendingEnable != null) continueEnable()
            }
        }
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this).setTitle("Exit Emoji Battery?")
            .setMessage("Would you like to close the app?")
            .setNegativeButton("Keep browsing", null).setPositiveButton("Exit") { _, _ -> finish() }.show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::bottomNav.isInitialized) updateChrome()
    }

    fun backWithAd(source: Fragment?, next: () -> Unit) {
        if (source == null || !ScreenRouter.canNavigate(this) || batteryAds.busy) return
        batteryAds.transition(source, ScreenIds.HOME) {
            if (ScreenRouter.canNavigate(this) && supportFragmentManager.findFragmentById(R.id.main_container) === source) next()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("target_screen")?.let { openScreen(it, false) }
    }

    fun openScreen(id: String, addToBackStack: Boolean, args: Bundle? = null) {
        if (!ScreenRouter.canNavigate(this) || batteryAds.busy) return
        val source = supportFragmentManager.findFragmentById(R.id.main_container)
        batteryAds.transition(source, id) {
            if (ScreenRouter.canNavigate(this) && supportFragmentManager.findFragmentById(R.id.main_container) === source) {
                openScreenDirect(id, addToBackStack, args)
            }
        }
    }

    private fun openScreenDirect(id: String, addToBackStack: Boolean, args: Bundle? = null) {
        pendingOverlay = null
        pendingEnable = null
        pendingEnableScreen = null
        val destination = if (id == ScreenIds.INTRO_1 && !(BuildConfig.UI_TEST_MODE && intent.getStringExtra("target_screen") == id) && !getSharedPreferences("emoji_battery", MODE_PRIVATE).getBoolean("language_selected", false)) ScreenIds.LANGUAGE else id
        if (ScreenRouter.isMainTab(destination)) supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        ScreenRouter.open(this, R.id.main_container, destination, args, addToBackStack && !ScreenRouter.isMainTab(destination))
    }

    private fun selectTab(id: String) {
        if (!ScreenRouter.canNavigate(this) || batteryAds.busy) return
        pendingOverlay = null
        pendingEnable = null
        pendingEnableScreen = null
        if (supportFragmentManager.findFragmentById(R.id.main_container)?.tag == id) return
        batteryAds.prepare(id)
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        ScreenRouter.open(this, R.id.main_container, id, false)
    }

    private fun updateChrome() {
        val fragment = supportFragmentManager.findFragmentById(R.id.main_container)
        val screen = (fragment as? BaseFeatureFragment<*>)?.getScreenId()
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.show(WindowInsetsCompat.Type.statusBars())
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        bottomNav.visibility = if (screen != null && ScreenRouter.isMainTab(screen)) View.VISIBLE else View.GONE
        val contentScreen = screen ?: fragment?.arguments?.getString("screen_id")
        val dockHeight = findViewById<View>(R.id.battery_bottom_dock).height
        findViewById<View>(R.id.battery_content_root).setPadding(0, 0, 0,
            if (contentScreen != null && contentScreen != ScreenIds.INTRO_3 && !ScreenRouter.isMainTab(contentScreen) && !ScreenRouter.isSticker(contentScreen)) dockHeight else 0)
        if (screen != null) {
            val selected = when (screen) { ScreenIds.PET -> 1; ScreenIds.CUSTOMIZE -> 2; ScreenIds.SETTINGS -> 3; else -> 0 }
            for (index in 0 until (bottomNav as android.view.ViewGroup).childCount) {
                val cell = (bottomNav as android.view.ViewGroup).getChildAt(index) as android.view.ViewGroup
                val active = index == selected
                cell.isSelected = active
                (cell.getChildAt(0) as android.widget.ImageView).apply {
                    isSelected = active
                    imageTintList = null
                }
                (cell.getChildAt(1) as android.widget.TextView).apply {
                    setTextColor(if (active) 0xff8b69fb.toInt() else 0xff9e9e9e.toInt())
                    typeface = android.graphics.Typeface.create(typeface, if (active) 600 else 500, false)
                    paint.shader = if (active) android.graphics.LinearGradient(
                        0f, 0f, paint.measureText(text.toString()), textSize,
                        intArrayOf(0xff76c0ff.toInt(), 0xff8b69fb.toInt(), 0xffcc70fc.toInt()),
                        floatArrayOf(0.0157f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP
                    ) else null
                    invalidate()
                }
            }
        }
    }

    private fun refreshVisibleScreen() {
        (supportFragmentManager.findFragmentById(R.id.main_container) as? BaseFeatureFragment<*>)?.refreshContent()
    }

    fun enableChargingMonitor(): Boolean {
        if (BuildConfig.UI_TEST_MODE) {
            getSharedPreferences("emoji_battery_preview", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
            return true
        }
        return startMonitor()
    }

    fun hasAcceptedAccessibilityUsage(): Boolean = appPrefs.getBoolean("accessibility_usage_accepted", false)

    fun hasOverlayPermissions(): Boolean = BuildConfig.UI_TEST_MODE ||
        (android.provider.Settings.canDrawOverlays(this) && (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED))

    /** Pet and charging windows use their own grant, independently of Accessibility. */
    fun requestOverlayEnable(onGranted: () -> Unit) {
        if (hasOverlayPermissions()) { onGranted(); return }
        pendingOverlay = onGranted
        pendingOverlayScreen = supportFragmentManager.findFragmentById(R.id.main_container)?.tag
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (appPrefs.getBoolean("overlay_notification_requested", false) &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                overlayNotificationSettings.launch(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
            } else {
                appPrefs.edit().putBoolean("overlay_notification_requested", true).apply()
                overlayNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else launchOverlayPermission()
    }

    private fun launchOverlayPermission() {
        if (pendingOverlay == null) return
        if (supportFragmentManager.findFragmentById(R.id.main_container)?.tag != pendingOverlayScreen) {
            pendingOverlay = null
            return
        }
        if (android.provider.Settings.canDrawOverlays(this)) { finishOverlayPermission(); return }
        AlertDialog.Builder(this).setTitle("Display over other apps")
            .setMessage("Allow Emoji Battery to show pets or charging animations over other apps.")
            .setNegativeButton("Cancel") { _, _ -> pendingOverlay = null }
            .setOnCancelListener { pendingOverlay = null }
            .setPositiveButton("Open settings") { _, _ ->
                try {
                    overlayPermission.launch(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")))
                } catch (_: SecurityException) {
                    pendingOverlay = null
                    overlaySettingsUnavailable()
                } catch (_: android.content.ActivityNotFoundException) {
                    pendingOverlay = null
                    overlaySettingsUnavailable()
                }
            }.show()
    }

    private fun finishOverlayPermission() {
        val action = pendingOverlay
        pendingOverlay = null
        val sameScreen = supportFragmentManager.findFragmentById(R.id.main_container)?.tag == pendingOverlayScreen
        pendingOverlayScreen = null
        if (sameScreen && hasOverlayPermissions()) action?.invoke()
        restoreEnabledPet()
        updateEnableControls()
    }


    fun requestEmojiEnable(
        enableBattery: Boolean = true,
        showGuide: Boolean = true,
        onAccepted: (() -> Unit)? = null
    ) {
        pendingEnable = onAccepted ?: {}
        pendingEnablesBattery = enableBattery
        pendingEnableScreen = supportFragmentManager.findFragmentById(R.id.main_container)?.tag
        if (showGuide && !hasEnabledPermissionsOnce() && pendingEnableScreen != ScreenIds.HOW_TO_USE) {
            // A tap starts the grant flow; keep the caller off until its service is ready.
            updateEnableControls()
            ScreenRouter.open(this, R.id.main_container, ScreenIds.HOW_TO_USE,
                Bundle().apply { putBoolean("enable_request_guide", true) }, true)
            return
        }
        if (hasAcceptedAccessibilityUsage()) {
            if (!BuildConfig.UI_TEST_MODE) {
                if (GestureAccessibilityService.active == null) {
                    updateEnableControls()
                    try {
                        enablePermission.launch(com.hd.emojibattery.runtime.AppSettings.accessibilitySettings(this))
                    } catch (_: SecurityException) {
                        accessibilitySettingsUnavailable()
                    } catch (_: android.content.ActivityNotFoundException) {
                        accessibilitySettingsUnavailable()
                    }
                    return
                }
            }
            appPrefs.edit().putBoolean("permissions_enabled_once", true).apply()
            if (enableBattery) {
                if (BuildConfig.UI_TEST_MODE) appPrefs.edit().putBoolean("status_overlay_enabled", true).apply()
                else if (!com.hd.emojibattery.runtime.FeatureApply.status(this)) {
                    pendingEnable = null
                    pendingEnableScreen = null
                    updateEnableControls()
                    return
                }
            }
            pendingEnable = null
            pendingEnableScreen = null
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
            pendingEnable = null
            dialog.dismiss()
        }

        next.setOnClickListener {
            appPrefs.edit()
                .putBoolean("accessibility_usage_accepted", true)
                .apply()

            dialog.dismiss()
            // Consent is separate from Android's Accessibility grant.
            requestEmojiEnable(enableBattery, showGuide = false, onAccepted = onAccepted)
        }

        dialog.setOnDismissListener {
            accessibilityDialog = null
            updateEnableControls()
        }
        dialog.setOnCancelListener { pendingEnable = null; pendingEnableScreen = null }
        batteryAds.bindPopup(dialog)

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

    fun hasEnabledPermissionsOnce(): Boolean {
        if (appPrefs.getBoolean("permissions_enabled_once", false)) return true
        val granted = hasAcceptedAccessibilityUsage() && (BuildConfig.UI_TEST_MODE ||
            GestureAccessibilityService.active != null)
        if (granted) appPrefs.edit().putBoolean("permissions_enabled_once", true).apply()
        return granted
    }

    fun continueEnableFromGuide() {
        val action = pendingEnable
        val battery = pendingEnablesBattery
        pendingEnable = null
        pendingEnableScreen = null
        if (action != null) {
            // Keep the guide visible during consent and permission settings.
            requestEmojiEnable(battery, showGuide = false, onAccepted = {
                // Restore the caller only when its permission/apply callback can run.
                if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return@requestEmojiEnable
                if (supportFragmentManager.findFragmentById(R.id.main_container)?.tag == ScreenIds.HOW_TO_USE) {
                    supportFragmentManager.popBackStackImmediate()
                }
                action()
            })
        } else if (!hasAcceptedAccessibilityUsage() || (!BuildConfig.UI_TEST_MODE &&
            GestureAccessibilityService.active == null)) {
            requestEmojiEnable(enableBattery = false, showGuide = false)
        } else if (!BuildConfig.UI_TEST_MODE) {
            try {
                enablePermission.launch(com.hd.emojibattery.runtime.AppSettings.accessibilitySettings(this))
            } catch (_: SecurityException) {
                accessibilitySettingsUnavailable()
            } catch (_: android.content.ActivityNotFoundException) {
                accessibilitySettingsUnavailable()
            }
        }
    }

    private fun overlaySettingsUnavailable() {
        pendingOverlay = null
        pendingOverlayScreen = null
        android.widget.Toast.makeText(this, "Could not open overlay settings. Open Settings > Apps > Special app access > Display over other apps and enable Emoji Battery.", android.widget.Toast.LENGTH_LONG).show()
        updateEnableControls()
    }

    private fun accessibilitySettingsUnavailable() {
        pendingEnable = null
        pendingEnableScreen = null
        com.hd.emojibattery.runtime.AppSettings.accessibilityUnavailable(this)
        updateEnableControls()
    }
    private fun continueEnable() {
        val action = pendingEnable ?: return
        if (supportFragmentManager.findFragmentById(R.id.main_container)?.tag != pendingEnableScreen) {
            pendingEnable = null
            return
        }
        // A denied grant leaves the control off and allows a later explicit retry.
        if (!BuildConfig.UI_TEST_MODE && GestureAccessibilityService.active == null) {
            if (!isAccessibilityConfigured()) pendingEnable = null
            updateEnableControls()
            return
        }
        requestEmojiEnable(pendingEnablesBattery, showGuide = false, onAccepted = action)
    }


    override fun onResume() {
        super.onResume()
        appPrefs.registerOnSharedPreferenceChangeListener(enableStateListener)
        com.hd.emojibattery.runtime.RestrictedApps.foreground(this, packageName)
        restoreEnabledPet()
        updateEnableControls()
        if (pendingEnable != null && GestureAccessibilityService.active != null) continueEnable()
    }

    private fun restoreEnabledPet() {
        if (BuildConfig.UI_TEST_MODE || !hasOverlayPermissions() ||
            !appPrefs.getBoolean("pet_overlay_enabled", false) || AnimatedPetService.running) return
        com.hd.emojibattery.runtime.FeatureApply.start(this, AnimatedPetService::class.java)
    }

    private fun isAccessibilityConfigured(): Boolean {
        val component = android.content.ComponentName(this, GestureAccessibilityService::class.java)
        val configured = android.provider.Settings.Secure.getString(contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return configured.split(':').any { android.content.ComponentName.unflattenFromString(it) == component }
    }

    fun isEmojiEnabled(): Boolean = hasAcceptedAccessibilityUsage() &&
        appPrefs.getBoolean("status_overlay_enabled", false) && (BuildConfig.UI_TEST_MODE ||
        GestureAccessibilityService.active != null)

    fun isDecorationEnabled(key: String): Boolean = appPrefs.getBoolean(key, false) &&
        (BuildConfig.UI_TEST_MODE || (hasAcceptedAccessibilityUsage() &&
            GestureAccessibilityService.active != null && DecorationService.running.value == true))

    fun updateEnableControls() {
        val current = supportFragmentManager.findFragmentById(R.id.main_container)
        val root = (current as? com.hd.emojibattery.ui.home.HomeFragment<*>)?.enableControlsRoot()
            ?: current?.view ?: return
        val screen = (supportFragmentManager.findFragmentById(R.id.main_container) as? BaseFeatureFragment<*>)?.getScreenId()
        val firstEnable = !hasEnabledPermissionsOnce()
        val notch = screen in setOf(ScreenIds.NOTCH, ScreenIds.NOTCH_EXIT)
        fun controls(startId: Int, toggleId: Int, labelId: Int, title: String) {
            root.findViewById<View>(startId)?.visibility = if (firstEnable) View.VISIBLE else View.GONE
            root.findViewById<View>(toggleId)?.visibility = if (firstEnable) View.GONE else View.VISIBLE
            root.findViewById<android.widget.TextView>(labelId)?.text = if (firstEnable) getString(R.string.enable_emoji_battery) else title
        }
        controls(R.id.home_start, R.id.home_enable, R.id.home_enable_label, "Enable Emoji Battery")
        val petReady = hasOverlayPermissions()
        root.findViewById<View>(R.id.pet_enable)?.visibility = if (petReady) View.GONE else View.VISIBLE
        root.findViewById<View>(R.id.pet_enabled)?.visibility = if (petReady) View.VISIBLE else View.GONE
        root.findViewById<android.widget.TextView>(R.id.pet_enable_label)?.setText(
            if (petReady) R.string.enable_pet else R.string.enable_pet_to_begin
        )
        controls(R.id.settings_enable, R.id.settings_enabled, R.id.settings_enable_label, "Enable Emoji Battery")
        controls(R.id.customize_enable, R.id.customize_enabled, R.id.customize_enable_label, "Enable Emoji Battery")
        if (notch) {
            root.findViewById<View>(R.id.customize_enable)?.visibility = View.GONE
            root.findViewById<View>(R.id.customize_enabled)?.visibility = View.VISIBLE
            root.findViewById<android.widget.TextView>(R.id.customize_enable_label)?.text = "Enable notch"
        }
        controls(R.id.status_start, R.id.status_enabled, R.id.status_enable_label, "Enable Emoji Battery")
        controls(R.id.troll_enable, R.id.troll_enabled, R.id.troll_enable_label, "Enable Emoji Battery")
        controls(R.id.sticker_start, R.id.sticker_enable, R.id.sticker_enable_label, "Show Sticker")
        root.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.gesture_enabled)?.apply {
            setOnCheckedChangeListener(null)
            isChecked = isEmojiEnabled() && appPrefs.getBoolean("gesture_enabled", false)
            (root.findViewById<View>(R.id.gesture_single)?.parent as? View)?.visibility =
                if (isChecked) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, checked ->
                (current as? com.hd.emojibattery.ui.customize.CustomizeFragment<*>)?.setGestureEnabled(checked)
            }
        }
        listOf(R.id.settings_enabled, R.id.customize_enabled, R.id.status_enabled, R.id.troll_enabled).forEach { id ->
            root.findViewById<androidx.appcompat.widget.SwitchCompat>(id)?.apply {
                setOnCheckedChangeListener(null)
                isChecked = if (notch && id == R.id.customize_enabled) isDecorationEnabled("notch_enabled") else isEmojiEnabled()
                setOnCheckedChangeListener { _, checked ->
                    val current = supportFragmentManager.findFragmentById(R.id.main_container)
                    if (notch && id == R.id.customize_enabled && current is com.hd.emojibattery.ui.customize.CustomizeFragment<*>) {
                        current.setNotchEnabled(checked)
                    } else if (id == R.id.status_enabled && current is com.hd.emojibattery.ui.statusbar.StatusBarFragment<*>) {
                        current.setStatusEnabled(checked)
                    } else if (checked) requestEmojiEnable() else { disableBatteryOverlay(); updateEnableControls() }
                }
            }
        }
        root.findViewById<View>(R.id.home_start)?.setOnClickListener { requestEmojiEnable() }
        root.findViewById<View>(R.id.troll_enable)?.setOnClickListener { requestEmojiEnable() }
        root.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.home_enable)?.apply {
            setOnCheckedChangeListener(null)
            isChecked = isEmojiEnabled()
            setOnCheckedChangeListener { _, checked -> if (checked) requestEmojiEnable() else { disableBatteryOverlay(); updateEnableControls() } }
        }
    }

    override fun onDestroy() {
        appPrefs.unregisterOnSharedPreferenceChangeListener(enableStateListener)
        pendingOverlay = null
        batteryAds.close()
        val ads = tpt.dev.monetization.ads.AdsManager.getInstance()
        if (ads.resumeAdEligibility === resumeRouteEligible) ads.resumeAdEligibility = { false }
        pendingEnable = null
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

    fun disableBatteryOverlay() {
        appPrefs.edit().putBoolean("status_overlay_enabled", false)
            .putBoolean("gesture_enabled", false).apply()
        if (BuildConfig.UI_TEST_MODE) return
        stopService(Intent(this, AppliedStatusService::class.java))
        stopService(Intent(this, StatusBarOverlayService::class.java))
        GestureAccessibilityService.active?.refreshStatus()
        if (GestureAccessibilityService.active != null) {
            com.hd.emojibattery.runtime.FeatureApply.decorations(this)
        }
    }

    private fun startMonitor(): Boolean {
        getSharedPreferences("emoji_battery", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
        return runCatching {
            ContextCompat.startForegroundService(this, Intent(this, BatteryMonitorService::class.java))
            true
        }.getOrElse {
            appPrefs.edit().putBoolean("enabled", false).apply()
            android.widget.Toast.makeText(this, "Could not enable Emoji Battery. Please try again.", android.widget.Toast.LENGTH_LONG).show()
            false
        }
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
