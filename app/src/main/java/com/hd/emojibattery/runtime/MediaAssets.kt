package com.hd.emojibattery.runtime

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import com.hd.emojibattery.data.SourceRemoteData
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Shared authenticated media cache. Decode off main; do not hold entire frame packs in RAM. */
object MediaAssets {
    val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val failedBackgrounds = java.util.WeakHashMap<android.view.View, String>()
    fun isCached(context: Context, url: String): Boolean = url.startsWith("content://") ||
        (url.isNotBlank() && destination(context, url).let { it.exists() && it.length() > 0 })
    fun retryFailedBackgrounds(root: android.view.View) {
        failedBackgrounds.entries.toList().forEach { (view, url) ->
            var parent = view.parent
            while (parent != null && parent !== root) parent = parent.parent
            if ((view === root || parent === root) && view.tag == url) background(view, url)
        }
    }
    fun file(context: Context, url: String): File? {
        if (url.isBlank()) return null
        if (url.startsWith("content://")) return runCatching {
            val file = destination(context, url)
            if (!file.exists()) {
                val temporary = File.createTempFile("import", ".tmp", file.parentFile)
                try {
                    context.contentResolver.openInputStream(android.net.Uri.parse(url))!!.use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 48 * 1024 * 1024) { "Image too large" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    check(temporary.renameTo(file) || file.exists())
                } finally { temporary.delete() }
            }
            file
        }.getOrNull()
        if (!url.startsWith("https://")) return null
        val target = destination(context, url)
        if (target.exists() && target.length() > 0) return target
        val bytes = SourceRemoteData.download(url, 48 * 1024 * 1024)?.takeIf { it.isNotEmpty() } ?: return null
        val temp = File.createTempFile("media", ".tmp", target.parentFile)
        return try {
            temp.writeBytes(bytes)
            if (temp.renameTo(target) || target.exists()) target else null
        } finally { temp.delete() }
    }
    private fun destination(context: Context, url: String): File {
        val dir = File(context.filesDir, "applied_media").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, hash)
    }
    fun drawable(file: File, size: Int = 512): Drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val scale = minOf(1f, size.toFloat() / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
    }
    fun load(view: ImageView, url: String, maxSize: Int = 512, finished: (Boolean) -> Unit = {}) {
        (view.drawable as? Animatable)?.stop()
        view.tag = url
        val app = view.context.applicationContext
        io.execute {
            val image = runCatching { file(app, url)?.let { drawable(it, maxSize) } }.getOrNull()
            main.post {
                if (view.tag != url) return@post
                view.setImageDrawable(image)
                (image as? Animatable)?.start()
                finished(image != null)
            }
        }
    }
    fun clear(view: ImageView) { view.tag = null; (view.drawable as? Animatable)?.stop(); view.setImageDrawable(null) }
    fun background(view: android.view.View, url: String, finished: (Boolean) -> Unit = {}) {
        failedBackgrounds.remove(view)
        (view.background as? Animatable)?.stop()
        view.tag = url
        val app = view.context.applicationContext
        io.execute {
            val image = runCatching { file(app, url)?.let { drawable(it) } }.getOrNull()
            main.post {
                if (view.tag != url) return@post
                if (image != null) {
                    view.background = image
                    (image as? Animatable)?.start()
                } else failedBackgrounds[view] = url
                finished(image != null)
            }
        }
    }
}
