package com.hd.emojibattery.data

import android.content.Context
import android.graphics.BitmapFactory
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

    fun load(context: Context, rawUrl: String?, target: ImageView, placeholder: View?) {
        val key = rawUrl?.trim().orEmpty()
        if (key.isEmpty()) return
        target.tag = key
        val appContext = context.applicationContext
        io.execute {
            val file = cacheFile(appContext, key)
            if (!file.exists() || file.length() == 0L) download(key, file)
            val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            target.post {
                if (target.tag == key) {
                    if (bitmap != null) {
                        target.setImageBitmap(bitmap)
                        target.visibility = View.VISIBLE
                        placeholder?.visibility = View.GONE
                    } else {
                        placeholder?.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun download(rawUrl: String, destination: File) {
        var connection: HttpURLConnection? = null
        var temporary: File? = null
        try {
            val url = URL(rawUrl)
            if (!url.protocol.equals("https", true)) return
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "EmojiBattery-Android")
            connection.connect()
            if (connection.responseCode !in 200..299 || !connection.url.protocol.equals("https", true)) return
            if (connection.contentLength > MAX_IMAGE_BYTES) return
            destination.parentFile?.mkdirs()
            val tempFile = File(destination.parentFile, destination.name + ".tmp")
            temporary = tempFile
            connection.inputStream.use { input -> tempFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_IMAGE_BYTES) return
                    output.write(buffer, 0, count)
                }
            } }
            if (tempFile.length() > 0L) {
                if (destination.exists()) destination.delete()
                tempFile.renameTo(destination)
            }
        } catch (_: Exception) {
            // The local emoji/color fallback stays visible when remote artwork is unavailable.
        } finally {
            temporary?.takeIf { it.exists() }?.delete()
            connection?.disconnect()
        }
    }

    private fun cacheFile(context: Context, key: String): File {
        val name = runCatching {
            MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }
        }.getOrElse { key.hashCode().toString(16) }
        return File(File(context.cacheDir, "remote-art"), "$name.img")
    }
}
