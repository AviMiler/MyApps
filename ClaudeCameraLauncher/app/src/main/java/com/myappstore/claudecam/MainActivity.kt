package com.myappstore.claudecam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * One screen, two states:
 *  - Camera: a live CameraX preview fills the screen with a single shutter
 *    button. Capturing writes straight to a file and jumps to Edit —
 *    there is no OS camera app involved, so there is no confirm/retake
 *    screen to dismiss first.
 *  - Edit: the captured photo (crop / marker / blackout / rotate) with a
 *    floating toolbar, and two small round corner buttons — retake
 *    (back to the live camera) and share (straight into Claude).
 */
class MainActivity : AppCompatActivity() {

    private val claudePackage = "com.anthropic.claude"

    private lateinit var cameraPreview: PreviewView
    private lateinit var shutterButton: ImageButton
    private lateinit var editContainer: FrameLayout
    private lateinit var drawView: DrawView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var toolRow: LinearLayout
    private lateinit var cropConfirmRow: LinearLayout

    private lateinit var cropButton: ImageButton
    private lateinit var markerButton: ImageButton
    private lateinit var blackoutButton: ImageButton

    private var imageCapture: ImageCapture? = null
    private var selectedColorSwatch: View? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview = findViewById(R.id.cameraPreview)
        shutterButton = findViewById(R.id.shutterButton)
        editContainer = findViewById(R.id.editContainer)
        drawView = findViewById(R.id.drawView)
        cropOverlay = findViewById(R.id.cropOverlay)
        toolRow = findViewById(R.id.toolRow)
        cropConfirmRow = findViewById(R.id.cropConfirmRow)
        cropButton = findViewById(R.id.cropButton)
        markerButton = findViewById(R.id.markerButton)
        blackoutButton = findViewById(R.id.blackoutButton)

        shutterButton.setOnClickListener { capturePhoto() }
        findViewById<ImageButton>(R.id.retakeButton).setOnClickListener { showCamera() }
        findViewById<ImageButton>(R.id.shareButton).setOnClickListener { shareToClaude() }

        findViewById<ImageButton>(R.id.rotateButton).setOnClickListener { drawView.rotate90() }
        findViewById<ImageButton>(R.id.undoButton).setOnClickListener { drawView.undo() }
        findViewById<ImageButton>(R.id.clearButton).setOnClickListener { drawView.clearStrokes() }

        cropButton.setOnClickListener { enterCropMode() }
        markerButton.setOnClickListener { selectMode(DrawMode.MARKER, markerButton) }
        blackoutButton.setOnClickListener { selectMode(DrawMode.BLACKOUT, blackoutButton) }

        findViewById<ImageButton>(R.id.cropConfirmButton).setOnClickListener { confirmCrop() }
        findViewById<ImageButton>(R.id.cropCancelButton).setOnClickListener { exitCropMode() }

        setupColorSwatch(R.id.colorRed, Color.RED)
        setupColorSwatch(R.id.colorYellow, Color.YELLOW, isDefault = true)
        setupColorSwatch(R.id.colorGreen, Color.GREEN)
        setupColorSwatch(R.id.colorWhite, Color.WHITE)

        requestCameraPermission()
    }

    private fun setupColorSwatch(viewId: Int, color: Int, isDefault: Boolean = false) {
        val swatch = findViewById<View>(viewId)
        swatch.setOnClickListener {
            drawView.markerColor = color
            selectMode(DrawMode.MARKER, markerButton)
            selectSwatch(swatch)
        }
        if (isDefault) selectSwatch(swatch)
    }

    private fun selectSwatch(swatch: View) {
        selectedColorSwatch?.scaleX = 1f
        selectedColorSwatch?.scaleY = 1f
        swatch.scaleX = 1.3f
        swatch.scaleY = 1.3f
        selectedColorSwatch = swatch
    }

    private fun selectMode(mode: DrawMode, activeButton: ImageButton) {
        drawView.mode = mode
        markerButton.setBackgroundResource(if (mode == DrawMode.MARKER) R.drawable.bg_icon_button_selected else android.R.color.transparent)
        blackoutButton.setBackgroundResource(if (mode == DrawMode.BLACKOUT) R.drawable.bg_icon_button_selected else android.R.color.transparent)
    }

    // --- Camera ---

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }
                val capture = ImageCapture.Builder().build()
                imageCapture = capture
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            } catch (e: Exception) {
                Toast.makeText(this, "לא ניתן לפתוח את המצלמה", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showCamera() {
        editContainer.visibility = View.GONE
        cameraPreview.visibility = View.VISIBLE
        shutterButton.visibility = View.VISIBLE
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        capture.targetRotation = cameraPreview.display?.rotation ?: 0

        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val photoFile = File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onPhotoCaptured(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "הצילום נכשל", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun onPhotoCaptured(file: File) {
        val bitmap = decodeSampledBitmap(file)
        if (bitmap == null) {
            Toast.makeText(this, "לא ניתן לטעון את התמונה", Toast.LENGTH_SHORT).show()
            return
        }
        drawView.setPhoto(bitmap)
        selectMode(DrawMode.NONE, markerButton)
        exitCropMode()

        cameraPreview.visibility = View.GONE
        shutterButton.visibility = View.GONE
        editContainer.visibility = View.VISIBLE
    }

    private fun decodeSampledBitmap(file: File): Bitmap? {
        val maxDimension = 2048
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    // --- Crop ---

    private fun enterCropMode() {
        drawView.mode = DrawMode.NONE
        toolRow.visibility = View.GONE
        cropConfirmRow.visibility = View.VISIBLE
        cropOverlay.startCrop(drawView.imageBounds)
    }

    private fun exitCropMode() {
        cropOverlay.visibility = View.GONE
        cropConfirmRow.visibility = View.GONE
        toolRow.visibility = View.VISIBLE
    }

    private fun confirmCrop() {
        drawView.applyCrop(cropOverlay.currentRect())
        exitCropMode()
    }

    // --- Share ---

    private fun shareToClaude() {
        val flattened = drawView.renderFlattened()
        if (flattened == null) {
            Toast.makeText(this, "אין תמונה לשיתוף", Toast.LENGTH_SHORT).show()
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
