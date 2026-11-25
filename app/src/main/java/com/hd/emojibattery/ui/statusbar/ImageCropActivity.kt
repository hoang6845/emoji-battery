package com.hd.emojibattery.ui.statusbar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.canhub.cropper.CropImageView
import com.hd.emojibattery.R
import java.io.File
import java.util.UUID

class BackgroundCropContract : ActivityResultContract<Uri, Uri?>() {
    override fun createIntent(context: Context, input: Uri) =
        Intent(context, ImageCropActivity::class.java).setData(input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

/** Crop is a separate confirmation step; cancelling never changes the status editor draft. */
class ImageCropActivity : AppCompatActivity(), CropImageView.OnCropImageCompleteListener {
    override fun onResume() {
        super.onResume()
        com.hd.emojibattery.analytics.ScreenAnalytics.track(this, "image_crop", javaClass.simpleName)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.screen_back_enter, R.anim.screen_back_exit)
    }

    private lateinit var crop: CropImageView
    private var output: Uri? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background_crop)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.screen_open_enter, R.anim.screen_open_exit)
        val source = intent.data ?: run { finish(); return }
        crop = findViewById(R.id.background_crop_image)
        crop.setOnCropImageCompleteListener(this)
        crop.setImageUriAsync(source)
        output = savedInstanceState?.getString("crop_output")?.let(Uri::parse)
        findViewById<View>(R.id.background_crop_back).setOnClickListener { finish() }
        findViewById<View>(R.id.background_crop_reset).setOnClickListener { crop.resetCropRect() }
        findViewById<View>(R.id.background_crop_rotate).setOnClickListener { crop.rotateImage(90) }
        findViewById<View>(R.id.background_crop_apply).setOnClickListener { button ->
            runCatching {
                val folder = File(filesDir, "cropped_backgrounds").apply { mkdirs() }
                val file = File(folder, "${UUID.randomUUID()}.png")
                output = FileProvider.getUriForFile(this, "$packageName.backgrounds", file)
                button.isEnabled = false
                crop.croppedImageAsync(saveCompressFormat = Bitmap.CompressFormat.PNG,
                    reqWidth = 2048, reqHeight = 2048, customOutputUri = output)
            }.onFailure {
                button.isEnabled = true
                Toast.makeText(this, "Unable to crop this image. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("crop_output", output?.toString())
        super.onSaveInstanceState(outState)
    }
    override fun onCropImageComplete(view: CropImageView, result: CropImageView.CropResult) {
        if (isFinishing || isDestroyed) return
        findViewById<View>(R.id.background_crop_apply).isEnabled = true
        val uri = result.uriContent
        if (!result.isSuccessful || uri == null) {
            Toast.makeText(this, "Unable to crop this image. Please try again.", Toast.LENGTH_LONG).show()
            return
        }
        setResult(RESULT_OK, Intent().setData(uri))
        finish()
    }
    override fun onDestroy() {
        if (::crop.isInitialized) crop.setOnCropImageCompleteListener(null)
        super.onDestroy()
    }
}
