package de.mimuc.senseeverything.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class ExportStatus {
    object Idle : ExportStatus()
    object Exporting : ExportStatus()
    object Success : ExportStatus()
    data class Error(val message: String) : ExportStatus()
}

@Singleton
class DatabaseExporter @Inject constructor(
    private val database: AppDatabase,
    private val dataStoreManager: DataStoreManager
) {
    private val _exportStatus = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val exportStatus: StateFlow<ExportStatus> get() = _exportStatus

    suspend fun exportDatabase(context: Context, onSuccess: (zipFile: java.io.File) -> Unit) {
        _exportStatus.value = ExportStatus.Exporting

        try {
            withContext(Dispatchers.IO) {
                // Get database path
                val dbFile = context.getDatabasePath("senseeverything-roomdb")

                if (!dbFile.exists()) {
                    _exportStatus.value = ExportStatus.Error("Database file not found")
                    return@withContext
                }

                // Checkpoint WAL to ensure all data is in main database file
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

                // Create export directory in cache
                val exportDir = java.io.File(context.cacheDir, "database_exports")
                exportDir.mkdirs()

                // Generate metadata
                val metadata = generateMetadata()

                // Create zip file
                val zipFile = createZipFile(dbFile, metadata, exportDir)

                // Share file on main thread
                withContext(Dispatchers.Main) {
                    shareFile(context, zipFile)
                    _exportStatus.value = ExportStatus.Success

                    // Callback for cleanup
                    onSuccess(zipFile)
                }
            }
        } catch (e: Exception) {
            WHALELog.e("DatabaseExporter", "Error exporting database: ${e.message}", e)
            _exportStatus.value = ExportStatus.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun generateMetadata(): String {
        val participantId = dataStoreManager.participantIdFlow.first()
        val logDataCount = database.logDataDao().all.size.toLong()
        val pendingQuestionnaireCount = database.pendingQuestionnaireDao().getAll().size.toLong()
        val notificationTriggerCount = database.notificationTriggerDao().getAll().size.toLong()
        val socialNetworkContactCount = database.socialNetworkContactDao().getAll().size.toLong()

        return buildString {
            appendLine("=== WHALE Database Export Metadata ===")
            appendLine()
            appendLine("Participant ID: $participantId")
            appendLine("Export Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine()
            appendLine("=== Table Row Counts ===")
            appendLine("LogData: $logDataCount")
            appendLine("PendingQuestionnaire: $pendingQuestionnaireCount")
            appendLine("NotificationTrigger: $notificationTriggerCount")
            appendLine("SocialNetworkContact: $socialNetworkContactCount")
            appendLine("ScheduledAlarm: (see database)")
            appendLine("GeneratedKey: (see database)")
            appendLine()
            appendLine("Note: Complete database with all tables is included in the export.")
        }
    }

    private fun createZipFile(dbFile: java.io.File, metadata: String, exportDir: java.io.File): java.io.File {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val zipFile = java.io.File(exportDir, "whale_database_$timestamp.zip")

        java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zipOut ->
            // Add database file
            java.io.FileInputStream(dbFile).use { fis ->
                val entry = java.util.zip.ZipEntry("senseeverything-roomdb.db")
                zipOut.putNextEntry(entry)
                fis.copyTo(zipOut)
                zipOut.closeEntry()
            }

            // Add WAL file if it exists
            val walFile = java.io.File(dbFile.parent, "${dbFile.name}-wal")
            if (walFile.exists()) {
                java.io.FileInputStream(walFile).use { fis ->
                    val entry = java.util.zip.ZipEntry("senseeverything-roomdb.db-wal")
                    zipOut.putNextEntry(entry)
                    fis.copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }

            // Add SHM file if it exists
            val shmFile = java.io.File(dbFile.parent, "${dbFile.name}-shm")
            if (shmFile.exists()) {
                java.io.FileInputStream(shmFile).use { fis ->
                    val entry = java.util.zip.ZipEntry("senseeverything-roomdb.db-shm")
                    zipOut.putNextEntry(entry)
                    fis.copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }

            // Add metadata
            val metadataEntry = java.util.zip.ZipEntry("metadata.txt")
            zipOut.putNextEntry(metadataEntry)
            zipOut.write(metadata.toByteArray())
            zipOut.closeEntry()
        }

        return zipFile
    }

    private fun shareFile(context: Context, file: java.io.File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "WHALE Database Export")
            putExtra(Intent.EXTRA_TEXT, "Database export from WHALE study app")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Database Export"))
    }
}
