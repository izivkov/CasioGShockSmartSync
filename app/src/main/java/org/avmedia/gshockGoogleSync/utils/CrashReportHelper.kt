package org.avmedia.gshockGoogleSync.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.avmedia.gshockGoogleSync.BuildConfig
import timber.log.Timber

/**
 * Helper class for crash reporting and logging. Writes crash information to files that users can
 * share for debugging.
 */
object CrashReportHelper {
    private const val CRASH_LOG_DIR = "crash_logs"
    private const val MAX_LOG_FILES = 5
    private const val PAIRING_CRASH_FLAG = "PairingCrashFlag"

    /** Log a crash with full context information */
    fun logCrash(context: Context, throwable: Throwable, additionalInfo: String = "") {
        try {
            val crashLog = buildCrashReport(context, throwable, additionalInfo)
            writeCrashLog(context, crashLog)
            Timber.e(throwable, "Crash logged: $additionalInfo")
        } catch (e: Exception) {
            Timber.e(e, "Failed to write crash log")
        }
    }

    /** Log a pairing-specific crash */
    fun logPairingCrash(
            context: Context,
            throwable: Throwable,
            deviceAddress: String? = null,
            deviceName: String? = null
    ) {
        val info = buildString {
            append("PAIRING CRASH\n")
            append("Device Address: ${deviceAddress ?: "Unknown"}\n")
            append("Device Name: ${deviceName ?: "Unknown"}\n")
            append(
                    "Last Device Address: ${LocalDataStorage.get(context, "LastDeviceAddress", "")}\n"
            )
            append("Last Device Name: ${LocalDataStorage.get(context, "LastDeviceName", "")}\n")
        }

        // Set crash flag for recovery
        LocalDataStorage.put(context, PAIRING_CRASH_FLAG, "true")

        logCrash(context, throwable, info)
    }

    /** Check if there was a previous pairing crash */
    fun hasPairingCrashFlag(context: Context): Boolean {
        return LocalDataStorage.get(context, PAIRING_CRASH_FLAG, "false") == "true"
    }

    /** Clear the pairing crash flag */
    fun clearPairingCrashFlag(context: Context) {
        LocalDataStorage.put(context, PAIRING_CRASH_FLAG, "false")
        Timber.i("Pairing crash flag cleared")
    }

    /** Build a comprehensive crash report */
    private fun buildCrashReport(
            context: Context,
            throwable: Throwable,
            additionalInfo: String
    ): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val stackTrace =
                StringWriter().apply { throwable.printStackTrace(PrintWriter(this)) }.toString()

        return buildString {
            appendLine("=== CRASH REPORT ===")
            appendLine("Timestamp: $timestamp")
            appendLine()

            appendLine("=== APP INFO ===")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine()

            appendLine("=== DEVICE INFO ===")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Brand: ${Build.BRAND}")
            appendLine()

            if (additionalInfo.isNotBlank()) {
                appendLine("=== ADDITIONAL INFO ===")
                appendLine(additionalInfo)
                appendLine()
            }

            appendLine("=== PAIRING STATE ===")
            try {
                appendLine(
                        "Last Device Address: ${LocalDataStorage.get(context, "LastDeviceAddress", "N/A")}"
                )
                appendLine(
                        "Last Device Name: ${LocalDataStorage.get(context, "LastDeviceName", "N/A")}"
                )
                val addresses = LocalDataStorage.getDeviceAddresses(context)
                appendLine("Stored Device Addresses: ${addresses.joinToString(", ")}")
            } catch (e: Exception) {
                appendLine("Error reading pairing state: ${e.message}")
            }
            appendLine()

            appendLine("=== STACK TRACE ===")
            appendLine(stackTrace)
        }
    }

    /** Write crash log to file */
    private fun writeCrashLog(context: Context, crashLog: String) {
        val logDir = File(context.cacheDir, CRASH_LOG_DIR)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        // Clean up old logs
        cleanupOldLogs(logDir)

        // Write new log
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val logFile = File(logDir, "crash_$timestamp.txt")
        logFile.writeText(crashLog)

        Timber.i("Crash log written to: ${logFile.absolutePath}")
    }

    /** Clean up old crash logs, keeping only the most recent ones */
    private fun cleanupOldLogs(logDir: File) {
        val logFiles = logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return

        if (logFiles.size > MAX_LOG_FILES) {
            logFiles.drop(MAX_LOG_FILES).forEach { file ->
                file.delete()
                Timber.d("Deleted old crash log: ${file.name}")
            }
        }
    }

    /** Get all crash logs for sharing */
    fun getCrashLogs(context: Context): List<File> {
        val logDir = File(context.cacheDir, CRASH_LOG_DIR)
        if (!logDir.exists()) {
            return emptyList()
        }

        return logDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    /** Get the most recent crash log content */
    fun getLatestCrashLog(context: Context): String? {
        val logs = getCrashLogs(context)
        return logs.firstOrNull()?.readText()
    }

    /** Delete all crash logs */
    fun clearAllCrashLogs(context: Context) {
        val logDir = File(context.cacheDir, CRASH_LOG_DIR)
        if (logDir.exists()) {
            logDir.deleteRecursively()
            Timber.i("All crash logs cleared")
        }
    }
}
