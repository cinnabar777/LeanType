/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings integration for gesture modes and caching features.
 */
class GestureSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gesture_modes", Context.MODE_PRIVATE)

    /**
     * Get the current gesture typing mode.
     */
    fun getGestureMode(): GestureMode {
        val modeString = prefs.getString(KEY_GESTURE_MODE, GestureMode.STANDARD.name) ?: GestureMode.STANDARD.name
        return GestureMode.fromString(modeString)
    }

    /**
     * Set the gesture typing mode.
     */
    fun setGestureMode(mode: GestureMode) {
        prefs.edit().putString(KEY_GESTURE_MODE, mode.name).apply()
    }

    /**
     * Check if session word caching (proofreading) is enabled.
     */
    fun isSessionWordCacheEnabled(): Boolean {
        return prefs.getBoolean(KEY_SESSION_WORD_CACHE, false)
    }

    /**
     * Enable/disable session word caching.
     */
    fun setSessionWordCacheEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SESSION_WORD_CACHE, enabled).apply()
    }

    /**
     * Get minimum word frequency to learn (for session cache filtering).
     */
    fun getMinWordFrequency(): Int {
        return prefs.getInt(KEY_MIN_WORD_FREQUENCY, 2)
    }

    /**
     * Set minimum word frequency.
     */
    fun setMinWordFrequency(frequency: Int) {
        prefs.edit().putInt(KEY_MIN_WORD_FREQUENCY, frequency).apply()
    }

    /**
     * Check if gesture mapping cache is enabled.
     */
    fun isGestureCacheEnabled(): Boolean {
        return prefs.getBoolean(KEY_GESTURE_CACHE, false)
    }

    /**
     * Enable/disable gesture mapping cache.
     */
    fun setGestureCacheEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GESTURE_CACHE, enabled).apply()
    }

    /**
     * Get gesture confidence threshold for fallback (used in SANDBOX mode).
     */
    fun getGestureConfidenceThreshold(): Float {
        return prefs.getFloat(KEY_GESTURE_CONFIDENCE_THRESHOLD, GESTURE_SANDBOX_CONFIDENCE_THRESHOLD)
    }

    /**
     * Set gesture confidence threshold.
     */
    fun setGestureConfidenceThreshold(threshold: Float) {
        prefs.edit().putFloat(KEY_GESTURE_CONFIDENCE_THRESHOLD, threshold).apply()
    }

    companion object {
        // Preference keys
        const val KEY_GESTURE_MODE = "pref_gesture_mode"
        const val KEY_SESSION_WORD_CACHE = "pref_session_word_cache"
        const val KEY_MIN_WORD_FREQUENCY = "pref_min_word_frequency"
        const val KEY_GESTURE_CACHE = "pref_gesture_cache"
        const val KEY_GESTURE_CONFIDENCE_THRESHOLD = "pref_gesture_confidence_threshold"
    }
}

/**
 * Gesture typing mode for dictionary access during gesture recognition.
 *
 * The gesture engine receives different dictionaries based on the selected mode:
 * - STANDARD: Full access to main dictionary + user learned words (history + personal)
 * - SANDBOX: Only user learned words (history + personal), no main dictionary
 * - SANDBOX_ONLY: Only user history words, no main or personal dictionary
 *
 * User learned words include:
 * - TYPE_USER_HISTORY: Words learned from typing
 * - TYPE_USER: Personal dictionary entries
 * Both are synced with Android's system user dictionary
 */
enum class GestureMode {
    STANDARD,      // All dictionaries: main + user history + personal
    SANDBOX,       // User-learned only: history + personal (no main dict)
    SANDBOX_ONLY;  // Minimal: user history only (no main, no personal)

    companion object {
        fun fromString(value: String?): GestureMode {
            return when (value?.uppercase()) {
                "SANDBOX" -> SANDBOX
                "SANDBOX_ONLY" -> SANDBOX_ONLY
                else -> STANDARD
            }
        }

        fun toStorageString(mode: GestureMode): String {
            return mode.name
        }
    }
}

/**
 * Confidence threshold for fallback in SANDBOX mode.
 * When gesture confidence drops below this value, SANDBOX mode can optionally
 * fall back to the main dictionary for additional suggestions.
 */
const val GESTURE_SANDBOX_CONFIDENCE_THRESHOLD = 0.5f  // 50%
