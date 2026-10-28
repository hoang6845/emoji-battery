package tpt.dev.monetization.ads.debug

import android.content.Context
import android.util.Log
import com.facebook.ads.AdSettings

object MetaTestDevices {
    fun register(context: Context, deviceHash: String, forceTestMode: Boolean) {
        // Explicit SDK test mode avoids depending on a copied hash remaining valid.
        // This implementation exists only in the Debug source set and is opt-in.
        AdSettings.setTestMode(forceTestMode)
        val hash = deviceHash.trim()
        val hasHash = hash.isNotEmpty() && hash != "META_TEST_DEVICE_HASH"
        if (hasHash) {
            AdSettings.addTestDevice(hash)
        }
        Log.d(
            "MetaTestDevices",
            "Before ads initialization: explicitTestMode=$forceTestMode " +
                "hashRegistered=$hasHash sdkTestMode=${AdSettings.isTestMode(context)}"
        )
    }
}
