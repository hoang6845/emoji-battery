package tpt.dev.monetization.ads.debug

import android.content.Context

/** Release builds never register Meta test devices. */
object MetaTestDevices {
    @Suppress("UNUSED_PARAMETER")
    fun register(context: Context, deviceHash: String, forceTestMode: Boolean) = Unit
}
