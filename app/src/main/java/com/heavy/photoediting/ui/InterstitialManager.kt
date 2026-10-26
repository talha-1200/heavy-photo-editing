package com.heavy.photoediting.ui

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.heavy.photoediting.BuildConfig

/**
 * Full-screen ad shown at a natural pause: when the user closes the "Saved" dialog (never before Share,
 * never on app start, never while shooting or editing).
 * Frequency cap: every 3rd saved photo AND at least 3 minutes since the last interstitial.
 * Debug builds use Google's test unit; release builds use the real unit (see app/build.gradle.kts).
 */
object InterstitialManager {
    private const val EVERY_N_SAVES = 3
    private const val MIN_GAP_MS = 3 * 60 * 1000L
    private const val TAG = "HeavyAds"

    @Volatile private var ad: InterstitialAd? = null
    @Volatile private var loading = false
    @Volatile private var saves = 0
    @Volatile private var lastShown = -MIN_GAP_MS

    /** Call after MobileAds.initialize has finished (see MainActivity), on the main thread. */
    fun preload() {
        if (ad != null || loading) return
        loading = true
        InterstitialAd.load(
            AdRequest.Builder(BuildConfig.ADMOB_INTERSTITIAL_ID).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    this@InterstitialManager.ad = ad
                    loading = false
                    Log.d(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    this@InterstitialManager.ad = null
                    loading = false
                    Log.w(TAG, "Interstitial failed to load: ${adError.message}")
                }
            }
        )
    }

    /** A photo was saved successfully. */
    fun noteSave() { saves++ }

    /** Call when the user leaves the "Saved" dialog via Keep editing / My Photos. Shows an ad only when due. */
    fun showIfDue(activity: Activity) {
        if (saves < EVERY_N_SAVES) return
        if (SystemClock.elapsedRealtime() - lastShown < MIN_GAP_MS) return
        val current = ad
        if (current == null) { preload(); return }
        current.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() {}
            override fun onAdDismissedFullScreenContent() { ad = null; preload() }
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.w(TAG, "Interstitial failed to show: ${fullScreenContentError.message}")
                ad = null; preload()
            }
            override fun onAdImpression() {}
            override fun onAdClicked() {}
        }
        saves = 0
        lastShown = SystemClock.elapsedRealtime()
        current.show(activity)
    }
}
