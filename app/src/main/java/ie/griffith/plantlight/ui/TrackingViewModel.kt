package ie.griffith.plantlight.ui

import androidx.lifecycle.ViewModel
import ie.griffith.plantlight.data.LightSessionRecorder

/**
 * Milestone 2: keeps the tracking session alive when the screen rotates
 * (the Activity is recreated, but this ViewModel is kept).
 */
class TrackingViewModel : ViewModel() {
    val recorder = LightSessionRecorder()
    var isTracking = false
    /** Recent graph points, so the graph can be redrawn after rotation. */
    val graphPoints = ArrayDeque<Float>()
}
