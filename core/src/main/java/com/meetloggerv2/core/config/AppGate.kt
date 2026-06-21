package com.meetloggerv2.core.config

/**
 * Immutable snapshot of the Remote Config values that drive the app access
 * gates. Captured from [AppConfig.snapshot] so gate evaluation stays a pure
 * function with no Firebase/Android dependencies (and is trivially unit-testable).
 */
data class GateConfig(
    val minSupportedVersion: Int,
    val maintenanceMode: Boolean,
    val maintenanceMessage: String,
    val blockedUserIds: Set<String>,
    val updateUrl: String,
)

/**
 * The outcome of evaluating the access gates for the current app version and
 * signed-in user. Anything other than [Allowed] means the app must show a
 * blocking screen instead of its normal content.
 */
sealed interface GateResult {
    data object Allowed : GateResult
    data class ForceUpdate(val updateUrl: String) : GateResult
    data object Blocked : GateResult
    data class Maintenance(val message: String) : GateResult
}

/**
 * Pure evaluator for the Remote Config access gates.
 *
 * Priority (highest first): ForceUpdate > Blocked > Maintenance > Allowed.
 *  - Force update wins because a below-minimum build may be unusable/insecure,
 *    so the user must update before anything else is meaningful.
 *  - A blocked account is shown its ban next, rather than a friendly
 *    "we'll be back" maintenance notice.
 *  - Maintenance is a temporary, global ops state shown to everyone else.
 *
 * Fail-open: with default config (min=1, maintenance=false, empty blocklist)
 * the result is [GateResult.Allowed], so a failed/empty Remote Config fetch
 * never locks users out. The server remains authoritative for genuine blocks.
 */
object AppGate {
    fun evaluate(
        config: GateConfig,
        currentVersionCode: Int,
        userId: String?,
    ): GateResult {
        if (currentVersionCode < config.minSupportedVersion) {
            return GateResult.ForceUpdate(config.updateUrl)
        }
        if (!userId.isNullOrBlank() && config.blockedUserIds.contains(userId)) {
            return GateResult.Blocked
        }
        if (config.maintenanceMode) {
            return GateResult.Maintenance(config.maintenanceMessage)
        }
        return GateResult.Allowed
    }
}
