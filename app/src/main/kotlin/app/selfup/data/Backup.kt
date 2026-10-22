package app.selfup.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Whole-database backup to a file the user picks, and restore from one. The file is the
 * SQLite database itself, so every quest, completion, record and XP event comes back exactly,
 * and an older backup is brought up to date by the normal migrations on the next launch.
 */
class Backup(private val context: Context, private val db: SelfUpDatabase) {
    private val dbFile: File get() = context.getDatabasePath(SelfUpDatabase.NAME)

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        // Fold the write-ahead log into the main file so the copy holds everything.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val out = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Can't write the backup file" }
        out.use { dbFile.inputStream().use { input -> input.copyTo(it) } }
    }

    /**
     * Replaces all data with the backup at [uri]. Returns false, changing nothing, when the
     * file isn't a SelfUp backup or comes from a newer app version. On success the database
     * is closed, so the caller must restart the app.
     */
    suspend fun restore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val staged = File(context.cacheDir, "restore.db")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
            input.use { it.copyToFile(staged) }
            if (!isSelfUpDatabase(staged)) return@withContext false
            db.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            staged.copyTo(dbFile, overwrite = true)
            true
        } finally {
            staged.delete()
        }
    }

    private fun isSelfUpDatabase(file: File): Boolean {
        val header = file.inputStream().use { it.readNBytesCompat(16) }
        if (!header.contentEquals(SQLITE_HEADER)) return false
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { backup ->
                val hasQuests = backup.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'quest'", null).use { it.moveToFirst() }
                hasQuests && backup.version in 1..SelfUpDatabase.VERSION
            }
        }.getOrDefault(false)
    }

    private fun InputStream.copyToFile(file: File) = file.outputStream().use { copyTo(it) }

    private fun InputStream.readNBytesCompat(n: Int): ByteArray {
        val buffer = ByteArray(n)
        var read = 0
        while (read < n) {
            val count = read(buffer, read, n - read)
            if (count < 0) break
            read += count
        }
        return buffer.copyOf(read)
    }

    private companion object {
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
