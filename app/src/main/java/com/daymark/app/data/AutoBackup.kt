package com.daymark.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daymark.app.AppContainer
import com.daymark.app.container
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Builds the full backup text (optionally password-protected) from the live database. */
object BackupWriter {
    suspend fun build(c: AppContainer, password: String = ""): String {
        val habits = c.database.habitDao().getAll()
        val plain = BackupCodec.encode(
            c.settings.current(), habits, c.relapses.allEntities(), c.urges.allEntities(), c.journal.allEntities(),
        )
        return if (password.isBlank()) plain else BackupCrypto.encrypt(plain, password)
    }
}

/**
 * Daily backups into a folder the user picked (Storage Access Framework — no storage permission, no internet).
 * Keeps the newest [KEEP] files named `daymark-auto-YYYY-MM-DD.json`; older ones are removed.
 */
object AutoBackup {
    private const val WORK = "daymark-auto-backup"
    private const val PREFIX = "daymark-auto-"
    private const val KEEP = 7

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Writes today's backup (replacing one already made today). Returns false if the folder can't be written. */
    suspend fun runOnce(context: Context): Boolean = runCatching {
        val c = context.container
        val s = c.settings.current()
        if (s.autoBackupUri.isEmpty() || !s.onboarded) return false
        val tree = Uri.parse(s.autoBackupUri)
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = "$PREFIX${LocalDate.now()}.json"
        val existing = children(context, tree)
        existing.filter { it.second == name }.forEach { DocumentsContract.deleteDocument(resolver, it.first) }
        val doc = DocumentsContract.createDocument(resolver, parent, "application/json", name) ?: return false
        val text = BackupWriter.build(c, s.autoBackupPassword)
        resolver.openOutputStream(doc, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: return false
        // Prune: names sort by date.
        children(context, tree).filter { it.second.startsWith(PREFIX) }.sortedByDescending { it.second }.drop(KEEP)
            .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.first) } }
        true
    }.getOrDefault(false)

    private fun children(context: Context, tree: Uri): List<Pair<Uri, String>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = ArrayList<Pair<Uri, String>>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getString(0) ?: continue
                val name = cur.getString(1) ?: continue
                out += DocumentsContract.buildDocumentUriUsingTree(tree, id) to name
            }
        }
        return out
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AutoBackup.runOnce(applicationContext)
        return Result.success()
    }
}
