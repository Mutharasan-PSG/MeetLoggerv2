package com.meetloggerv2.core.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.meetloggerv2.core.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object AppConfig {
    private const val TAG = "AppConfig"
    private const val KEY_FREE_PLAN_LIMIT = "free_plan_limit"
    private const val KEY_FREE_PLAN_AUDIO_LIMIT_MINUTES = "free_plan_audio_limit_minutes"
    private const val KEY_REMOTE_CONFIG_CACHE_MINUTES = "remote_config_cache_minutes"
    private const val KEY_PRICE_USD = "price_usd"
    private const val KEY_PRICE_INR = "price_inr"
    private const val KEY_PRICE_EUR = "price_eur"
    private const val KEY_PRICE_GBP = "price_gbp"
    private const val KEY_PRICE_JPY = "price_jpy"

    // --- Access gate keys (force-update / maintenance / per-user block) ---
    private const val KEY_MIN_SUPPORTED_VERSION = "min_supported_version"
    private const val KEY_MAINTENANCE_MODE = "maintenance_mode"
    private const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"
    private const val KEY_BLOCKED_USER_IDS = "blocked_user_ids"
    private const val KEY_UPDATE_URL = "update_url"

    private const val DEFAULT_MAINTENANCE_MESSAGE =
        "MeetLogger is undergoing scheduled maintenance. Please check back shortly."
    private const val DEFAULT_UPDATE_URL =
        "https://play.google.com/store/apps/details?id=com.meetloggerv2"

    @Volatile
    var freePlanLimit: Int = 7
        private set

    @Volatile
    var freePlanAudioLimitMinutes: Int = 30
        private set

    @Volatile
    var remoteConfigCacheMinutes: Int = 10
        private set

    @Volatile
    var priceUsd: String = "9.99"
        private set

    @Volatile
    var priceInr: String = "849"
        private set

    @Volatile
    var priceEur: String = "9.49"
        private set

    @Volatile
    var priceGbp: String = "7.99"
        private set

    @Volatile
    var priceJpy: String = "1490"
        private set

    // --- Access gate values ---
    @Volatile
    var minSupportedVersion: Int = 1
        private set

    @Volatile
    var maintenanceMode: Boolean = false
        private set

    @Volatile
    var maintenanceMessage: String = DEFAULT_MAINTENANCE_MESSAGE
        private set

    @Volatile
    var blockedUserIds: Set<String> = emptySet()
        private set

    @Volatile
    var updateUrl: String = DEFAULT_UPDATE_URL
        private set

    @Volatile
    private var lastFetchedTimeMillis: Long = 0

    // Increments on every successful activate so live screens (e.g. HomeActivity)
    // can re-evaluate the access gate in real-time when the console changes.
    private val _configUpdates = MutableStateFlow(0)
    val configUpdates: StateFlow<Int> = _configUpdates.asStateFlow()

    /** Captures the current gate-relevant values for pure [AppGate] evaluation. */
    fun snapshot(): GateConfig = GateConfig(
        minSupportedVersion = minSupportedVersion,
        maintenanceMode = maintenanceMode,
        maintenanceMessage = maintenanceMessage,
        blockedUserIds = blockedUserIds,
        updateUrl = updateUrl,
    )

    /**
     * Reads every Remote Config value off a freshly activated config into the
     * cached fields, refreshes the fetch timestamp, and signals [configUpdates].
     * Shared by the initial fetch, the real-time listener, and the suspending
     * refetch so all paths stay in sync as keys are added.
     */
    private fun applyActivatedValues(remoteConfig: FirebaseRemoteConfig) {
        freePlanLimit = remoteConfig.getLong(KEY_FREE_PLAN_LIMIT).toInt()
        freePlanAudioLimitMinutes = remoteConfig.getLong(KEY_FREE_PLAN_AUDIO_LIMIT_MINUTES).toInt()
        remoteConfigCacheMinutes = remoteConfig.getLong(KEY_REMOTE_CONFIG_CACHE_MINUTES).toInt()
        priceUsd = remoteConfig.getString(KEY_PRICE_USD)
        priceInr = remoteConfig.getString(KEY_PRICE_INR)
        priceEur = remoteConfig.getString(KEY_PRICE_EUR)
        priceGbp = remoteConfig.getString(KEY_PRICE_GBP)
        priceJpy = remoteConfig.getString(KEY_PRICE_JPY)

        minSupportedVersion = remoteConfig.getLong(KEY_MIN_SUPPORTED_VERSION).toInt()
        maintenanceMode = remoteConfig.getBoolean(KEY_MAINTENANCE_MODE)
        maintenanceMessage = remoteConfig.getString(KEY_MAINTENANCE_MESSAGE)
            .ifBlank { DEFAULT_MAINTENANCE_MESSAGE }
        blockedUserIds = parseBlockedUserIds(remoteConfig.getString(KEY_BLOCKED_USER_IDS))
        updateUrl = remoteConfig.getString(KEY_UPDATE_URL).ifBlank { DEFAULT_UPDATE_URL }

        lastFetchedTimeMillis = System.currentTimeMillis()
        _configUpdates.value = _configUpdates.value + 1
        AppLogger.d(
            TAG,
            "Remote Config applied. freePlanLimit=$freePlanLimit, " +
                "minSupportedVersion=$minSupportedVersion, maintenanceMode=$maintenanceMode, " +
                "blockedUserIds=${blockedUserIds.size}"
        )
    }

    private fun parseBlockedUserIds(raw: String): Set<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** Keys that should trigger a re-activation when changed in the console. */
    private val WATCHED_KEYS = setOf(
        KEY_FREE_PLAN_LIMIT,
        KEY_FREE_PLAN_AUDIO_LIMIT_MINUTES,
        KEY_REMOTE_CONFIG_CACHE_MINUTES,
        KEY_PRICE_USD,
        KEY_PRICE_INR,
        KEY_PRICE_EUR,
        KEY_PRICE_GBP,
        KEY_PRICE_JPY,
        KEY_MIN_SUPPORTED_VERSION,
        KEY_MAINTENANCE_MODE,
        KEY_MAINTENANCE_MESSAGE,
        KEY_BLOCKED_USER_IDS,
        KEY_UPDATE_URL,
    )

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
                KEY_FREE_PLAN_AUDIO_LIMIT_MINUTES to 30,
                KEY_REMOTE_CONFIG_CACHE_MINUTES to 10,
                KEY_PRICE_USD to "9.99",
                KEY_PRICE_INR to "849",
                KEY_PRICE_EUR to "9.49",
                KEY_PRICE_GBP to "7.99",
                KEY_PRICE_JPY to "1490",
                KEY_MIN_SUPPORTED_VERSION to 1,
                KEY_MAINTENANCE_MODE to false,
                KEY_MAINTENANCE_MESSAGE to DEFAULT_MAINTENANCE_MESSAGE,
                KEY_BLOCKED_USER_IDS to "",
                KEY_UPDATE_URL to DEFAULT_UPDATE_URL
            ))

            // Fetch and activate initial values asynchronously
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        applyActivatedValues(remoteConfig)
                        AppLogger.d(TAG, "Initial Remote Config fetch succeeded.")
                    } else {
                        AppLogger.e(TAG, "Initial Remote Config fetch failed", task.exception)
                    }
                }

            // Also attach a real-time listener to get immediate config updates when updated on the console
            remoteConfig.addOnConfigUpdateListener(object : com.google.firebase.remoteconfig.ConfigUpdateListener {
                override fun onUpdate(configUpdate: com.google.firebase.remoteconfig.ConfigUpdate) {
                    AppLogger.d(TAG, "Remote Config updated in real-time: ${configUpdate.updatedKeys}")
                    if (configUpdate.updatedKeys.any { it in WATCHED_KEYS }) {
                        remoteConfig.activate().addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                applyActivatedValues(remoteConfig)
                                AppLogger.d(TAG, "Real-time Remote Config activated.")
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
     * Checks if configured cache minutes have elapsed since the last fetch and performs a blocking
     * suspending refetch before any operation to ensure limit validation is current.
     */
    suspend fun ensureLimitValidated() {
        val now = System.currentTimeMillis()
        val cacheIntervalMs = remoteConfigCacheMinutes * 60 * 1000L
        if (now - lastFetchedTimeMillis > cacheIntervalMs) {
            AppLogger.d(TAG, "Last fetch was before $remoteConfigCacheMinutes minutes. Refetching Remote Config...")
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
                                applyActivatedValues(remoteConfig)
                                AppLogger.d(TAG, "Refetch succeeded.")
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
