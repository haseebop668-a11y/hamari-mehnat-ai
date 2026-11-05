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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    SQLiteOpenHelper(context.applicationContext, "personal_interviews.db", null, 1) {
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(record: InterviewRecord) {
        val values = ContentValues().apply {
            put("id", record.id)
            put("application_id", record.applicationId)
            put("company", record.company.take(300))
            put("role", record.role.take(300))
            put("scheduled_at", record.scheduledAtEpochMs)
            put("timezone", record.timeZoneId.take(80))
            put("location_or_link", record.locationOrLink.take(1000))
            put("notes", record.notes.take(2000))
        }
        writableDatabase.insertWithOnConflict("interviews", null, values, SQLiteDatabase.CONFLICT_REPLACE)
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
                    cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                    cursor.getLong(4), cursor.getString(5), cursor.getString(6), cursor.getString(7)
                )
            }
        }
        return items
    }

    fun delete(id: String): Boolean = writableDatabase.delete("interviews", "id = ?", arrayOf(id)) > 0
}

object InterviewReminderScheduler {
    fun schedule(context: Context, interview: InterviewRecord) {
        val delay = interview.scheduledAtEpochMs - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        require(delay > 0L) { "Interview must be more than one hour in the future." }
        val data = Data.Builder()
            .putString("interview_id", interview.id)
            .putString("company", interview.company)
            .putString("role", interview.role)
            .putLong("scheduled_at", interview.scheduledAtEpochMs)
            .putString("timezone", interview.timeZoneId)
            .putString("location", interview.locationOrLink)
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
}

class InterviewReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val company = inputData.getString("company").orEmpty()
            val role = inputData.getString("role").orEmpty()
            val timezone = inputData.getString("timezone").orEmpty()
            val location = inputData.getString("location").orEmpty()
            val scheduledAt = inputData.getLong("scheduled_at", 0L)
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
            val timestamp = Instant.ofEpochMilli(scheduledAt)
                .atZone(ZoneId.of(timezone))
                .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy • HH:mm z"))
            val body = listOf(timestamp, location).filter { it.isNotBlank() }.joinToString(" • ")
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Interview: $role at $company")
                .setContentText(body.ifBlank { "Your interview is coming up." })
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
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
