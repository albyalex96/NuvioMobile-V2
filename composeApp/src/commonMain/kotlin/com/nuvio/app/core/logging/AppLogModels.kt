package com.nuvio.app.core.logging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Levels shown by the in-app logger; Kermit's Verbose folds into [DEBUG], Assert into [ERROR]. */
enum class AppLogLevel(val shortLabel: String) {
    DEBUG("D"),
    INFO("I"),
    WARN("W"),
    ERROR("E"),
}

/** Where an entry comes from: the app's own Kermit loggers or an import of the system log (logcat). */
enum class AppLogSource {
    APP,
    SYSTEM,
}

/** One stored log line. Short serial names keep the on-disk JSON lines compact. */
@Serializable
data class AppLogEntry(
    @SerialName("i") val id: Long,
    @SerialName("t") val epochMs: Long,
    @SerialName("l") val level: AppLogLevel,
    @SerialName("g") val tag: String,
    @SerialName("m") val message: String,
    @SerialName("x") val throwable: String? = null,
    @SerialName("s") val source: AppLogSource = AppLogSource.APP,
)

/** An entry not yet numbered by [AppLogStore]. */
internal data class PendingAppLogEntry(
    val epochMs: Long,
    val level: AppLogLevel,
    val tag: String,
    val message: String,
    val throwable: String? = null,
    val source: AppLogSource = AppLogSource.APP,
)
