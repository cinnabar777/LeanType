/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.personalization

import android.content.Context
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale

/**
 * Cache for gesture patterns and their associated words.
 *
 * Stores gesture coordinate paths with words and confidence scores.
 * Can be used to:
 * - Improve gesture accuracy by recognizing repeated patterns
 * - Provide proofreading reference ("you usually gesture 'hello' like this")
 * - Debug gesture recognition issues
 *
 * NOTE: Gesture cache is device-specific due to varying DPI, keyboard layout, finger size.
 */
class GestureRecognitionCache(context: Context, private val locale: Locale) {
    private val mContext = context.applicationContext
    private val mCacheDir = File(DeviceProtectedUtils.getFilesDir(mContext), "gesture_cache_${locale.toLanguageTag()}")

    // In-memory: word -> list of (gestureHash, points, confidence)
    private val mGestureCache = mutableMapOf<String, MutableList<GesturePattern>>()
    private val mCacheLock = Any()

    data class GesturePattern(
        val word: String,
        val gestureHash: String,        // Simplified hash of gesture path
        val points: String,             // Serialized coordinate points
        val confidence: Float = 1.0f,   // User confidence in this gesture
        val timestamp: Long = System.currentTimeMillis(),
        val occurrences: Int = 1        // How many times this pattern seen
    )

    init {
        mCacheDir.mkdirs()
    }

    /**
     * Record a gesture pattern for a word.
     * Simplifies coordinates using Douglas-Peucker-like algorithm.
     */
    fun recordGesture(
        word: String,
        composedData: ComposedData,
        confidence: Float = 1.0f
    ) {
        synchronized(mCacheLock) {
            try {
                val hash = computeGestureHash(composedData)
                val points = serializeCoordinates(composedData)

                val patterns = mGestureCache.getOrPut(word) { mutableListOf() }
                val existing = patterns.firstOrNull { it.gestureHash == hash }

                if (existing != null) {
                    // Update existing pattern
                    val index = patterns.indexOf(existing)
                    patterns[index] = existing.copy(
                        occurrences = existing.occurrences + 1,
                        confidence = (existing.confidence + confidence) / 2f,
                        timestamp = System.currentTimeMillis()
                    )
                } else {
                    // Add new pattern
                    patterns.add(GesturePattern(
                        word = word,
                        gestureHash = hash,
                        points = points,
                        confidence = confidence
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record gesture for word: $word", e)
            }
        }
    }

    /**
     * Get all gesture variants for a word.
     * Returns patterns sorted by confidence and occurrence.
     */
    fun getGestureVariants(word: String): List<GesturePattern> {
        synchronized(mCacheLock) {
            return mGestureCache[word]?.sortedWith(
                compareBy({ -it.occurrences }, { -it.confidence })
            ) ?: emptyList()
        }
    }

    /**
     * Get the most confident gesture pattern for a word.
     */
    fun getMostConfidentGesture(word: String): GesturePattern? {
        synchronized(mCacheLock) {
            return mGestureCache[word]?.maxByOrNull { it.confidence }
        }
    }

    /**
     * Compute a fuzzy hash of gesture coordinates.
     * Simplifies path to reduce storage and improve matching.
     */
    private fun computeGestureHash(composedData: ComposedData): String {
        return try {
            val sb = StringBuilder()
            // Sample every Nth point to reduce data
            val sampleRate = maxOf(1, composedData.mInputPointers.size / 20)
            for (i in composedData.mInputPointers.indices step sampleRate) {
                val pointer = composedData.mInputPointers[i]
                sb.append("${pointer.mX / 100},${pointer.mY / 100};")
            }
            sb.toString().hashCode().toString(16)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute gesture hash", e)
            "unknown"
        }
    }

    /**
     * Serialize gesture coordinates to a compact string format.
     */
    private fun serializeCoordinates(composedData: ComposedData): String {
        return try {
            composedData.mInputPointers.joinToString(";") { "${it.mX},${it.mY}" }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to serialize coordinates", e)
            ""
        }
    }

    /**
     * Deserialize coordinates from string format.
     */
    fun deserializeCoordinates(serialized: String): List<Pair<Int, Int>> {
        return try {
            serialized.split(";").mapNotNull { point ->
                val parts = point.split(",")
                if (parts.size == 2) {
                    Pair(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize coordinates", e)
            emptyList()
        }
    }

    /**
     * Clear all cached gesture patterns.
     */
    fun clear() {
        synchronized(mCacheLock) {
            mGestureCache.clear()
        }
    }

    /**
     * Get cache statistics.
     */
    fun getStats(): String {
        synchronized(mCacheLock) {
            val totalWords = mGestureCache.size
            val totalPatterns = mGestureCache.values.sumOf { it.size }
            return "Cached $totalPatterns gesture patterns for $totalWords words"
        }
    }

    companion object {
        private val TAG = GestureRecognitionCache::class.java.simpleName
        private val instances = mutableMapOf<String, GestureRecognitionCache>()

        fun getInstance(context: Context, locale: Locale): GestureRecognitionCache {
            val key = locale.toLanguageTag()
            return instances.getOrPut(key) {
                GestureRecognitionCache(context, locale)
            }
        }

        fun clearAllInstances() {
            instances.clear()
        }
    }
}
