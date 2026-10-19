package com.wisprfox.android.queue

import android.content.Context
import androidx.work.ListenableWorker
import com.wisprfox.android.core.AppContainer
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.PipelineProgress
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.history.AccuracyTranscript
import com.wisprfox.android.history.AltKind
import com.wisprfox.android.history.ConversationPass
import com.wisprfox.android.history.ConversationPasses
import com.wisprfox.android.history.EngineInfo
import com.wisprfox.android.history.PassState
import com.wisprfox.android.history.RecordingStatus
import com.wisprfox.android.history.SpeakerTurn
import com.wisprfox.android.history.SpeakerTurnsCodec
import com.wisprfox.android.history.SpeakerTurnsDocument
import com.wisprfox.android.provider.Prompts
import com.wisprfox.android.provider.ProviderCatalog
import com.wisprfox.android.provider.SttProgress
import com.wisprfox.android.provider.SttProgressEvent
import com.wisprfox.android.sync.SyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * The conversation pipeline (`docs/CONVERSATIONS_CONTRACT.md`):
 *
 *  1. **Speakers** (required): a diarizing engine on the whole file, never split.
 *  2. **Accuracy** (optional): Groq whisper-large-v3 on the same audio.
 *  3. **Notes** (optional): meeting notes from the speakers transcript, with the
 *     accuracy text as reference when present.
 *
 * Each pass succeeds or fails on its own and is recorded in the v2
 * `speaker_turns` document (`passes` / `errors`). A failed pass never discards
 * one that worked. With [forced] set, only those passes run (the per-pass retry
 * buttons); otherwise every requested pass that has no result yet runs, which
 * also makes a WorkManager retry resume where the last attempt stopped.
 */
internal class ConversationPipeline(
    private val context: Context,
    private val container: AppContainer,
    private val attempt: Int,
    private val maxAttempts: Int,
) {
    private val recordings get() = container.recordings

    suspend fun run(id: String, forced: Set<ConversationPass>?): ListenableWorker.Result {
        val rec = recordings.get(id) ?: return ListenableWorker.Result.failure()
        val settings = container.currentSettings().withRecordingOverrides(rec)
        val requested = rec.requestedOutputs.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        val fullRun = forced == null
        val wanted: Set<ConversationPass> = forced ?: buildSet {
            add(ConversationPass.SPEAKERS)
            if ("accuracy" in requested) add(ConversationPass.ACCURACY)
            if ("meeting" in requested) add(ConversationPass.NOTES)
        }

        var doc = SpeakerTurnsCodec.decodeDocument(rec.speakerTurns) ?: SpeakerTurnsDocument()
        var notesText = rec.meetingNotesText
        if (fullRun) {
            for (pass in ConversationPass.entries - wanted) {
                if (!ConversationPasses.hasResult(doc, pass, notesText)) doc = ConversationPasses.skipped(doc, pass)
            }
        }
        val toRun = ConversationPass.entries.filter { pass ->
            pass in wanted && (!fullRun ||
                !(ConversationPasses.state(doc, pass) == PassState.DONE && ConversationPasses.hasResult(doc, pass, notesText)))
        }
        for (pass in toRun) doc = ConversationPasses.running(doc, pass)
        save(id, doc)
        recordings.setStatus(id, RecordingStatus.TRANSCRIBING)
        claimLiveState(id)

        val audio = File(rec.audioPath)
        val hasAudio = !rec.remote && rec.audioPath.isNotBlank() && audio.isFile
        var transient = false
        val failures = mutableListOf<String>()

        suspend fun attemptPass(pass: ConversationPass, sttProvider: String?, llmProvider: String?, block: suspend () -> Unit) {
            try {
                block()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                val failure = describeFailure(t, sttProvider, llmProvider)
                transient = transient || failure.transient
                failures += "${pass.label}: ${failure.message}"
                doc = ConversationPasses.failed(doc, pass, failure.message, ConversationPasses.hasResult(doc, pass, notesText))
                save(id, doc)
            }
        }

        // ── 1. Speakers ─────────────────────────────────────────────────────
        if (ConversationPass.SPEAKERS in toRun) {
            val (provider, model) = if (rec.sttProviderOverride != null) {
                ProviderCatalog.compatibleDiarizationSelection(settings.sttProvider, settings.sttModel)
            } else {
                ProviderCatalog.compatibleDiarizationSelection(settings.diarizationProvider, settings.diarizationModel)
            }
            attemptPass(ConversationPass.SPEAKERS, provider, null) {
                if (!hasAudio) throw PassException("The audio for this conversation isn't on this phone.")
                setStage(id, RecordingStatus.DIARIZING, PipelineState.DIARIZING)
                report(id, "Uploading…")
                val stt = container.providerFactory.stt(settings.copy(sttProvider = provider, sttModel = model))
                val progress = SttProgress { event ->
                    when (event) {
                        is SttProgressEvent.Uploading -> report(id, uploadLine(event, "Uploading"), percentOf(event))
                        is SttProgressEvent.Processing -> report(id, "Labelling speakers…")
                    }
                }
                val result = stt.transcribe(audio.absolutePath, settings.languageHint, true, progress)
                val turns = SpeakerTurnsCodec.decode(result.speakerTurnsJson).ifEmpty {
                    // An engine that heard speech but returned no turns: keep the
                    // words as a single speaker rather than throwing them away.
                    if (result.text.isBlank()) throw PassException("No speech was found in this recording.")
                    listOf(SpeakerTurn("Speaker 1", result.text.trim(), 0.0, result.durationSeconds))
                }
                recordings.setTranscript(id, SpeakerTurnsCodec.plainTranscript(turns), stt.name)
                doc = ConversationPasses.speakersDone(doc, turns, EngineInfo(provider, model))
                save(id, doc)
            }
        }

        // ── 2. Accuracy (Whisper) ───────────────────────────────────────────
        if (ConversationPass.ACCURACY in toRun) {
            val provider = ProviderCatalog.ACCURACY_PROVIDER
            val model = ProviderCatalog.ACCURACY_MODEL
            attemptPass(ConversationPass.ACCURACY, provider, null) {
                if (!hasAudio) throw PassException("The audio for this conversation isn't on this phone.")
                setStage(id, RecordingStatus.TRANSCRIBING, PipelineState.TRANSCRIBING)
                report(id, "Whisper · uploading…")
                val stt = container.providerFactory.stt(settings.copy(sttProvider = provider, sttModel = model))
                val progress = SttProgress { event ->
                    when (event) {
                        is SttProgressEvent.Uploading -> report(
                            id,
                            uploadLine(event, if (event.parts > 1) "Whisper · uploading part ${event.part} of ${event.parts}" else "Whisper · uploading"),
                            percentOf(event),
                        )
                        is SttProgressEvent.Processing -> report(
                            id,
                            if (event.parts > 1) "Transcribing part ${event.part} of ${event.parts}" else "Whisper · transcribing…",
                        )
                    }
                }
                val result = stt.transcribe(audio.absolutePath, settings.languageHint, false, progress)
                val text = result.text.trim()
                if (text.isEmpty()) throw PassException("Whisper returned no text.")
                doc = ConversationPasses.accuracyDone(doc, AccuracyTranscript(text, provider, model))
                save(id, doc)
                // A conversation whose speakers pass failed still gets a searchable transcript.
                if (recordings.get(id)?.transcript.isNullOrBlank()) recordings.setTranscript(id, text, stt.name)
            }
        }

        // ── 3. Notes ────────────────────────────────────────────────────────
        if (ConversationPass.NOTES in toRun) {
            val strong = settings.withLlmSelection(
                rec.strongProviderOverride ?: settings.strongProvider,
                rec.strongModelOverride ?: settings.strongModel,
            )
            attemptPass(ConversationPass.NOTES, null, strong.llmProvider) {
                setStage(id, RecordingStatus.MEETING_NOTES, PipelineState.MEETING_NOTES)
                report(id, "Writing notes…")
                val names = SpeakerTurnsCodec.names(recordings.get(id)?.speakerNames)
                val speakersText = SpeakerTurnsCodec.plainTranscript(doc.turns, names)
                val reference = doc.accuracy?.text?.takeIf { it.isNotBlank() }
                val source = speakersText.ifBlank { reference.orEmpty() }
                if (source.isBlank()) throw PassException("There's no transcript to write notes from yet.")
                val useReference = speakersText.isNotBlank() && reference != null
                val llm = container.providerFactory.llm(strong)
                val text = llm.complete(
                    Prompts.meetingNotesSystem(useReference),
                    Prompts.meetingNotesUserMessage(source, if (useReference) reference else null),
                    0.2f,
                ).trim()
                if (text.isEmpty()) throw PassException("The model didn't return any notes.")
                recordings.setAlt(id, AltKind.MEETING_NOTES, text, llm.name, true, null)
                notesText = text
                doc = ConversationPasses.notesDone(doc)
                save(id, doc)
            }
        }

        // ── Title ───────────────────────────────────────────────────────────
        val titleSource = notesText?.takeIf { it.isNotBlank() }
            ?: SpeakerTurnsCodec.plainTranscript(doc.turns).takeIf { it.isNotBlank() }
            ?: doc.accuracy?.text
        if (titleSource != null && (fullRun || recordings.get(id)?.title.isNullOrBlank())) {
            val fallback = rec.sourceName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Conversation"
            val title = runCatching {
                val titleSettings = settings.withLlmSelection(settings.titleProvider, settings.titleModel)
                container.providerFactory.llm(titleSettings).complete(
                    Prompts.TITLE_SYSTEM,
                    "<transcript>${titleSource.take(4_000)}</transcript>",
                    0.1f,
                ).trim().trim('"').trimEnd('.', '!', '?').take(80)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
            recordings.setTitle(id, title)
        }

        // ── Finish ──────────────────────────────────────────────────────────
        if (transient && attempt < maxAttempts) {
            recordings.bumpRetry(id)
            if (AppState.state.value.activeRecordingId == id) {
                AppState.update {
                    copy(
                        pipeline = PipelineState.IDLE,
                        activeRecordingId = null,
                        message = "Network hiccup — will retry in background",
                        messageIsError = false,
                    )
                }
            }
            return ListenableWorker.Result.retry()
        }

        val anyContent = ConversationPasses.hasResult(doc, ConversationPass.SPEAKERS, notesText) ||
            ConversationPasses.hasResult(doc, ConversationPass.ACCURACY, notesText) ||
            !notesText.isNullOrBlank()
        if (!anyContent) {
            val message = failures.firstOrNull() ?: "Couldn't process this conversation."
            recordings.setError(id, message)
            if (AppState.state.value.activeRecordingId == id) {
                AppState.update {
                    copy(pipeline = PipelineState.ERROR, message = message, messageIsError = true, activeRecordingId = null, targetPackage = null)
                }
                scheduleIdleReset()
            }
            return ListenableWorker.Result.failure()
        }

        recordings.setStatus(id, RecordingStatus.DONE)
        SyncWorker.enqueueOnce(context)

        var copied = false
        if (fullRun) {
            val latest = recordings.get(id)
            val clip = notesText?.takeIf { it.isNotBlank() }?.let { SpeakerTurnsCodec.applyNames(it, latest?.speakerNames) }
                ?: SpeakerTurnsCodec.plainTranscript(doc.turns, SpeakerTurnsCodec.names(latest?.speakerNames)).takeIf { it.isNotBlank() }
                ?: doc.accuracy?.text
            if (!clip.isNullOrBlank()) {
                container.delivery.deliver(clip, autoPaste = false, expectedPackage = null, conversation = true)
                copied = true
            }
        }
        if (AppState.state.value.activeRecordingId == id) {
            val summary = when {
                failures.isNotEmpty() -> "Done · " + failures.joinToString(" · ") { it.substringBefore(':') + " failed" }
                copied && !notesText.isNullOrBlank() -> "Meeting notes copied"
                copied -> "Transcript copied"
                else -> "Done"
            }
            AppState.update {
                copy(pipeline = PipelineState.DONE, message = summary, messageIsError = false, activeRecordingId = null, targetPackage = null)
            }
            scheduleIdleReset()
        } else if (!fullRun && failures.isNotEmpty()) {
            AppState.toast(failures.joinToString("\n"), isError = true)
        }
        return ListenableWorker.Result.success()
    }

    private suspend fun save(id: String, doc: SpeakerTurnsDocument) {
        recordings.setMeetingMetadata(id, SpeakerTurnsCodec.encodeDocument(doc), isMeeting = true, diarized = true)
    }

    private suspend fun setStage(id: String, status: RecordingStatus, pipeline: PipelineState) {
        recordings.setStatus(id, status)
        if (AppState.state.value.activeRecordingId == id) AppState.setPipeline(pipeline)
    }

    /** Drive the live avatar only when nothing else owns it (never hijack a fresh dictation). */
    private fun claimLiveState(id: String) {
        val live = AppState.state.value
        if (live.activeRecordingId == null &&
            live.pipeline in setOf(PipelineState.IDLE, PipelineState.DONE, PipelineState.ERROR)
        ) {
            AppState.update { copy(pipeline = PipelineState.TRANSCRIBING, activeRecordingId = id, messageIsError = false) }
        }
    }

    private fun report(id: String, text: String, percent: Int? = null) =
        PipelineProgress.report(context, id, text, percent, conversation = true)

    private fun scheduleIdleReset() {
        container.applicationScope.launch {
            delay(1800)
            val p = AppState.state.value.pipeline
            if (p == PipelineState.DONE || p == PipelineState.ERROR) {
                AppState.update { copy(pipeline = PipelineState.IDLE, message = null) }
            }
        }
    }
}

internal fun percentOf(event: SttProgressEvent.Uploading): Int =
    (event.sent * 100 / event.total.coerceAtLeast(1)).toInt()

/** "Uploading 3.2 of 7.0 MB". */
internal fun uploadLine(event: SttProgressEvent.Uploading, prefix: String): String =
    String.format(Locale.ROOT, "%s %.1f of %.1f MB", prefix, event.sent / MB, event.total / MB)

private const val MB = 1024.0 * 1024.0
