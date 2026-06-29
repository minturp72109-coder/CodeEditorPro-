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
 * Upgraded to production quality with strict timing limits, action count thresholds,
 * transition points integration, leak safety, and non-blocking recovery.
 */
object AdManager {
    private const val TAG = "AdManager"

    // Google's official TEST Interstitial Ad Unit ID for Android.
    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var isAdShowing = false

    // Timing state
    private var appLaunchTime: Long = System.currentTimeMillis()
    private var lastAdShowTime: Long = 0

    // Meaningful user actions counter
    private val actionCounter = AtomicInteger(0)

    // Dynamic threshold: show an ad after 10 to 15 actions (randomized after every successful ad display)
    private var nextAdThreshold = Random.nextInt(10, 16)

    /**
     * Initializes the Mobile Ads SDK, records the launch time, and preloads the first interstitial ad.
     */
    fun initialize(context: Context) {
        try {
            appLaunchTime = System.currentTimeMillis()
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "Google Mobile Ads SDK Initialized. Status: $status")
                // Start loading the first ad in the background using application context
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
            context.applicationContext, // always use applicationContext to prevent memory leaks
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
     * Increments the user action counter. This is for tracking general user engagements
     * (e.g. typing, executing commands, switching views, git commits).
     */
    fun logUserAction(context: Context): Boolean {
        val currentCount = actionCounter.incrementAndGet()
        Log.d(TAG, "Meaningful action logged. Current count: $currentCount / Threshold: $nextAdThreshold")
        return false
    }

    /**
     * Safe helper to extract Activity context from generic Context.
     */
    fun getActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    /**
     * Determines whether an ad is currently eligible to be shown.
     */
    fun isEligible(): Boolean {
        val now = System.currentTimeMillis()

        // 1. Wait at least 60 seconds after app launch before the first ad becomes eligible.
        if (now - appLaunchTime < 60_000) {
            Log.d(TAG, "Ad not eligible: App launch was less than 60 seconds ago.")
            return false
        }

        // 2. At least 2 minutes have passed since the previous interstitial was shown.
        if (lastAdShowTime > 0 && (now - lastAdShowTime < 120_000)) {
            Log.d(TAG, "Ad not eligible: Less than 120 seconds since previous ad show.")
            return false
        }

        // 3. Ensure the user has completed at least 10–15 meaningful actions.
        val currentActions = actionCounter.get()
        if (currentActions < nextAdThreshold) {
            Log.d(TAG, "Ad not eligible: Actions logged ($currentActions) below threshold ($nextAdThreshold).")
            return false
        }

        // 4. Ensure we aren't already displaying an ad.
        if (isAdShowing) {
            Log.d(TAG, "Ad not eligible: An ad is currently displaying.")
            return false
        }

        // 5. Ensure an ad is actually loaded.
        if (mInterstitialAd == null) {
            Log.d(TAG, "Ad not eligible: No preloaded ad available.")
            return false
        }

        return true
    }

    /**
     * Displays the preloaded interstitial ad at a natural transition point, if eligible.
     * If not eligible or if no ad is ready, executes [onAdClosed] immediately to continue normally.
     */
    fun showAdIfEligible(context: Context, onAdClosed: () -> Unit) {
        val activity = getActivity(context)
        if (activity == null) {
            Log.d(TAG, "Cannot show ad: context is not or does not wrap an Activity.")
            onAdClosed()
            return
        }

        if (!isEligible()) {
            onAdClosed()
            return
        }

        val ad = mInterstitialAd
        if (ad != null) {
            isAdShowing = true
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad dismissed by user.")
                    mInterstitialAd = null
                    isAdShowing = false
                    // Preload the next ad using applicationContext
                    preloadAd(activity.applicationContext)
                    activity.runOnUiThread { onAdClosed() }
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Ad failed to show: ${adError.message}")
                    mInterstitialAd = null
                    isAdShowing = false
                    // Preload the next ad using applicationContext
                    preloadAd(activity.applicationContext)
                    activity.runOnUiThread { onAdClosed() }
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad displayed successfully.")
                    lastAdShowTime = System.currentTimeMillis()
                    // Reset meaningful action count and set a new threshold
                    actionCounter.set(0)
                    nextAdThreshold = Random.nextInt(10, 16)
                }
            }

            activity.runOnUiThread {
                try {
                    ad.show(activity)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception showing ad", e)
                    mInterstitialAd = null
                    isAdShowing = false
                    preloadAd(activity.applicationContext)
                    onAdClosed()
                }
            }
        } else {
            onAdClosed()
        }
    }
}
