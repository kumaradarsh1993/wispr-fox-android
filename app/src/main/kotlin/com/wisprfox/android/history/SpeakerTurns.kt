package com.wisprfox.android.history

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Versioned, cross-client speaker-turn contract persisted as JSON in Room/Supabase
 * (`speaker_turns` column). See `docs/CONVERSATIONS_CONTRACT.md`.
 *
 * Version 2 adds the optional [engine], [accuracy] (the second, Whisper
 * transcript), [passes] and [errors]. `turns` keeps the v1 shape so old clients
 * still read speakers. Readers accept v1 and v2 and treat a missing field as
 * absent; writers always write v2.
 */
@Serializable
data class SpeakerTurnsDocument(
    val version: Int = SpeakerTurnsCodec.CURRENT_VERSION,
    val turns: List<SpeakerTurn> = emptyList(),
    val engine: EngineInfo? = null,
    val accuracy: AccuracyTranscript? = null,
    val passes: Map<String, String>? = null,
    val errors: Map<String, String>? = null,
)

@Serializable
data class SpeakerTurn(
    val speaker: String,
    val text: String,
    val start: Double? = null,
    val end: Double? = null,
)

@Serializable
data class EngineInfo(
    val provider: String,
    val model: String,
)

@Serializable
data class AccuracyTranscript(
    val text: String,
    val provider: String,
    val model: String,
)

object SpeakerTurnsCodec {
    const val CURRENT_VERSION = 2

    // explicitNulls=false: optional v2 fields are omitted rather than written as
    // null, matching the contract's "absent means not made".
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun encode(turns: List<SpeakerTurn>): String = encodeDocument(SpeakerTurnsDocument(turns = turns))

    fun encodeDocument(doc: SpeakerTurnsDocument): String =
        json.encodeToString(SpeakerTurnsDocument.serializer(), doc.copy(version = CURRENT_VERSION))

    /** The whole document, or null when absent or unreadable. Accepts v1 and v2. */
    fun decodeDocument(raw: String?): SpeakerTurnsDocument? = raw?.takeIf { it.isNotBlank() }?.let {
        runCatching { json.decodeFromString(SpeakerTurnsDocument.serializer(), it) }.getOrNull()
    }

    fun decode(raw: String?): List<SpeakerTurn> = decodeDocument(raw)?.turns.orEmpty()

    fun names(raw: String?): Map<String, String> = raw?.let {
        runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
    }.orEmpty()

    fun encodeNames(names: Map<String, String>): String = json.encodeToString(names)

    fun render(turnsRaw: String?, namesRaw: String?): String {
        val names = names(namesRaw)
        return decode(turnsRaw).joinToString("\n\n") { turn ->
            "${names[turn.speaker]?.takeIf(String::isNotBlank) ?: turn.speaker}:\n${turn.text.trim()}"
        }
    }

    /** Turns as `Speaker N: text` blocks, the shape stored in the `transcript` column. */
    fun plainTranscript(turns: List<SpeakerTurn>, names: Map<String, String> = emptyMap()): String =
        turns.filter { it.text.isNotBlank() }.joinToString("\n\n") { turn ->
            "${displayName(turn.speaker, names)}: ${turn.text.trim()}"
        }

    fun displayName(speaker: String, names: Map<String, String>): String =
        names[speaker]?.trim()?.takeIf(String::isNotEmpty) ?: speaker

    /** Distinct speaker labels in order of first appearance. */
    fun speakers(turns: List<SpeakerTurn>): List<String> = turns.map { it.speaker }.distinct()

    /** Substitute display names at render/copy time without mutating source text. */
    fun applyNames(text: String, namesRaw: String?): String = names(namesRaw).entries.fold(text) { current, entry ->
        val name = entry.value.trim()
        if (name.isEmpty()) current
        else current.replace(Regex("(?i)\\b${Regex.escape(entry.key)}\\b(?=\\s*:?)"), name)
    }
}

/** The three passes of a conversation, keyed as in the contract's `passes` map. */
enum class ConversationPass(val key: String, val label: String) {
    SPEAKERS("speakers", "Speakers"),
    ACCURACY("accuracy", "Whisper transcript"),
    NOTES("notes", "Meeting notes");

    companion object {
        fun parse(key: String): ConversationPass? = entries.firstOrNull { it.key == key.trim() }
    }
}

object PassState {
    const val DONE = "done"
    const val FAILED = "failed"
    const val SKIPPED = "skipped"
    const val RUNNING = "running"
}

/**
 * Pure bookkeeping for the per-pass state in a v2 document. Each pass succeeds
 * or fails on its own, and a failure never discards a result a pass already
 * produced.
 */
object ConversationPasses {

    fun state(doc: SpeakerTurnsDocument?, pass: ConversationPass): String? = doc?.passes?.get(pass.key)

    fun withState(doc: SpeakerTurnsDocument, pass: ConversationPass, state: String): SpeakerTurnsDocument {
        val passes = (doc.passes.orEmpty() + (pass.key to state))
        val errors = if (state == PassState.FAILED) doc.errors else doc.errors?.minus(pass.key)?.takeIf { it.isNotEmpty() }
        return doc.copy(passes = passes, errors = errors)
    }

    fun running(doc: SpeakerTurnsDocument, pass: ConversationPass) = withState(doc, pass, PassState.RUNNING)

    fun skipped(doc: SpeakerTurnsDocument, pass: ConversationPass) = withState(doc, pass, PassState.SKIPPED)

    fun speakersDone(doc: SpeakerTurnsDocument, turns: List<SpeakerTurn>, engine: EngineInfo) =
        withState(doc.copy(turns = turns, engine = engine), ConversationPass.SPEAKERS, PassState.DONE)

    fun accuracyDone(doc: SpeakerTurnsDocument, accuracy: AccuracyTranscript) =
        withState(doc.copy(accuracy = accuracy), ConversationPass.ACCURACY, PassState.DONE)

    fun notesDone(doc: SpeakerTurnsDocument) = withState(doc, ConversationPass.NOTES, PassState.DONE)

    /**
     * Record a failed attempt. When the pass already has a result from an
     * earlier attempt ([hasResult]), the result is kept and the pass stays
     * `done`: a failed retry must never make a good transcript look missing.
     * Otherwise the pass is `failed` and [message] lands in `errors`.
     */
    fun failed(doc: SpeakerTurnsDocument, pass: ConversationPass, message: String, hasResult: Boolean): SpeakerTurnsDocument {
        if (hasResult) return withState(doc, pass, PassState.DONE)
        val failedDoc = withState(doc, pass, PassState.FAILED)
        return failedDoc.copy(errors = failedDoc.errors.orEmpty() + (pass.key to message))
    }

    /** True when the document holds a result for [pass] ([notesText] is the row's notes column). */
    fun hasResult(doc: SpeakerTurnsDocument?, pass: ConversationPass, notesText: String?): Boolean = when (pass) {
        ConversationPass.SPEAKERS -> !doc?.turns.isNullOrEmpty()
        ConversationPass.ACCURACY -> !doc?.accuracy?.text.isNullOrBlank()
        ConversationPass.NOTES -> !notesText.isNullOrBlank()
    }

    /**
     * Passes the user can (re)run from the conversation screen: anything failed,
     * skipped, never recorded, or left `running` by a worker that is no longer
     * running ([inFlight] false).
     */
    fun retryable(doc: SpeakerTurnsDocument?, notesText: String?, inFlight: Boolean): List<ConversationPass> {
        if (inFlight) return emptyList()
        return ConversationPass.entries.filter { pass -> !hasResult(doc, pass, notesText) }
    }

    /** Human-readable problems for the card's status line, e.g. "Whisper transcript failed". */
    fun problems(doc: SpeakerTurnsDocument?, inFlight: Boolean): List<String> {
        if (doc == null) return emptyList()
        return ConversationPass.entries.mapNotNull { pass ->
            when (state(doc, pass)) {
                PassState.FAILED -> "${pass.label} failed"
                PassState.RUNNING -> if (inFlight) null else "${pass.label} interrupted"
                else -> null
            }
        }
    }
}
