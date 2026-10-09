package com.xiaoman.memo.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/* 情书 / 相册共用同一条图片管线（设计方案 · 第十三章）：
   选图 → 压缩到 maxW 宽 JPEG → 存应用私有目录，返回绝对路径。 */
object ImageStore {
    fun dir(context: Context, sub: String): File {
        val d = File(context.filesDir, sub)
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun saveFromUri(context: Context, uri: Uri, sub: String, maxW: Int, quality: Float): String? = runCatching {
        val src = decode(context, uri) ?: return null
        val w = minOf(maxW, src.width)
        val h = Math.max(1, Math.round(src.height * w.toFloat() / src.width))
        val scaled = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
        val f = File(dir(context, sub), "img_${System.currentTimeMillis()}_${(0..999).random()}.jpg")
        FileOutputStream(f).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, (quality * 100).toInt(), out) }
        if (scaled !== src) scaled.recycle()
        src.recycle()
        f.absolutePath
    }.getOrNull()

    fun loadScaled(path: String, reqW: Int): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val sample = maxOf(1, opts.outWidth / reqW)
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun decode(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }
}
