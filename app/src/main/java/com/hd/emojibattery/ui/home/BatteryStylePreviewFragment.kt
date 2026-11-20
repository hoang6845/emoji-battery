package com.hd.emojibattery.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import com.hd.emojibattery.R
import com.hd.emojibattery.runtime.AppliedContent
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.navigation.ScreenIds

/** Battery catalog preview; charging media keeps its existing, separate preview route. */
class BatteryStylePreviewFragment : BaseFeatureFragment<HomeViewModel>() {
    private var selectedId: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedId = savedInstanceState?.getString("preview_battery_id") ?: arguments?.getString("content_id")
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("preview_battery_id", selectedId)
        super.onSaveInstanceState(outState)
    }
    override fun initView() {
        binding.featureToolbar.isVisible = false
        binding.featureScroll.isVisible = false
        bindFeature()
    }
    override fun bindFeature() {
        val panel = view<View>(R.id.battery_preview_screen)
        panel.isVisible = true
        val item = AppliedContent.find(requireContext(), "battery_styles", selectedId)
        val all = catalog().array("battery_styles")
        val items = (0 until (all?.length() ?: 0)).mapNotNull { all?.optJSONObject(it) }
            .filter { item == null || it.optString("category") == item.optString("category") }
        com.hd.emojibattery.ui.common.BatteryPreviewCarousel.bind(panel, items, selectedId, "battery_styles", prefs) {
            selectedId = it.optString("id")
        }
        panel.findViewById<View>(R.id.battery_preview_back).setOnClickListener { finishFeature() }
        panel.findViewById<View>(R.id.battery_preview_get).apply {
            isEnabled = item != null
            setOnClickListener { navigate(ScreenIds.STATUS_BAR, Bundle().apply {
                putString("content_id", selectedId)
                putString("content_source", "battery_styles")
            }) }
        }
    }
}
