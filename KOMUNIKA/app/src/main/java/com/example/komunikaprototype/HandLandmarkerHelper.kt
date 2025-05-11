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
    // This parameter is for detection; however, for certain categories we override it to 1.
    private var detectionNumHands: Int = DEFAULT_NUM_HANDS,
    private var currentDelegate: Int = DELEGATE_CPU,
    private var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    private val context: Context,
    private val handLandmarkerHelperListener: LandmarkerListener? = null
) {

    // MODEL_NUM_HANDS is always 2 since the model input expects keypoints for two hands.
    private val MODEL_NUM_HANDS = 2

    private var handLandmarker: HandLandmarker? = null
    private var modelInterpreters: List<Interpreter> = emptyList()
    private var labels: List<String> = emptyList()
    private var numClasses: Int = 0 // The dynamic number of classes

    init {
        setupHandLandmarker()
    }

    /**
     * Loads the models and labels dynamically based on the provided category.
     *
     * For the five categories ("A-D, F-M, U", "E, N-T, V-Z", "1-5", "6-10", "20-100"),
     * we detect only one hand (detectionNumHands = 1) but still feed the model with
     * an input tensor for two hands (the second half will be zeros).
     */
    @Synchronized
    fun loadModelsAndLabels(category: String) {
        // Unload any previous models to avoid memory leaks.
        unloadModels()

        // Set the number of hands to detect based on the category.
        if (category in listOf("alphabets", "1-5", "6-10", "20-100")) {
            detectionNumHands = 1
        } else {
            detectionNumHands = DEFAULT_NUM_HANDS
        }
        // Reinitialize the hand landmarker so that the new detectionNumHands is applied.
        clearHandLandmarker()
        setupHandLandmarker()

        Log.d(TAG, "Loading models for category: $category")
        val tfliteOptions = Interpreter.Options().apply {
            addDelegate(FlexDelegate()) // Support custom TensorFlow operations.
        }

        try {
            when (category) {
                "alphabets" -> {
                    val modelFile = "alphabets_model_fold_1.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )
                    labels = loadLabels("merged_alphabets_labels.txt")
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
                "verbs" -> {
                    val modelFile = "Verbs_model_fold_2.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )
                    labels = loadLabels("verbs_labels.txt")
                    numClasses = labels.size
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
                "calendar" -> { // Not yet implemented
                    val modelFile = "Calendar_model_fold_2.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )
                    labels = loadLabels("months_labels.txt")
                    numClasses = labels.size
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
                "time" -> {
                    val modelFile = "Time_model_fold_2.tflite"
                    modelInterpreters = listOf(
                        Interpreter(loadModelFile(modelFile), tfliteOptions).also {
                            Log.d(TAG, "Loading model file: $modelFile")
                        }
                    )
                    labels = loadLabels("time_labels.txt")
                    numClasses = labels.size
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
                // Use detectionNumHands (which may be 1 for some categories) for detection.
                .setNumHands(detectionNumHands)
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
        // 1) Always notify the listener
        handLandmarkerHelperListener?.onResults(ResultBundle(result, input.height, input.width))

        // 2) If there are landmarks, do inference
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
        }
    }

    private fun returnLivestreamError(error: RuntimeException) {
        handLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred", ERROR_CODE
        )
    }

    /**
     * Extracts keypoints from the detection result.
     *
     * Regardless of how many hands are detected (detectionNumHands may be 1),
     * the returned FloatArray is always sized for two hands (MODEL_NUM_HANDS).
     * For any undetected hand, its keypoints remain zero.
     */
    private fun extractKeypoints(result: HandLandmarkerResult): FloatArray? {
        if (result.landmarks().isEmpty()) {
            return null
        }
        // Always allocate space for keypoints of two hands.
        val expectedNumKeypoints = NUM_KEYPOINTS_PER_HAND * MODEL_NUM_HANDS
        val keypoints = FloatArray(expectedNumKeypoints) { 0f }
        val landmarks = result.landmarks()

        var index = 0
        // Process detected hands (up to detectionNumHands)
        for (handIndex in 0 until detectionNumHands) {
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
        // The remaining keypoints (if any) for the undetected hand(s) remain zero.
        return keypoints
    }

    /**
     * Runs inference on the collected keypoint sequence.
     *
     * The input tensor is always built with keypoints for two hands.
     */
    @Synchronized
    private fun runInference(keypointSequence: List<FloatArray>): String {
        val input = Array(1) { Array(SEQUENCE_LENGTH) { FloatArray(NUM_KEYPOINTS_PER_HAND * MODEL_NUM_HANDS) } }
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

        // Average the outputs across all model folds.
        val avgOutput = FloatArray(numClasses) { 0f }
        outputs.forEach { output ->
            for (i in output.indices) {
                avgOutput[i] += output[i] / modelInterpreters.size
            }
        }
        Log.d(TAG, "Average output scores: ${avgOutput.joinToString()}")

        val maxIdx = avgOutput.indices.maxByOrNull { avgOutput[it] } ?: -1
        return if (maxIdx >= 0 && labels.isNotEmpty()) {
            Log.d(TAG, "Predicted label: ${labels[maxIdx]}")
            labels[maxIdx]
        } else {
            Log.e(TAG, "Failed to predict label. Returning 'Prediction unavailable'")
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
