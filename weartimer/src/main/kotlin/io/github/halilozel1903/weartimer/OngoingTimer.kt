package io.github.halilozel1903.weartimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import io.github.halilozel1903.weartimer.core.TimeFormat
import io.github.halilozel1903.weartimer.core.TimerStatus
import io.github.halilozel1903.weartimer.core.WorkoutSnapshot

/**
 * What an ongoing timer notification shows.
 *
 * @param title the workout name, for example "Tabata".
 * @param phaseLabel the current phase, for example "Work · 3/8".
 * @param phaseEndElapsedRealtime when the phase reaches zero in `SystemClock.elapsedRealtime` time,
 *   or null while paused. The system counts it down on the watch face and in the recents list
 *   without the app waking up.
 * @param pausedRemainingMillis time left, shown while paused.
 */
public data class OngoingTimerContent(
    val title: String,
    val phaseLabel: String,
    val phaseEndElapsedRealtime: Long?,
    val pausedRemainingMillis: Long = 0L,
) {
    public companion object {
        /** Content for [snapshot] of a workout named [title], read at `SystemClock.elapsedRealtime()`. */
        public fun of(
            title: String,
            snapshot: WorkoutSnapshot,
            status: TimerStatus,
            elapsedRealtimeNow: Long = SystemClock.elapsedRealtime(),
        ): OngoingTimerContent {
            val phase = snapshot.phase.label
            val label = if (snapshot.round > 0 && snapshot.totalRounds > 0) {
                "$phase · ${snapshot.round}/${snapshot.totalRounds}"
            } else {
                phase
            }
            return OngoingTimerContent(
                title = title,
                phaseLabel = label,
                phaseEndElapsedRealtime = if (status == TimerStatus.Running) {
                    elapsedRealtimeNow + snapshot.phaseRemainingMillis
                } else {
                    null
                },
                pausedRemainingMillis = snapshot.phaseRemainingMillis,
            )
        }
    }
}

/**
 * Builds the notification for a timer that keeps running in a foreground service, as a Wear OS
 * [OngoingActivity]: the watch face shows its icon and the system counts down the phase with no
 * work from the app, and tapping it opens [touchIntent].
 *
 * ```kotlin
 * // In a Service:
 * val ongoing = OngoingTimer(this, smallIcon = R.drawable.ic_timer, touchIntent = openAppIntent)
 * ongoing.createChannel()
 * val notification = ongoing.build(OngoingTimerContent.of("Tabata", workout.snapshot, workout.status))
 * ServiceCompat.startForeground(this, OngoingTimer.NOTIFICATION_ID, notification, type)
 * // On each phase change or pause:
 * ongoing.update(OngoingTimerContent.of("Tabata", workout.snapshot, workout.status))
 * ```
 */
public class OngoingTimer(
    private val context: Context,
    private val smallIcon: Int,
    private val touchIntent: PendingIntent,
    public val channelId: String = DEFAULT_CHANNEL_ID,
    public val notificationId: Int = NOTIFICATION_ID,
) {
    /** Creates the low importance channel the notification uses. Safe to call more than once. */
    public fun createChannel(name: CharSequence = "Workout timer") {
        val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Builds the notification and applies the ongoing activity to it. */
    public fun build(content: OngoingTimerContent): Notification {
        val text = if (content.phaseEndElapsedRealtime == null) {
            "${content.phaseLabel} · ${TimeFormat.countdown(content.pausedRemainingMillis)}"
        } else {
            content.phaseLabel
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(content.title)
            .setContentText(text)
            .setContentIntent(touchIntent)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val end = content.phaseEndElapsedRealtime
        val timePart: Status.Part = if (end != null) {
            Status.TimerPart(end)
        } else {
            Status.TextPart(TimeFormat.countdown(content.pausedRemainingMillis))
        }
        val status = Status.Builder()
            .addTemplate("#phase# #time#")
            .addPart("phase", Status.TextPart(content.phaseLabel))
            .addPart("time", timePart)
            .build()

        val ongoingActivity = OngoingActivity.Builder(context, notificationId, builder)
            .setStaticIcon(smallIcon)
            .setTouchIntent(touchIntent)
            .setStatus(status)
            .build()
        ongoingActivity.apply(context)
        return builder.build()
    }

    /** Rebuilds the notification with new [content] and posts it under the same id. */
    public fun update(content: OngoingTimerContent) {
        val notification = build(content)
        context.getSystemService(NotificationManager::class.java)?.notify(notificationId, notification)
    }

    public companion object {
        public const val DEFAULT_CHANNEL_ID: String = "weartimer_ongoing"
        public const val NOTIFICATION_ID: Int = 4_201
    }
}
