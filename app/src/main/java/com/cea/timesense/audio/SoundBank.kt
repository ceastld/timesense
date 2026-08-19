package com.cea.timesense.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Copies user-picked audio into app-private storage so playback
 * does not depend on the original URI staying permissioned.
 * Customs are a library: many files per cue, listed after builtins.
 */
object SoundBank {

    const val MAX_BYTES: Long = 5L * 1024L * 1024L
    const val CUSTOM_PREFIX = "c_"

    fun isCustomId(id: String): Boolean = id.startsWith(CUSTOM_PREFIX)

    fun fileFor(context: Context, id: String): File {
        return File(dir(context), "$id.bin")
    }

    fun import(context: Context, cue: Cue, uri: Uri): Result<Imported> {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri) ?: "自定义音频"
        val id = CUSTOM_PREFIX + UUID.randomUUID().toString().replace("-", "")
        val dest = fileFor(context, id)
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        try {
            resolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output ->
                    val copied = input.copyTo(output)
                    if (copied <= 0L) {
                        return Result.failure(IOException("empty audio"))
                    }
                    if (copied > MAX_BYTES) {
                        return Result.failure(IOException("audio larger than 5 MB"))
                    }
                }
            } ?: return Result.failure(IOException("cannot open audio"))
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            dest.delete()
            return Result.failure(e)
        }
        return Result.success(Imported(id = id, cue = cue, name = displayName))
    }

    fun delete(context: Context, id: String) {
        if (!isCustomId(id)) return
        fileFor(context, id).delete()
    }

    /**
     * Moves the v1 single-file custom (tick.bin / kata.bin / ding.bin)
     * into the library if present.
     */
    fun migrateV1(context: Context, cue: Cue): Imported? {
        val legacy = File(dir(context), "${cue.name.lowercase()}.bin")
        if (!legacy.isFile || legacy.length() <= 0L) return null
        val id = CUSTOM_PREFIX + UUID.randomUUID().toString().replace("-", "")
        val dest = fileFor(context, id)
        if (!legacy.renameTo(dest)) {
            legacy.copyTo(dest, overwrite = true)
            legacy.delete()
        }
        return Imported(id = id, cue = cue, name = "自定义")
    }

    private fun dir(context: Context): File {
        return File(context.applicationContext.filesDir, "sounds").apply { mkdirs() }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return uri.lastPathSegment
        cursor.use {
            if (!it.moveToFirst()) return uri.lastPathSegment
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return uri.lastPathSegment
            return it.getString(idx)?.takeIf { name -> name.isNotBlank() }
        }
    }

    data class Imported(
        val id: String,
        val cue: Cue,
        val name: String,
    )
}
