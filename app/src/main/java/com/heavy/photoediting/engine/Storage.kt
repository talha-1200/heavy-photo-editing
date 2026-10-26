package com.heavy.photoediting.engine

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object Storage {
    const val DIR = "Pictures/HeavyPhotoEditing"

    fun newValues(prefix: String = "HEAVY"): ContentValues = ContentValues().apply {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        put(MediaStore.Images.Media.DISPLAY_NAME, "${prefix}_$ts.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, DIR)
    }

    fun saveJpeg(ctx: Context, bmp: Bitmap, quality: Int = 95): Uri {
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newValues("EDIT"))
            ?: error("MediaStore insert failed")
        ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return uri
    }

    /** Decodes with EXIF orientation applied, downscaled so the longest side <= maxSide. */
    fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = max(info.size.width, info.size.height)
            if (s > maxSide) {
                val f = maxSide.toFloat() / s
                decoder.setTargetSize(max(1, (info.size.width * f).toInt()), max(1, (info.size.height * f).toInt()))
            }
        }
    }

    fun listSaved(ctx: Context): List<Uri> {
        val out = mutableListOf<Uri>()
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        ctx.contentResolver.query(
            base, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("$DIR%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) out += ContentUris.withAppendedId(base, c.getLong(id))
        }
        return out
    }

    fun share(ctx: Context, uri: Uri) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share photo"))
    }
}
