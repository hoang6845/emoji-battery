package com.hd.emojibattery.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.navigation.ScreenIds

/** A standalone page: no intro artwork or copy underneath the fullscreen ad. */
open class IntroNativeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return IntroSwipeLayout(requireContext()).apply {
            setBackgroundResource(R.drawable.bg_screen_gradient)
            onSwipe = { forward -> navigate(forward) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                fun button(label: String, forward: Boolean) = TextView(context).apply {
                    text = label
                    textSize = 18f
                    gravity = android.view.Gravity.CENTER
                    minHeight = (48 * resources.displayMetrics.density).toInt()
                    setPadding(24, 16, 24, 16)
                    setSingleLine()
                    setOnClickListener { navigate(forward) }
                }
                addView(button(getString(R.string.intro_back), false))
                addView(button(getString(R.string.intro_next), true))
            }, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
            addView(FrameLayout(context).apply {
                id = R.id.intro_ad_host
                visibility = View.GONE
            }, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun navigate(forward: Boolean) {
        (requireActivity() as MainActivity).openScreen(if (forward) ScreenIds.INTRO_5 else ScreenIds.INTRO_2, false)
    }
}
