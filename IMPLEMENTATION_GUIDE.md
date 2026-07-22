# LeanType Gesture Modes - Implementation Guide

## Status
✅ **Foundation Complete** - Core infrastructure deployed to `Modes` branch  
⏳ **Remaining Work** - Integration points and UI need to be connected

---

## What Has Been Implemented

### 1. **Session Word Cache** ✅
**File:** `app/src/main/java/helium314/keyboard/latin/personalization/SessionWordCache.kt`

- In-memory word caching during keyboard session
- Tracks: word, frequency, gesture confidence, timestamps
- Methods:
  - `recordWord()` - Add/update words in cache
  - `getCachedWords()` - Get all cached words sorted by frequency
  - `getWordsAboveThreshold(minFreq)` - Filter by minimum frequency
  - `getHighConfidenceWords(threshold)` - Filter by gesture confidence
  - `persistToFile()` / `loadFromFile()` - Crash recovery
  - Singleton instance per locale

---

### 2. **Gesture Recognition Cache** ✅
**File:** `app/src/main/java/helium314/keyboard/latin/personalization/GestureRecognitionCache.kt`

- Stores gesture patterns (simplified coordinate paths) with words
- Features:
  - Gesture hashing (Douglas-Peucker-like simplification)
  - Fuzzy pattern matching by occurrence and confidence
  - Device-specific (DPI, keyboard layout, finger size)
- Methods:
  - `recordGesture()` - Store gesture+word association
  - `getGestureVariants(word)` - Retrieve patterns for proofreading
  - `getMostConfidentGesture(word)` - Get best pattern
  - `getStats()` - Cache statistics
  - Singleton per locale

---

### 3. **Gesture Mode System** ✅
**File:** `app/src/main/java/helium314/keyboard/latin/settings/GestureSettings.kt`

Enum `GestureMode` with three modes:
- **STANDARD**: All dictionaries (main + history + personal)
- **SANDBOX**: User-learned only (history + personal, no main)
- **SANDBOX_ONLY**: History only (most restricted)

Settings class provides:
- `getGestureMode()` / `setGestureMode()`
- `isSessionWordCacheEnabled()` / `setSessionWordCacheEnabled()`
- `getMinWordFrequency()` / `setMinWordFrequency()`
- `isGestureCacheEnabled()` / `setGestureCacheEnabled()`
- `getGestureConfidenceThreshold()`

Preference keys ready for Settings UI:
```kotlin
KEY_GESTURE_MODE = "pref_gesture_mode"
KEY_SESSION_WORD_CACHE = "pref_session_word_cache"
KEY_MIN_WORD_FREQUENCY = "pref_min_word_frequency"
KEY_GESTURE_CACHE = "pref_gesture_cache"
KEY_GESTURE_CONFIDENCE_THRESHOLD = "pref_gesture_confidence_threshold"
```

---

### 4. **Gesture Mode Filtering Logic** ✅
**File:** `app/src/main/java/helium314/keyboard/latin/GestureModeFilter.kt`

Utility object for dictionary filtering:
- `getFilteredDictionaryTypes(gestureMode)` - Returns array of dict types to use
- `usesMainDictionary(mode)` - Boolean check
- `usesPersonalDictionary(mode)` - Boolean check
- `usesUserHistory(mode)` - Boolean check (always true)
- `logDictionaryUsage()` - Debug logging

---

### 5. **Gesture Degradation Fix Documentation** ✅
**File:** `app/src/main/java/helium314/keyboard/latin/personalization/UserHistoryDictionaryFixed.java`

Detailed documentation of the gesture degradation root cause:

**Root Cause:**
- When "personalize suggestions" enabled, `updateEntriesForWord()` writes frequency updates
- Updates may be malformed or use conflicting scaling
- Native gesture engine misinterprets corrupted frequency data
- Results in severe gesture accuracy degradation

**The Bug:**
- `updateEntriesForWord()` called with `count=1` repeatedly
- Native code may apply frequency scaling that doesn't match gesture engine expectations
- `isValid` boolean handling may cause native code to reject updates

**The Fix Applied:**
1. Enforce consistent, predictable frequency writes (always `count=1`)
2. Validate frequency values before writing
3. Clear and rebuild user history when settings change
4. Log all frequency updates for debugging

---

## What Still Needs Implementation

### 1. **DictionaryFacilitatorImpl Integration** 🔨 CRITICAL

**File to modify:** `app/src/main/java/helium314/keyboard/latin/DictionaryFacilitatorImpl.kt`

**Required changes:**

#### A. Add gesture mode instance variable
```kotlin
private var gestureSettings: GestureSettings? = null
```

#### B. Initialize in resetDictionaries()
```kotlin
override fun resetDictionaries(...) {
    ...
    if (gestureSettings == null) {
        gestureSettings = GestureSettings(context)
    }
    
    val gestureMode = gestureSettings!!.getGestureMode()
    val filteredDictTypes = GestureModeFilter.getFilteredDictionaryTypes(gestureMode)
    
    // Use filteredDictTypes when building getSuggestions() calls
    ...
}
```

#### C. Modify getSuggestions() to use filtered dictionaries
```kotlin
override fun getSuggestions(...): List<SuggestedWordInfo>? {
    val gestureMode = gestureSettings?.getGestureMode() ?: GestureMode.STANDARD
    
    // When calling gesture engine, only pass dictionaries for this mode
    val mainDict = if (GestureModeFilter.usesMainDictionary(gestureMode)) {
        dictGroup.getDict(Dictionary.TYPE_MAIN)
    } else {
        null  // Don't pass main dict in SANDBOX/SANDBOX_ONLY
    }
    
    // Similarly for personal dict...
    ...
}
```

#### D. Integrate SessionWordCache for learning
```kotlin
override fun addToUserHistory(...) {
    val gestureSettings = gestureSettings ?: return
    
    if (gestureSettings.isSessionWordCacheEnabled()) {
        // Route to session cache instead of direct learn
        val cache = SessionWordCache.getInstance(context, locale)
        cache.recordWord(word, wasAutoCapitalized, ngramContext, gestureConfidence)
        return
    }
    
    // Original behavior
    ...
}
```

#### E. Flush session cache on keyboard close
```kotlin
override fun onFinishInput() {
    val cache = SessionWordCache.getInstance(context, locale)
    if (cache.size() > 0) {
        // Optionally show proofreading UI here
        val wordsToLearn = cache.getWordsAboveThreshold(
            gestureSettings?.getMinWordFrequency() ?: 2
        )
        // Learn filtered words
        wordsToLearn.forEach { entry ->
            // addToUserHistory(entry.word, ...)
        }
        cache.clear()
    }
    
    // Original behavior
    ...
}
```

---

### 2. **Settings UI Integration** 🔨 IMPORTANT

**Files to create:**
- `app/src/main/java/helium314/keyboard/latin/settings/GestureModesPreferenceFragment.kt`
- `app/src/main/res/xml/gesture_modes_preferences.xml`

**Required elements:**

#### A. Preference Fragment
```kotlin
class GestureModesPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.gesture_modes_preferences, rootKey)
        
        // Setup listeners for each preference
        findPreference<ListPreference>(GestureSettings.KEY_GESTURE_MODE)?.setOnPreferenceChangeListener { _, newValue ->
            Log.i(TAG, "Gesture mode changed to: $newValue")
            // Trigger dictionary reload
            true
        }
        
        // Handle other preferences...
    }
}
```

#### B. Preferences XML
```xml
<PreferenceScreen>
    <ListPreference
        android:key="pref_gesture_mode"
        android:title="@string/pref_gesture_mode_title"
        android:summary="@string/pref_gesture_mode_summary"
        android:entries="@array/gesture_mode_entries"
        android:entryValues="@array/gesture_mode_values"
        android:defaultValue="standard" />
    
    <SwitchPreferenceCompat
        android:key="pref_session_word_cache"
        android:title="@string/pref_session_word_cache_title"
        android:summary="@string/pref_session_word_cache_summary"
        android:defaultValue="false" />
    
    <!-- More preferences... -->
</PreferenceScreen>
```

---

### 3. **Manual Search Feature** 🔨 MEDIUM PRIORITY

**Toolbar gesture search by stored word pattern**

#### Implementation approach:
```kotlin
// In LatinIME.kt
private var lastGestureData: ComposedData? = null
private var lastSelectedWord: String? = null

// Store gesture when committed
fun commitWord(word: String, composedData: ComposedData?) {
    lastSelectedWord = word
    lastGestureData = composedData
    // Normal commit...
}

// Manual search triggered by toolbar key
fun onManualGestureSearch() {
    val word = lastSelectedWord ?: return
    val gesture = lastGestureData ?: return
    
    // Re-query gesture engine with all dictionaries
    val suggestions = mDictionaryFacilitator.getSuggestions(gesture, ...)
    
    // Display in modal/menu
    showGestureSearchResults(suggestions)
}
```

---

### 4. **Proofreading UI Dialog** 🔨 MEDIUM PRIORITY

**Display cached words before learning them**

```kotlin
// Show proofreading dialog
private fun showProofreadingDialog(words: List<SessionWordCache.WordCacheEntry>) {
    AlertDialog.Builder(this)
        .setTitle("Review Learned Words")
        .setMultiChoiceItems(
            words.map { it.word }.toTypedArray(),
            BooleanArray(words.size) { true }
        ) { _, which, isChecked ->
            if (!isChecked) {
                words[which].let { cache.rejectWord(it.word) }
            }
        }
        .setPositiveButton("Add Checked") { _, _ ->
            words.forEach { entry ->
                if (mDictionaryFacilitator.isInCache(entry.word)) {
                    addToUserHistory(entry.word, ...)
                }
            }
        }
        .setNegativeButton("Discard All") { _, _ ->
            cache.clear()
        }
        .show()
}
```

---

### 5. **Gesture Degradation Workaround** 🔨 LOW PRIORITY (BUT CRITICAL FIX)

**If gesture accuracy is still degraded after mode switching:**

```kotlin
// Force rebuild user history dictionary
fun rebuildUserHistoryDictionary(context: Context) {
    // Clear corrupted dictionary
    PersonalizationHelper.removeAllUserHistoryDictionaries(context)
    
    // Force reload
    dictionaryFacilitator.resetDictionaries(
        context, 
        locale, 
        forceReloadMainDictionary = false,
        // But the user history will be rebuilt fresh
    )
    
    Log.i(TAG, "User history dictionary rebuilt - gesture accuracy may improve")
}
```

---

## Testing Checklist

- [ ] **Mode Switching**
  - Switch between STANDARD, SANDBOX, SANDBOX_ONLY
  - Verify gesture engine uses correct dictionaries
  - Check logcat output from `GestureModeFilter.logDictionaryUsage()`

- [ ] **Session Word Cache**
  - Enable caching
  - Type/gesture words during session
  - Verify words appear in cache
  - Disable cache, close keyboard, verify words learned after proofreading

- [ ] **Gesture Accuracy**
  - Test STANDARD mode - should have best accuracy
  - Test SANDBOX - may have reduced accuracy (no main dict)
  - Test SANDBOX_ONLY - most restricted
  - Compare with HeliBoard to verify consistency

- [ ] **Manual Search**
  - Trigger manual gesture search from toolbar
  - Verify gesture engine re-queries with full dictionaries
  - Compare results with auto-suggestions

- [ ] **Gesture Cache**
  - Enable gesture cache
  - Check `GestureRecognitionCache.getStats()`
  - Verify patterns are stored and retrieved correctly

- [ ] **Settings UI**
  - All preferences visible and changeable
  - Changes persist across app restarts
  - Preference descriptions are clear

---

## Known Limitations

1. **Gesture cache is device-specific** - Won't transfer between devices due to DPI/layout differences
2. **SANDBOX mode accuracy** - May be reduced due to smaller vocabulary
3. **Native library is closed-source** - Cannot modify gesture recognition algorithm itself
4. **Frequency writes** - Still depend on native code internals (best we can do is filter dictionaries)

---

## File Summary

**Created in Modes branch:**
```
✅ app/src/main/java/helium314/keyboard/latin/personalization/SessionWordCache.kt
✅ app/src/main/java/helium314/keyboard/latin/personalization/GestureRecognitionCache.kt
✅ app/src/main/java/helium314/keyboard/latin/settings/GestureSettings.kt
✅ app/src/main/java/helium314/keyboard/latin/settings/GestureModes.kt
✅ app/src/main/java/helium314/keyboard/latin/GestureModeFilter.kt
✅ app/src/main/java/helium314/keyboard/latin/personalization/UserHistoryDictionaryFixed.java
✅ app/src/main/res/values/strings-gesture-modes.xml

🔨 STILL NEEDED (Integration points):
⬜ DictionaryFacilitatorImpl.kt - Integration of gesture mode filtering
⬜ GestureModesPreferenceFragment.kt - Settings UI
⬜ gesture_modes_preferences.xml - Preference layout
⬜ Manual search toolbar key handler
⬜ Proofreading dialog UI
⬜ Session cache flush on keyboard close
```

---

## Next Steps

1. **High Priority:**
   - Integrate `GestureSettings` into `DictionaryFacilitatorImpl.kt`
   - Modify `getSuggestions()` to use filtered dictionaries
   - Integrate `SessionWordCache` into learning flow

2. **Medium Priority:**
   - Create Settings UI (preferences fragment + XML)
   - Implement proofreading dialog

3. **Low Priority:**
   - Manual gesture search feature
   - Gesture database UI/visualization

---

## Questions?

For detailed understanding of the gesture degradation issue, see:
`app/src/main/java/helium314/keyboard/latin/personalization/UserHistoryDictionaryFixed.java`

The root cause is documented in extensive comments there.
