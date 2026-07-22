/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.settings

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
