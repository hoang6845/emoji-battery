package com.hd.emojibattery.runtime

import android.content.SharedPreferences

/** Scope IDs by collection: sticker and emoji feeds can share the same ID. */
object Favorites {
    fun key(source: String, id: String) = "favorite:$source:$id"
    fun contains(prefs: SharedPreferences, source: String, id: String): Boolean =
        prefs.getBoolean(key(source, id), prefs.getBoolean("fav_$id", false))
    fun set(prefs: SharedPreferences, source: String, id: String, value: Boolean) {
        prefs.edit().putBoolean(key(source, id), value).apply()
    }
}
