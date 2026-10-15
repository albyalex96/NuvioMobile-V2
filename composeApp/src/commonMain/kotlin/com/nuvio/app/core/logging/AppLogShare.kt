package com.nuvio.app.core.logging

import androidx.compose.runtime.Composable

/** Writes an export file in a temporary, shareable folder; returns its path, or null on failure. Blocking. */
internal expect fun writeAppLogExportFile(fileName: String, text: String): String?

/** Returns an action that opens the system share sheet for a file written by [writeAppLogExportFile]. */
@Composable
internal expect fun rememberAppLogFileSharer(): (path: String) -> Unit
