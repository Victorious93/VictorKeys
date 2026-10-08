/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.nlp.BreakIteratorGroup
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock
import org.k3lp.runtime.K3Content
import org.k3lp.runtime.K3TextRange
import java.io.File
import java.util.TreeMap

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"

        private const val MIN_PREFIX_LENGTH = 2

        // Built-in developer/Linux words rank just below the most common English words, so that e.g. "gi" suggests
        // "git" before "give", but "th" still prefers "the".
        private const val DEV_WORD_FREQUENCY = 240
        private const val LEARNED_BOOST = 4
        private const val MAX_LEARNED_ENTRIES = 5000
        private const val USER_DATA_DIR = "nlp"
        private const val LEARNED_FILE = "learned_words.json"
        private const val BLOCKED_FILE = "blocked_words.json"
    }

    private val appContext by context.appContext()
    private val prefs by FlorisPreferenceStore

    // Sorted by lowercase word, which allows cheap prefix lookups via subMap().
    private val wordData = guardedByLock { TreeMap<String, Int>() }
    // Preferred display form for words that are not plain lowercase (e.g. "Python", "NixOS", "std::string").
    private val displayForms = guardedByLock { mutableMapOf<String, String>() }
    // Lowercase keys that come from dev.json, and the original frequency of those that also exist in data.json.
    // Needed so that disabling the dev dictionary restores the plain English behavior.
    private val devKeys = guardedByLock { mutableSetOf<String>() }
    private val baseFrequencies = guardedByLock { mutableMapOf<String, Int>() }
    // Learned usage counts, used to favor words the user actually picks.
    private val learned = guardedByLock { mutableMapOf<String, Int>() }
    private val blocked = guardedByLock { mutableSetOf<String>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val devWordsSerializer = ListSerializer(String.serializer())
    private val learnedSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val blockedSerializer = ListSerializer(String.serializer())

    private fun userDataFile(name: String) = File(appContext.filesDir, "$USER_DATA_DIR/$name")

    private fun <T> readUserData(name: String, read: (String) -> T): T? {
        return try {
            val file = userDataFile(name)
            if (file.isFile) read(file.readText()) else null
        } catch (e: Exception) {
            flogWarning { "Failed to read $name: $e" }
            null
        }
    }

    // Writes to a temp file first so a crash mid-write cannot corrupt the existing data.
    private fun writeUserData(name: String, content: String) {
        try {
            val file = userDataFile(name)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "$name.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            flogWarning { "Failed to write $name: $e" }
        }
    }

    private suspend fun saveLearned() = withContext(Dispatchers.IO) {
        val snapshot = learned.withLock { learned ->
            if (learned.size > MAX_LEARNED_ENTRIES) {
                val keep = learned.entries.sortedByDescending { it.value }.take(MAX_LEARNED_ENTRIES)
                learned.clear()
                keep.forEach { learned[it.key] = it.value }
            }
            learned.toMap()
        }
        writeUserData(LEARNED_FILE, Json.encodeToString(learnedSerializer, snapshot))
    }

    private suspend fun saveBlocked() = withContext(Dispatchers.IO) {
        val snapshot = blocked.withLock { it.toList() }
        writeUserData(BLOCKED_FILE, Json.encodeToString(blockedSerializer, snapshot))
    }

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Here we have the chance to preload dictionaries and prepare a neural network for a specific language.
        // Is kept in sync with the active keyboard subtype of the user, however a new preload does not necessary mean
        // the previous language is not needed anymore (e.g. if the user constantly switches between two subtypes)

        // To read a file from the APK assets the following methods can be used:
        // appContext.assets.open()
        // appContext.assets.reader()
        // appContext.assets.bufferedReader()
        // appContext.assets.readText()
        // To copy an APK file/dir to the file system cache (appContext.cacheDir), the following methods are available:
        // appContext.assets.copy()
        // appContext.assets.copyRecursively()

        // The subtype we get here contains a lot of data, however we are only interested in subtype.primaryLocale and
        // subtype.secondaryLocales.

        wordData.withLock { wordData ->
            if (wordData.isEmpty()) {
                readUserData(LEARNED_FILE) { Json.decodeFromString(learnedSerializer, it) }?.let { saved ->
                    learned.withLock { it.putAll(saved) }
                }
                readUserData(BLOCKED_FILE) { Json.decodeFromString(blockedSerializer, it) }?.let { saved ->
                    blocked.withLock { it.addAll(saved) }
                }
                // Here we use readText() because the test dictionary is a json dictionary
                val rawData = appContext.assets.readText("ime/dict/data.json")
                val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
                wordData.putAll(jsonData)
                val devWords = Json.decodeFromString(
                    devWordsSerializer, appContext.assets.readText("ime/dict/dev.json")
                )
                displayForms.withLock { forms ->
                    for (word in devWords) {
                        val key = word.lowercase()
                        devKeys.withLock { it.add(key) }
                        wordData[key]?.let { orig -> baseFrequencies.withLock { it[key] = orig } }
                        wordData[key] = maxOf(wordData[key] ?: 0, DEV_WORD_FREQUENCY)
                        if (word != key) forms[key] = word
                    }
                }
            }
        }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        return when (word.lowercase()) {
            // Use typo for typing errors
            "typo" -> SpellingResult.typo(arrayOf("typo1", "typo2", "typo3"))
            // Use grammar error if the algorithm can detect this. On Android 11 and lower grammar errors are visually
            // marked as typos due to a lack of support
            "gerror" -> SpellingResult.grammarError(arrayOf("grammar1", "grammar2", "grammar3"))
            // Use valid word for valid input
            else -> SpellingResult.validWord()
        }
    }

    /**
     * Extends the default word-break based composing range so that identifiers joined by `-` or `_`
     * (`apt-get`, `snake_case`, `--dry-run`) are treated as a single word instead of being split at the joiner.
     */
    override suspend fun determineLocalComposing(
        subtype: Subtype,
        textBeforeSelection: CharSequence,
        breakIterators: BreakIteratorGroup,
        localLastCommitPosition: Int,
    ): K3TextRange {
        val base = super.determineLocalComposing(subtype, textBeforeSelection, breakIterators, localLastCommitPosition)
        val end = textBeforeSelection.length
        val baseStart = if (base == K3TextRange.Zero) end else base.start
        var start = baseStart
        while (start > 0 && textBeforeSelection[start - 1].let { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            start--
        }
        // Leading dashes belong to command-line flags (`--verbose`), not to the word being completed.
        while (start < end && textBeforeSelection[start] == '-') start++
        val hasWordChar = (start until end).any { i ->
            textBeforeSelection[i].let { it.isLetterOrDigit() || it == '_' }
        }
        // Only deviate from the default when the word actually extends further back (or the trailing joiner
        // was ignored), so regular prose behaves exactly as before.
        return if (hasWordChar && start < end && start != baseStart) K3TextRange(start, end) else base
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: K3Content,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val typed = content.compositionText?.toString() ?: return emptyList()
        if (typed.length < MIN_PREFIX_LENGTH || typed.first() == ':' || typed.any { it.isWhitespace() }) {
            return emptyList()
        }
        val prefix = typed.lowercase()
        val capitalize = typed.first().isUpperCase()
        val allCaps = typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() }

        val devEnabled = prefs.suggestion.devDictionaryEnabled.get()
        val devSet = if (devEnabled) emptySet() else devKeys.withLock { it.toSet() }
        val baseFreqs = if (devEnabled) emptyMap() else baseFrequencies.withLock { it.toMap() }

        val matches = wordData.withLock { words ->
            val learnedCounts = learned.withLock { it.toMap() }
            val blockedWords = blocked.withLock { it.toSet() }
            words.subMap(prefix, prefix + Character.MAX_VALUE).entries
                .filter { it.key != prefix && it.key !in blockedWords }
                // With the dev dictionary off, drop dev-only words and restore original frequencies of shared ones.
                .filter { devEnabled || it.key !in devSet || it.key in baseFreqs }
                .map { java.util.AbstractMap.SimpleEntry(it.key, baseFreqs[it.key] ?: it.value) }
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> {
                        it.value + (learnedCounts[it.key] ?: 0) * LEARNED_BOOST
                    }.thenBy { it.key.length }.thenBy { it.key }
                )
                .take(maxCandidateCount)
                .map { it.key to it.value }
        }
        if (matches.isEmpty()) return emptyList()

        val forms = if (devEnabled) displayForms.withLock { it.toMap() } else emptyMap()
        return matches.map { (key, freq) ->
            val display = forms[key] ?: key
            val text = when {
                allCaps && forms[key] == null -> display.uppercase()
                capitalize && forms[key] == null -> display.replaceFirstChar { it.uppercase() }
                else -> display
            }
            WordSuggestionCandidate(
                text = text,
                confidence = freq / 255.0,
                isEligibleForAutoCommit = false,
                sourceProvider = this,
            )
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
        if (candidate.sourceProvider !== this) return
        val key = candidate.text.toString().lowercase()
        learned.withLock { it[key] = (it[key] ?: 0) + 1 }
        saveLearned()
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        if (candidate.sourceProvider !== this) return false
        blocked.withLock { it.add(candidate.text.toString().lowercase()) }
        saveBlocked()
        return true
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return wordData.withLock { it.getOrDefault(word, 0) / 255.0 }
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
    }
}
