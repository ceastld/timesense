package com.cea.timesense.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/**
 * Copies user-picked audio into app-private storage so playback
 * does not depend on the original URI staying permissioned.
 */
object SoundBank {

    const val MAX_BYTES: Long = 5L * 1024L * 1024L

    fun fileFor(context: Context, cue: Cue): File {
        return File(dir(context), "${cue.name.lowercase()}.bin")
    }

    fun hasCustom(context: Context, cue: Cue): Boolean {
        val file = fileFor(context, cue)
        return file.isFile && file.length() > 0L
    }

    fun import(context: Context, cue: Cue, uri: Uri): Result<String> {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri) ?: "自定义音频"
        val dest = fileFor(context, cue)
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
            if (dest.exists() && !dest.delete()) {
                return Result.failure(IOException("cannot replace audio"))
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            return Result.failure(e)
        }
        return Result.success(displayName)
    }

    fun clear(context: Context, cue: Cue) {
        fileFor(context, cue).delete()
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
}
