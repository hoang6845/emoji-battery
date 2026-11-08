package com.hd.emojibattery.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

/** HTTPS-only image loader with a small disk cache for remote JSON artwork. */
object RemoteImageLoader {
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private val io = Executors.newFixedThreadPool(3)
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private class Request { @Volatile var cancelled = false }

    fun clear(target: ImageView) {
        (target.tag as? Request)?.cancelled = true
        target.tag = null
        (target.drawable as? android.graphics.drawable.Animatable)?.stop()
        target.setImageDrawable(null)
    }

    fun load(context: Context, rawUrl: String?, target: ImageView, placeholder: View?) {
        val key = rawUrl?.trim().orEmpty()
        clear(target)
        if (key.isEmpty()) return
        val request = Request()
        target.tag = request
        val size = (120 * target.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val cacheKey = "$key@$size"
        fun display(bitmap: Bitmap?) {
            if (target.tag !== request) return
            if (bitmap != null) {
                target.setImageBitmap(bitmap)
                target.visibility = View.VISIBLE
                placeholder?.visibility = View.GONE
            } else {
                placeholder?.visibility = View.VISIBLE
            }
        }
        memory.get(cacheKey)?.let { display(it); return }
        val appContext = context.applicationContext
        io.execute {
            if (request.cancelled) return@execute
            val bitmap = memory.get(cacheKey) ?: runCatching {
                val file = cacheFile(appContext, key)
                if (!file.exists() || file.length() == 0L) download(key, file)
                if (request.cancelled) return@execute
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > size * 2) {
                    options.inSampleSize *= 2
                }
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(file.absolutePath, options)?.also { memory.put(cacheKey, it) }
            }.getOrNull()
            target.post {
                display(bitmap)
            }
        }
    }

    private fun download(rawUrl: String, destination: File) {
        val bytes = SourceRemoteData.download(rawUrl, MAX_IMAGE_BYTES) ?: return
        destination.parentFile?.mkdirs()
        val temporary = File.createTempFile("art-", ".tmp", destination.parentFile)
        try {
            temporary.writeBytes(bytes)
            temporary.renameTo(destination)
        } finally { temporary.delete() }
    }

    private fun cacheFile(context: Context, key: String): File {
        val name = runCatching {
            MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }
        }.getOrElse { key.hashCode().toString(16) }
        return File(File(context.cacheDir, "remote-art"), "$name.img")
    }
}
