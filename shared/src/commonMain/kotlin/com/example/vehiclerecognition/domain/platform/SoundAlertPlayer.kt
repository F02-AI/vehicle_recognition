package com.example.vehiclerecognition.domain.platform

/**
 * Interface for a platform-specific sound alert player.
 * Defines the contract for triggering an audible alert according to FR 1.12, FR 1.13, FR 1.14, FR 1.15.
 */
interface SoundAlertPlayer {

    /**
     * Plays the alert sound.
     * Implementations should ensure:
     * - The sound plays for exactly 2 seconds (FR 1.13).
     * - The alert does not repeat immediately for the same detection event (FR 1.14).
     *   (This might involve some internal state management or coordination from the caller).
     * - The alert is not manually dismissible by the user (FR 1.15).
     */
    fun playAlert()
} 