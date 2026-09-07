package com.aes.grammplayer.network.tmdb

import android.content.Context
import android.util.Log
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller

object TlsHelper {

    private const val TAG = "TlsHelper"
    @Volatile
    private var providerUpgraded = false

    /**
     * Updates the device TLS provider (Conscrypt) when Google Play services are available.
     * Fixes TMDB HTTPS failures on some emulators/TVs with stale OCSP or CA data.
     */
    fun upgradeSecurityProvider(context: Context) {
        if (providerUpgraded) return
        synchronized(this) {
            if (providerUpgraded) return
            try {
                ProviderInstaller.installIfNeeded(context.applicationContext)
                providerUpgraded = true
                Log.i(TAG, "Security provider upgraded for HTTPS")
            } catch (e: GooglePlayServicesRepairableException) {
                Log.w(TAG, "Security provider upgrade repairable failure", e)
            } catch (e: GooglePlayServicesNotAvailableException) {
                Log.w(TAG, "Google Play services unavailable — using system TLS provider", e)
                providerUpgraded = true
            } catch (e: Exception) {
                Log.w(TAG, "Security provider upgrade failed — using system TLS provider", e)
                providerUpgraded = true
            }
        }
    }
}