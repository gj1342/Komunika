package com.example.komunikaprototype

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class HandLandmarkerHelper(
    private var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    private var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    private var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    private var maxNumHands: Int = DEFAULT_NUM_HANDS,
    private var currentDelegate: Int = DELEGATE_CPU,
    private var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    private val context: Context,
    private val handLandmarkerHelperListener: LandmarkerListener? = null
) {

    private var handLandmarker: HandLandmarker? = null
    private var modelInterpreters: List<Interpreter> = emptyList()
    private var labels: List<String> = emptyList()
    private var numClasses: Int = 0 // The dynamic number of classes

    init {
        setupHandLandmarker()
    }

    // Function to load model folds and labels dynamically based on the provided category
    @Synchronized
    fun loadModelsAndLabels(category: String) {
        // Ensure proper unloading of previous models to avoid memory leaks
        unloadModels()

        Log.d(TAG, "Loading models for category: $category")
        val tfliteOptions = Interpreter.Options().apply {
            addDelegate(FlexDelegate()) // Add FlexDelegate to support custom TensorFlow operations
        }

        try {
            when (category) {
                "A-D, F-M, U" -> {
                    // Load the specific model for alphabets category (fold 2 only)
                    val modelFile = "Alphabets_1_model_fold_2.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("alphabets_1_labels.txt")
                    numClasses = labels.size
                }
                "E, N-T, V-Z" -> {
                    val modelFile = "Alphabets_2_model_fold_2.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("alphabets_2_labels.txt")
                    numClasses = labels.size
                }
                "1-5" -> {
                    val modelFile = "1-5_model_fold_2.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("1-5_labels.txt")
                    numClasses = labels.size
                }
                "6-10" -> {
                    val modelFile = "6-10_model_fold_2.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("6-10_labels.txt")
                    numClasses = labels.size
                }
                "20-100" -> {
                    val modelFile = "20-100_model_fold_2.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("20-100_labels.txt")
                    numClasses = labels.size
                }
                "greetings" -> {
                    val modelFile = "Greetings_model_fold_3.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("greetings_labels.txt")
                    numClasses = labels.size
                }
                "responses" -> {
                    val modelFile = "Responses_model_fold_5.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("responses_labels.txt")
                    numClasses = labels.size
                }
                "family" -> {
                    val modelFile = "Family_model_fold_3.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("family_labels.txt")
                    numClasses = labels.size
                }
                "colors" -> {
                    val modelFile = "Colors_model_fold_3.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("colors_labels.txt")
                    numClasses = labels.size
                }
                "pronouns" -> {
                    val modelFile = "Pronouns_model_fold_1.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("pronouns_labels.txt")
                    numClasses = labels.size
                }
                "nouns" -> {
                    val modelFile = "Nouns_model_fold_4.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("nouns_labels.txt")
                    numClasses = labels.size
                }
                "verbs" -> { //wala pa

                }
                "school" -> {
                    val modelFile = "School_model_fold_3.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("school_labels.txt")
                    numClasses = labels.size
                }
                "JanToJune" -> { //wala pa

                }
                "JulyToDec" -> { //wala pa

                }
                "weeks" -> {
                    val modelFile = "Weeks_model_fold_5.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("weeks_labels.txt")
                    numClasses = labels.size
                }
                "time" -> { //wala pa

                }
                "questions" -> {
                    val modelFile = "Questions_model_fold_5.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("questions_labels.txt")
                    numClasses = labels.size
                }
                "phrases" -> {
                    val modelFile = "Phrases_model_fold_4.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )

                    labels = loadLabels("phrases_labels.txt")
                    numClasses = labels.size
                }
                else -> {
                    Log.e(TAG, "Unknown model category: $category")
                    return
                }
            }

            // Log success for loaded models and labels
            Log.d(TAG, "Successfully loaded ${modelInterpreters.size} models for category: $category")
            Log.d(TAG, "Successfully loaded ${labels.size} labels for category: $category")

            labels.forEachIndexed { index, label -> Log.d(TAG, "Label $index: $label") }

        } catch (e: Exception) {
            Log.e(TAG, "Error while loading models and labels: ${e.message}", e)
        }
    }

    private val keypointSequenceBuffer = mutableListOf<FloatArray>()

    fun clearHandLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }

    private fun setupHandLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()

        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
        }

        baseOptionBuilder.setModelAssetPath(MP_HAND_LANDMARKER_TASK)

        if (runningMode == RunningMode.LIVE_STREAM && handLandmarkerHelperListener == null) {
            throw IllegalStateException(
                "handLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
            )
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(minHandDetectionConfidence)
                .setMinTrackingConfidence(minHandTrackingConfidence)
                .setMinHandPresenceConfidence(minHandPresenceConfidence)
                .setNumHands(maxNumHands)
                .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder.setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            handLandmarkerHelperListener?.onError("Hand Landmarker failed to initialize. See error logs for details", ERROR_CODE)
            Log.e(TAG, "MediaPipe failed to load the task with error: ${e.message}")
        }
    }

    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f)
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        try {
            handLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error during async detection: ${e.message}", e)
        }
    }

    private fun returnLivestreamResult(result: HandLandmarkerResult, input: MPImage) {
        if (result.landmarks().isNotEmpty()) {
            val keypoints = extractKeypoints(result)
            if (keypoints != null) {
                keypointSequenceBuffer.add(keypoints)

                if (keypointSequenceBuffer.size == SEQUENCE_LENGTH) {
                    val prediction = runInference(keypointSequenceBuffer)
                    keypointSequenceBuffer.clear()
                    handLandmarkerHelperListener?.onPrediction(prediction)
                }
            }

            handLandmarkerHelperListener?.onResults(ResultBundle(result, input.height, input.width))
        }
    }

    private fun returnLivestreamError(error: RuntimeException) {
        handLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred", ERROR_CODE
        )
    }

    private fun extractKeypoints(result: HandLandmarkerResult): FloatArray? {
        if (result.landmarks().isEmpty()) {
            return null
        }

        val keypoints = FloatArray(NUM_KEYPOINTS) { 0f } // Initialize with zero
        val landmarks = result.landmarks()

        var index = 0
        for (handIndex in 0 until maxNumHands) {
            if (handIndex < landmarks.size) {
                for (landmark in landmarks[handIndex]) {
                    keypoints[index++] = landmark.x()
                    keypoints[index++] = landmark.y()
                    keypoints[index++] = landmark.z()
                }
            } else {
                repeat(NUM_KEYPOINTS_PER_HAND) {
                    keypoints[index++] = 0f
                }
            }
        }
        return keypoints
    }

    @Synchronized
    private fun runInference(keypointSequence: List<FloatArray>): String {
        return try {
            val input = Array(1) { Array(SEQUENCE_LENGTH) { FloatArray(NUM_KEYPOINTS) } }
            for (i in keypointSequence.indices) {
                input[0][i] = keypointSequence[i]
            }

            Log.d(TAG, "Running inference on keypoint sequence of size: ${keypointSequence.size}")

            val outputs = modelInterpreters.mapIndexed { index, interpreter ->
                val output = Array(1) { FloatArray(numClasses) }
                interpreter.run(input, output)
                Log.d(TAG, "Inference output from model $index: ${output[0].joinToString()}")
                output[0]
            }

            // Average the outputs across all model folds
            val avgOutput = FloatArray(numClasses) { 0f }
            outputs.forEach { output ->
                for (i in output.indices) {
                    avgOutput[i] += output[i] / modelInterpreters.size
                }
            }

            // Log average output scores
            Log.d(TAG, "Average output scores: ${avgOutput.joinToString()}")

            val maxIdx = avgOutput.indices.maxByOrNull { avgOutput[it] } ?: -1
            if (maxIdx >= 0 && labels.isNotEmpty()) {
                Log.d(TAG, "Predicted label: ${labels[maxIdx]}")
                labels[maxIdx]
            } else {
                Log.e(TAG, "Failed to predict label. Returning 'Prediction unavailable'")
                "Prediction unavailable"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference: ${e.message}", e)
            "Prediction unavailable"
        }
    }

    private fun loadLabels(labelFileName: String): List<String> {
        val labels = mutableListOf<String>()
        try {
            val inputStream = context.assets.open(labelFileName)
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.forEachLine { labels.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading labels file: ${e.message}")
        }
        return labels
    }

    private fun loadModelFile(modelFileName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelFileName)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @Synchronized
    fun unloadModels() {
        if (modelInterpreters.isNotEmpty()) {
            modelInterpreters.forEach { interpreter ->
                interpreter.close()
            }
            modelInterpreters = emptyList()
            Log.d(TAG, "Models unloaded successfully.")
        }
    }

    companion object {
        const val TAG = "HandLandmarkerHelper"
        private const val MP_HAND_LANDMARKER_TASK = "hand_landmarker.task"
        private const val SEQUENCE_LENGTH = 60
        private const val NUM_KEYPOINTS_PER_HAND = 63
        private const val NUM_KEYPOINTS = NUM_KEYPOINTS_PER_HAND * 2

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_HANDS = 2
        const val ERROR_CODE = 1
    }

    data class ResultBundle(
        val results: HandLandmarkerResult,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = ERROR_CODE)
        fun onResults(resultBundle: ResultBundle)
        fun onPrediction(prediction: String)
    }
}
