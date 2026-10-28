package hoang.dqm.codebase.base.activity

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import hoang.dqm.codebase.base.application.appInfo
import hoang.dqm.codebase.base.viewmodel.BaseViewModel
import hoang.dqm.codebase.service.session.saveFirst
import hoang.dqm.codebase.utils.AppMonetization
import hoang.dqm.codebase.utils.BindingReflex
import hoang.dqm.codebase.utils.ads
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.nativeAd.view.ViewNativeAd
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import kotlin.time.Duration.Companion.seconds

@Suppress("unused")
abstract class BaseFragment<VB : ViewBinding, VM : BaseViewModel> : Fragment(),
    View.OnClickListener, DefaultLifecycleObserver {

    private var isLoaded = false
    private var lastClickTime = 0L
    private var lastTimeLoadBannerNativeAd = 0L
    private var nativeAd: NativeAd? = null
    private var nativeAdKey: String? = null
    private var nativeLoadCancel: (() -> Unit)? = null
    private val interval by lazy { 500L }
    protected var _binding: VB? = null
    protected val binding
        get() = _binding ?: BindingReflex.reflexViewBinding(
            javaClass, layoutInflater
        )

    @Suppress("UNCHECKED_CAST")
    private fun <KClass> genericViewModel(): Class<KClass> {
        val bindings = mutableMapOf<TypeVariable<*>, Type>()
        var cursor: Class<*>? = javaClass
        while (cursor != null && cursor != Any::class.java) {
            val parent = cursor.genericSuperclass ?: break
            val raw = when (parent) {
                is ParameterizedType -> parent.rawType as? Class<*>
                is Class<*> -> parent
                else -> null
            } ?: break
            if (parent is ParameterizedType) {
                parent.actualTypeArguments.forEachIndexed { index, type ->
                    val variable = raw.typeParameters.getOrNull(index) ?: return@forEachIndexed
                    bindings[variable] = resolveType(type, bindings)
                }
                if (raw == BaseFragment::class.java) {
                    return when (val type = resolveType(parent.actualTypeArguments[1], bindings)) {
                        is Class<*> -> type as Class<KClass>
                        is ParameterizedType -> type.rawType as Class<KClass>
                        else -> error("BaseFragment ViewModel must resolve to a concrete class: $type")
                    }
                }
            }
            cursor = raw
        }
        error("${javaClass.name} must declare a concrete BaseFragment ViewModel type")
    }

    private fun resolveType(type: Type, bindings: Map<TypeVariable<*>, Type>): Type =
        if (type is TypeVariable<*>) bindings[type]?.let { resolveType(it, bindings) } ?: type else type

    protected open val viewModelFactory: ViewModelProvider.Factory
        get() = defaultViewModelProviderFactory

//    protected val viewModel: VM by lazy {
//        val clazz = genericViewModel<VM>()
//        if (viewModelFactory != null) {
//            ViewModelProvider(this, viewModelFactory!!)[clazz]
//        } else {
//            ViewModelProvider(this)[clazz]
//        }
//    }

    protected val viewModel: VM by lazy {
        val clazz = genericViewModel<VM>()
        ViewModelProvider(this, viewModelFactory)[clazz]
    }

    protected var activity: Activity? = null

    abstract fun initView()
    abstract fun initListener()
    abstract fun initData()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity = requireActivity()
        FragmentManager.addFragment(this)
    }

    override fun onPause() {
        super<Fragment>.onPause()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        try {
            lifecycle.addObserver(viewModel)
            _binding = BindingReflex.reflexViewBinding(javaClass, inflater)
            return _binding?.root
        } catch (e: Exception) {
            e.printStackTrace()
            return View(context)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("SCREEN_APP", this::class.java.name)
        try {
//            trackingScreen()
            initView()
            initListener()
            viewModel.isLoading.observe { showLoading(it) }
            if (!isLoaded) {
                initData()
                isLoaded = true
            }
//            logApp("onViewCreated $this")
            if (appInfo().isHomeClass(this::class.java.name)) {
                saveFirst(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super<Fragment>.onResume()
    }

    open fun idFragmentContainer(): Int = 0

    override fun onDestroy() {
        isLoaded = false
        super<Fragment>.onDestroy()
        FragmentManager.removeFragment(this)
    }

    override fun onDetach() {
        _binding = null
        lifecycle.removeObserver(this)
        super.onDetach()
    }

    override fun onClick(v: View) {
        val nowTime = System.currentTimeMillis()
        if (nowTime - lastClickTime > interval) {
            onSingleClickFrag(v)
            lastClickTime = nowTime
        }
    }

    open fun onSingleClickFrag(v: View) {

    }

    protected fun <T> MutableLiveData<T>.observe(function: (T) -> Unit) {
        this.observe(viewLifecycleOwner) {
            function.invoke(it)
        }
    }

    protected fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }

    fun showLoading(show: Boolean) {
    }

    fun showForceLoading(show: Boolean) {
    }

    override fun onDestroyView() {
        nativeLoadCancel?.invoke()
        nativeLoadCancel = null
        showLoading(false)
        nativeAd?.destroy()
        nativeAd = null
        nativeAdKey?.let { AppMonetization.ads.preloadNativeManagement.markHidden(it) }
        nativeAdKey = null
        _binding = null
        super.onDestroyView()
    }

    fun showInterstitialAd(
        adKey: String = AdsManager.AdPlacement.ADS_INTERSTITIAL_ID,
        onAdsClosed: () -> Unit
    ) {
        (activity as? AppCompatActivity)?.let { activity ->
            Log.d(
                "CommonInterAdDebug",
                "WRAPPER_FRAGMENT_SHOW_CALL fragment=${javaClass.simpleName} " +
                        "activity=${activity.javaClass.simpleName} " +
                        "adKey=$adKey " +
                        AppMonetization.ads.preloadInterstitialManagement.getDebugState(adKey)
            )
            AppMonetization.ads.preloadInterstitialManagement.show(
                activity = activity,
                adKey = adKey,
                reload = true,
                onAdClosed = {
                    Log.d(
                        "CommonInterAdDebug",
                        "WRAPPER_FRAGMENT_SHOW_CLOSED fragment=${javaClass.simpleName} " +
                                "activity=${activity.javaClass.simpleName} adKey=$adKey isShowed=$it " +
                                AppMonetization.ads.preloadInterstitialManagement.getDebugState(adKey)
                    )
                    onAdsClosed()
                },
                onAdShowed = {
                    Log.d(
                        "CommonInterAdDebug",
                        "WRAPPER_FRAGMENT_SHOWED fragment=${javaClass.simpleName} " +
                                "activity=${activity.javaClass.simpleName} " +
                                "adKey=$adKey " +
                                AppMonetization.ads.preloadInterstitialManagement.getDebugState(adKey)
                    )
                }
            )
        }
    }

    fun showRewardedAd(
        onAdShowed: () -> Unit = {},
        onAdDismissed: () -> Unit = {},
        onLoadFailed: () -> Unit = {},
        onRewarded: (com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem) -> Unit
    ) {
        (activity as? AppCompatActivity)?.let { activity ->
            AppMonetization.ads.rewardedAdUtils.showRewardedAd(
                activity = activity,
                onAdShowed = onAdShowed,
                onAdDismissed = onAdDismissed,
                onLoadFailed = onLoadFailed,
                onRewarded = onRewarded
            )
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

    /**
     * Neo view phía trên navigation bar (bottom margin)
     */
    protected fun adjustInsetsAboveNavigationBar(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            try {
                val params = v.layoutParams as ViewGroup.MarginLayoutParams
                val navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                params.bottomMargin = navigationBarInset
                v.layoutParams = params
            } catch (e: Exception) {
                e.printStackTrace()
            }
            insets
        }
    }

    /**
     * Neo root view để tránh navigation bar (padding bottom)
     */
    protected fun applyNavigationBarInsetPadding(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            try {
                val navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                v.setPadding(
                    v.paddingLeft,
                    v.paddingTop,
                    v.paddingRight,
                    navigationBarInset
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            insets
        }
    }

    private fun updateLastTimeLoadBannerNativeAd(interval: Long) {
        lastTimeLoadBannerNativeAd = interval
    }

    fun loadSingleNative(
        viewNativeAd: ViewNativeAd,
        adId: Int,
        updateTimeout: Boolean = true
    ) {
        loadSingleNative(
            viewNativeAd = viewNativeAd,
            adKey = resources.getResourceEntryName(adId),
            updateTimeout = updateTimeout
        )
    }

    fun loadSingleNative(
        viewNativeAd: ViewNativeAd,
        adKey: String,
        updateTimeout: Boolean = true
    ) {
        showPreloadedNativeOrLoad(viewNativeAd, adKey, updateTimeout)
    }

    fun showPreloadedNativeOrLoad(
        viewNativeAd: ViewNativeAd,
        adId: Int,
        updateTimeout: Boolean = false,
        reload: Boolean = true
    ) {
        showPreloadedNativeOrLoad(
            viewNativeAd = viewNativeAd,
            adKey = resources.getResourceEntryName(adId),
            updateTimeout = updateTimeout,
            reload = reload
        )
    }

    fun showPreloadedNativeOrLoad(
        viewNativeAd: ViewNativeAd,
        adKey: String,
        updateTimeout: Boolean = false,
        reload: Boolean = true
    ) {
        if (viewModel.isSubscribed || System.currentTimeMillis() - lastTimeLoadBannerNativeAd < 10.seconds.inWholeMilliseconds) return
        val owner = viewLifecycleOwnerLiveData.value ?: return
        val manager = AppMonetization.ads.preloadNativeManagement
        val shouldReload = AppMonetization.ads.shouldReloadNativePlacement(adKey, reload)
        if (!manager.canShow(adKey, shouldReload)) return
        val resolvedLayoutRes = AppMonetization.ads.resolveNativeLayoutRes(requireContext(), adKey)
        if (resolvedLayoutRes != 0) viewNativeAd.setLayoutNativeAd(resolvedLayoutRes)
        val hadCachedAd = manager.isLoaded(adKey)
        nativeLoadCancel?.invoke()
        var cancelled = false
        val callback: (NativeAd?) -> Unit = callback@{ loaded ->
            if (cancelled || !isAdded || view == null || viewLifecycleOwnerLiveData.value !== owner || viewModel.isSubscribed) return@callback
            if (loaded == null) {
                updateLastTimeLoadBannerNativeAd(0L)
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
            if (shouldReload && hadCachedAd) manager.load(requireActivity(), adKey)
        }
        nativeLoadCancel = {
            cancelled = true
            manager.cancelLoadCallback(adKey, callback)
        }
        // Cache hit, new load and joining preload all use the same manager.
        manager.loadForOwner(requireActivity(), adKey, owner, callback)
    }
}

fun Fragment.onBackPressed(runnable: Runnable? = null) {
    activity?.onBackPressedDispatcher?.addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                runnable?.run()
            }
        })
}
