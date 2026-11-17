package com.hamari.mehenat.jobhunter.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hamari.mehenat.jobhunter.MainActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class InterviewRecord(
    val id: String,
    val applicationId: String,
    val company: String,
    val role: String,
    val scheduledAtEpochMs: Long,
    val timeZoneId: String,
    val locationOrLink: String,
    val notes: String
)

class InterviewStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "personal_interviews.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE interviews (
                id TEXT PRIMARY KEY NOT NULL,
                application_id TEXT NOT NULL,
                company TEXT NOT NULL,
                role TEXT NOT NULL,
                scheduled_at INTEGER NOT NULL,
                timezone TEXT NOT NULL,
                location_or_link TEXT NOT NULL,
                notes TEXT NOT NULL
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) encryptLegacyRows(db)
    }

    private fun encryptLegacyRows(db: SQLiteDatabase) {
        val cipher = LocalFieldCipher()
        val rows = mutableListOf<Array<String>>()
        db.rawQuery("SELECT id, company, role, location_or_link, notes FROM interviews", null).use { cursor ->
            while (cursor.moveToNext()) {
                rows += arrayOf(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4))
            }
        }
        rows.forEach { row ->
            val values = ContentValues().apply {
                put("company", cipher.encryptIfNeeded(row[1]))
                put("role", cipher.encryptIfNeeded(row[2]))
                put("location_or_link", cipher.encryptIfNeeded(row[3]))
                put("notes", cipher.encryptIfNeeded(row[4]))
            }
            db.update("interviews", values, "id = ?", arrayOf(row[0]))
        }
    }

    fun save(record: InterviewRecord): Boolean {
        val values = ContentValues().apply {
            put("id", record.id)
            put("application_id", record.applicationId)
            put("company", LocalFieldCipher().encrypt(record.company.take(300)))
            put("role", LocalFieldCipher().encrypt(record.role.take(300)))
            put("scheduled_at", record.scheduledAtEpochMs)
            put("timezone", record.timeZoneId.take(80))
            put("location_or_link", LocalFieldCipher().encrypt(record.locationOrLink.take(1000)))
            put("notes", LocalFieldCipher().encrypt(record.notes.take(2000)))
        }
        return writableDatabase.insertWithOnConflict("interviews", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    fun all(): List<InterviewRecord> {
        val items = mutableListOf<InterviewRecord>()
        readableDatabase.query(
            "interviews",
            arrayOf("id", "application_id", "company", "role", "scheduled_at", "timezone", "location_or_link", "notes"),
            null, null, null, null, "scheduled_at ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += InterviewRecord(
                    cursor.getString(0), cursor.getString(1), LocalFieldCipher().decrypt(cursor.getString(2)), LocalFieldCipher().decrypt(cursor.getString(3)),
                    cursor.getLong(4), cursor.getString(5), LocalFieldCipher().decrypt(cursor.getString(6)), LocalFieldCipher().decrypt(cursor.getString(7))
                )
            }
        }
        return items
    }

    fun delete(id: String): Boolean = writableDatabase.delete("interviews", "id = ?", arrayOf(id)) > 0
}

object InterviewReminderScheduler {
    fun rescheduleAll(context: Context) {
        val store = InterviewStore(context)
        val records = try {
            store.all()
        } finally {
            store.close()
        }
        records.forEach { interview ->
            val delay = interview.scheduledAtEpochMs - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
            if (delay > 0L) runCatching { schedule(context, interview) }
        }
    }

    fun schedule(context: Context, interview: InterviewRecord) {
        require(interview.id.matches(Regex("[0-9a-fA-F-]{36}"))) { "Interview identifier is invalid." }
        ZoneId.of(interview.timeZoneId)
        val delay = interview.scheduledAtEpochMs - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        require(delay > 0L) { "Interview must be more than one hour in the future." }
        // Keep personally identifying interview details out of WorkManager's own database.
        val data = Data.Builder()
            .putString("interview_id", interview.id)
            .build()
        val request = OneTimeWorkRequestBuilder<InterviewReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "interview-reminder-${interview.id}",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, interviewId: String) {
        require(interviewId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Interview identifier is invalid." }
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork("interview-reminder-$interviewId")
    }
}

class InterviewReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val interviewId = inputData.getString("interview_id")
                ?: return Result.failure()
            val store = InterviewStore(applicationContext)
            val interview = try {
                store.all().firstOrNull { it.id == interviewId }
            } finally {
                store.close()
            } ?: return Result.success()
            val channelId = "interview_reminders"
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Interview reminders", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val intent = Intent(applicationContext, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentTitle("Interview reminder")
                .setContentText("You have an upcoming interview. Open the app for details.")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("You have an upcoming interview. Open Hamari Mehnat to view the details stored on this device.")
                )
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(inputData.getString("interview_id").orEmpty().hashCode(), notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
