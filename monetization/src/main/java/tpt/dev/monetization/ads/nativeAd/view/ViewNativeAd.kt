package tpt.dev.monetization.ads.nativeAd.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.core.view.children
import com.faltenreich.skeletonlayout.Skeleton
import com.faltenreich.skeletonlayout.SkeletonConfig
import com.faltenreich.skeletonlayout.SkeletonLayout
import com.faltenreich.skeletonlayout.createSkeleton
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import tpt.dev.monetization.R
import tpt.dev.monetization.ads.nativeAd.NativeAdCalculator
import tpt.dev.monetization.databinding.ViewNativeAdBinding

class ViewNativeAd : FrameLayout {
    private var binding: ViewNativeAdBinding? = null
    private var nativeAdViewLayout: View? = null
    private var skeleton: Skeleton? = null
    private var isCollapsing = false
    var onCollapseStarted: (() -> Unit)? = null
    var onCollapsed: (() -> Unit)? = null
    /** The host can replace the expanded creative with a compact native using the same owner. */
    var onCollapseRequested: (() -> Unit)? = null

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(attrs, defStyleAttr)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        binding = ViewNativeAdBinding.inflate(LayoutInflater.from(context), this, true)

        // Load attributes
        val styledAttributes = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.ViewNativeAd,
            defStyle,
            0
        )
        val layoutNativeAdRes = styledAttributes.getResourceId(
            R.styleable.ViewNativeAd_native_ad_layout,
            R.layout.layout_medium_native_ads
        )
        val maskColor = styledAttributes.getColor(
            R.styleable.ViewNativeAd_mask_color,
            resources.getColor(R.color.viewNativeAdMaskColor, context.theme)
        )
        val shimmerColor = styledAttributes.getColor(
            R.styleable.ViewNativeAd_shimmer_color,
            resources.getColor(R.color.viewNativeAdShimmerColor, context.theme)
        )
        styledAttributes.recycle()

        binding?.run {
            // Inflate and add the native ad layout to ViewFlipper
            nativeAdViewLayout = LayoutInflater.from(context).inflate(layoutNativeAdRes, viewFlipper, false)
            viewFlipper.addView(nativeAdViewLayout)
            updateContentHeight()
            nativeAdViewLayout?.let {
                setupCollapseButton(it)
            }
            // Create Skeleton for the inflated layout
            skeleton = nativeAdViewLayout?.createSkeleton(
                config = SkeletonConfig(
                    maskColor = maskColor,
                    maskCornerRadius = 8f,
                    showShimmer = true,
                    shimmerColor = shimmerColor,
                    shimmerDurationInMillis = 1500L,
                    shimmerDirection = SkeletonLayout.DEFAULT_SHIMMER_DIRECTION,
                    shimmerAngle = 20
                )
            )
            skeleton?.showSkeleton()
        }
    }

    fun setLayoutNativeAd(@LayoutRes newLayoutRes: Int) {

        if (nativeAdViewLayout != null) {
            binding?.viewFlipper?.removeAllViews()
        }

        // Supply the actual parent so root XML dimensions and margins survive inflation.
        val newNativeAdViewLayout = LayoutInflater.from(context).inflate(newLayoutRes, binding?.viewFlipper, false)
        binding?.viewFlipper?.addView(newNativeAdViewLayout)

        nativeAdViewLayout = newNativeAdViewLayout
        updateContentHeight()

        setupCollapseButton(newNativeAdViewLayout)

        skeleton = nativeAdViewLayout?.createSkeleton(
            config = SkeletonConfig(
                maskColor = resources.getColor(R.color.viewNativeAdMaskColor, context.theme),
                maskCornerRadius = 8f,
                showShimmer = true,
                shimmerColor = resources.getColor(R.color.viewNativeAdShimmerColor, context.theme),
                shimmerDurationInMillis = 1500L,
                shimmerDirection = SkeletonLayout.DEFAULT_SHIMMER_DIRECTION,
                shimmerAngle = 20
            )
        )

        skeleton?.showSkeleton()
    }

    private fun updateContentHeight() {
        val root = binding?.root ?: return
        val isFullNative = nativeAdViewLayout?.layoutParams?.height == ViewGroup.LayoutParams.MATCH_PARENT
        // Keep an opaque backdrop behind the skeleton while it replaces the creative.
        root.setBackgroundColor(
            if (isFullNative) resources.getColor(R.color.adsBackground, context.theme)
            else android.graphics.Color.TRANSPARENT
        )
        // Full-screen creatives need every wrapper to fill the host. Fixed-height and
        // compact creatives still measure from their own XML dimensions.
        root.layoutParams = root.layoutParams.apply {
            height = if (isFullNative) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
    }

    private fun setupCollapseButton(root: View) {
        root.findViewById<View?>(R.id.iv_close)?.setOnClickListener {
            onCollapseRequested?.invoke() ?: collapseWithMoveDown()
        }
    }

    private fun collapseAd() {
        visibility = View.GONE
    }

    fun expandAd() {
        visibility = View.VISIBLE
    }

    private fun collapseWithMoveDown() {
        if (isCollapsing) return
        isCollapsing = true
        onCollapseStarted?.invoke()

        val moveDistance = if (height > 0) {
            height.toFloat()
        } else {
            200f * resources.displayMetrics.density
        }

        animate()
            .translationY(moveDistance)
            .alpha(0f)
            .setDuration(250L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                visibility = View.GONE

                translationY = 0f
                alpha = 1f
                isCollapsing = false
                onCollapsed?.invoke()
            }
            .start()
    }

    fun populate(nativeAd: NativeAd) {
        if (nativeAdViewLayout is NativeAdView) {
            skeleton?.showOriginal()
            NativeAdCalculator.populateNativeAdView(
                nativeAd,
                nativeAdViewLayout as NativeAdView
            )
            binding?.viewFlipper?.displayedChild = 0
        }
        else if (nativeAdViewLayout is ViewGroup) {
            val viewGroup = nativeAdViewLayout as ViewGroup
            for (child in viewGroup.children) {
                if (child is NativeAdView) {
                    skeleton?.showOriginal()
                    NativeAdCalculator.populateNativeAdView(nativeAd, child)
                    binding?.viewFlipper?.displayedChild = 0
                    break
                }
            }
        } else {
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
    }
}
