package hoang.dqm.codebase.base.activity

import android.content.Context
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import hoang.dqm.codebase.R
import hoang.dqm.codebase.base.viewmodel.BaseViewModel
import hoang.dqm.codebase.service.network.NetworkStatusReceiver
import hoang.dqm.codebase.utils.AppMonetization
import hoang.dqm.codebase.utils.BindingReflex
import hoang.dqm.codebase.utils.ads
import hoang.dqm.codebase.utils.hideNavigationBarsCompat
import hoang.dqm.codebase.utils.showSystemBarsCompat
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.nativeAd.view.ViewNativeAd
import java.lang.reflect.ParameterizedType
import kotlin.time.Duration.Companion.seconds

abstract class BaseActivity<VB : ViewBinding, VM : BaseViewModel> : AppCompatActivity(),
    View.OnClickListener {
    val binding: VB by lazy(mode = LazyThreadSafetyMode.NONE) {
        BindingReflex.reflexViewBinding(javaClass, layoutInflater)
    }
    private var lastTimeLoadBannerNativeAd = 0L
    private var nativeAd: NativeAd? = null
    private var nativeAdKey: String? = null
    private var nativeLoadCancel: (() -> Unit)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val currentFocusedView = currentFocus

            if (currentFocusedView is EditText) {
                val outRect = Rect()
                currentFocusedView.getGlobalVisibleRect(outRect)

                val x = event.rawX.toInt()
                val y = event.rawY.toInt()

                if (!outRect.contains(x, y)) {
                    val newFocusView = findViewAt(binding.root, x, y)

                    // ✅ Chỉ hide keyboard khi KHÔNG click vào EditText khác
                    if (newFocusView !is EditText) {
                        currentFocusedView.clearFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(currentFocusedView.windowToken, 0)
                    }
                    // ✅ Nếu click EditText khác → không làm gì,
                    //    hệ thống tự chuyển focus + giữ keyboard
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun findViewAt(view: View, x: Int, y: Int): View? {
        if (view !is ViewGroup) {
            val rect = Rect()
            view.getGlobalVisibleRect(rect)
            return if (rect.contains(x, y)) view else null
        }

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val result = findViewAt(child, x, y)
            if (result != null) return result
        }

        return null
    }

    open var onPermissionsGranted: (() -> Unit)? = null

    protected open val viewModelFactory: ViewModelProvider.Factory? = null
    protected val viewModel: VM by lazy {
        val clazz = getGenericSuperclass<VM>(1)
        if (viewModelFactory != null) {
            ViewModelProvider(this, viewModelFactory!!)[clazz]
        } else {
            ViewModelProvider(this)[clazz]
        }
    }

    private fun <KClass> Context.getGenericSuperclass(position: Int): Class<KClass> {
        val baseType = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments
        return baseType[position] as Class<KClass>
    }

    private var networkStatusReceiver: NetworkStatusReceiver? = null

    private fun registerNetwork() {
        try {
            if (null == networkStatusReceiver) {
                networkStatusReceiver = NetworkStatusReceiver()
            }
            networkStatusReceiver?.let { networkStatusReceiver ->
                if (networkStatusReceiver.isOrderedBroadcast.not()) {
                    val filter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
                    registerReceiver(networkStatusReceiver, filter)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            networkStatusReceiver?.let {
                unregisterReceiver(networkStatusReceiver)
                networkStatusReceiver = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
        registerNetwork()
    }

    abstract fun initView()
    abstract fun initListener()
    abstract fun initData()

    override fun onClick(v: View?) {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            ActivityManager.addActivity(this)
            lifecycle.addObserver(viewModel)
            configureEdgeToEdge()
            setContentView(binding.root)
            if (isHideNavBar()) {
                hideNavBar()
            }
            initView()
            initListener()
            initData()
            val className = this@BaseActivity::class.java.name
//            if (appInfo().isDebug) {
//                logApp("SCREEN_APP $className")
//            }
            onBackPressedDispatcher()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun configureEdgeToEdge() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
    }

    override fun onDestroy() {
        nativeLoadCancel?.invoke()
        nativeLoadCancel = null
        nativeAd?.destroy()
        nativeAd = null
        onBackPressedCallback.remove()
        super.onDestroy()
        try {
            nativeAdKey?.let { AppMonetization.ads.preloadNativeManagement.markHidden(it) }
            nativeAdKey = null
            lifecycle.removeObserver(viewModel)
            ActivityManager.removeActivity(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            handleBackExit()
        }
    }

    open fun handleBackExit() {
        finish()
    }

    open fun onBackPressedDispatcher() {
        // Keep this fallback in its original position. Lifecycle registration can
        // re-add it after Fragment callbacks on START, causing Back to exit on resume.
        onBackPressedCallback.remove()
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    open fun handleOnBackPressed() {
        onBackPressedCallback.handleOnBackPressed()
    }

    protected fun <T> MutableLiveData<T>.observe(function: (T) -> Unit) {
        this.observe(this@BaseActivity) {
            function.invoke(it)
        }
    }

    protected fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }

    protected fun hideNavBar() {
        try {
            hideNavigationBarsCompat()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected fun adjustInsetsForBottomNavigation(viewBottom: View) {
        ViewCompat.setOnApplyWindowInsetsListener(viewBottom) { view, insets ->
            try {
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                val displayCutout =
                    insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
                params.topMargin = (displayCutout.top + viewBottom.top / 5f).toInt()
                view.layoutParams = params
            } catch (e: Exception) {
                e.printStackTrace()
            }
            insets
        }
    }

    protected fun adjustInsetsFullBottomNavigation(viewBottom: View) {
        ViewCompat.setOnApplyWindowInsetsListener(viewBottom) { view, insets ->
            try {
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                val displayCutout =
                    insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
                params.topMargin = displayCutout.top
                params.leftMargin = displayCutout.left
                params.rightMargin = displayCutout.right
                params.bottomMargin = displayCutout.bottom
                view.layoutParams = params
            } catch (e: Exception) {
                e.printStackTrace()
            }
            insets
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isHideNavBar()) {
            hideNavBar()
        }
    }

    protected fun showNavBar() {
        showSystemBarsCompat()
    }

    open fun isHideNavBar(): Boolean = true

    protected fun currentLanguage(): String {
        return AppCompatDelegate.getApplicationLocales().toLanguageTags()
    }

    protected fun updateStatusBarAppearance() {
        val isDarkMode = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        // Xác định màu dựa trên dark mode
        val color = if (isDarkMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE

        // Set màu chữ/icon cho cả status bar và navigation bar
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }

        // Lấy root content view thay vì decorView
        val contentView = findViewById<ViewGroup>(android.R.id.content)

        // ===== STATUS BAR SCRIM =====
        var statusBarView = window.decorView.findViewWithTag<View>("status_bar_scrim")
        if (statusBarView == null) {
            statusBarView = View(this).apply {
                tag = "status_bar_scrim"
                (window.decorView as ViewGroup).addView(
                    this, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0
                    )
                )
            }
        }
        statusBarView.setBackgroundColor(color)
        ViewCompat.setOnApplyWindowInsetsListener(statusBarView) { view, insets ->
            view.layoutParams.height = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(statusBarView)
    }

    fun showInterstitialAd(
        adKey: String = AdsManager.AdPlacement.ADS_INTERSTITIAL_ID,
        onAdsClosed: () -> Unit
    ) {
        Log.d(
            "CommonInterAdDebug",
            "WRAPPER_ACTIVITY_SHOW_CALL activity=${javaClass.simpleName} " +
                    "adKey=$adKey " +
                    AppMonetization.ads.preloadInterstitialManagement.getDebugState(adKey)
        )
        AppMonetization.ads.preloadInterstitialManagement.show(
            activity = this,
            adKey = adKey,
            reload = true,
            onAdClosed = {
                Log.d(
                    "CommonInterAdDebug",
                    "WRAPPER_ACTIVITY_SHOW_CLOSED activity=${javaClass.simpleName} adKey=$adKey isShowed=$it " +
                            AppMonetization.ads.preloadInterstitialManagement.getDebugState(adKey)
                )
                onAdsClosed()
            },
            onAdShowed = {
                Log.d(
                    "CommonInterAdDebug",
                    "WRAPPER_ACTIVITY_SHOWED activity=${javaClass.simpleName} adKey=$adKey " +
                            AppMonetization.ads.preloadInterstitialManagement.getDebugState(adKey)
                )
            }
        )
    }

    fun showRewardedAd(
        onAdShowed: () -> Unit = {},
        onAdDismissed: () -> Unit = {},
        onLoadFailed: () -> Unit = {},
        onRewarded: (com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem) -> Unit
    ) {
        AppMonetization.ads.rewardedAdUtils.showRewardedAd(
            activity = this,
            onAdShowed = onAdShowed,
            onAdDismissed = onAdDismissed,
            onLoadFailed = onLoadFailed,
            onRewarded = onRewarded
        )
    }

    private fun updateLastTimeLoadBannerNativeAd(interval: Long) {
        lastTimeLoadBannerNativeAd = interval
    }

    fun loadSingleNative(
        viewNativeAd: ViewNativeAd,
        adId: Int,
        updateTimeout: Boolean = true,
        onAdsLoaded: (() -> Unit)? = {},
        onLoadFailed: (() -> Unit)? = {},
    ) {
        loadSingleNative(
            viewNativeAd = viewNativeAd,
            adKey = resources.getResourceEntryName(adId),
            updateTimeout = updateTimeout,
            onAdsLoaded = { onAdsLoaded?.invoke() },
            onLoadFailed = { onLoadFailed?.invoke() }
        )
    }

    fun loadSingleNative(
        viewNativeAd: ViewNativeAd,
        adKey: String,
        updateTimeout: Boolean = true,
        onAdsLoaded: () -> Unit = {},
        onLoadFailed: () -> Unit = {},
    ) {
        showPreloadedNativeOrLoad(viewNativeAd, adKey, updateTimeout, onAdsLoaded = onAdsLoaded, onLoadFailed = onLoadFailed)
    }

    fun showPreloadedNativeOrLoad(
        viewNativeAd: ViewNativeAd,
        adId: Int,
        updateTimeout: Boolean = false,
        reload: Boolean = true,
        onAdsLoaded: (() -> Unit)? = null,
        onLoadFailed: (() -> Unit)? = null,
        layoutRes: Int = tpt.dev.monetization.R.layout.layout_iptv_native
    ) {
        showPreloadedNativeOrLoad(
            viewNativeAd = viewNativeAd,
            adKey = resources.getResourceEntryName(adId),
            updateTimeout = updateTimeout,
            reload = reload,
            onAdsLoaded = onAdsLoaded,
            onLoadFailed = onLoadFailed,
            layoutRes = layoutRes
        )
    }

    fun showPreloadedNativeOrLoad(
        viewNativeAd: ViewNativeAd,
        adKey: String,
        updateTimeout: Boolean = false,
        reload: Boolean = true,
        onAdsLoaded: (() -> Unit)? = null,
        onLoadFailed: (() -> Unit)? = null,
        layoutRes: Int = tpt.dev.monetization.R.layout.layout_iptv_native
    ) {
        if (viewModel.isSubscribed || System.currentTimeMillis() - lastTimeLoadBannerNativeAd < 10.seconds.inWholeMilliseconds) return
        if (isFinishing || isDestroyed) return
        val manager = AppMonetization.ads.preloadNativeManagement
        val shouldReload = AppMonetization.ads.shouldReloadNativePlacement(adKey, reload)
        if (!manager.canShow(adKey, shouldReload)) return
        val resolvedLayoutRes = AppMonetization.ads.resolveNativeLayoutRes(this, adKey).takeIf { it != 0 } ?: layoutRes
        if (resolvedLayoutRes != 0) viewNativeAd.setLayoutNativeAd(resolvedLayoutRes)
        val hadCachedAd = manager.isLoaded(adKey)
        nativeLoadCancel?.invoke()
        var cancelled = false
        val callback: (NativeAd?) -> Unit = callback@{ loaded ->
            if (cancelled || isFinishing || isDestroyed || viewModel.isSubscribed) return@callback
            if (loaded == null) {
                updateLastTimeLoadBannerNativeAd(0L)
                viewNativeAd.visibility = View.GONE
                onLoadFailed?.invoke()
                return@callback
            }
            // Transfer ownership once; concurrent subscribers must not share an ad.
            val ad = manager.getNativeAd(adKey, removeAfterGet = true) ?: return@callback
            nativeAdKey?.let { if (it != adKey) manager.markHidden(it) }
            nativeAd?.destroy()
            nativeAd = ad
            nativeAdKey = adKey
            viewNativeAd.visibility = View.VISIBLE
            viewNativeAd.expandAd()
            viewNativeAd.populate(ad)
            manager.markShowing(adKey)
            if (updateTimeout) updateLastTimeLoadBannerNativeAd(System.currentTimeMillis())
            if (shouldReload && hadCachedAd) manager.load(this, adKey)
            onAdsLoaded?.invoke()
        }
        nativeLoadCancel = {
            cancelled = true
            manager.cancelLoadCallback(adKey, callback)
        }
        // Cache hit, new load and joining preload all use the same manager.
        manager.loadForOwner(this, adKey, this, callback)
    }
}
