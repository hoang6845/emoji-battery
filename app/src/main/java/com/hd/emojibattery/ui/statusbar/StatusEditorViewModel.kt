package com.hd.emojibattery.ui.statusbar

import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** An editor session shared by the main status editor and its child pickers. */
class StatusEditorViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val changes: Bundle get() = savedState["changes"] ?: Bundle()
    fun reset() { savedState["changes"] = Bundle() }
    @Suppress("DEPRECATION")
    fun hasChanges(base: SharedPreferences): Boolean = changes.keySet().any { key ->
        val navigationOnly = key in setOf("icon_tab", "status_theme_tab", "status_category") ||
            key.startsWith("status_picker_category_") || key.startsWith("custom_palette_")
        !navigationOnly && changes.get(key) != base.all[key]
    }
    fun preferences(base: SharedPreferences): SharedPreferences = object : SharedPreferences by base {
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        private fun notifyChanged(key: String) { listeners.toList().forEach { it.onSharedPreferenceChanged(this, key) } }
        override fun getInt(key: String?, defValue: Int) = if (changes.containsKey(key)) changes.getInt(key, defValue) else base.getInt(key, defValue)
        override fun getBoolean(key: String?, defValue: Boolean) = if (changes.containsKey(key)) changes.getBoolean(key, defValue) else base.getBoolean(key, defValue)
        override fun getLong(key: String?, defValue: Long) = if (changes.containsKey(key)) changes.getLong(key, defValue) else base.getLong(key, defValue)
        override fun getFloat(key: String?, defValue: Float) = if (changes.containsKey(key)) changes.getFloat(key, defValue) else base.getFloat(key, defValue)
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            if (changes.containsKey(key)) changes.getStringArrayList(key)?.toMutableSet() else base.getStringSet(key, defValues)
        override fun getString(key: String?, defValue: String?): String? = if (changes.containsKey(key)) changes.getString(key) else base.getString(key, defValue)
        @Suppress("DEPRECATION")
        override fun contains(key: String?) = if (changes.containsKey(key)) changes.get(key) != null else base.contains(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.add(listener) }
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.remove(listener) }
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = Bundle()
            override fun putString(key: String, value: String?) = apply { pending.putString(key, value) }
            override fun putInt(key: String, value: Int) = apply { pending.putInt(key, value) }
            override fun putBoolean(key: String, value: Boolean) = apply { pending.putBoolean(key, value) }
            override fun putLong(key: String, value: Long) = apply { pending.putLong(key, value) }
            override fun putFloat(key: String, value: Float) = apply { pending.putFloat(key, value) }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending.putStringArrayList(key, values?.let { ArrayList(it) }) }
            override fun remove(key: String) = apply { pending.putString(key, null) }
            override fun clear() = apply { base.all.keys.forEach { remove(it) }; changes.keySet().forEach { remove(it) } }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                val next = Bundle(changes)
                val immediate = base.edit()
                for (key in pending.keySet()) {
                    @Suppress("DEPRECATION") val value = pending.get(key)
                    if (key.startsWith("fav_") || key.startsWith("favorite:")) {
                        if (value is Boolean) immediate.putBoolean(key, value)
                    } else {
                        when (value) {
                            is Int -> next.putInt(key, value)
                            is Boolean -> next.putBoolean(key, value)
                            is Long -> next.putLong(key, value)
                            is Float -> next.putFloat(key, value)
                            is ArrayList<*> -> next.putStringArrayList(key, ArrayList(value.filterIsInstance<String>()))
                            is String -> next.putString(key, value)
                            null -> next.putString(key, null)
                        }
                    }
                }
                immediate.apply()
                savedState["changes"] = next
                pending.keySet().forEach(::notifyChanged)
            }
        }
    }

    fun commit(base: SharedPreferences, applyOverlay: () -> Boolean): Boolean {
        val previous = base.all
        val edit = base.edit()
        for (key in changes.keySet()) {
            @Suppress("DEPRECATION") val value = changes.get(key)
            when (value) {
                is Int -> edit.putInt(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is ArrayList<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                is String -> edit.putString(key, value)
                null -> edit.remove(key)
            }
        }
        edit.apply()
        if (applyOverlay()) { reset(); return true }
        val rollback = base.edit()
        for (key in changes.keySet()) {
            when (val value = previous[key]) {
                is Int -> rollback.putInt(key, value)
                is Boolean -> rollback.putBoolean(key, value)
                is Long -> rollback.putLong(key, value)
                is Float -> rollback.putFloat(key, value)
                is Set<*> -> rollback.putStringSet(key, value.filterIsInstance<String>().toSet())
                is String -> rollback.putString(key, value)
                null -> rollback.remove(key)
            }
        }
        rollback.apply()
        return false
    }
}
