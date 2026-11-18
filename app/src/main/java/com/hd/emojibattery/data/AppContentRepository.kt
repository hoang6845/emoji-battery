package com.hd.emojibattery.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Bundled → last-known-good → Firebase Remote Config URL → GitHub JSON fallback chain. */
class AppContentRepository private constructor(context: Context) {
    companion object {
        const val REMOTE_URL_KEY = "emoji_battery_content_url"
        private const val PREFS = "emoji_battery_content"
        private const val CACHE_KEY = "last_good_json"
        private const val MAX_JSON_BYTES = 3 * 1024 * 1024
        @Volatile private var instance: AppContentRepository? = null

        fun get(context: Context): AppContentRepository = instance ?: synchronized(this) {
            instance ?: AppContentRepository(context.applicationContext).also { instance = it }
        }
    }

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var content = readAsset()
    @Volatile private var refreshRunning = false
    private val listeners = mutableListOf<(JSONObject, Boolean) -> Unit>()
    private val observers = mutableSetOf<() -> Unit>()
    val isLoading: Boolean get() = refreshRunning

    /** UI observers are owned by the started view, never retained after onStop. */
    fun observe(observer: () -> Unit) { synchronized(this) { observers.add(observer) } }
    fun removeObserver(observer: () -> Unit) { synchronized(this) { observers.remove(observer) } }
    private fun notifyObservers() {
        main.post { synchronized(this) { observers.toList() }.forEach { it() } }
    }

    init {
        parseValid(prefs.getString(CACHE_KEY, "").orEmpty())?.let { content = it }
    }

    @Synchronized fun snapshot(): JSONObject = if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) PreviewContent.create()
        else runCatching { JSONObject(content.toString()) }.getOrElse { JSONObject() }
    // Copy only the requested collection, preserving snapshot isolation without
    // serializing every catalog each time a screen binds one list.
    fun array(key: String): JSONArray? {
        val source = if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) PreviewContent.create() else content
        return collectionArray(source, key)?.let { JSONArray(it.toString()) }
    }

    private fun collectionArray(source: JSONObject, key: String): JSONArray? = source.optJSONArray(key)

    /** Copy only this screen's collection and configured category metadata. */
    fun collection(key: String): JSONObject {
        val source = if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) PreviewContent.create() else content
        val selected = JSONObject()
        (listOf(key) + com.hd.emojibattery.runtime.CatalogGroups.metadataKeys(key)).forEach { field ->
            collectionArray(source, field)?.let { selected.put(field, it) }
        }
        if (key == "stickers" && !com.hd.emojibattery.BuildConfig.UI_TEST_MODE) {
            val items = selected.optJSONArray(key)
            val names = (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it)?.optString("category") }
                .filter { it.isNotBlank() }.distinct()
            selected.put("sticker_categories", JSONArray().apply { names.forEach { put(JSONObject().put("name", it)) } })
        }
        return JSONObject(selected.toString())
    }

    /** Delivers local/cached data immediately, then calls back again if remote content changes. */
    fun load(context: Context, callback: ((JSONObject, Boolean) -> Unit)?) {
        callback?.invoke(snapshot(), false)
        refresh(callback)
    }

    fun refresh(callback: ((JSONObject, Boolean) -> Unit)?) {
        if (com.hd.emojibattery.BuildConfig.UI_TEST_MODE) return
        synchronized(this) {
            if (callback != null) listeners.add(callback)
            if (refreshRunning) return
            refreshRunning = true
        }
        notifyObservers()
        worker.execute {
            try {
                if (FirebaseApp.getApps(app).isEmpty()) {
                    finish(false)
                    return@execute
                }
                val remoteConfig = FirebaseRemoteConfig.getInstance()
                val config = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build()
                remoteConfig.setConfigSettingsAsync(config)
                remoteConfig.setDefaultsAsync((SourceCatalog.emojiKeys.keys + SourceCatalog.petKeys.keys +
                    listOf(REMOTE_URL_KEY, SourceRemoteData.EMOJI_TOKEN, SourceRemoteData.EMOJI_BACKUP_TOKEN,
                        SourceRemoteData.PET_TOKEN)).associateWith { "" })
                remoteConfig.fetchAndActivate().addOnCompleteListener {
                    worker.execute {
                        var updated = false
                        try {
                            // Activated values remain usable even if this fetch fails (offline/throttled).
                            val remoteUrl = remoteConfig.getString(REMOTE_URL_KEY).trim()
                            val remote = if (remoteUrl.isNotEmpty()) downloadJson(remoteUrl) else null
                            val merged = SourceRemoteData.load(app, remoteConfig, remote ?: snapshot())
                            updated = merged.toString() != content.toString()
                            if (updated) {
                                synchronized(this) { content = merged }
                                prefs.edit().putString(CACHE_KEY, merged.toString()).apply()
                            }
                        } catch (_: Exception) {
                            // Preserve last known good content and always release the refresh gate.
                        }
                        finish(updated)
                    }
                }
            } catch (_: Exception) {
                finish(false)
            }
        }
    }

    private fun finish(updated: Boolean) {
        val pending = synchronized(this) {
            refreshRunning = false
            listeners.toList().also { listeners.clear() }
        }
        val latest = snapshot()
        notifyObservers()
        main.post { pending.forEach { it(JSONObject(latest.toString()), updated) } }
    }

    private fun downloadJson(rawUrl: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(rawUrl)
            if (!url.protocol.equals("https", true)) return null
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "EmojiBattery-Android")
            connection.connect()
            if (connection.responseCode !in 200..299 || !connection.url.protocol.equals("https", true)) return null
            if (connection.contentLength > MAX_JSON_BYTES) return null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_JSON_BYTES) return null
                    output.write(buffer, 0, count)
                }
            }
            parseValid(String(output.toByteArray(), Charsets.UTF_8))
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseValid(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        return try {
            JSONObject(raw).takeIf { value ->
                value.optInt("schema_version", 0) in 1..2 &&
                    value.optJSONArray("themes") != null && value.optJSONArray("emojis") != null
            }?.let(PetSourcePolicy::filterContent)
        } catch (_: Exception) {
            null
        }
    }

    private fun readAsset(): JSONObject = try {
        val raw = app.assets.open("app_content.json").use { String(it.readBytes(), Charsets.UTF_8) }
        parseValid(raw) ?: JSONObject()
    } catch (_: Exception) {
        JSONObject()
    }
}
