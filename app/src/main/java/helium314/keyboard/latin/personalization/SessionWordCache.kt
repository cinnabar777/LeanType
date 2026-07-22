/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.personalization

import android.content.Context
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale

/**
 * Caches typed/gestured words during a keyboard session.
 *
 * Words are held in memory until the keyboard closes (onFinishInput).
 * This allows users to proofread and filter out incorrect entries before
 * they are permanently added to user history.
 *
 * Features:
 * - Tracks word frequency, gesture confidence, and timestamps
 * - Provides proofreading UI with option to filter words before committing
 * - Persists pending words to file if needed
 */
class SessionWordCache(context: Context, private val locale: Locale) {
    private val mContext = context.applicationContext
    private val mCacheFile = File(DeviceProtectedUtils.getFilesDir(mContext), "session_word_cache_${locale.toLanguageTag()}.txt")

    // In-memory cache: word -> (frequency, gestureConfidence, timestamp)
    private val mWordCache = LinkedHashMap<String, WordCacheEntry>()
    private val mCacheLock = Any()

    data class WordCacheEntry(
        val word: String,
        var frequency: Int = 1,
        var gestureConfidence: Float = 1.0f,  // 0.0 to 1.0 for gesture; 1.0 for tapped
        var firstSeen: Long = System.currentTimeMillis(),
        var lastSeen: Long = System.currentTimeMillis(),
        var wasAutoCapitalized: Boolean = false,
        var ngramContext: NgramContext? = null
    )

    /**
     * Add or update a word in the session cache.
     * Called every time a word would normally be added to user history.
     */
    fun recordWord(
        word: String,
        wasAutoCapitalized: Boolean = false,
        ngramContext: NgramContext? = null,
        gestureConfidence: Float = 1.0f  // 1.0 = tapped, < 1.0 = gesture
    ) {
        synchronized(mCacheLock) {
            val existing = mWordCache[word]
            if (existing != null) {
                existing.frequency++
                existing.lastSeen = System.currentTimeMillis()
                // Average gesture confidence for multi-gesture words
                if (gestureConfidence < 1.0f) {
                    existing.gestureConfidence = (existing.gestureConfidence + gestureConfidence) / 2f
                }
            } else {
                mWordCache[word] = WordCacheEntry(
                    word = word,
                    wasAutoCapitalized = wasAutoCapitalized,
                    ngramContext = ngramContext,
                    gestureConfidence = gestureConfidence
                )
            }
        }
    }

    /**
     * Get all cached words for proofreading.
     * Returns a list sorted by frequency (most frequent first).
     */
    fun getCachedWords(): List<WordCacheEntry> {
        synchronized(mCacheLock) {
            return mWordCache.values
                .sortedByDescending { it.frequency }
                .toList()
        }
    }

    /**
     * Get words that meet the minimum frequency threshold.
     * Typical threshold: 2-3 repetitions to avoid learning typos.
     */
    fun getWordsAboveThreshold(minFrequency: Int = 2): List<WordCacheEntry> {
        synchronized(mCacheLock) {
            return mWordCache.values
                .filter { it.frequency >= minFrequency }
                .sortedByDescending { it.frequency }
                .toList()
        }
    }

    /**
     * Get words with high gesture confidence (> threshold).
     * Useful for filtering out low-confidence gesture inputs.
     */
    fun getHighConfidenceWords(confidenceThreshold: Float = 0.7f): List<WordCacheEntry> {
        synchronized(mCacheLock) {
            return mWordCache.values
                .filter { it.gestureConfidence >= confidenceThreshold || it.gestureConfidence == 1.0f }
                .sortedByDescending { it.frequency }
                .toList()
        }
    }

    /**
     * Remove a word from the cache (user rejected it in proofreading).
     */
    fun rejectWord(word: String) {
        synchronized(mCacheLock) {
            mWordCache.remove(word)
        }
    }

    /**
     * Clear the entire session cache.
     */
    fun clear() {
        synchronized(mCacheLock) {
            mWordCache.clear()
        }
    }

    /**
     * Get total count of cached words.
     */
    fun size(): Int {
        synchronized(mCacheLock) {
            return mWordCache.size
        }
    }

    /**
     * Persist cache to file (for recovery if app crashes).
     * Format: word|frequency|confidence|timestamp\n
     */
    fun persistToFile() {
        synchronized(mCacheLock) {
            try {
                mCacheFile.parentFile?.mkdirs()
                mCacheFile.bufferedWriter().use { writer ->
                    mWordCache.values.forEach { entry ->
                        writer.write("${entry.word}|${entry.frequency}|${entry.gestureConfidence}|${entry.firstSeen}\n")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist session word cache", e)
            }
        }
    }

    /**
     * Load cache from file (on app restart).
     */
    fun loadFromFile() {
        synchronized(mCacheLock) {
            try {
                if (!mCacheFile.exists()) return
                mCacheFile.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        val parts = line.split("|")
                        if (parts.size >= 4) {
                            try {
                                mWordCache[parts[0]] = WordCacheEntry(
                                    word = parts[0],
                                    frequency = parts[1].toIntOrNull() ?: 1,
                                    gestureConfidence = parts[2].toFloatOrNull() ?: 1.0f,
                                    firstSeen = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse cache line: $line", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load session word cache from file", e)
            }
        }
    }

    companion object {
        private val TAG = SessionWordCache::class.java.simpleName
        private val instances = mutableMapOf<String, SessionWordCache>()

        fun getInstance(context: Context, locale: Locale): SessionWordCache {
            val key = locale.toLanguageTag()
            return instances.getOrPut(key) {
                SessionWordCache(context, locale)
            }
        }

        fun clearAllInstances() {
            instances.clear()
        }
    }
}
