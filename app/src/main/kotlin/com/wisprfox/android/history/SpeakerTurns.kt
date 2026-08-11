package com.wisprfox.android.history

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Versioned, cross-client speaker-turn contract persisted as JSON in Room/Supabase. */
@Serializable
data class SpeakerTurnsDocument(
    val version: Int = 1,
    val turns: List<SpeakerTurn> = emptyList(),
)

@Serializable
data class SpeakerTurn(
    val speaker: String,
    val text: String,
    val start: Double? = null,
    val end: Double? = null,
)

object SpeakerTurnsCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(turns: List<SpeakerTurn>): String =
        json.encodeToString(SpeakerTurnsDocument.serializer(), SpeakerTurnsDocument(turns = turns))

    fun decode(raw: String?): List<SpeakerTurn> = raw?.let {
        runCatching { json.decodeFromString(SpeakerTurnsDocument.serializer(), it).turns }.getOrNull()
    }.orEmpty()

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

    /** Substitute display names at render/copy time without mutating source text. */
    fun applyNames(text: String, namesRaw: String?): String = names(namesRaw).entries.fold(text) { current, entry ->
        val name = entry.value.trim()
        if (name.isEmpty()) current
        else current.replace(Regex("(?i)\\b${Regex.escape(entry.key)}\\b(?=\\s*:?)"), name)
    }
}
