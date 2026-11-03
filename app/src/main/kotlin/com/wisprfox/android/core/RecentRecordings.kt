package com.wisprfox.android.core

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/** One audio file the phone's recorder saved, as Upload lists it. */
data class RecentRecording(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val modifiedAtMs: Long,
    val folder: String,
)

/**
 * The newest recordings in a folder, read from MediaStore (no file access, no
 * copying). Upload shows the top 3 so a just-finished Voice Recorder file is
 * one tap away.
 */
object RecentRecordings {

    val permission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Up to [limit] newest audio files whose folder starts with [folderPrefix]
     * (e.g. "Recordings/Voice Recorder/"). An empty prefix means any folder.
     */
    fun newest(ctx: Context, folderPrefix: String, limit: Int = 3): List<RecentRecording> {
        if (!hasPermission(ctx)) return emptyList()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.RELATIVE_PATH,
        )
        val (selection, args) = if (folderPrefix.isBlank()) null to null
        else "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" to arrayOf("$folderPrefix%")
        val out = mutableListOf<RecentRecording>()
        runCatching {
            ctx.contentResolver.query(
                collection, projection, selection, args,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val modCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                while (c.moveToNext() && out.size < limit) {
                    out += RecentRecording(
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                        name = c.getString(nameCol).orEmpty(),
                        durationMs = c.getLong(durCol),
                        modifiedAtMs = c.getLong(modCol) * 1000L,
                        folder = c.getString(pathCol).orEmpty(),
                    )
                }
            }
        }
        return out
    }

    /** Folders that hold audio, newest first, for the Settings picker. */
    fun audioFolders(ctx: Context, limit: Int = 12): List<String> {
        if (!hasPermission(ctx)) return emptyList()
        val seen = LinkedHashSet<String>()
        runCatching {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.RELATIVE_PATH),
                null, null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC",
            )?.use { c ->
                val col = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                while (c.moveToNext() && seen.size < limit) c.getString(col)?.let { seen += it }
            }
        }
        return seen.toList()
    }
}
