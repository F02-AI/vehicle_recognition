package com.example.vehiclerecognition.platform

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.example.vehiclerecognition.domain.platform.SoundAlertPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

// Assume R.raw.alert_sound is a valid sound resource in res/raw/
// For example, if your package is com.example.app, then use:
// import com.example.app.R // Import your app's R class

class AndroidSoundAlertPlayer(
    private val context: Context, // Inject ApplicationContext via DI
    private val soundResId: Int // e.g., R.raw.alert_sound, provide via DI
) : SoundAlertPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var alertJob: Job? = null
    // Use SupervisorJob so that a failure in one coroutine doesn't cancel the scope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isPlayingPreventRepeat = false
    private var isEnabled = true
    private val alertDurationMillis = 2000L // FR 1.13: 2 seconds
    private val cooldownMillis = 500L // FR 1.14: Cooldown to prevent immediate re-trigger

    override fun playAlert() {
        if (!isEnabled) {
            Log.d("SoundAlertPlayer", "Sound alerts are disabled")
            return
        }
        
        if (isPlayingPreventRepeat) {
            Log.d("SoundAlertPlayer", "Alert already playing or in cooldown. Skipping.")
            return
        }

        Log.d("SoundAlertPlayer", "Attempting to play alert sound resource ID: $soundResId")
        isPlayingPreventRepeat = true
        alertJob?.cancel() // Cancel any previous job just in case

        alertJob = scope.launch {
            var tempMediaPlayer: MediaPlayer? = null
            try {
                tempMediaPlayer = MediaPlayer.create(context, soundResId)
                if (tempMediaPlayer == null) {
                    Log.e("SoundAlertPlayer", "Failed to create MediaPlayer. Sound resource ID correct? ($soundResId)")
                    // Reset flag after cooldown even if sound didn't play
                    delay(alertDurationMillis + cooldownMillis)
                    isPlayingPreventRepeat = false
                    return@launch
                }
                mediaPlayer = tempMediaPlayer // Assign to class member for release

                tempMediaPlayer.setOnCompletionListener {
                    Log.d("SoundAlertPlayer", "MediaPlayer playback completed.")
                    // No need to set isPlayingPreventRepeat = false here, handled by delay
                }
                tempMediaPlayer.setOnErrorListener { _, what, extra ->
                    Log.e("SoundAlertPlayer", "MediaPlayer error. What: $what, Extra: $extra")
                    // Reset flag after cooldown even if sound failed
                    scope.launch { // Launch a new coroutine for the delay
                         delay(alertDurationMillis + cooldownMillis)
                         isPlayingPreventRepeat = false
                    }
                    true // Error handled
                }
                tempMediaPlayer.start()
                Log.d("SoundAlertPlayer", "MediaPlayer started for ${alertDurationMillis}ms.")

                delay(alertDurationMillis) // FR 1.13: Alert duration
                // Sound will stop on its own if shorter than duration, or cut off if longer by releasing.
                // For precise 2-second play even if sound is longer, one might need to stop() it.
                // However, MediaPlayer.create and start is async. If sound is very short, it completes before delay.

            } catch (e: IOException) {
                Log.e("SoundAlertPlayer", "IOException during MediaPlayer setup: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.e("SoundAlertPlayer", "IllegalStateException with MediaPlayer: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("SoundAlertPlayer", "Generic error playing sound: ${e.message}", e)
            } finally {
                Log.d("SoundAlertPlayer", "MediaPlayer playback attempt finished. Releasing temporary instance.")
                tempMediaPlayer?.release() // Release the instance created in this job
                if (mediaPlayer == tempMediaPlayer) { // If it was assigned
                    mediaPlayer = null
                }
                // Cooldown before allowing another play, regardless of success/failure of this attempt
                delay(cooldownMillis) 
                isPlayingPreventRepeat = false
                Log.d("SoundAlertPlayer", "Cooldown finished. Ready for next alert.")
            }
        }
        // FR 1.15: Alert Non-Dismissible - Handled by not providing a dismiss UI and fixed duration.
    }

    override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d("SoundAlertPlayer", "Sound alerts ${if (enabled) "enabled" else "disabled"}")
    }

    // Call this when the owner component is destroyed (e.g., in Application or a long-lived service if needed)
    fun release() {
        alertJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        Log.d("SoundAlertPlayer", "Released all MediaPlayer resources.")
    }
} 