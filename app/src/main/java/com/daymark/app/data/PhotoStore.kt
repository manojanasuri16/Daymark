package com.daymark.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * Journal photos live only in Daymark's private storage (never the gallery, never shared): `files/journal_photos/<uuid>.jpg`.
 * Each picked image is downscaled and re-encoded, which also drops camera metadata such as location.
 */
object PhotoStore {
    const val MAX_PER_ENTRY = 3
    private const val DIR = "journal_photos"
    private const val MAX_EDGE = 1600
    private const val QUALITY = 82
    /** Files younger than this are never swept — they may belong to an entry that's still being written. */
    private const val SWEEP_GRACE_MS = 60 * 60 * 1000L

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), File(name).name)

    /** Copies a picked image into private storage. Returns the stored file name. */
    fun import(context: Context, uri: Uri): String {
        val bitmap = decode(context, uri)
        return save(context, bitmap).also { bitmap.recycle() }
    }

    private fun save(context: Context, bitmap: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        file(context, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        return name
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder applies EXIF rotation for us.
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Couldn't read that image")
    }

    /** A small bitmap for thumbnails, or null if the file is gone. */
    fun thumbnail(context: Context, name: String, maxEdgePx: Int): Bitmap? {
        val f = file(context, name)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx) })
    }

    private fun sampleSize(w: Int, h: Int, maxEdge: Int): Int {
        var s = 1
        while (maxOf(w, h) / (s * 2) >= maxEdge) s *= 2
        return s
    }

    fun delete(context: Context, names: Collection<String>) = names.forEach { file(context, it).delete() }

    fun toBase64(context: Context, name: String): String? =
        file(context, name).takeIf { it.exists() }?.readBytes()?.let { java.util.Base64.getEncoder().encodeToString(it) }

    /** Writes a backed-up photo back into private storage. Returns its new file name. */
    fun fromBase64(context: Context, data: String): String? = runCatching {
        val bytes = java.util.Base64.getDecoder().decode(data)
        val name = "${UUID.randomUUID()}.jpg"
        file(context, name).writeBytes(bytes)
        name
    }.getOrNull()

    /** Removes image files no journal entry refers to (deleted entries, trackers, resets, abandoned drafts). */
    fun sweep(context: Context, referenced: Set<String>, now: Long = System.currentTimeMillis()) {
        dir(context).listFiles()?.forEach { f ->
            if (f.name !in referenced && now - f.lastModified() > SWEEP_GRACE_MS) f.delete()
        }
    }

    /** Removes every stored photo (used by "Erase all data" and before a restore). */
    fun clearAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
