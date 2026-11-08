package hoang.dqm.codebase.firebase

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

internal class BaseFirebaseRemoteConfig : RemoteConfig {
    private var onConfigActivated: Runnable? = null
    private val remoteConfig by lazy { FirebaseRemoteConfig.getInstance() }

    override fun getString(config: String): String? {
        val value = remoteConfig.getString(config)
        Log.d("check data", "getString key=$config value=$value")

        return value
    }

    override fun getLong(config: String): Long? {
        return remoteConfig.getLong(config)
    }

    override fun getBoolean(config: String): Boolean? {
        return remoteConfig.getBoolean(config)
    }

    override fun getDouble(config: String): Double? {
        return remoteConfig.getDouble(config)
    }

    override fun fetchConfig(runnable: Runnable?) {
        onConfigActivated = runnable
        val firebaseRemoteConfig = try {
            FirebaseRemoteConfig.getInstance()
        } catch (error: IllegalStateException) {
            Log.e("RC", "Firebase is not initialized; skipping Remote Config fetch", error)
            onConfigActivated?.run()
            onConfigActivated = null
            return
        }
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        firebaseRemoteConfig.setConfigSettingsAsync(configSettings)
        firebaseRemoteConfig.fetchAndActivate()
            .addOnSuccessListener {
                completeFetch()
                Log.d(
                    "RC",
                    "fetchConfig COMPLETE success"
                )
            }
            .addOnFailureListener {
                completeFetch()
                Log.d(
                    "RC",
                    "fetchConfig COMPLETE fail"
                )
            }
            .addOnCompleteListener {
                completeFetch()
                Log.d(
                    "RC",
                    "fetchConfig COMPLETE "
                )
            }
    }

    private fun completeFetch() {
        onConfigActivated?.run()
        onConfigActivated = null
    }

}
