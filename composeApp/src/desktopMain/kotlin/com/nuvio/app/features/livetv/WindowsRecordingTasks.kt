package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * One Windows scheduled task per scheduled recording ("Nuvio Recording <id>"), created with
 * schtasks from an XML definition (no admin rights, no locale-dependent dates). A minute before
 * the start it opens Nuvio minimised with [LiveTvRecordingPlatform.LAUNCH_FLAG]; Nuvio then starts
 * the recording itself (rearmAll at startup). The task wakes the PC from sleep, runs late if the PC
 * was off, and Windows deletes it after the end of the recording.
 */
internal object WindowsRecordingTasks {
    private val log = Logger.withTag("LiveTvRecording")
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val localFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /** Nuvio.exe of the installed app; null when running from Gradle (java.exe). */
    private val launcher: String? by lazy {
        ProcessHandle.current().info().command().orElse(null)
            ?.takeIf { File(it).name.equals("Nuvio.exe", ignoreCase = true) }
    }

    private fun taskName(recordingId: String) = "Nuvio Recording $recordingId"

    fun create(recording: LiveTvRecording) {
        if (!isWindows) return
        val exe = launcher
        if (exe == null) {
            log.i { "Not the installed Nuvio.exe: no Windows task for '${recording.title}' (works only while open)" }
            return
        }
        val zone = ZoneId.systemDefault()
        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(recording.startMillis - 60_000L), zone)
            .truncatedTo(ChronoUnit.SECONDS)
        val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(recording.endMillis), zone).truncatedTo(ChronoUnit.SECONDS)
        val xml = """
            <?xml version="1.0" encoding="UTF-16"?>
            <Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
              <RegistrationInfo>
                <Description>Nuvio: starts the scheduled Live TV recording "${escape(recording.title)}".</Description>
              </RegistrationInfo>
              <Triggers>
                <TimeTrigger>
                  <StartBoundary>${start.format(localFormat)}</StartBoundary>
                  <EndBoundary>${end.format(localFormat)}</EndBoundary>
                  <Enabled>true</Enabled>
                </TimeTrigger>
              </Triggers>
              <Principals>
                <Principal id="Author">
                  <LogonType>InteractiveToken</LogonType>
                  <RunLevel>LeastPrivilege</RunLevel>
                </Principal>
              </Principals>
              <Settings>
                <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
                <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
                <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
                <StartWhenAvailable>true</StartWhenAvailable>
                <WakeToRun>true</WakeToRun>
                <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
                <DeleteExpiredTaskAfter>PT1H</DeleteExpiredTaskAfter>
                <AllowStartOnDemand>true</AllowStartOnDemand>
                <Enabled>true</Enabled>
              </Settings>
              <Actions Context="Author">
                <Exec>
                  <Command>${escape(exe)}</Command>
                  <Arguments>${LiveTvRecordingPlatform.LAUNCH_FLAG}</Arguments>
                </Exec>
              </Actions>
            </Task>
        """.trimIndent()
        val file = File.createTempFile("nuvio-recording-", ".xml")
        try {
            // UTF-16 little endian with BOM, the encoding schtasks expects for /XML.
            file.writeText("\uFEFF" + xml, Charsets.UTF_16LE)
            val ok = schtasks("/Create", "/TN", taskName(recording.id), "/XML", file.absolutePath, "/F")
            if (ok) {
                log.i { "Windows task created for '${recording.title}' at $start" }
            } else {
                log.w { "Windows task not created for '${recording.title}': it starts only while Nuvio is open" }
            }
        } finally {
            file.delete()
        }
    }

    fun delete(recordingId: String) {
        if (!isWindows || launcher == null) return
        // Missing task (already run and expired, or never created): nothing to do.
        schtasks("/Delete", "/TN", taskName(recordingId), "/F", logFailure = false)
    }

    private fun schtasks(vararg args: String, logFailure: Boolean = true): Boolean = runCatching {
        val process = ProcessBuilder(listOf("schtasks.exe") + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("schtasks timed out")
        }
        val code = process.exitValue()
        if (code != 0 && logFailure) log.w { "schtasks ${args.first()} failed ($code): ${output.trim()}" }
        code == 0
    }.onFailure { log.e(it) { "schtasks ${args.first()} could not run" } }.getOrDefault(false)

    private fun escape(text: String) = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
