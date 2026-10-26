package com.heavy.photoediting.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * Anchored adaptive banner. If there is no internet or no ad is available it simply stays empty (zero height),
 * so the app keeps working offline. Only place it on calm screens (Home, My Photos), never on Camera or Editor.
 */
@Composable
fun BannerAdView(adUnitId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adView = remember { AdView(context) }
    val adSize = remember { AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, 360) }
    val preview = LocalInspectionMode.current

    LaunchedEffect(adSize) {
        if (!preview) {
            adView.loadAd(
                BannerAdRequest.Builder(adUnitId, adSize).build(),
                object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) { Log.d("HeavyAds", "Banner loaded") }
                    override fun onAdFailedToLoad(adError: LoadAdError) { Log.w("HeavyAds", "Banner failed: $adError") }
                }
            )
        }
    }
    DisposableEffect(Unit) { onDispose { adView.destroy() } }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AndroidView(modifier = Modifier.wrapContentSize(), factory = { adView })
    }
}
