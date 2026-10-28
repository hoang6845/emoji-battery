package hoang.dqm.codebase.firebase

import android.app.Activity
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import hoang.dqm.codebase.base.application.appInfo
import hoang.dqm.codebase.base.application.getBaseApplication
import hoang.dqm.codebase.data.CategoryItem
import hoang.dqm.codebase.data.CategoryItemChannel
import hoang.dqm.codebase.data.CategoryItemUrl
import hoang.dqm.codebase.utils.gsonStrToList
import hoang.dqm.codebase.utils.readAssetsFile
import hoang.dqm.codebase.utils.strToObj
import tpt.dev.monetization.ads.remote.RemoteAdsConfig

object AppRemoteConfig {
    private val baseConfig by lazy { RemoteConfigImpl() }
    private val gson by lazy { Gson() }
    const val REMOTE_DATA_CATEGORY_APP = "data_category_app"
    private const val ACCESS_KEY_REMOTE_DATA = "access_key_remote_data"
    const val DATA_CATEGORY_URL = "data_category_url"
    const val DATA_AVATAR = "data_avatar"
    const val DATA_PRICE = "data_price"
    const val TIME_DELAY_INTER_SPLASH_OPEN = "time_delay_inter_splash_vs_open"
    const val TIME_DELAY_SHOW_INTER = "time_delay_show_inter"
    const val IS_SHOW_AD_OPEN = "is_show_ad_open"
    const val IS_SHOW_ADS_APP = "is_show_ads_app"
    const val IS_SHOW_INTER_SPLASH = "is_show_inter_splash"
    const val COUNT_CHANGE_TAB_SHOW_INTER = "count_change_tab_show_inter"
    const val TIME_DELAY_CHANGE_TAB_SHOW_INTER = "time_delay_change_tab_show_inter"
    const val TIME_LOAD_NATIVE = "time_load_native"
    const val IAP_SELECTED_PRODUCT_ID = "iap_selected_product_id"
    const val IS_LOCK_IMPORT_LINK_IAP = "is_lock_import_link_iap"
    const val IS_LOCK_XTREAM_SERVER_IAP = "is_lock_xtream_server_iap"
    const val DAILY_FREE_CHANNEL_VIEWS = "daily_free_channel_views"
    const val ADS_CONFIG = "ads_config"
    const val ADS_PLACEMENTS = "ads_placements"
    const val ADS_AD_ITEMS = "ads_ad_items"
    const val ADS_AD_UNITS = "ads_ad_units"
    const val ADS_CONFIGS = "ads_configs"
    private const val ADS_CONFIG_ASSET = "ads_config.json"

    fun fetchConfig( callback: Runnable){
        Log.d("AppRemoteConfig", "fetchConfig called, passing callback to baseConfig")
        baseConfig.fetchConfig(callback)
    }
    fun getAccessToken(): String {
        return baseConfig.getString(ACCESS_KEY_REMOTE_DATA) ?: ""
    }

    fun getStringValue(key: String, defaultValue: String = ""): String {
        return baseConfig.getString(key) ?: defaultValue
    }

    fun getBooleanValue(key: String, defaultValue: Boolean = false): Boolean {
        return baseConfig.getBoolean(key) ?: defaultValue
    }

    fun getLongValue(key: String, defaultValue: Long = 0L): Long {
        return baseConfig.getLong(key) ?: defaultValue
    }

    fun getRemoteAdsConfig(): RemoteAdsConfig? {
        return try {
            val fullConfig = firstNotBlankRemoteValue(ADS_CONFIG, "remote_ads_config")
            if (fullConfig.isNotBlank()) {
                Log.d(
                    "AppRemoteConfig",
                    "ADS_CONFIG_REMOTE_FULL_FOUND key=$ADS_CONFIG length=${fullConfig.length}"
                )
                gson.fromJson(fullConfig, RemoteAdsConfig::class.java)
                    ?.also { config ->
                        logAdsConfigResult(
                            source = "firebase_full",
                            config = config,
                            fallback = false
                        )
                    }
            } else {
                val root = JsonObject()
                addRemoteJsonObject(root, "configs", ADS_CONFIGS, "configs")
                addRemoteJsonObject(root, "placements", ADS_PLACEMENTS, "placements")
                addRemoteJsonObject(root, "ad_items", ADS_AD_ITEMS, "ad_items", "ad_item")
                addRemoteJsonObject(root, "ad_units", ADS_AD_UNITS, "ad_units", "units")

                if (root.size() == 0) {
                    Log.d(
                        "AppRemoteConfig",
                        "ADS_CONFIG_REMOTE_EMPTY_USE_DEFAULT checkedKeys=" +
                            "$ADS_CONFIG,remote_ads_config,$ADS_CONFIGS,configs," +
                            "$ADS_PLACEMENTS,placements,$ADS_AD_ITEMS,ad_items,ad_item," +
                            "$ADS_AD_UNITS,ad_units,units"
                    )
                    getDefaultRemoteAdsConfig()
                } else {
                    Log.d(
                        "AppRemoteConfig",
                        "ADS_CONFIG_REMOTE_SPLIT_FOUND sections=${root.keySet()}"
                    )
                    gson.fromJson(root, RemoteAdsConfig::class.java)
                        ?.also { config ->
                            logAdsConfigResult(
                                source = "firebase_split",
                                config = config,
                                fallback = false
                            )
                        }
                }
            }
        } catch (e: Exception) {
            Log.e(
                "AppRemoteConfig",
                "ADS_CONFIG_REMOTE_PARSE_ERROR_USE_DEFAULT message=${e.message}",
                e
            )
            getDefaultRemoteAdsConfig()
        }
    }

    private fun getDefaultRemoteAdsConfig(): RemoteAdsConfig? {
        return try {
            val defaultJson = getBaseApplication().assets.readAssetsFile(ADS_CONFIG_ASSET)
            if (defaultJson.isBlank()) {
                Log.d("AppRemoteConfig", "ADS_CONFIG_DEFAULT_EMPTY asset=$ADS_CONFIG_ASSET")
                null
            } else {
                Log.d(
                    "AppRemoteConfig",
                    "ADS_CONFIG_DEFAULT_FOUND asset=$ADS_CONFIG_ASSET length=${defaultJson.length}"
                )
                gson.fromJson(defaultJson, RemoteAdsConfig::class.java)
                    ?.also { config ->
                        logAdsConfigResult(
                            source = "assets/$ADS_CONFIG_ASSET",
                            config = config,
                            fallback = true
                        )
                    }
            }
        } catch (e: Exception) {
            Log.e(
                "AppRemoteConfig",
                "ADS_CONFIG_DEFAULT_PARSE_ERROR asset=$ADS_CONFIG_ASSET message=${e.message}",
                e
            )
            null
        }
    }

    private fun firstNotBlankRemoteValue(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            getStringValue(key, "").takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun addRemoteJsonObject(root: JsonObject, rootKey: String, vararg remoteKeys: String) {
        val rawValue = firstNotBlankRemoteValue(*remoteKeys)
        if (rawValue.isBlank()) {
            Log.d(
                "AppRemoteConfig",
                "ADS_CONFIG_REMOTE_SPLIT_SECTION_MISSING section=$rootKey keys=${remoteKeys.joinToString()}"
            )
            return
        }

        val jsonElement = JsonParser.parseString(rawValue)
        if (jsonElement.isJsonObject) {
            root.add(rootKey, jsonElement.asJsonObject)
            Log.d(
                "AppRemoteConfig",
                "ADS_CONFIG_REMOTE_SPLIT_SECTION_ADDED section=$rootKey keys=${remoteKeys.joinToString()} length=${rawValue.length}"
            )
        } else {
            Log.d(
                "AppRemoteConfig",
                "ADS_CONFIG_REMOTE_SPLIT_SECTION_NOT_OBJECT section=$rootKey keys=${remoteKeys.joinToString()}"
            )
        }
    }

    private fun logAdsConfigResult(
        source: String,
        config: RemoteAdsConfig,
        fallback: Boolean
    ) {
        Log.d(
            "AppRemoteConfig",
            "ADS_CONFIG_RESULT source=$source fallback=$fallback " +
                "hasRemoteData=${config.hasRemoteData()} " +
                "globalEnabled=${config.isGlobalEnabled(true)} " +
                "loadingTime=${config.preloadLoadingTime(-1L)} " +
                "placements=${config.placements.orEmpty().size} " +
                "adItems=${config.adItems.orEmpty().size} " +
                "adUnits=${config.adUnits.orEmpty().size}"
        )
    }

    fun <T> getData(key: String, clazz: Class<T>): T? {
        return try {
            val json = baseConfig.getString(key)?.ifEmpty {
                getBaseApplication().assets.readAssetsFile("$key.json")
            }

            strToObj(json, clazz)

        } catch (e: Exception) {
            e.printStackTrace()

            if (appInfo().isDebug) {
                val json = getBaseApplication().assets.readAssetsFile("$key.json")
                strToObj(json, clazz)
            } else {
                null
            }
        }
    }

    fun <T> getListData(key: String, claszz: Class<T>): List<T> {
        return try {
            val json = baseConfig.getString(key)?.ifEmpty {
                getBaseApplication().assets.readAssetsFile("$key.json")
            }
            gsonStrToList(json, claszz)

        } catch (e: Exception) {
            e.printStackTrace()
//            emptyList()
            if (appInfo().isDebug) {
                val json = getBaseApplication().assets.readAssetsFile("$key.json")
                gsonStrToList(json, claszz)
            } else {
                emptyList()
            }
        }
    }

    fun getListCategoryMovie(languageKey: String): List<CategoryItem> {
        return try {
            val json = baseConfig.getString(REMOTE_DATA_CATEGORY_APP)?.ifEmpty {
                getBaseApplication().assets.readAssetsFile("$REMOTE_DATA_CATEGORY_APP.json")
            }
            Log.d("check data", "getListCategoryMovie: $json")
            json?.let { convertJsonToCategoryList(it, languageKey) } ?: emptyList()

        } catch (e: Exception) {
            e.printStackTrace()
//            emptyList()
            if (appInfo().isDebug) {
                val json = getBaseApplication().assets.readAssetsFile("$REMOTE_DATA_CATEGORY_APP.json")
                convertJsonToCategoryList(json, languageKey)
            } else {
                emptyList()
            }
        }
    }

    fun getListCategoryChannels(languageKey: String): List<CategoryItemChannel> {
        return try {
            val json = baseConfig.getString(REMOTE_DATA_CATEGORY_APP)?.ifEmpty {
                getBaseApplication().assets.readAssetsFile("$REMOTE_DATA_CATEGORY_APP.json")
            }
            Log.d("check data", "getListCategoryMovie: $json")
            json?.let { convertJsonToCategoryChannel(it, languageKey) } ?: emptyList()

        } catch (e: Exception) {
            e.printStackTrace()
//            emptyList()
            if (appInfo().isDebug) {
                val json = getBaseApplication().assets.readAssetsFile("$REMOTE_DATA_CATEGORY_APP.json")
                convertJsonToCategoryChannel(json, languageKey)
            } else {
                emptyList()
            }
        }
    }

    fun getListCategoryUrl(languageKey: String): List<CategoryItemUrl> {
        return try {
            val json = baseConfig.getString(DATA_CATEGORY_URL)?.ifEmpty {
                getBaseApplication().assets.readAssetsFile("$DATA_CATEGORY_URL.json")
            }
            Log.d("check data", "getListCategoryMovie data_category_url: $json")
            json?.let { convertJsonToCategoryUrlList(it, languageKey) } ?: emptyList()

        } catch (e: Exception) {
            e.printStackTrace()
//            emptyList()
            if (appInfo().isDebug) {
                val json = getBaseApplication().assets.readAssetsFile("$DATA_CATEGORY_URL.json")
                convertJsonToCategoryUrlList(json, languageKey)
            } else {
                emptyList()
            }
        }
    }

    fun convertJsonToCategoryUrlList(jsonString: String, langKey: String): List<CategoryItemUrl> {
        val jsonArray: JsonArray = JsonParser.parseString(jsonString).asJsonArray

        return jsonArray.map { jsonElement ->
            val obj = jsonElement.asJsonObject
            val type = obj.get("type")?.asString ?: ""
            var value = obj.get(langKey)?.asString ?: ""
            if (value.isEmpty()) value = obj.get("en")?.asString?: ""
            val url = obj.get("url")?.asString ?: ""
            CategoryItemUrl(type = type, value = value, url = url)
        }
    }

    fun convertJsonToCategoryList(jsonString: String, langKey: String): List<CategoryItem> {
        val jsonArray: JsonArray = JsonParser.parseString(jsonString).asJsonArray

        return jsonArray.map { jsonElement ->
            val obj = jsonElement.asJsonObject
            val type = obj.get("type")?.asString ?: ""
            val value = obj.get(langKey)?.asString ?: ""
            CategoryItem(type = type, value = value)
        }
    }

    fun convertJsonToCategoryChannel(jsonString: String, langKey: String): List<CategoryItemChannel> {
        val jsonArray: JsonArray = JsonParser.parseString(jsonString).asJsonArray

        return jsonArray.map { jsonElement ->
            val obj = jsonElement.asJsonObject
            val type = obj.get("type")?.asString ?: ""
            val value = obj.get(langKey)?.asString ?: ""
            CategoryItemChannel(type = type, value = value)
        }
    }




}
