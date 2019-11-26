package com.example.camera

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.graphics.Matrix
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText
import org.w3c.dom.Text
import kotlinx.android.synthetic.main.activity_main.view.*
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// This is an arbitrary number we are using to keep track of the permission request
// Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts.
private const val REQUEST_CODE_PERMISSIONS = 10
// This is an array of all the permission specified in the manifest.
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)



class MainActivity : AppCompatActivity(), LifecycleOwner {

    public var textShow:MutableList<String> = mutableListOf<String>()
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        label = findViewById(R.id.text)
        viewFinder = findViewById(R.id.view_finder)

        // Request camera permissions
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
//            != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)
//        } else {
//            viewFinder.post{startCamera()}
//        }
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

//
// Every time the provided texture view changes, recompute layout
//        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
//            updateTransform()
//        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var viewFinder: TextureView

    private fun startCamera() {
        // TODO: Implement CameraX operations
        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetResolution(Size(640, 480))
        }.build()


        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }


        //instantiate the ImageAnalysis

        // Setup image analysis pipeline that computes average pixel luminance
        val analyzerConfig = ImageAnalysisConfig.Builder().apply{
            val analyzerThread = HandlerThread("labelAnalysis").apply{
                start()
            }
            // In our analysis, we care more about the latest image than
            // analyzing *every* image
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()

        // Build the image analysis use case and instantiate our analyzer
        val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(executor, MLAnalyzer())
        }

        //To capture photos, when the capture button is pressed,
        // we need to update the startCamera() method
        // Create configuration object for the image capture use case
        //remove
//        val imageCaptureConfig = ImageCaptureConfig.Builder()
//            .apply {
//                // We don't set a resolution for image capture; instead, we
//                // select a capture mode which will infer the appropriate
//                // resolution based on aspect ration and requested mode
//                setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
//            }.build()
//
//        // Build the image capture use case and attach button click listener
//        val imageCapture = ImageCapture(imageCaptureConfig)
//        findViewById<ImageButton>(R.id.capture_button).setOnClickListener {
//            val file = File(externalMediaDirs.first(),
//                "${System.currentTimeMillis()}.jpg")
//
//            imageCapture.takePicture(file, executor,
//                object : ImageCapture.OnImageSavedListener {
//                    override fun onError(
//                        imageCaptureError: ImageCapture.ImageCaptureError,
//                        message: String,
//                        exc: Throwable?
//                    ) {
//                        val msg = "Photo capture failed: $message"
//                        Log.e("CameraXApp", msg, exc)
//                        viewFinder.post {
//                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                        }
//                    }
//
//                    override fun onImageSaved(file: File) {
//                        val msg = "Photo capture succeeded: ${file.absolutePath}"
//                        Log.d("CameraXApp", msg)
//                        viewFinder.post {
//                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                        }
//                    }
//                })
//        }



        // Bind use cases to lifecycle
        CameraX.bindToLifecycle(this, preview, analyzerUseCase)
    }

    private fun updateTransform() {
        // TODO: Implement camera viewfinder transformations
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when(viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix)
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private inner class MLAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L
        //set rotation for firebase
        private fun degreesTOFirebaseRotation(degree: Int): Int = when(degree){
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
        }
        /**
         * Helper extension function used to extract a byte array from an
         * image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy, rotationDegrees: Int) {
            val currentTimestamp = System.currentTimeMillis()
            // Calculate the average luma no more often than every second
//            if (currentTimestamp - lastAnalyzedTimestamp >=
//                TimeUnit.SECONDS.toMillis(1)) {
                // Since format in ImageAnalysis is YUV, image.planes[0]
                // contains the Y (luminance) plane
                val y = image.planes[0]
                val u = image.planes[1]
                val v = image.planes[2]
                val Yb = y.buffer.remaining()
                val Ub = u.buffer.remaining()
                val Vb = v.buffer.remaining()
                val data = ByteArray(Yb + Ub + Vb)
                y.buffer.get(data, 0 , Yb)
                u.buffer.get(data, Yb, Ub)
                v.buffer.get(data, Yb + Ub, Vb)

                val buffer = image.planes[0].buffer
                // Extract image data from callback object
                //val data = buffer.toByteArray()

                // Convert the data into an array of pixel values
                val pixels = data.map { it.toInt() and 0xFF }

                // Compute average luminance for the image
                val luma = pixels.average()

                // Log the new luma value
                Log.d("CameraXApp", "Average luminosity: $luma")
                // Update timestamp of last analyzed frame
                lastAnalyzedTimestamp = currentTimestamp

            val metadata = FirebaseVisionImageMetadata.Builder()
                .setWidth(image.width) // 480x360 is typically sufficient for
                .setHeight(image.height) // image recognition
                .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_YV12)
                .setRotation(rotationDegrees)
                .build()

            val image = FirebaseVisionImage.fromByteArray(data, metadata)
            val detector = FirebaseVision.getInstance()
                .onDeviceTextRecognizer
            detector.processImage(image)
                .addOnSuccessListener { firebaseVisionText: FirebaseVisionText->
                    val resultText = firebaseVisionText.textBlocks
                    for(block in resultText) {
                        val blockText = block.text
                        Log.d("MLApp", "Text: $blockText")
                        textShow.add(blockText)
                        var t: TextView = findViewById(R.id.text)
                        t.text = blockText

                        }
                }

                    .addOnFailureListener{e ->

            }
        }
    }


    //convert the rotation to one of ML Kit's ROTATION constants
    private class YourImageAnalyzer : ImageAnalysis.Analyzer {
        private fun degreesToFirebaseRotation(degrees: Int): Int = when(degrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
        }

        override fun analyze(imageProxy: ImageProxy?, degrees: Int) {
            val mediaImage = imageProxy?.image
            val imageRotation = degreesToFirebaseRotation(degrees)
            if (mediaImage != null) {
                val image = FirebaseVisionImage.fromMediaImage(mediaImage, imageRotation)
                // Pass image to an ML Kit Vision API

//                val metadata = FirebaseVisionImageMetadata.Builder()
//                    .setWidth(480) // 480x360 is typically sufficient for
//                    .setHeight(360) // image recognition
//                    .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
//                    .setRotation(imageRotation)
//                    .build()

                //Get an instance of FirebaseVisionTextRecognizer (to use the on-device model)
                val detector = FirebaseVision.getInstance()
                    .onDeviceTextRecognizer

                //pass the image to the processImage method
                val result = detector.processImage(image)
                    .addOnSuccessListener { firebaseVisionText ->
                        // Task completed successfully
                        processTextRecognitionResult(firebaseVisionText)
                    }
                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        e.printStackTrace();
                    }
            }
        }
        private fun processTextRecognitionResult(texts: FirebaseVisionText) : String {
            val blocks = texts.textBlocks
            if(blocks.size == 0) {
                return ""
            }
            return texts.text;

    }


}




}
