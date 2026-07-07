package com.example.util

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object ConsentManager {
    private const val TAG = "ConsentManager"
    private var consentInformation: ConsentInformation? = null

    interface OnConsentCheckCompleteListener {
        fun onConsentCheckComplete()
    }

    fun gatherConsent(
        activity: Activity,
        onConsentCheckCompleteListener: OnConsentCheckCompleteListener
    ) {
        consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation?.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity
                ) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form load/show failed: Code ${formError.errorCode}, Msg: ${formError.message}")
                    }
                    onConsentCheckCompleteListener.onConsentCheckComplete()
                }
            },
            { requestConsentError ->
                Log.w(TAG, "Consent info update failed: Code ${requestConsentError.errorCode}, Msg: ${requestConsentError.message}")
                onConsentCheckCompleteListener.onConsentCheckComplete()
            }
        )
    }

    fun canRequestAds(): Boolean {
        return consentInformation?.canRequestAds() ?: false
    }
}
