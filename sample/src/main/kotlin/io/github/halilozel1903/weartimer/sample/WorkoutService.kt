package io.github.halilozel1903.weartimer.sample

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.halilozel1903.weartimer.IntervalWorkoutState
import io.github.halilozel1903.weartimer.OngoingTimer
import io.github.halilozel1903.weartimer.OngoingTimerContent
import io.github.halilozel1903.weartimer.WearHaptics
import io.github.halilozel1903.weartimer.core.TimerAlerts
import io.github.halilozel1903.weartimer.core.TimerStatus

/**
 * Keeps the workout of [WorkoutSession] going with the screen off: a foreground service that polls
 * the workout four times a second, plays its alerts with [WearHaptics], and shows it as a Wear OS
 * ongoing activity (the watch face counts the phase down on its own). It stops when the workout
 * ends or is closed.
 */
class WorkoutService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ongoing: OngoingTimer
    private lateinit var haptics: WearHaptics
    private var shownPhase = -1
    private var shownStatus: TimerStatus? = null

    private val loop = object : Runnable {
        override fun run() {
            if (step()) handler.postDelayed(this, POLL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        haptics = WearHaptics(this)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ongoing = OngoingTimer(this, smallIcon = R.drawable.ic_timer, touchIntent = openApp)
        ongoing.createChannel(getString(R.string.channel_name))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = WorkoutSession.state
        if (state == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, ongoing.notificationId, ongoing.build(content(state)), type)
        shownPhase = state.snapshot.phaseIndex
        shownStatus = state.status
        handler.removeCallbacks(loop)
        handler.post(loop)
        return START_NOT_STICKY
    }

    /** One poll; false once the service has stopped. */
    private fun step(): Boolean {
        val state = WorkoutSession.state ?: return stop()
        TimerAlerts.mostImportant(state.poll())?.let { haptics.play(it) }
        if (state.status == TimerStatus.Finished) return stop()
        val phase = state.snapshot.phaseIndex
        if (phase != shownPhase || state.status != shownStatus) {
            shownPhase = phase
            shownStatus = state.status
            ongoing.update(content(state))
        }
        return true
    }

    private fun content(state: IntervalWorkoutState): OngoingTimerContent =
        OngoingTimerContent.of(state.plan.name, state.snapshot, state.status)

    private fun stop(): Boolean {
        handler.removeCallbacks(loop)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        return false
    }

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val POLL_MILLIS = 250L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }
}
