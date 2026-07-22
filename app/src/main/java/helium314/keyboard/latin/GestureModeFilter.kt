/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Gesture Mode Filtering Extension
 */

package helium314.keyboard.latin

import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.GestureMode
import helium314.keyboard.latin.utils.Log

/**
 * Extension to DictionaryFacilitatorImpl for gesture mode filtering.
 * 
 * This controls which dictionaries are passed to the gesture engine
 * based on the selected gesture mode.
 */
object GestureModeFilter {
    private val TAG = "GestureModeFilter"

    /**
     * Determine which dictionary types should be used for gesture recognition
     * based on the gesture mode setting.
     *
     * STANDARD: [TYPE_MAIN, TYPE_USER_HISTORY, TYPE_USER]
     *   - Full access: main dict + all user-learned words
     *   - Best accuracy, but sensitive to main dict quality
     *
     * SANDBOX: [TYPE_USER_HISTORY, TYPE_USER]
     *   - User-learned only: history + personal dictionary
     *   - Ignores main dictionary (50,000+ words)
     *   - Better isolation, prevents main dict errors from affecting gestures
     *
     * SANDBOX_ONLY: [TYPE_USER_HISTORY]
     *   - Most restricted: only system user history
     *   - No personal dictionary additions
     *   - Useful for testing or conservative users
     */
    fun getFilteredDictionaryTypes(gestureMode: GestureMode): Array<String> {
        return when (gestureMode) {
            GestureMode.STANDARD -> arrayOf(
                Dictionary.TYPE_MAIN,
                Dictionary.TYPE_USER_HISTORY,
                Dictionary.TYPE_USER
            )
            GestureMode.SANDBOX -> arrayOf(
                Dictionary.TYPE_USER_HISTORY,
                Dictionary.TYPE_USER
            )
            GestureMode.SANDBOX_ONLY -> arrayOf(
                Dictionary.TYPE_USER_HISTORY
            )
        }
    }

    /**
     * Log which dictionaries are being used for gesture recognition.
     * Useful for debugging gesture accuracy issues.
     */
    fun logDictionaryUsage(gestureMode: GestureMode) {
        val types = getFilteredDictionaryTypes(gestureMode)
        Log.i(TAG, "Gesture mode: $gestureMode, using dictionaries: ${types.joinToString(", ")}")
    }

    /**
     * Check if main dictionary should be used for gestures.
     */
    fun usesMainDictionary(gestureMode: GestureMode): Boolean {
        return gestureMode == GestureMode.STANDARD
    }

    /**
     * Check if personal dictionary should be used for gestures.
     */
    fun usesPersonalDictionary(gestureMode: GestureMode): Boolean {
        return gestureMode == GestureMode.STANDARD || gestureMode == GestureMode.SANDBOX
    }

    /**
     * Check if user history should be used for gestures.
     * (Always true for all modes)
     */
    fun usesUserHistory(gestureMode: GestureMode): Boolean {
        return true
    }
}
