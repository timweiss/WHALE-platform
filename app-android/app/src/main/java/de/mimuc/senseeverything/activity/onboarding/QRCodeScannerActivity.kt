package de.mimuc.senseeverything.activity.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.logging.WHALELog
import java.util.concurrent.Executors

/**
 * Activity that uses CameraX + ML Kit bundled barcode scanning to scan QR codes.
 * Camera permission is requested only within this Activity (one-time, activity-scoped).
 *
 * Returns result with:
 * - EXTRA_STUDY_KEY: The parsed study key from the QR code
 * - EXTRA_SOURCE: The source parameter (defaults to "qr_code")
 */
class QRCodeScannerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STUDY_KEY = "study_key"
        const val EXTRA_SOURCE = "source"
    }

    private var previewView: PreviewView? = null
    private var hasScanned = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            WHALELog.w("QRCodeScannerActivity", "Camera permission denied")
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        previewView = findViewById(R.id.preview_view)

        // Setup cancel button
        findViewById<Button>(R.id.cancel_button).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        // Check camera permission and request if needed
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView?.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        QRCodeAnalyzer { studyKey, source ->
                            if (!hasScanned) {
                                hasScanned = true
                                WHALELog.i("QRCodeScannerActivity", "QR Code detected: studyKey=$studyKey, source=$source")
                                returnResult(studyKey, source)
                            }
                        }
                    )
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                WHALELog.e("QRCodeScannerActivity", "Camera binding failed: ${e.message}")
                setResult(RESULT_CANCELED)
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun returnResult(studyKey: String, source: String) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_STUDY_KEY, studyKey)
            putExtra(EXTRA_SOURCE, source)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /**
     * Image analyzer that uses ML Kit bundled barcode scanning to detect and decode QR codes.
     */
    private class QRCodeAnalyzer(
        private val onQRCodeDetected: (studyKey: String, source: String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()
        private var lastProcessedTime = 0L
        private val processingInterval = 1000L // Process at most once per second

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessedTime < processingInterval) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            when (barcode.valueType) {
                                Barcode.TYPE_URL -> {
                                    barcode.url?.url?.let { url ->
                                        WHALELog.d("QRCodeAnalyzer", "URL detected: $url")
                                        parseOnboardingUrl(url.toUri())?.let { (studyKey, source) ->
                                            lastProcessedTime = currentTime
                                            onQRCodeDetected(studyKey, source)
                                        }
                                    }
                                }
                                Barcode.TYPE_TEXT -> {
                                    barcode.displayValue?.let { text ->
                                        WHALELog.d("QRCodeAnalyzer", "Text detected: $text")
                                        parseOnboardingUrl(text.toUri())?.let { (studyKey, source) ->
                                            lastProcessedTime = currentTime
                                            onQRCodeDetected(studyKey, source)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        WHALELog.e("QRCodeAnalyzer", "Barcode scanning failed: ${e.message}")
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
