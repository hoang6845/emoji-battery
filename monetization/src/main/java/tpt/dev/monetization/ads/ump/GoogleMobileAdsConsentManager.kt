package tpt.dev.monetization.ads.ump

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import tpt.dev.monetization.R
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * The Google Mobile Ads SDK provides the User Messaging Platform (Google's IAB Certified consent
 * management platform) as one solution to capture consent for users in GDPR impacted countries.
 * This is an example and you can choose another consent management platform to capture consent.
 */
class GoogleMobileAdsConsentManager private constructor(
    context: Context
) {
    /** App-owned analytics consent policy; separate from permission to request Google ads. */
    var onConsentUpdated: (() -> Unit)? = null

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /** Interface definition for a callback to be invoked when consent gathering is complete. */
    fun interface OnConsentGatheringCompleteListener {
        fun consentGatheringComplete(error: FormError?)
    }

    /** Helper variable to determine if the app can request ads. */
    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds().also {
        }

    /** Helper variable to determine if the privacy options form is required. */
    val isPrivacyOptionsRequired: Boolean
        get() =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Helper method to call the UMP SDK methods to request consent information and load/show a
     * consent form if necessary.
     */
    fun gatherConsent(
        activity: Activity,
        onConsentGatheringCompleteListener: OnConsentGatheringCompleteListener
    ) {
        val params =
            ConsentRequestParameters
                .Builder()
                .apply {
                    if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                        // Repeat the first-install consent flow for manual debug checks.
                        consentInformation.reset()
                        val debugSettings = ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        activity.resources.getStringArray(R.array.ump_debug_test_device_ids)
                            .filter { it.isNotBlank() }
                            .forEach { debugSettings.addTestDeviceHashedId(it) }
                        setConsentDebugSettings(debugSettings.build())
                        Log.d("UMPConsent", "Debug consent reset; EEA override requires a UMP test device")
                    }
                }.build()

        // Requesting an update to consent information should be called on every app launch.
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    formError?.let { Log.w("UMPConsent", "Form error ${it.errorCode}: ${it.message}") }
                    // Consent has been gathered.
                    onConsentUpdated?.invoke()
                    onConsentGatheringCompleteListener.consentGatheringComplete(formError)
                }
            },
            { requestConsentError ->
                Log.w("UMPConsent", "Update error ${requestConsentError.errorCode}: ${requestConsentError.message}")
                onConsentUpdated?.invoke()
                onConsentGatheringCompleteListener.consentGatheringComplete(requestConsentError)
            }
        )
    }

    /** Helper method to call the UMP SDK method to show the privacy options form. */
    fun showPrivacyOptionsForm(
        activity: Activity,
        onConsentFormDismissedListener: ConsentForm.OnConsentFormDismissedListener
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            onConsentUpdated?.invoke()
            onConsentFormDismissedListener.onConsentFormDismissed(error)
        }
    }

    companion object {
        @Volatile
        private var instance: GoogleMobileAdsConsentManager? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: GoogleMobileAdsConsentManager(context).also { instance = it }
            }
    }
}
