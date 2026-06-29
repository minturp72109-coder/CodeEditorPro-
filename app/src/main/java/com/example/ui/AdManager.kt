package com.example.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * AdManager manages Google AdMob Interstitial Ads in a clean, self-contained way.
 */
object AdManager {
    private const val TAG = "AdManager"

    // Google's official TEST Interstitial Ad Unit ID for Android.
    // Replace with real ad unit ID when ready for production.
    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private val actionCounter = AtomicInteger(0)

    // Dynamic threshold: show an ad after 3 to 5 actions (randomized after every show)
    private var nextAdThreshold = Random.nextInt(3, 6)

    /**
     * Initializes the Mobile Ads SDK and preloads the first interstitial ad.
     */
    fun initialize(context: Context) {
        try {
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "Google Mobile Ads SDK Initialized. Status: $status")
                // Start loading the first ad in the background
                preloadAd(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Mobile Ads SDK", e)
        }
    }

    /**
     * Preloads an interstitial ad if not already loaded or loading.
     */
    fun preloadAd(context: Context) {
        if (mInterstitialAd != null || isAdLoading) {
            Log.d(TAG, "Ad already loaded or currently loading. Skipping preload.")
            return
        }

        isAdLoading = true
        Log.d(TAG, "Preloading AdMob Interstitial Ad with Unit ID: $AD_UNIT_ID")

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    isAdLoading = false
                    Log.d(TAG, "AdMob Interstitial Ad loaded successfully.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    mInterstitialAd = null
                    isAdLoading = false
                    Log.e(TAG, "AdMob Interstitial Ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})")
                }
            }
        )
    }

    /**
     * Increments the user action counter. If the counter reaches the randomized threshold (3-5),
     * triggers the ad to show.
     *
     * @return true if an ad was shown, false otherwise.
     */
    fun logUserAction(activity: Activity): Boolean {
        val currentCount = actionCounter.incrementAndGet()
        Log.d(TAG, "User action logged. Current count: $currentCount / Threshold: $nextAdThreshold")

        if (currentCount >= nextAdThreshold) {
            // Reset counter and assign a new randomized threshold for the next sequence
            actionCounter.set(0)
            nextAdThreshold = Random.nextInt(3, 6)
            return showAd(activity)
        }
        return false
    }

    /**
     * Overload that extracts the Activity from context and logs the action.
     */
    fun logUserAction(context: Context): Boolean {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is Activity) {
                return logUserAction(currentContext)
            }
            currentContext = currentContext.baseContext
        }
        return false
    }

    /**
     * Displays the preloaded interstitial ad if available.
     *
     * @return true if an ad show attempt was initiated, false if ad was not ready.
     */
    fun showAd(activity: Activity): Boolean {
        val ad = mInterstitialAd
        if (ad != null) {
            Log.d(TAG, "Showing preloaded interstitial ad.")
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad dismissed by user.")
                    mInterstitialAd = null
                    // Immediately preload the next ad
                    preloadAd(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Ad failed to show: ${adError.message}")
                    mInterstitialAd = null
                    // Preload next ad in case of failure
                    preloadAd(activity.applicationContext)
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad displayed successfully.")
                }
            }

            activity.runOnUiThread {
                ad.show(activity)
            }
            return true
        } else {
            Log.d(TAG, "Ad is not loaded yet. Requesting preload...")
            preloadAd(activity.applicationContext)
            return false
        }
    }
}
