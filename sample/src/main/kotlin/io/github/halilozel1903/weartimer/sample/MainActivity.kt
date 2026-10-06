package io.github.halilozel1903.weartimer.sample

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme

/**
 * Taps and crown turns can't be timed reliably through adb on a fresh emulator, so
 * `scripts/screenshots.sh` starts the app with `--es scene <scene>` to open a screen with a fixed
 * clock and fixed data:
 *
 * - `picker`: the work interval picker at 0:20
 * - `running`: round 3 of 8, work, 0:12 left
 * - `rest`: round 3 of 8, rest, 0:06 left
 * - `done`: the summary of the finished workout
 *
 * Without the extra the app opens the picker and runs real workouts in [WorkoutService].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scene = Scene.from(intent.getStringExtra(EXTRA_SCENE))
        // Keeps the watch from dimming into ambient mode during screenshots and workouts.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = TabataColors) {
                // The scaffold paints the theme background, so every screen and its text follow the theme.
                AppScaffold {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (scene != null) SceneScreen(scene) else TabataApp()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCENE = "scene"
    }
}
