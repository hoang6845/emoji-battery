package com.hd.emojibattery.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.gson.Gson
import org.json.JSONObject
import tpt.dev.monetization.ads.remote.RemoteAdsConfig

/** Fetch and apply a launch snapshot before any ad preload. */
object BatteryAdsConfig {
    private const val PREFS = "battery_ads_config"
    private const val KEY = "ads_config"
    private const val FETCH_TIMEOUT_MS = 10_000L
    var splashLoadingTimeMs = 9_000L
        private set
    var collapseIntervalMs = 15_000L
        private set
    var nativePlacements: Set<String> = emptySet()
        private set

    private fun parse(raw: String): RemoteAdsConfig =
        Gson().fromJson(raw, RemoteAdsConfig::class.java).also { require(it.hasRemoteData()) }

    private fun activatedJson(): String? = runCatching {
        val remote = FirebaseRemoteConfig.getInstance()
        if (remote.getValue(KEY).source != FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) return@runCatching null
        val json = JSONObject(remote.getString(KEY))
        val interval = remote.getValue("collapse_expand_interval_seconds")
        if (interval.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
            val configs = json.optJSONObject("configs") ?: JSONObject().also { json.put("configs", it) }
            configs.put("collapse_expand_interval_seconds", interval.asLong())
        }
        json.toString().also { parse(it) }
    }.getOrNull()

    fun load(context: Context): RemoteAdsConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val remote = activatedJson()
        val cached = prefs.getString(KEY, null)?.takeIf { runCatching { parse(it) }.isSuccess }
        val raw = remote ?: cached ?: context.assets.open("ads_config.json").bufferedReader().use { it.readText() }
        val snapshot = parse(raw)
        if (remote != null) prefs.edit().putString(KEY, raw).apply()
        splashLoadingTimeMs = snapshot.preloadLoadingTime(9_000L).coerceAtLeast(9_000L)
        collapseIntervalMs = JSONObject(raw).optJSONObject("configs")
            ?.optLong("collapse_expand_interval_seconds", 15)?.coerceIn(0, 3600)?.times(1000) ?: 15_000L
        nativePlacements = snapshot.placements.orEmpty().keys.filter { it.contains("native") }.toSet()
        Log.d("BatteryAdsConfig", "Apply snapshot source=${if (remote != null) "firebase" else if (cached != null) "cache" else "asset"}")
        return snapshot
    }

    fun fetch(context: Context, onReady: (RemoteAdsConfig) -> Unit) {
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        lateinit var timeout: Runnable
        fun finish(reason: String) {
            if (finished) return
            finished = true
            handler.removeCallbacks(timeout)
            Log.d("BatteryAdsConfig", "Fetch $reason; resolve config before preload")
            onReady(load(app))
        }
        timeout = Runnable { finish("timeout") }
        handler.postDelayed(timeout, FETCH_TIMEOUT_MS)
        runCatching {
            val remote = FirebaseRemoteConfig.getInstance()
            remote.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600).setFetchTimeoutInSeconds(10).build())
                .addOnCompleteListener {
                    if (!finished) remote.fetchAndActivate().addOnCompleteListener { task ->
                        // Late activation is available next launch; never swap units after preload.
                        finish(if (task.isSuccessful) "complete" else "failed")
                    }
                }
        }.onFailure { finish("unavailable") }
    }
}
