package com.example.bike_.data.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bike_.MainActivity
import com.example.bike_.R
import com.example.bike_.data.crash.CrashReporting
import com.example.bike_.data.db.entity.WorkoutEntity
import com.example.bike_.data.repository.WorkoutRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sends a workout summary as a regular phone notification. Paired smartwatches
 * (Wear OS and most fitness watches with notification mirroring) show the same
 * notification, so no companion app or wearable-specific API is required.
 */
class WatchSyncManager(
    private val context: Context,
    private val repository: WorkoutRepository
) {
    sealed interface SendResult {
        data class Success(val workoutId: Long) : SendResult
        data object NoWorkout : SendResult
        data object PermissionsNeeded : SendResult
        data class Error(val message: String) : SendResult
    }

    suspend fun sendLastWorkout(): SendResult {
        val workout = repository.getLastFinishedWorkout() ?: return SendResult.NoWorkout
        return try {
            if (!canPostNotifications()) return SendResult.PermissionsNeeded
            createChannel()

            val lines = buildSummary(workout)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.watch_notification_title))
                .setContentText(lines.take(2).joinToString(" · "))
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(openAppPendingIntent())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(false)
                // Do not set localOnly: Wear OS bridges notifications to the watch only
                // when they are allowed to leave the phone.
                .setLocalOnly(false)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            CrashReporting.log("Workout summary notification sent for workout ${workout.id}")
            SendResult.Success(workout.id)
        } catch (e: SecurityException) {
            CrashReporting.record(e, "Watch notification permission")
            SendResult.PermissionsNeeded
        } catch (e: Exception) {
            CrashReporting.record(e, "Watch notification send")
            SendResult.Error(e.message ?: context.getString(R.string.unknown_error))
        }
    }

    fun canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.watch_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.watch_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildSummary(workout: WorkoutEntity): List<String> {
        val typeLabel = when (workout.workoutType.uppercase(Locale.US)) {
            "RUNNING" -> context.getString(R.string.workout_type_running)
            "WALKING" -> context.getString(R.string.workout_type_walking)
            else -> context.getString(R.string.workout_type_cycling)
        }
        val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            .format(Date(workout.startTimeMs))

        val lines = mutableListOf<String>(
            "$typeLabel · $date",
            context.getString(R.string.watch_summary_distance_fmt, workout.distanceMeters / 1000.0),
            context.getString(R.string.watch_summary_time_fmt, formatDuration(workout.elapsedTimeMs)),
            context.getString(R.string.watch_summary_speed_fmt, workout.avgSpeedKmh)
        )
        workout.avgHeartRateBpm?.let {
            lines += context.getString(R.string.watch_summary_hr_fmt, it)
        }
        workout.caloriesKcal?.let {
            lines += context.getString(R.string.watch_summary_calories_fmt, it)
        }
        workout.avgPowerWatts?.let {
            lines += context.getString(R.string.watch_summary_power_fmt, it)
        }
        return lines
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    private companion object {
        const val CHANNEL_ID = "watch_workout_summary"
        const val NOTIFICATION_ID = 2102
    }
}
