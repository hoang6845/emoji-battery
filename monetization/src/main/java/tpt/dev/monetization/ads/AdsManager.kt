package tpt.dev.monetization.ads

import android.app.Activity
import kotlinx.coroutines.flow.update
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import tpt.dev.monetization.ads.bannerAd.BannerAdUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tpt.dev.monetization.ads.appOpenAd.AppOpenAdUtils
import tpt.dev.monetization.ads.appOpenAd.OnShowAdCompleteListener
import tpt.dev.monetization.ads.constants.AbstractAdsConstants
import tpt.dev.monetization.ads.debug.AdMediationDebugLogger
import tpt.dev.monetization.ads.interstitlaAd.InterstitialAdUtils
import tpt.dev.monetization.ads.interstitlaAd.InterstitialType
import tpt.dev.monetization.ads.nativeAd.SingleNativeAdUtils
import tpt.dev.monetization.ads.preload.PreloadBannerManagement
import tpt.dev.monetization.ads.preload.PreloadInterstitialManagement
import tpt.dev.monetization.ads.preload.PreloadNativeManagement
import tpt.dev.monetization.ads.preload.PreloadRewardedManagement
import tpt.dev.monetization.ads.rewardedAd.RewardedAdUtils
import tpt.dev.monetization.ads.remote.RemoteAdsConfig
import tpt.dev.monetization.ads.remote.ResolvedAdRequest
import tpt.dev.monetization.ads.ump.GoogleMobileAdsConsentManager
import tpt.dev.monetization.common.premium.IPremiumManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.security.MessageDigest
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class AdsManager private constructor(
    private val application: Application,
    private val premiumManager: IPremiumManager
) : DefaultLifecycleObserver {
    private val activityLifecycleCallbacks = AdActivityLifecycleCallbacks()
    private lateinit var disableAppOpenAdActivities: List<Class<*>>
    lateinit var appOpenAdUtils: AppOpenAdUtils
    lateinit var interstitialAdUtils: InterstitialAdUtils
    lateinit var rewardedAdUtils: RewardedAdUtils
    private var adMobAppId: String = ""
    private var commonInterstitialAdKey: String = ""
    private var lastTimeShowInterstitialAd = 0L
    private var timeIntervalShowInterstitialAd: Duration =
        DEFAULT_TIME_INTERVAL_SHOW_INTERSTITIAL_AD

    private var lastTimeShowOpenAd = 0L

    private var isShowingFullScreenAd = AtomicBoolean(false)
    
    // Flag để track xem app đã qua splash chưa
    // true = đã qua splash, có thể show App Open Ad khi comeback
    // false = chưa qua splash (cold start), không show App Open Ad
    private var hasCompletedSplash = AtomicBoolean(false)

    private val googleMobileAdsConsentManager =
        GoogleMobileAdsConsentManager.Companion.getInstance(application)
    private val mobileAdsInitialization = MobileAdsInitializationGate()

    val isMobileAdsReady: Boolean
        get() = mobileAdsInitialization.isReady

    /** Consent alone does not mean the Next-Gen SDK can accept requests. */
    val canRequestAds: Boolean
        get() = isMobileAdsReady && googleMobileAdsConsentManager.canRequestAds

    // Re-evaluate route-owned ads after consent/SDK or remote switches change.
    private val availabilityVersion = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val adAvailabilityChanges: kotlinx.coroutines.flow.StateFlow<Long> = availabilityVersion

    private fun notifyAdAvailabilityChanged() {
        availabilityVersion.update { it + 1 }
    }
    val singleNativeAdUtils: SingleNativeAdUtils by lazy {
        SingleNativeAdUtils(this, premiumManager, googleMobileAdsConsentManager)
    }
    val bannerAdUtils: BannerAdUtils by lazy {
        BannerAdUtils(premiumManager, googleMobileAdsConsentManager)
    }

    // Preload Management
    val preloadInterstitialManagement: PreloadInterstitialManagement by lazy {
        PreloadInterstitialManagement(application, this, premiumManager, googleMobileAdsConsentManager)
    }
    val preloadNativeManagement: PreloadNativeManagement by lazy {
        PreloadNativeManagement(application, this, premiumManager, googleMobileAdsConsentManager)
    }
    val preloadBannerManagement: PreloadBannerManagement by lazy {
        PreloadBannerManagement(application, this, premiumManager, googleMobileAdsConsentManager)
    }
    val preloadRewardedManagement: PreloadRewardedManagement by lazy {
        PreloadRewardedManagement(application, this, premiumManager, googleMobileAdsConsentManager)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appOpenAdTimeoutRunnable: Runnable? = null
    private var appOpenForegroundRunnable: Runnable? = null
    private var isRecoveringMobileAds = false
    private val resumeAdOpportunity = ResumeAdOpportunity()
    private var appOpenLoadingAdsDialog: AppOpenLoadingAdsDialog? = null
    private var timeIntervalShowInterVsOpen: Duration= DEFAULT_TIME_INTERVAL_SHOW_INTERSTITIAL_AD
    
    private var isGlobalAdsEnabled = AtomicBoolean(true)
    private var isAppOpenAdEnabled = AtomicBoolean(true)
    private var isInterSplashEnabled = AtomicBoolean(true)
    private var timeFullNative = AtomicLong(5000)
    @Volatile
    private var remoteAdsConfig: RemoteAdsConfig? = null
    private var sessionPlacementResolver = SessionPlacementResolver(isFirstSession = true)

    val isFirstAdsSession: Boolean
        get() = sessionPlacementResolver.isFirstSession

    fun configure(
        adsConstants: AbstractAdsConstants,
        disableAppOpenAdActivities: List<Class<*>> = emptyList(),
        isAllowShowOpenAd: Boolean,
        isFirstSession: Boolean = true
    ) {
        sessionPlacementResolver = SessionPlacementResolver(isFirstSession)
        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        this.disableAppOpenAdActivities = disableAppOpenAdActivities
        adMobAppId = resolveStringResource(adsConstants.ADMOB_APP_ID)
        commonInterstitialAdKey = adsConstants.ADMOB_INTERSTITIAL_ID

        appOpenAdUtils = AppOpenAdUtils(
            isAllowShowOpenAd = isAllowShowOpenAd,
            adsConstants.ADMOB_APP_OPEN_ID,
            application,
            this,
            premiumManager,
            googleMobileAdsConsentManager
        )

        interstitialAdUtils = InterstitialAdUtils(
            adsConstants.ADMOB_INTERSTITIAL_ID,
            application,
            this,
            premiumManager,
            googleMobileAdsConsentManager,
            InterstitialType.COMMON
        )

        rewardedAdUtils = RewardedAdUtils(
            adsConstants.REWARD_ID,
            this,
            premiumManager,
            googleMobileAdsConsentManager
        )

//        openInterstitialAdUtils = InterstitialAdUtils(
//            adsConstants.ADMOB_LAUNCH_INTERSTITIAL_ID,
//            application,
//            this,
//            premiumManager,
//            googleMobileAdsConsentManager,
//            InterstitialType.OPEN
//        )
    }

    fun gatherConsent(
        activity: Activity,
        listener: OnGatherConsentListener
    ) {
        googleMobileAdsConsentManager.gatherConsent(activity) { consentError ->
            if (googleMobileAdsConsentManager.canRequestAds) {
                initializeMobileAdsSdk { initializationError ->
                    listener.onCompletion(initializationError ?: consentError?.message)
                }
            } else {
                notifyAdAvailabilityChanged()
                listener.onCompletion(consentError?.message)
            }
        }
    }

    private fun initializeMobileAdsSdk(onInitialized: (String?) -> Unit) {
        mobileAdsInitialization.initialize(
            start = { complete ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        MobileAds.initialize(
                            application,
                            InitializationConfig.Builder(adMobAppId)
                                .setNativeValidatorDisabled()
                                .build()
                        ) { status ->
                            configureDebugTestDeviceIds()
                            logAdapterInitializationStatus(status.adapterStatusMap)
                            mainHandler.post { complete(null) }
                        }
                    } catch (error: Exception) {
                        Log.e("AdsManager", "Mobile Ads initialization failed", error)
                        mainHandler.post {
                            complete(error.message ?: "Mobile Ads initialization failed")
                        }
                    }
                }
            },
            onComplete = { error ->
                notifyAdAvailabilityChanged()
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    onInitialized(error)
                } else {
                    mainHandler.post { onInitialized(error) }
                }
            }
        )
    }

    fun openAdInspector() {
        if (!isMobileAdsReady) {
            Log.d("AdMediationDebug", "Skip Ad Inspector: Mobile Ads SDK is not ready")
            return
        }
        MobileAds.openAdInspector { error ->
            AdMediationDebugLogger.logAdInspectorClosed(
                error?.let { "code=${it.code} message=${it.message}" }
            )
        }
    }

    private fun configureDebugTestDeviceIds() {
        if (!application.isDebuggableApp()) {
            return
        }

        logDebugTestDeviceIdCandidate()

        val testDeviceIds = application.resolveStringArray("admob_debug_test_device_ids")
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                    it != "TEST_DEVICE_ID" &&
                    it != "PASTE_YOUR_ADMOB_TEST_DEVICE_ID_HERE"
            }

        if (testDeviceIds.isEmpty()) {
            Log.d(
                "AdMediationDebug",
                "DEBUG_TEST_DEVICES none. Add hashed/device IDs to admob_debug_test_device_ids for debug builds."
            )
            return
        }

        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
        )
        Log.d("AdMediationDebug", "DEBUG_TEST_DEVICES configured count=${testDeviceIds.size}")
    }

    private fun logDebugTestDeviceIdCandidate() {
        val androidId = Settings.Secure.getString(
            application.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()

        val hashedAndroidId = androidId.md5Uppercase()
        if (hashedAndroidId.isEmpty()) {
            Log.d("AdMediationDebug", "TEST_DEVICE_ID_CANDIDATE unavailable")
            return
        }

        Log.d(
            "AdMediationDebug",
            "TEST_DEVICE_ID_CANDIDATE=$hashedAndroidId " +
                "Put this value in app/src/debug/res/values/admob_debug.xml if Google SDK log does not print a different test device ID."
        )
    }

    private fun logAdapterInitializationStatus(adapterStatusMap: Map<String, *>) {
        adapterStatusMap.forEach { (adapterName, status) ->
            Log.d("AdMediationDebug", "INIT_ADAPTER adapter=$adapterName status=$status")
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)

        recoverMobileAdsIfNeeded()

        // Process ON_RESUME precedes the Activity's Lifecycle ON_RESUME. A cache
        // hit must wait until that dispatch finishes before checking the host.
        clearAppOpenForegroundRequest()
        val opportunity = resumeAdOpportunity.consume() ?: return
        appOpenForegroundRunnable = Runnable {
            appOpenForegroundRunnable = null
            showAppOpenAdOnForeground(opportunity)
        }
        mainHandler.post(appOpenForegroundRunnable!!)
    }

    /** Retry a failed launch initialization on a later foreground, without a late fullscreen show. */
    private fun recoverMobileAdsIfNeeded() {
        if (isMobileAdsReady || isRecoveringMobileAds || !hasCompletedSplash.get() ||
            activeRemoteAdsConfig() == null || !isGlobalAdsEnabled.get() || premiumManager.isSubscribed()
        ) return
        val activity = activityLifecycleCallbacks.currentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        isRecoveringMobileAds = true
        Log.d("AdsManager", "Retry consent/SDK initialization on foreground")
        val listener = object : OnGatherConsentListener {
            override fun onCompletion(error: String?) {
                isRecoveringMobileAds = false
                Log.d("AdsManager", "Foreground SDK recovery: ready=$isMobileAdsReady error=$error")
                val current = activityLifecycleCallbacks.currentActivity ?: return
                if (!canRequestAds || current.isFinishing || current.isDestroyed ||
                    !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) return
                preloadCommonInterstitialAd(current)
                preloadResumeAd(current)
            }
        }
        try {
            gatherConsent(activity, listener)
        } catch (error: Exception) {
            isRecoveringMobileAds = false
            Log.e("AdsManager", "Foreground SDK recovery failed", error)
        }
    }

    private fun clearAppOpenForegroundRequest() {
        appOpenForegroundRunnable?.let { mainHandler.removeCallbacks(it) }
        appOpenForegroundRunnable = null
    }

    private fun showAppOpenAdOnForeground(opportunity: Long) {
        if (!resumeAdOpportunity.isCurrent(opportunity)) return
        if (!canRequestAds) {
            Log.d("AppLifecycle", "Skip: Consent unavailable or Mobile Ads SDK is not ready")
            return
        }
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            Log.d("AppLifecycle", "Skip: Process is no longer resumed")
            return
        }

        if (!isGlobalAdsEnabled.get()) {
            Log.d("AppLifecycle", "Skip: Global ads disabled")
            return
        }

        if (!isAppOpenAdEnabled.get()) {
            Log.d("AppLifecycle", "Skip: App Open Ad disabled")
            return
        }

        if (!hasCompletedSplash.get()) {
            Log.d("AppLifecycle", "Skip: Splash chưa hoàn thành")
            return
        }

        if (checkIsShowingFullScreenAd()) {
            Log.d("AppLifecycle", "Skip: Đang hiển thị fullscreen ad")
            return
        }

        val currentActivity = activityLifecycleCallbacks.currentActivity as? AppCompatActivity
        if (currentActivity == null) {
            Log.d("AppLifecycle", "Skip: currentActivity null hoặc không phải AppCompatActivity")
            return
        }

        if (!currentActivity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            Log.d("AppLifecycle", "Skip: Activity not resumed, state=${currentActivity.lifecycle.currentState}")
            return
        }

        Log.d("AppLifecycle", "Current Activity: ${currentActivity.javaClass.name}")

        if (disableAppOpenAdActivities.any { it.name == currentActivity.javaClass.name }) {
            Log.d("AppLifecycle", "Skip: Activity bị disable app open ad")
            return
        }

        if (appOpenLoadingAdsDialog?.isShowing == true) {
            Log.d("AppLifecycle", "Skip: Loading dialog đang hiển thị")
            return
        }

        if (usesInterSplashOnResume()) {
            showResumeInterstitial(currentActivity, opportunity)
        } else {
            Log.d("AppLifecycle", "Show App Open Ad triggered")
            showAppOpenAdWithLoading(currentActivity)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        clearAppOpenForegroundRequest()
        resumeAdOpportunity.onBackground(hasCompletedSplash.get(), checkIsShowingFullScreenAd())
        if (usesInterSplashOnResume() && hasCompletedSplash.get() && !checkIsShowingFullScreenAd()) {
            activityLifecycleCallbacks.currentActivity?.let(::preloadResumeAd)
        }
        clearAppOpenAdTimeout()
        dismissAppOpenLoadingAdsDialog()
        appOpenAdUtils.setPendingShowAd(false)
    }

    fun usesInterSplashOnResume(): Boolean =
        remoteAdsConfig?.configs?.useInterSplashOnResume == true

    /** Prepare the selected resume format; a miss on foreground never shows Inter later. */
    fun preloadResumeAd(activity: Activity) {
        if (!isAppOpenAdEnabled.get() || !isGlobalAdsEnabled.get()) return
        if (usesInterSplashOnResume()) {
            preloadInterstitialManagement.load(activity, AdPlacement.ADS_INTERSTITIAL_ID_AFTER_SESSION)
        } else {
            appOpenAdUtils.preload()
        }
    }

    private fun canShowResumeInterstitial(activity: AppCompatActivity, opportunity: Long): Boolean {
        val now = System.currentTimeMillis()
        val interval = timeIntervalShowInterVsOpen.inWholeMilliseconds
        return resumeAdOpportunity.isCurrent(opportunity) && usesInterSplashOnResume() &&
            isAppOpenAdEnabled.get() && isGlobalAdsEnabled.get() && hasCompletedSplash.get() &&
            !premiumManager.isSubscribed() && canRequestAds &&
            canUsePlacement(AdPlacement.ADS_INTERSTITIAL_ID_AFTER_SESSION) &&
            activityLifecycleCallbacks.currentActivity === activity &&
            !activity.isDestroyed && !activity.isFinishing &&
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            now - lastTimeShowInterstitialAd >= interval && now - lastTimeShowOpenAd >= interval
    }

    private fun showResumeInterstitial(activity: AppCompatActivity, opportunity: Long) {
        if (!canShowResumeInterstitial(activity, opportunity)) return
        // Resume always uses the after-session pool, including the first launch session.
        val key = AdPlacement.ADS_INTERSTITIAL_ID_AFTER_SESSION
        if (!preloadInterstitialManagement.isLoaded(key)) {
            Log.d("AppLifecycle", "Resume inter cache miss; preload for next opportunity")
            preloadResumeAd(activity)
            return
        }
        preloadInterstitialManagement.showGuarded(
            activity = activity,
            adKey = key,
            shouldShow = { canShowResumeInterstitial(activity, opportunity) },
            onAdClosed = { preloadResumeAd(activity) }
        )
    }

    private fun showAppOpenAdWithLoading(activity: AppCompatActivity) {
        Log.d("AppLifecycle", "showAppOpenAdWithLoading: start")

        if (activity.isFinishing || activity.isDestroyed) {
            Log.d("AppLifecycle", "showAppOpenAdWithLoading: activity invalid (finishing/destroyed)")
            return
        }

        if (!appOpenAdUtils.canShowOpenAdNow()) return

        val hasCachedAd = appOpenAdUtils.isAdAvailable()
        if (!hasCachedAd) {
            Log.d("AppLifecycle", "showAppOpenAdWithLoading: show loading dialog")
            showAppOpenLoadingAdsDialog(activity)
        }

        Log.d("AppLifecycle", "showAppOpenAdWithLoading: start timeout")
        startAppOpenAdTimeout()

        Log.d("AppLifecycle", "showAppOpenAdWithLoading: call showAdIfAvailable")

        appOpenAdUtils.showAdIfAvailable(activity, object : OnShowAdCompleteListener {
            override fun onAdShown() {
                clearAppOpenAdTimeout()
                dismissAppOpenLoadingAdsDialog()
            }

            override fun onShowAdComplete() {
                Log.d("AppLifecycle", "onShowAdComplete: app open ad finished")

                clearAppOpenAdTimeout()
                Log.d("AppLifecycle", "onShowAdComplete: timeout cleared")

                dismissAppOpenLoadingAdsDialog()
                Log.d("AppLifecycle", "onShowAdComplete: loading dialog dismissed")

                appOpenAdUtils.setPendingShowAd(false)
                Log.d("AppLifecycle", "onShowAdComplete: pending show reset")
            }
        })
    }
    private fun showAppOpenLoadingAdsDialog(activity: Activity) {
        dismissAppOpenLoadingAdsDialog()
        try {
            appOpenLoadingAdsDialog = AppOpenLoadingAdsDialog(activity)
            appOpenLoadingAdsDialog?.show()
        } catch (e: Exception) {
            appOpenLoadingAdsDialog = null
        }
    }

    private fun dismissAppOpenLoadingAdsDialog() {
        try {
            if (appOpenLoadingAdsDialog?.isShowing == true) {
                appOpenLoadingAdsDialog?.dismiss()
            }
        } catch (e: Exception) {
        } finally {
            appOpenLoadingAdsDialog = null
        }
    }

    private fun startAppOpenAdTimeout() {
        clearAppOpenAdTimeout()
        appOpenAdTimeoutRunnable = Runnable {
            appOpenAdUtils.setPendingShowAd(false)
            dismissAppOpenLoadingAdsDialog()
        }
        mainHandler.postDelayed(
            appOpenAdTimeoutRunnable!!,
            APP_OPEN_AD_TIMEOUT.inWholeMilliseconds
        )
    }

    private fun clearAppOpenAdTimeout() {
        appOpenAdTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        appOpenAdTimeoutRunnable = null
    }

    fun preloadCommonInterstitialAd(activity: Activity) {
        if (commonInterstitialAdKey.isEmpty()) {
            Log.d("CommonInterAdDebug", "PRELOAD_COMMON_SKIP_EMPTY_KEY")
            return
        }

        Log.d(
            "CommonInterAdDebug",
            "PRELOAD_COMMON_CALL activity=${activity.javaClass.simpleName} " +
                    getCommonInterstitialDebugState()
        )
        preloadInterstitialManagement.load(activity, commonInterstitialAdKey)
    }

    fun showCommonInterstitialAd(
        activity: Activity,
        onAdShowed: (() -> Unit)? = null,
        onAdClosed: (Boolean) -> Unit
    ) {
        if (commonInterstitialAdKey.isEmpty()) {
            Log.d("CommonInterAdDebug", "SHOW_COMMON_SKIP_EMPTY_KEY")
            onAdClosed(false)
            return
        }

        Log.d(
            "CommonInterAdDebug",
            "SHOW_COMMON_CALL activity=${activity.javaClass.simpleName} " +
                    getCommonInterstitialDebugState()
        )
        preloadInterstitialManagement.show(
            activity = activity,
            adKey = commonInterstitialAdKey,
            reload = true,
            onAdShowed = onAdShowed,
            onAdClosed = onAdClosed
        )
    }

    fun getCommonInterstitialDebugState(): String {
        return if (commonInterstitialAdKey.isEmpty()) {
            "state{adKey=empty}"
        } else {
            "state{adKey=$commonInterstitialAdKey, " +
                    preloadInterstitialManagement.getDebugState(commonInterstitialAdKey) +
                    "}"
        }
    }

    fun updateTimeIntervalShowInterstitialAd(interval: Duration) {
        timeIntervalShowInterstitialAd = interval
    }

    internal fun getTimeIntervalShowInterstitialAd() = timeIntervalShowInterstitialAd

    internal fun updateLastTimeShowInterstitialAd(value: Long) {
        lastTimeShowInterstitialAd = value
    }

    internal fun getLastTimeShowInterstitialAd() = lastTimeShowInterstitialAd


    internal fun updateLastTimeShowOpenAd(value: Long) {
        lastTimeShowOpenAd = value
    }

    fun updateTimeIntervalShowInterVsOpen(interval: Duration) {
        timeIntervalShowInterVsOpen = interval
    }

    internal fun getTimeIntervalShowInterVsOpen() = timeIntervalShowInterVsOpen
    
    /**
     * Bật/tắt tất cả ads trong app
     */
    fun setGlobalAdsEnabled(enabled: Boolean) {
        isGlobalAdsEnabled.set(enabled)
        notifyAdAvailabilityChanged()
        Log.d("AdsManager", "Global ads enabled: $enabled")
    }
    
    fun isGlobalAdsEnabled() = isGlobalAdsEnabled.get()

    fun updateRemoteAdsConfig(config: RemoteAdsConfig?) {
        remoteAdsConfig = config?.takeIf { it.hasRemoteData() }
        notifyAdAvailabilityChanged()
        Log.d(
            "AdsManager",
            "Remote ads config applied: ${remoteAdsConfig?.placements.orEmpty().size} placements"
        )
    }

    internal fun activeRemoteAdsConfig(): RemoteAdsConfig? {
        return remoteAdsConfig?.takeIf { it.hasRemoteData() }
    }

    /** Require a real configured placement; never request a placement key as a raw ad unit. */
    fun isConfiguredPlacementEnabled(requestKey: String): Boolean =
        activeRemoteAdsConfig() != null && canUsePlacement(requestKey) &&
            resolveAdRequests(requestKey).isNotEmpty()

    fun resolveSessionPlacementKey(requestKey: String): String {
        val config = activeRemoteAdsConfig()
        val key = config?.resolvePlacementKey(requestKey) ?: requestKey.trim()
        return sessionPlacementResolver.resolve(key, config?.placements.orEmpty().keys)
    }

    internal fun resolveAdPoolKey(requestKey: String): String = resolveSessionPlacementKey(requestKey)

    internal fun resolveAdRequests(requestKey: String): List<ResolvedAdRequest> {
        val key = resolveSessionPlacementKey(requestKey)
        if (key.isEmpty()) return emptyList()

        val remoteConfig = activeRemoteAdsConfig()
        if (remoteConfig != null) {
            return remoteConfig.resolveAdRequests(key)
        }

        return listOf(
            ResolvedAdRequest(
                requestKey = key,
                placementKey = key,
                adItemKey = key,
                unitKey = key,
                network = "admob",
                unitId = key,
                nativeConfig = null
            )
        )
    }

    internal fun canUsePlacement(requestKey: String): Boolean {
        val key = resolveSessionPlacementKey(requestKey)
        if (key.isEmpty()) return false
        if (!isGlobalAdsEnabled.get()) return false

        return activeRemoteAdsConfig()?.isPlacementEnabled(key) ?: true
    }

    internal fun isResolvedAdStillEnabled(source: ResolvedAdRequest?): Boolean {
        if (source == null) return activeRemoteAdsConfig() == null
        return activeRemoteAdsConfig()?.isResolvedSourceStillEnabled(source) ?: true
    }

    internal fun shouldReloadPlacement(requestKey: String, requestedReload: Boolean): Boolean {
        return activeRemoteAdsConfig()?.shouldReload(resolveSessionPlacementKey(requestKey), requestedReload) ?: requestedReload
    }

    fun shouldReloadNativePlacement(requestKey: String, requestedReload: Boolean): Boolean {
        val placementKey = resolveSessionPlacementKey(requestKey).removeSuffix("_after_session")
        if (placementKey == AdPlacement.ADS_NATIVE_LANGUAGE_ID ||
            placementKey == AdPlacement.ADS_NATIVE_LANGUAGE_CLICK
        ) return false
        return activeRemoteAdsConfig()?.shouldReloadNative(resolveSessionPlacementKey(requestKey), requestedReload) ?: requestedReload
    }

    fun resolveNativeLayoutRes(context: Context, requestKey: String): Int {
        val layoutName = activeRemoteAdsConfig()
            ?.nativeLayoutNameForRequest(resolveSessionPlacementKey(requestKey))
            ?: return 0

        return context.resources.getIdentifier(
            layoutName,
            "layout",
            context.packageName
        )
    }

    private fun resolveStringResource(nameOrValue: String): String {
        val resourceId = application.resources.getIdentifier(
            nameOrValue,
            "string",
            application.packageName
        )
        return if (resourceId != 0) {
            application.getString(resourceId)
        } else {
            nameOrValue
        }
    }

    private fun Context.resolveStringArray(name: String): List<String> {
        val resourceId = resources.getIdentifier(name, "array", packageName)
        if (resourceId == 0) return emptyList()
        return resources.getStringArray(resourceId).toList()
    }

    private fun Context.isDebuggableApp(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun String.md5Uppercase(): String {
        if (isBlank()) return ""
        val digest = MessageDigest.getInstance("MD5").digest(toByteArray())
        return digest.joinToString(separator = "") { "%02X".format(Locale.US, it) }
    }
    
    /**
     * Bật/tắt App Open Ad
     */
    fun setAppOpenAdEnabled(enabled: Boolean) {
        isAppOpenAdEnabled.set(enabled)
        Log.d("AdsManager", "App Open Ad enabled: $enabled")
    }
    
    fun isAppOpenAdEnabled() = isAppOpenAdEnabled.get()
    
    /**
     * Bật/tắt Interstitial sau splash
     */
    fun setInterSplashEnabled(enabled: Boolean) {
        isInterSplashEnabled.set(enabled)
        Log.d("AdsManager", "Inter Splash enabled: $enabled")
    }

    fun setTimeFullNative(time: Long) {
        timeFullNative.set(time)
    }
    
    fun isInterSplashEnabled() = isInterSplashEnabled.get()



    internal fun getLastTimeShowOpenAd() = lastTimeShowOpenAd


    internal fun checkIsShowingFullScreenAd() = isShowingFullScreenAd.get()

    internal fun updateIsShowingFullScreenAd(value: Boolean) {
        isShowingFullScreenAd.set(value)
    }
    
    fun markSplashCompleted() {
        hasCompletedSplash.set(true)
    }

    fun resetSplashFlag() {
        hasCompletedSplash.set(false)
    }

    interface OnGatherConsentListener {
        fun onCompletion(error: String?)
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        clearAppOpenForegroundRequest()
        resumeAdOpportunity.invalidate()
        Log.d("AppLifecycle", "pause")

    }

    object AdPlacement {
        const val ADS_INTERSTITIAL_ID = "ads_interstitial_id"
        const val ADS_INTERSTITIAL_ID_AFTER_SESSION = "ads_interstitial_id_after_session"
        const val ADS_INTER_SPLASH = "ads_inter_splash"
        const val FULL_BACK = "full_back"
        const val INTER_CHANGE_TAB = "inter_change_tab"
        const val ADS_OPEN_ID = "ads_open_id"
        const val REWARD = "reward"
        const val ADS_BANNER_ADD_BOTTOM_SHEET = "ads_banner_add_bottom_sheet"
        const val ADS_COLLAPSE_CHANNEL = "ads_collapse_channel"
        const val ADS_NATIVE_HOME = "ads_native_home"
        const val ADS_NATIVE_IMPORT_LINK = "ads_native_import_link"
        const val ADS_NATIVE_UPLOAD_FILE = "ads_native_upload_file"
        const val ADS_NATIVE_INTRO_1 = "ads_native_intro1"
        const val ADS_NATIVE_INTRO_2 = "ads_native_intro2"
        const val ADS_NATIVE_INTRO_FULL = "ads_native_intro_full_id"
        const val ADS_NATIVE_LANGUAGE_ID = "ads_native_language_id"
        const val ADS_NATIVE_LANGUAGE_CLICK = "ads_native_language_click"
    }

    companion object {
        private val DEFAULT_TIME_INTERVAL_SHOW_INTERSTITIAL_AD: Duration = 20.seconds
        private val APP_OPEN_AD_TIMEOUT: Duration = 10.seconds

        @Volatile
        private var instance: AdsManager? = null

        /**
         * Returns the singleton instance of AdsManager.
         *
         * @return The AdsManager instance.
         */
        fun getInstance(): AdsManager =
            instance ?: synchronized(this) {
                instance ?: throw AssertionError("You have to call initialize first")
            }

        fun initialize(
            application: Application,
            premiumManager: IPremiumManager
        ): AdsManager {
            if (instance != null) throw AssertionError("You already initialized me")
            return AdsManager(application, premiumManager).also { instance = it }
        }
    }
}

