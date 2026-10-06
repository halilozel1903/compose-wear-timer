package io.github.halilozel1903.weartimer

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.halilozel1903.weartimer.core.HapticPattern
import io.github.halilozel1903.weartimer.core.HapticPatterns
import io.github.halilozel1903.weartimer.core.TimerAlert

/**
 * Plays timer alerts on the watch's vibration motor: a soft tick for 3-2-1, two strong pulses when
 * work starts, one long soft pulse for rest and three long pulses at the end (see [HapticPatterns]).
 * Works from a composable ([rememberWearHaptics]) and from a service alike.
 *
 * Alerts use the alarm vibration usage on Android 13 and newer, so they come through while the
 * watch is in a workout. The library's manifest adds the `VIBRATE` permission.
 */
public class WearHaptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** Turn off to mute alerts, for example from a settings switch. */
    public var enabled: Boolean = true

    /** False on devices (and most emulators) without a vibration motor. */
    public val isAvailable: Boolean get() = vibrator?.hasVibrator() == true

    /** Plays the pattern [HapticPatterns.forAlert] picks for [alert]. */
    public fun play(alert: TimerAlert) {
        playPattern(HapticPatterns.forAlert(alert))
    }

    /** Plays [pattern] once, replacing whatever is vibrating. */
    public fun playPattern(pattern: HapticPattern) {
        val motor = vibrator ?: return
        if (!enabled || !motor.hasVibrator()) return
        val timings = pattern.timings.toLongArray()
        val effect = if (motor.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, pattern.amplitudes.toIntArray(), -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            motor.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            motor.vibrate(effect)
        }
    }

    /** Stops a pattern that is still playing. */
    public fun cancel() {
        vibrator?.cancel()
    }
}

/** Remembers a [WearHaptics] for the current context. */
@Composable
public fun rememberWearHaptics(): WearHaptics {
    val context = LocalContext.current
    return remember(context) { WearHaptics(context.applicationContext) }
}
