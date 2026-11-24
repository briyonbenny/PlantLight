package ie.griffith.plantlight.ui

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * Milestone 2 fix for the lecturer's feedback: "UI overlaps the OS status bar".
 *
 * Android 15 (API 35) and newer always draw apps edge-to-edge, so the
 * toolbar went underneath the clock and battery icons. We now:
 *  1. turn edge-to-edge on for every Android version (same look everywhere),
 *  2. grow the toolbar by the status bar height and push its content down,
 *  3. pad the bottom of the screen by the navigation bar (or keyboard) height.
 *
 * Call this once in onCreate, after setContentView.
 */
object SystemBars {

    fun setUp(activity: ComponentActivity, root: View, toolbar: View) {
        activity.enableEdgeToEdge(
            // Light icons on our dark green toolbar.
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val toolbarHeight = toolbar.layoutParams.height
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Toolbar: taller by the status bar height, content pushed below it.
            toolbar.updatePadding(top = bars.top)
            toolbar.updateLayoutParams { height = toolbarHeight + bars.top }

            // Rest of the screen: keep clear of the side and bottom bars / keyboard.
            root.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = maxOf(bars.bottom, keyboard.bottom)
            )
            insets
        }
    }
}
