package com.meetloggerv2.core.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.meetloggerv2.core.util.AppLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object AppConfig {
    private const val TAG = "AppConfig"
    private const val KEY_FREE_PLAN_LIMIT = "free_plan_limit"
    private const val KEY_FREE_PLAN_AUDIO_LIMIT_MINUTES = "free_plan_audio_limit_minutes"
    private const val TEN_MINUTES_MS = 10 * 60 * 1000L

    @Volatile
    var freePlanLimit: Int = 7
        private set

    @Volatile
    var freePlanAudioLimitMinutes: Int = 30
        private set

    @Volatile
    private var lastFetchedTimeMillis: Long = 0

    /**
     * Initializes Remote Config defaults and attempts an initial fetch.
     */
    fun initialize() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 600 // 10 minutes fetch interval
            }
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.setDefaultsAsync(mapOf(
                KEY_FREE_PLAN_LIMIT to 7,
                KEY_FREE_PLAN_AUDIO_LIMIT_MINUTES to 30
            ))

            // Fetch and activate initial values asynchronously
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        freePlanLimit = remoteConfig.getLong(KEY_FREE_PLAN_LIMIT).toInt()
                        freePlanAudioLimitMinutes = remoteConfig.getLong(KEY_FREE_PLAN_AUDIO_LIMIT_MINUTES).toInt()
                        lastFetchedTimeMillis = System.currentTimeMillis()
                        AppLogger.d(TAG, "Initial Remote Config fetch succeeded. freePlanLimit = $freePlanLimit, freePlanAudioLimitMinutes = $freePlanAudioLimitMinutes")
                    } else {
                        AppLogger.e(TAG, "Initial Remote Config fetch failed", task.exception)
                    }
                }

            // Also attach a real-time listener to get immediate config updates when updated on the console
            remoteConfig.addOnConfigUpdateListener(object : com.google.firebase.remoteconfig.ConfigUpdateListener {
                override fun onUpdate(configUpdate: com.google.firebase.remoteconfig.ConfigUpdate) {
                    AppLogger.d(TAG, "Remote Config updated in real-time: ${configUpdate.updatedKeys}")
                    if (configUpdate.updatedKeys.contains(KEY_FREE_PLAN_LIMIT) || 
                        configUpdate.updatedKeys.contains(KEY_FREE_PLAN_AUDIO_LIMIT_MINUTES)) {
                        remoteConfig.activate().addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                freePlanLimit = remoteConfig.getLong(KEY_FREE_PLAN_LIMIT).toInt()
                                freePlanAudioLimitMinutes = remoteConfig.getLong(KEY_FREE_PLAN_AUDIO_LIMIT_MINUTES).toInt()
                                lastFetchedTimeMillis = System.currentTimeMillis()
                                AppLogger.d(TAG, "Real-time Remote Config activated. freePlanLimit = $freePlanLimit, freePlanAudioLimitMinutes = $freePlanAudioLimitMinutes")
                            }
                        }
                    }
                }

                override fun onError(error: com.google.firebase.remoteconfig.FirebaseRemoteConfigException) {
                    AppLogger.e(TAG, "Config update listener error", error)
                }
            })
        } catch (e: Exception) {
            AppLogger.e(TAG, "Remote Config initialization error", e)
        }
    }

    /**
     * Checks if 10 minutes have elapsed since the last fetch and performs a blocking
     * suspending refetch before any operation to ensure limit validation is current.
     */
    suspend fun ensureLimitValidated() {
        val now = System.currentTimeMillis()
        if (now - lastFetchedTimeMillis > TEN_MINUTES_MS) {
            AppLogger.d(TAG, "Last fetch was before 10 minutes. Refetching Remote Config...")
            fetchConfigSuspended()
        }
    }

    private suspend fun fetchConfigSuspended(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            remoteConfig.fetch(0) // Force fetch with 0 interval to bypass caching
                .addOnCompleteListener { fetchTask ->
                    if (fetchTask.isSuccessful) {
                        remoteConfig.activate().addOnCompleteListener { activateTask ->
                            if (activateTask.isSuccessful) {
                                freePlanLimit = remoteConfig.getLong(KEY_FREE_PLAN_LIMIT).toInt()
                                freePlanAudioLimitMinutes = remoteConfig.getLong(KEY_FREE_PLAN_AUDIO_LIMIT_MINUTES).toInt()
                                lastFetchedTimeMillis = System.currentTimeMillis()
                                AppLogger.d(TAG, "Refetch succeeded. freePlanLimit = $freePlanLimit, freePlanAudioLimitMinutes = $freePlanAudioLimitMinutes")
                                continuation.resume(true)
                            } else {
                                AppLogger.e(TAG, "Refetch: Activation failed", activateTask.exception)
                                continuation.resume(false)
                            }
                        }
                    } else {
                        AppLogger.e(TAG, "Refetch: Fetch failed", fetchTask.exception)
                        continuation.resume(false)
                    }
                }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Suspended fetch encountered error", e)
            continuation.resume(false)
        }
    }
}
