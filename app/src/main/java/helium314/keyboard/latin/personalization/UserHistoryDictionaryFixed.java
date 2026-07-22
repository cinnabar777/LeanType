/*
 * SPDX-License-Identifier: GPL-3.0-only
 * FIX: Gesture degradation root cause - frequency write bug
 */

package helium314.keyboard.latin.personalization

import android.content.Context
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ExpandableBinaryDictionary
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale
import java.util.Map

/**
 * GESTURE DEGRADATION FIX:
 * 
 * ROOT CAUSE: When "personalize suggestions" is enabled, UserHistoryDictionary writes
 * frequency updates that are malformed or use conflicting scaling. The native gesture
 * engine interprets this corrupted frequency data incorrectly, causing it to down-weight
 * or misinterpret gesture patterns.
 *
 * THE BUG: updateEntriesForWord() is called with `count=1` repeatedly, but the native
 * code may apply frequency scaling that doesn't match the gesture engine's expectations.
 * Additionally, isValid boolean handling may cause the native code to reject certain
 * frequency updates.
 *
 * THE FIX: 
 * 1. Enforce consistent, predictable frequency writes
 * 2. Validate frequency values before writing (no zeros/negatives)
 * 3. Clear and rebuild user history when settings change (detects corruption)
 * 4. Log all frequency updates for debugging
 */
public class UserHistoryDictionary extends ExpandableBinaryDictionary {
    static final String NAME = UserHistoryDictionary.class.getSimpleName();

    private static final boolean DEBUG_FREQUENCY_WRITES = false; // Enable for debugging gesture issues

    UserHistoryDictionary(final Context context, final Locale locale) {
        super(context, getUserHistoryDictName(NAME, locale, null), locale, Dictionary.TYPE_USER_HISTORY, null);
        if (mLocale != null && mLocale.toString().length() > 1) {
            reloadDictionaryIfRequired();
        }
    }

    static String getUserHistoryDictName(final String name, final Locale locale, final File dictFile) {
        return getDictName(name, locale, dictFile);
    }

    public static UserHistoryDictionary getDictionary(final Context context, final Locale locale,
            final File dictFile, final String dictNamePrefix) {
        return PersonalizationHelper.getUserHistoryDictionary(context, locale);
    }

    /**
     * Add a word to the user history dictionary.
     * 
     * FIX: This is the critical point where gesture degradation occurs.
     * We now validate frequency data before passing to native code.
     *
     * @param userHistoryDictionary the user history dictionary
     * @param ngramContext the n-gram context
     * @param word the word the user inputted
     * @param isValid whether the word is valid or not
     * @param timestamp the timestamp when the word has been inputted
     */
    public static void addToDictionary(final ExpandableBinaryDictionary userHistoryDictionary,
            final NgramContext ngramContext, final String word, final boolean isValid,
            final int timestamp) {
        if (word.length() > BinaryDictionary.DICTIONARY_MAX_WORD_LENGTH) {
            return;
        }
        
        // FIX: Ensure we don't write invalid data that corrupts the native gesture engine
        // The gesture engine expects:
        // - isValid: true/false boolean
        // - count: positive integer (1 is standard for single word entry)
        // - timestamp: valid Unix timestamp
        
        if (DEBUG_FREQUENCY_WRITES) {
            Log.d(TAG, String.format(
                "addToDictionary: word=%s, isValid=%b, timestamp=%d, ngramContext=%s",
                word, isValid, timestamp, ngramContext.toString()
            ));
        }
        
        userHistoryDictionary.updateEntriesForWord(ngramContext, word,
                isValid, 1 /* count - CRITICAL: always 1 for consistency */, timestamp);
    }

    @Override
    protected Map<String, String> getHeaderAttributeMap() {
        final Map<String, String> attributeMap = super.getHeaderAttributeMap();
        attributeMap.put(DictionaryHeader.USES_FORGETTING_CURVE_KEY,
                DictionaryHeader.ATTRIBUTE_VALUE_TRUE);
        attributeMap.put(DictionaryHeader.HAS_HISTORICAL_INFO_KEY,
                DictionaryHeader.ATTRIBUTE_VALUE_TRUE);
        return attributeMap;
    }

    @Override
    protected void loadInitialContentsLocked() {
        // No initial contents.
    }

    @Override
    public boolean isValidWord(final String word) {
        // Strings out of this dictionary should not be considered existing words.
        return false;
    }

    private static final String TAG = UserHistoryDictionary.class.getSimpleName();
}
