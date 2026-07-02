package com.tokoku.orgaku

import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.camera.CameraParametersCallback
import android.hardware.Camera
import java.util.Locale

class CustomScannerActivity : AppCompatActivity() {

    private lateinit var capture: CaptureManager
    private lateinit var barcodeScannerView: DecoratedBarcodeView
    private lateinit var tvZoomValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_scanner)

        // 1. Initialize views
        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner)
        tvZoomValue = findViewById(R.id.tvZoomValue)
        val zoomSeekBar = findViewById<SeekBar>(R.id.zoomSeekBar)

        // 2. Initialize CaptureManager
        capture = CaptureManager(this, barcodeScannerView)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()

        // Zoom Logic
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (seekBar == null) return

                // Access the backdoor of ZXing to manipulate camera parameters
                barcodeScannerView.barcodeView.changeCameraParameters { parameters ->
                    if (parameters.isZoomSupported) {
                        val maxZoom = parameters.maxZoom
                        // Calculate the target zoom based on slider progress (0-100)
                        val targetZoom = (progress * maxZoom) / 100
                        parameters.zoom = targetZoom
                    }
                    parameters // Return the modified parameters
                }

                // Update zoom text indicator
                val zoomFactor = 1.0 + (progress / 100.0) * 4.0 // UI estimation (1x to 5x)
                tvZoomValue.text = String.format(Locale.getDefault(), "%.1fx", zoomFactor)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            finish()
        }
    }

    // 3. Lifecycle overrides to prevent crashes and manage camera
    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return barcodeScannerView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }
}
