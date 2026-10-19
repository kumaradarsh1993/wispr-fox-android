package com.wisprfox.android.history

import com.wisprfox.android.provider.ProviderCatalog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Text renderings of a conversation: the Markdown export (format fixed by
 * `docs/CONVERSATIONS_CONTRACT.md`, identical on every client) and the
 * Copy variants. Pure, so it is unit-tested on the JVM.
 */
object ConversationExport {

    const val NOT_GENERATED = "(not generated)"

    data class Input(
        val title: String,
        val createdAtMillis: Long,
        val durationMs: Long,
        val meetingNotes: String?,
        val doc: SpeakerTurnsDocument?,
        val names: Map<String, String>,
        val zone: ZoneId = ZoneId.systemDefault(),
    )

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** `<YYYY-MM-DD> <title>.md`, with the title made safe for a filename. */
    fun fileName(input: Input): String {
        val date = dateFmt.format(Instant.ofEpochMilli(input.createdAtMillis).atZone(input.zone))
        return "$date ${sanitizeTitle(input.title)}.md"
    }

    fun sanitizeTitle(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "-")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', '-')
            .take(80)
            .trim()
            .ifBlank { "Conversation" }

    fun markdown(input: Input): String {
        val doc = input.doc
        val turns = doc?.turns.orEmpty()
        val speakers = SpeakerTurnsCodec.speakers(turns).map { SpeakerTurnsCodec.displayName(it, input.names) }
        val date = dateTimeFmt.format(Instant.ofEpochMilli(input.createdAtMillis).atZone(input.zone))
        return buildString {
            append("# ").append(input.title.trim().ifBlank { "Conversation" }).append("\n\n")
            append("- Date: ").append(date).append('\n')
            append("- Length: ").append(hms(input.durationMs)).append('\n')
            append("- Speakers: ").append(speakers.joinToString(", ").ifBlank { NOT_GENERATED }).append('\n')
            append("- Speaker engine: ").append(
                doc?.engine?.takeIf { turns.isNotEmpty() }?.let { engineLabel(it.provider, it.model) } ?: NOT_GENERATED,
            ).append('\n')
            append("- Word engine: ").append(
                doc?.accuracy?.takeIf { it.text.isNotBlank() }?.let { engineLabel(it.provider, it.model) } ?: NOT_GENERATED,
            ).append("\n\n")
            append("> Two transcripts of the same recording follow. The speaker transcript\n")
            append("> knows who spoke but can mishear words. The Whisper transcript has more\n")
            append("> accurate wording but no speakers. Use the speaker transcript for who said\n")
            append("> what, and the Whisper transcript to correct the wording.\n\n")
            append("## Meeting notes\n\n")
            append(notesText(input).ifBlank { NOT_GENERATED }).append("\n\n")
            append("## Speaker transcript\n\n")
            append(speakerMarkdown(turns, input.names).ifBlank { NOT_GENERATED }).append("\n\n")
            append("## Whisper transcript\n\n")
            append(whisperText(input).ifBlank { NOT_GENERATED }).append('\n')
        }
    }

    fun notesText(input: Input): String =
        input.meetingNotes?.trim().orEmpty().let { notes ->
            if (notes.isEmpty()) "" else SpeakerTurnsCodec.applyNames(notes, SpeakerTurnsCodec.encodeNames(input.names))
        }

    fun whisperText(input: Input): String = input.doc?.accuracy?.text?.trim().orEmpty()

    /** Copy → Speakers: `[mm:ss] Name: text` paragraphs, names substituted. */
    fun speakersPlain(turns: List<SpeakerTurn>, names: Map<String, String>): String =
        turns.filter { it.text.isNotBlank() }.joinToString("\n\n") { turn ->
            val stamp = turn.start?.let { "[${mmss(it)}] " }.orEmpty()
            "$stamp${SpeakerTurnsCodec.displayName(turn.speaker, names)}: ${turn.text.trim()}"
        }

    private fun speakerMarkdown(turns: List<SpeakerTurn>, names: Map<String, String>): String =
        turns.filter { it.text.isNotBlank() }.joinToString("\n\n") { turn ->
            val stamp = turn.start?.let { "[${mmss(it)}] " }.orEmpty()
            "**$stamp${SpeakerTurnsCodec.displayName(turn.speaker, names)}:** ${turn.text.trim()}"
        }

    fun engineLabel(provider: String, model: String): String = "${ProviderCatalog.label(provider)} $model"

    /** `mm:ss`; minutes keep counting past 59 (a 75-minute mark is `75:12`). */
    fun mmss(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0).toLong()
        return "%02d:%02d".format(Locale.ROOT, total / 60, total % 60)
    }

    /** `h:mm:ss`. */
    fun hms(durationMs: Long): String {
        val total = (durationMs.coerceAtLeast(0L) / 1000)
        return "%d:%02d:%02d".format(Locale.ROOT, total / 3600, (total % 3600) / 60, total % 60)
    }
}
