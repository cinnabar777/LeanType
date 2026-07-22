# LeanType Gesture Modes - Quick Reference

## What You Have Now (Modes Branch)

### ✅ Core Components Implemented

1. **SessionWordCache.kt** - In-memory word caching with proofreading support
2. **GestureRecognitionCache.kt** - Gesture pattern storage and matching
3. **GestureSettings.kt** - Settings management for all gesture modes
4. **GestureModeFilter.kt** - Dictionary filtering logic
5. **UserHistoryDictionaryFixed.java** - Root cause analysis of gesture degradation
6. **strings-gesture-modes.xml** - UI string resources

---

## Three Gesture Modes Explained

### 🔵 STANDARD (Default)
- Uses: Main dict + user history + personal dict
- Best accuracy, full dictionary access
- May be affected by main dict quality issues

### 🟡 SANDBOX
- Uses: User history + personal dict ONLY
- Excludes: Main dictionary (50,000+ words)
- Better isolation from dictionary corruption
- Slightly reduced accuracy but more stable

### 🔴 SANDBOX_ONLY
- Uses: User history ONLY
- Most restricted mode
- Best for testing or conservative users

---

## Gesture Degradation Root Cause

**The Problem:**
When "personalize suggestions" is enabled, the `updateEntriesForWord()` function writes frequency updates to the native gesture engine that become corrupted or misaligned with the engine's expectations.

**The Solution:**
1. **Gesture modes** - Exclude problematic main dictionary
2. **Session caching** - Prevent learning of incorrect words
3. **Consistent writes** - Always use `count=1` for predictable behavior

**Quick Test:**
Switch to SANDBOX or SANDBOX_ONLY mode - if gesture accuracy improves, the main dictionary is corrupted.

---

## Files to Integrate (Integration Work Remaining)

### 1. DictionaryFacilitatorImpl.kt (CRITICAL)
**What to modify:**

```kotlin
// Add at class level
private var gestureSettings: GestureSettings? = null

// In resetDictionaries(), add:
gestureSettings = GestureSettings(context)

// In getSuggestions(), add dictionary filtering:
val mode = gestureSettings?.getGestureMode() ?: GestureMode.STANDARD
val filteredTypes = GestureModeFilter.getFilteredDictionaryTypes(mode)
// Use filteredTypes when querying gesture engine

// In addToUserHistory(), add session caching:
if (gestureSettings?.isSessionWordCacheEnabled() == true) {
    SessionWordCache.getInstance(context, locale).recordWord(...)
    return
}

// In onFinishInput(), add cache flush:
val cache = SessionWordCache.getInstance(context, locale)
cache.getWordsAboveThreshold(...).forEach { learnWord(...) }
```

### 2. Settings UI (IMPORTANT)
**Create new files:**
- `GestureModesPreferenceFragment.kt`
- `res/xml/gesture_modes_preferences.xml`

**In preferences XML:**
```xml
<ListPreference
    android:key="pref_gesture_mode"
    android:title="Gesture Typing Mode"
    android:entries="@array/gesture_mode_entries"
    android:entryValues="@array/gesture_mode_values" />
```

### 3. Proofreading Dialog (OPTIONAL but nice)
Show cached words when keyboard closes, let user select which to learn.

### 4. Manual Search Feature (OPTIONAL)
Toolbar button to re-search gesture with full dictionaries.

---

## Testing the Implementation

### Quick Test
1. Clone/pull Modes branch
2. Switch app to SANDBOX mode
3. Type/gesture several words
4. Compare gesture accuracy with STANDARD mode
5. Check if SANDBOX mode feels more stable

### Full Test
1. Enable session word cache
2. Type words during session
3. Close keyboard
4. Check if proofreading dialog appears
5. Verify words are learned after approval

---

## Key Code Locations

| File | Purpose |
|------|---------|
| `SessionWordCache.kt` | Buffers words before learning them |
| `GestureRecognitionCache.kt` | Stores gesture patterns for debugging |
| `GestureSettings.kt` | Reads/writes gesture preferences |
| `GestureModeFilter.kt` | Determines which dicts to use |
| `UserHistoryDictionaryFixed.java` | Explains gesture degradation bug |

---

## Settings Keys (Ready to Use)

```kotlin
pref_gesture_mode              // STANDARD, SANDBOX, SANDBOX_ONLY
pref_session_word_cache        // true/false - enable proofreading
pref_min_word_frequency        // 1-10 - minimum frequency to learn
pref_gesture_cache             // true/false - store gesture patterns
pref_gesture_confidence_threshold // 0.0-1.0 - fallback threshold
```

---

## Next Developer Steps

1. **Start here:** Integrate `GestureSettings` into `DictionaryFacilitatorImpl.kt`
2. **Then:** Add dictionary filtering in `getSuggestions()`
3. **Then:** Create Settings UI fragment
4. **Optional:** Add proofreading dialog and manual search

See `IMPLEMENTATION_GUIDE.md` for detailed code examples.

---

## Expected Improvements

✅ Users can choose gesture engine isolation level  
✅ Gesture accuracy stabilizes in SANDBOX modes  
✅ Words can be proofread before learning  
✅ Gesture patterns stored for debugging  
✅ Settings persist across app restarts  

---

## Branch Info

**Current Branch:** `cinnabar777/LeanType:Modes`  
**Base:** Latest LeanType/main  
**Commits:** 8 files added with core implementation  
**Ready to:** Integrate into DictionaryFacilitatorImpl

Pull branch into your local development and start with integration step 1 above!
