package com.hamari.mehenat.jobhunter.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.hamari.mehenat.jobhunter.domain.JobListing

data class TrackedApplication(
    val id: String,
    val title: String,
    val company: String,
    val url: String,
    val stage: String,
    val notes: String
)

class ApplicationTrackerStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "personal_job_tracker.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE applications (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                company TEXT NOT NULL,
                source_url TEXT NOT NULL,
                stage TEXT NOT NULL DEFAULT 'Saved',
                created_at INTEGER NOT NULL,
                expires_at INTEGER,
                notes TEXT NOT NULL DEFAULT ''
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE applications ADD COLUMN expires_at INTEGER")
        if (oldVersion < 3) db.execSQL("ALTER TABLE applications ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        if (oldVersion < 4) encryptLegacyRows(db)
    }

    private fun encryptLegacyRows(db: SQLiteDatabase) {
        val cipher = LocalFieldCipher()
        val rows = mutableListOf<Array<String>>()
        db.rawQuery("SELECT id, title, company, source_url, notes FROM applications", null).use { cursor ->
            while (cursor.moveToNext()) {
                rows += arrayOf(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4))
            }
        }
        rows.forEach { row ->
            val values = ContentValues().apply {
                put("title", cipher.encrypt(row[1]))
                put("company", cipher.encrypt(row[2]))
                put("source_url", cipher.encrypt(row[3]))
                put("notes", cipher.encrypt(row[4]))
            }
            db.update("applications", values, "id = ?", arrayOf(row[0]))
        }
    }

    fun save(job: JobListing): Boolean {
        val values = ContentValues().apply {
            put("id", job.id)
            put("title", LocalFieldCipher().encrypt(job.title.take(300)))
            put("company", LocalFieldCipher().encrypt(job.company.take(300)))
            put("source_url", LocalFieldCipher().encrypt(job.sourceUrl.take(2048)))
            put("stage", "Saved")
            put("created_at", System.currentTimeMillis())
            if (job.expiresAtEpochMs != null) put("expires_at", job.expiresAtEpochMs)
        }
        return writableDatabase.insertWithOnConflict(
            "applications", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun all(): List<TrackedApplication> {
        val now = System.currentTimeMillis()
        writableDatabase.delete("applications", "expires_at IS NOT NULL AND expires_at <= ? AND stage = 'Saved'", arrayOf(now.toString()))
        val result = mutableListOf<TrackedApplication>()
        readableDatabase.query(
            "applications",
            arrayOf("id", "title", "company", "source_url", "stage", "notes"),
            null, null, null, null, "created_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += TrackedApplication(
                    id = cursor.getString(0),
                    title = LocalFieldCipher().decrypt(cursor.getString(1)),
                    company = LocalFieldCipher().decrypt(cursor.getString(2)),
                    url = LocalFieldCipher().decrypt(cursor.getString(3)),
                    stage = cursor.getString(4),
                    notes = LocalFieldCipher().decrypt(cursor.getString(5))
                )
            }
        }
        return result
    }

    fun setStage(id: String, stage: String): Boolean {
        require(stage in ALLOWED_STAGES) { "Unsupported application stage" }
        val values = ContentValues().apply { put("stage", stage) }
        return writableDatabase.update("applications", values, "id = ?", arrayOf(id)) > 0
    }

    fun updateNotes(id: String, notes: String): Boolean {
        val values = ContentValues().apply { put("notes", LocalFieldCipher().encrypt(notes.take(4000))) }
        return writableDatabase.update("applications", values, "id = ?", arrayOf(id)) > 0
    }

    fun delete(id: String): Boolean =
        writableDatabase.delete("applications", "id = ?", arrayOf(id)) > 0

    companion object {
        private val ALLOWED_STAGES = setOf("Saved", "Contacted", "Awaiting response", "Applied", "Interview", "Offer", "Rejected")
    }
}
