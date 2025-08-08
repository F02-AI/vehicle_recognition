package com.example.vehiclerecognition.domain.platform

/**
 * Interface for playing sound alerts when vehicles are detected.
 * Provides abstraction for platform-specific sound playing implementations.
 */
interface SoundAlertPlayer {
    /**
     * Plays an alert sound.
     * Implementation may vary by platform and available resources.
     */
    fun playAlert()
    
    /**
     * Sets the enabled state of sound alerts.
     * 
     * @param enabled Whether sound alerts should be played.
     */
    fun setEnabled(enabled: Boolean)
}
