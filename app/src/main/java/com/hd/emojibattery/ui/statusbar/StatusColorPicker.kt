package com.hd.emojibattery.ui.statusbar

import android.content.Context
import android.graphics.Color
import android.widget.*
import androidx.appcompat.app.AlertDialog

/** Native controls keep arbitrary RGB selection accessible without an external picker. */
internal object StatusColorPicker {
    fun show(context: Context, initial: Int, chosen: (Int) -> Unit) {
        val density = context.resources.displayMetrics.density
        val host = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val inset = (24 * density).toInt()
            setPadding(inset, inset / 2, inset, 0)
        }
        val preview = android.view.View(context)
        host.addView(preview, LinearLayout.LayoutParams(-1, (48 * density).toInt()))
        val input = EditText(context).apply { setSingleLine(); hint = "#RRGGBB" }
        val channels = intArrayOf(Color.red(initial), Color.green(initial), Color.blue(initial))
        fun refresh() {
            val color = Color.rgb(channels[0], channels[1], channels[2])
            preview.setBackgroundColor(color)
            input.setText(String.format("#%06X", color and 0xffffff))
        }
        listOf("Red", "Green", "Blue").forEachIndexed { index, name ->
            host.addView(TextView(context).apply { text = name })
            host.addView(SeekBar(context).apply {
                max = 255; progress = channels[index]; contentDescription = name
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                        if (user) { channels[index] = value; refresh() }
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                })
            })
        }
        host.addView(input)
        refresh()
        val dialog = AlertDialog.Builder(context).setTitle("Custom color").setView(host)
            .setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString().trim()
                val color = if (text.matches(Regex("#[0-9a-fA-F]{6}"))) runCatching { Color.parseColor(text) }.getOrNull() else null
                if (color == null) input.error = "Enter a color such as #3B82F6"
                else { chosen(color); dialog.dismiss() }
            }
        }
        dialog.show()
        (context as? com.hd.emojibattery.MainActivity)?.batteryAds?.bindPopup(dialog)
    }
}
