package hoang.dqm.codebase.base.activity

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import hoang.dqm.codebase.utils.AppMonetization
import hoang.dqm.codebase.utils.ads
import tpt.dev.monetization.ads.AdsManager

abstract class BaseBottomSheetFragment<VB : ViewBinding> : BottomSheetDialogFragment() {
    protected var _binding: VB? = null
    protected val binding get() = _binding!!

    abstract fun getVB(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = getVB(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            initView()
            initData()
            initObserver()
//            resizeWhenOpenKeyboard(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
        
        // Thiết lập window flags để bottom sheet không ảnh hưởng navigation bar của activity
        dialog?.window?.let { window ->
            // Đảm bảo dialog không thay đổi system UI của activity bên dưới
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    private fun resizeWhenOpenKeyboard(view: View) {
        val bottomSheetBehavior = BottomSheetBehavior.from(view.parent as View)
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    abstract fun initView()

    open fun initData() = Unit
    open fun initObserver() = Unit
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
    fun showInterstitialAd(
        adKey: String = AdsManager.AdPlacement.ADS_INTERSTITIAL_ID,
        onAdsClosed: () -> Unit
    ) {
        (activity as? AppCompatActivity)?.let { activity ->
            Log.d(
                "CommonInterAdDebug",
                "WRAPPER_BOTTOM_SHEET_SHOW_CALL bottomSheet=${javaClass.simpleName} " +
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
                        "WRAPPER_BOTTOM_SHEET_SHOW_CLOSED bottomSheet=${javaClass.simpleName} " +
                                "activity=${activity.javaClass.simpleName} adKey=$adKey isShowed=$it " +
                                AppMonetization.ads.preloadInterstitialManagement.getDebugState(adKey)
                    )
                    onAdsClosed()
                },
                onAdShowed = {
                    Log.d(
                        "CommonInterAdDebug",
                        "WRAPPER_BOTTOM_SHEET_SHOWED bottomSheet=${javaClass.simpleName} " +
                                "activity=${activity.javaClass.simpleName} " +
                                "adKey=$adKey " +
                                AppMonetization.ads.preloadInterstitialManagement.getDebugState(adKey)
                    )
                }
            )
        }
    }
}
