package com.myappstore.claudecam

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream

/**
 * Single screen: tap to open the camera, annotate the photo with finger
 * drawing, then a single button shares the flattened image straight into
 * the Claude app via Android's share sheet (ACTION_SEND, package-targeted).
 */
class MainActivity : AppCompatActivity() {

    private val claudePackage = "com.anthropic.claude"

    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var drawView: DrawView
    private lateinit var toolbar: LinearLayout

    private var capturedPhotoUri: Uri? = null
    private var pendingCameraUri: Uri? = null

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                pendingCameraUri?.let { onPhotoCaptured(it) }
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        drawView = findViewById(R.id.drawView)
        toolbar = findViewById(R.id.toolbar)

        findViewById<Button>(R.id.takePhotoButton).setOnClickListener { requestCamera() }
        findViewById<ImageButton>(R.id.retakeButton).setOnClickListener { requestCamera() }
        findViewById<ImageButton>(R.id.rotateButton).setOnClickListener { drawView.rotate90() }
        findViewById<ImageButton>(R.id.undoButton).setOnClickListener { drawView.undo() }
        findViewById<ImageButton>(R.id.clearButton).setOnClickListener { drawView.clearStrokes() }
        findViewById<Button>(R.id.shareButton).setOnClickListener { shareToClaude() }

        findViewById<android.view.View>(R.id.colorRed).setOnClickListener { drawView.setColor(Color.RED) }
        findViewById<android.view.View>(R.id.colorYellow).setOnClickListener { drawView.setColor(Color.YELLOW) }
        findViewById<android.view.View>(R.id.colorWhite).setOnClickListener { drawView.setColor(Color.WHITE) }
    }

    private fun requestCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val photoFile = File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun onPhotoCaptured(uri: Uri) {
        capturedPhotoUri = uri
        val bitmap = decodeSampledBitmap(uri)
        if (bitmap == null) {
            Toast.makeText(this, "לא ניתן לטעון את התמונה", Toast.LENGTH_SHORT).show()
            return
        }
        drawView.setPhoto(bitmap)
        emptyStateLayout.visibility = android.view.View.GONE
        drawView.visibility = android.view.View.VISIBLE
        toolbar.visibility = android.view.View.VISIBLE
    }

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        val maxDimension = 2048
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun shareToClaude() {
        val flattened = drawView.renderFlattened()
        if (flattened == null) {
            Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_SHORT).show()
            return
        }

        val shareFile = File(File(cacheDir, "images").apply { mkdirs() }, "share_${System.currentTimeMillis()}.jpg")
        FileOutputStream(shareFile).use { out ->
            flattened.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        val shareUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", shareFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val claudeIntent = Intent(intent).setPackage(claudePackage)
        if (claudeIntent.resolveActivity(packageManager) != null) {
            startActivity(claudeIntent)
        } else {
            // Claude isn't installed (or doesn't register a matching share target) — fall back
            // to the normal chooser so the user can still pick something, or install Claude.
            startActivity(Intent.createChooser(intent, getString(R.string.share_to_claude)))
        }
    }
}
