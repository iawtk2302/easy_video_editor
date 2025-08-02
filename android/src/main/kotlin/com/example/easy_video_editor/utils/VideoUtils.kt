package com.example.easy_video_editor.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.source.UriDataSource
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri
import android.os.Build
import android.util.Size
import android.media.ThumbnailUtils
import kotlin.math.roundToInt

@UnstableApi
class VideoUtils {
    companion object {
        /**
         * Gets metadata information about a video file
         * 
         * @param context Android context
         * @param videoPath Path to the video file
         * @return VideoMetadata object containing video information
         */
        suspend fun getVideoMetadata(context: Context, videoPath: String): VideoMetadata {
            return withContext(Dispatchers.IO) {
                val videoFile = File(videoPath)
                require(videoFile.exists()) { "Video file does not exist" }
                
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(videoPath)
                    
                    // Get basic metadata
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                    
                    // Get title and author (may be null)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) 
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                    
                    // Get rotation
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0

                    // Get date
                    val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)

                    // Get file size
                    val fileSize = videoFile.length()
                    
                    VideoMetadata(
                        duration = duration,
                        width = width,
                        height = height,
                        title = title,
                        author = author,
                        rotation = rotation,
                        fileSize = fileSize,
                        date = date
                    )
                } finally {
                    retriever.release()
                }
            }
        }
        /**
         * Compress a video while maintaining aspect ratio
         * @param context Android context
         * @param videoPath Path to the input video file
         * @param targetHeight Target height for the compressed video (default: 720p)
         * @return Path to the compressed video file
         */
        suspend fun compressVideo(
            context: Context,
            videoPath: String,
            targetHeight: Int = 720, // Default to 720p
        ): String {
            withContext(Dispatchers.IO) {
                require(File(videoPath).exists()) { "Input video file does not exist" }
                require(targetHeight > 0) { "Target height must be positive" }
            }
            
            // Create temp directory if it doesn't exist
            val tempDir: String = context.getExternalFilesDir("easy_video_editor")!!.absolutePath
            val outputFileName = "VID_${SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())}_${videoPath.hashCode()}.mp4"
            val outputPath = "$tempDir${File.separator}$outputFileName"
            val outputFile = File(outputPath)
            if (outputFile.exists()) outputFile.delete()
            
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    // Define video compression strategy based on targetHeight
                    val videoTrackStrategy = DefaultVideoStrategy.atMost(targetHeight).build()
                    
                    // Configure audio strategy - always include audio for the simple version
                    val audioTrackStrategy = DefaultAudioStrategy.builder()
                        .channels(DefaultAudioStrategy.CHANNELS_AS_INPUT)
                        .sampleRate(DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT)
                        .build()
                    
                    // Create data source (no trimming in the simple version)
                    val dataSource = UriDataSource(context, videoPath.toUri())
                    
                    // Create a variable to store the transcode future for cancellation
                    val transcodeFuture = Transcoder.into(outputPath)
                        .addDataSource(dataSource)
                        .setVideoTrackStrategy(videoTrackStrategy)
                        .setAudioTrackStrategy(audioTrackStrategy)
                        .setListener(object : TranscoderListener {
                            override fun onTranscodeProgress(progress: Double) {
                                // Report progress to ProgressManager (0.0 to 1.0)
                                ProgressManager.getInstance().reportProgress(progress)
                            }
                            
                            override fun onTranscodeCompleted(successCode: Int) {
                                if (continuation.isActive) {
                                    // Mark progress as 100% complete
                                    ProgressManager.getInstance().reportProgress(1.0)
                                    
                                    // Return the output path to the caller
                                    continuation.resume(outputPath)
                                }
                            }
                            
                            override fun onTranscodeCanceled() {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        VideoException("Video compression was canceled")
                                    )
                                }
                                // Clean up output file if canceled
                                outputFile.delete()
                            }
                            
                            override fun onTranscodeFailed(exception: Throwable) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        VideoException(
                                            "Failed to compress video: ${exception.message}",
                                            exception
                                        )
                                    )
                                }
                                // Clean up output file if failed
                                outputFile.delete()
                            }
                        }).transcode()
                    
                    // Set up cancellation handling
                    continuation.invokeOnCancellation {
                        transcodeFuture.cancel(true)
                        outputFile.delete()
                    }
                }
            }
        }
        suspend fun trimVideo(
                context: Context,
                videoPath: String,
                startTimeMs: Long,
                endTimeMs: Long
        ): String {
            // File operations on IO thread
            withContext(Dispatchers.IO) {
                require(startTimeMs >= 0) { "Start time must be non-negative" }
                require(endTimeMs > startTimeMs) { "End time must be greater than start time" }
                require(File(videoPath).exists()) { "Input video file does not exist" }
            }

            val outputFile =
                    withContext(Dispatchers.IO) {
                        File(context.cacheDir, "trimmed_video_${System.currentTimeMillis()}.mp4")
                                .apply { if (exists()) delete() }
                    }

            // Transformer operations on Main thread
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val mediaItem =
                            MediaItem.Builder()
                                    .setUri(Uri.fromFile(File(videoPath)))
                                    .setClippingConfiguration(
                                            MediaItem.ClippingConfiguration.Builder()
                                                    .setStartPositionMs(startTimeMs)
                                                    .setEndPositionMs(endTimeMs)
                                                    .build()
                                    )
                                    .build()

                    val transformer =
                            Transformer.Builder(context)
                                    .addListener(
                                            object : Transformer.Listener {
                                                override fun onCompleted(
                                                        composition: Composition,
                                                        exportResult: ExportResult
                                                ) {
                                                    if (continuation.isActive) {
                                                        continuation.resume(outputFile.absolutePath)
                                                    }
                                                }

                                                override fun onError(
                                                        composition: Composition,
                                                        exportResult: ExportResult,
                                                        exportException: ExportException
                                                ) {
                                                    if (continuation.isActive) {
                                                        continuation.resumeWithException(
                                                                VideoException(
                                                                        "Failed to trim video: ${exportException.message}",
                                                                        exportException
                                                                )
                                                        )
                                                    }
                                                    outputFile.delete()
                                                }
                                            }
                                    )
                                    .build()

                    transformer.start(mediaItem, outputFile.absolutePath)
                    
                    // Set up progress tracking
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post(
                        object : Runnable {
                            override fun run() {
                                val progressState = transformer.getProgress(progressHolder)
                                // Report progress to ProgressManager
                                // Send progress updates more frequently
                                // Always report progress as long as we have a valid progress value
                                if (progressHolder.progress >= 0) {
                                    // Report progress to ProgressManager
                                    ProgressManager.getInstance().reportProgress(progressHolder.progress / 100.0)
                                }
                                
                                // Continue polling if the transformer has started (simplified condition)
                                // The original Media3 example uses this condition, which might be more reliable
                                if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                    mainHandler.postDelayed(this, 200) // Update every 200ms - better balance
                                }
                            }
                        }
                    )

                    continuation.invokeOnCancellation {
                        transformer.cancel()
                        outputFile.delete()
                    }
                }
            }
        }

        suspend fun mergeVideos(context: Context, videoPaths: List<String>): String {
            // File operations on IO thread
            withContext(Dispatchers.IO) {
                require(videoPaths.isNotEmpty()) { "Video paths list cannot be empty" }
                videoPaths.forEachIndexed { index, path ->
                    require(File(path).exists()) {
                        "Video file at index $index does not exist: $path"
                    }
                }
            }

            val outputFile = withContext(Dispatchers.IO) {
                File(context.cacheDir, "merged_video_${System.currentTimeMillis()}.mp4")
                    .apply { if (exists()) delete() }
            }

            // Transformer operations on Main thread
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val editedMediaItems =
                        videoPaths.map { path ->
                            EditedMediaItem.Builder(
                                MediaItem.fromUri(Uri.fromFile(File(path)))
                            )
                            .build()
                        }

                    val sequence = EditedMediaItemSequence(editedMediaItems)

                    val composition = Composition.Builder(listOf(sequence)).build()

                    val transformer =
                        Transformer.Builder(context)
                            .addListener(
                                object : Transformer.Listener {
                                    override fun onCompleted(
                                        composition: Composition,
                                        exportResult: ExportResult
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resume(
                                                outputFile.absolutePath
                                            )
                                        }
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                VideoException(
                                                    "Failed to merge videos: ${exportException.message}",
                                                    exportException
                                                )
                                            )
                                        }
                                        outputFile.delete()
                                    }
                                }
                            )
                            .build()

                    transformer.start(composition, outputFile.absolutePath)
                    
                    // Set up progress tracking
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post(
                        object : Runnable {
                            override fun run() {
                                val progressState = transformer.getProgress(progressHolder)
                                // Report progress to ProgressManager
                                // Send progress updates more frequently
                                // Always report progress as long as we have a valid progress value
                                if (progressHolder.progress >= 0) {
                                    // Report progress to ProgressManager
                                    ProgressManager.getInstance().reportProgress(progressHolder.progress / 100.0)
                                }
                                
                                // Continue polling if the transformer has started (simplified condition)
                                // The original Media3 example uses this condition, which might be more reliable
                                if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                    mainHandler.postDelayed(this, 200) // Update every 200ms - better balance
                                }
                            }
                        }
                    )

                    continuation.invokeOnCancellation {
                        transformer.cancel()
                        outputFile.delete()
                    }
                    }
                }
            }

        suspend fun extractAudio(context: Context, videoPath: String): String {
            // File operations on IO thread
            withContext(Dispatchers.IO) {
                require(File(videoPath).exists()) { "Input video file does not exist" }
            }

            val outputFile =
                    withContext(Dispatchers.IO) {
                        File(context.cacheDir, "extracted_audio_${System.currentTimeMillis()}.aac")
                                .apply { if (exists()) delete() }
                    }

            // Transformer operations on Main thread
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val mediaItem =
                            MediaItem.Builder().setUri(Uri.fromFile(File(videoPath))).build()

                    val editedMediaItem =
                            EditedMediaItem.Builder(mediaItem).setRemoveVideo(true).build()

                    val transformer =
                            Transformer.Builder(context)
                                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                                    .addListener(
                                            object : Transformer.Listener {
                                                override fun onCompleted(
                                                        composition: Composition,
                                                        exportResult: ExportResult
                                                ) {
                                                    if (continuation.isActive) {
                                                        continuation.resume(outputFile.absolutePath)
                                                    }
                                                }

                                                override fun onError(
                                                        composition: Composition,
                                                        exportResult: ExportResult,
                                                        exportException: ExportException
                                                ) {
                                                    if (continuation.isActive) {
                                                        continuation.resumeWithException(
                                                                VideoException(
                                                                        "Failed to extract audio: ${exportException.message}",
                                                                        exportException
                                                                )
                                                        )
                                                    }
                                                    outputFile.delete()
                                                }
                                            }
                                    )
                                    .build()

                    transformer.start(editedMediaItem, outputFile.absolutePath)

                    // Set up progress tracking
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post(
                        object : Runnable {
                            override fun run() {
                                val progressState = transformer.getProgress(progressHolder)
                                // Report progress to ProgressManager
                                // Send progress updates more frequently
                                // Always report progress as long as we have a valid progress value
                                if (progressHolder.progress >= 0) {
                                    // Report progress to ProgressManager
                                    ProgressManager.getInstance().reportProgress(progressHolder.progress / 100.0)
                                }
                                
                                // Continue polling if the transformer has started (simplified condition)
                                // The original Media3 example uses this condition, which might be more reliable
                                if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                    mainHandler.postDelayed(this, 200) // Update every 200ms - better balance
                                }
                            }
                        }
                    )

                    continuation.invokeOnCancellation {
                        transformer.cancel()
                        outputFile.delete()
                    }
                }
            }
        }
        suspend fun adjustVideoSpeed(
            context: Context,
            videoPath: String,
            speedMultiplier: Float
        ): String {
            // File operations on IO thread
            withContext(Dispatchers.IO) {
                require(File(videoPath).exists()) { "Input video file does not exist" }
                require(speedMultiplier > 0) { "Speed multiplier must be positive" }
            }

            val outputFile = withContext(Dispatchers.IO) {
                File(context.cacheDir, "speed_adjusted_video_${System.currentTimeMillis()}.mp4")
                    .apply { if (exists()) delete() }
            }

            // Transformer operations on Main thread
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val mediaItem =
                        MediaItem.Builder().setUri(Uri.fromFile(File(videoPath))).build()

                    val videoEffect = SpeedChangeEffect(speedMultiplier)
                    val audio = SonicAudioProcessor()
                    
                    audio.setSpeed(speedMultiplier)

                    val effects = Effects(listOf(audio), listOf(videoEffect))
                    
                    val editedMediaItem = EditedMediaItem.Builder(mediaItem).setEffects(effects).build()
                    
                    val transformer = Transformer.Builder(context)
                            .addListener(
                                object : Transformer.Listener {
                                    override fun onCompleted(
                                        composition: Composition,
                                        exportResult: ExportResult
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resume(outputFile.absolutePath)
                                        }
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                VideoException(
                                                    "Failed to adjust video speed: ${exportException.message}",
                                                    exportException
                                                )
                                            )
                                        }
                                        outputFile.delete()
                                    }
                                }
                            )
                            .build()
                    
                    transformer.start(editedMediaItem, outputFile.absolutePath)
                    
                    // Set up progress tracking
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post(
                        object : Runnable {
                            override fun run() {
                                val progressState = transformer.getProgress(progressHolder)
                                // Report progress to ProgressManager
                                // Send progress updates more frequently
                                // Always report progress as long as we have a valid progress value
                                if (progressHolder.progress >= 0) {
                                    // Report progress to ProgressManager
                                    ProgressManager.getInstance().reportProgress(progressHolder.progress / 100.0)
                                }
                                
                                // Continue polling if the transformer has started (simplified condition)
                                // The original Media3 example uses this condition, which might be more reliable
                                if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                    mainHandler.postDelayed(this, 200) // Update every 200ms - better balance
                                }
                            }
                        }
                    )
                    
                    continuation.invokeOnCancellation {
                        transformer.cancel()
                        outputFile.delete()
                    }
                }
            }
        }

        suspend fun removeAudioFromVideo(context: Context, videoPath: String): String {
            // File operations on IO thread
            withContext(Dispatchers.IO) {
                require(File(videoPath).exists()) { "Input video file does not exist" }
            }

            val outputFile = withContext(Dispatchers.IO) {
                File(context.cacheDir, "muted_video_${System.currentTimeMillis()}.mp4")
                    .apply { if (exists()) delete() }
            }

            // Transformer operations on Main thread
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val mediaItem =
                        MediaItem.Builder().setUri(Uri.fromFile(File(videoPath))).build()

                    val editedMediaItem =
                        EditedMediaItem.Builder(mediaItem)
                            .setRemoveAudio(true) // Remove audio
                            .build()

                    val transformer =
                        Transformer.Builder(context)
                            .addListener(
                                object : Transformer.Listener {
                                    override fun onCompleted(
                                        composition: Composition,
                                        exportResult: ExportResult
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resume(outputFile.absolutePath)
                                        }
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                VideoException(
                                                    "Failed to remove audio: ${exportException.message}",
                                                    exportException
                                                )
                                            )
                                        }
                                        outputFile.delete()
                                    }
                                }
                            )
                            .build()

                    transformer.start(editedMediaItem, outputFile.absolutePath)

                    // Set up progress tracking
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post(
                        object : Runnable {
                            override fun run() {
                                val progressState = transformer.getProgress(progressHolder)
                                // Report progress to ProgressManager
                                // Send progress updates more frequently
                                // Always report progress as long as we have a valid progress value
                                if (progressHolder.progress >= 0) {
                                    // Report progress to ProgressManager
                                    ProgressManager.getInstance().reportProgress(progressHolder.progress / 100.0)
                                }
                                
                                // Continue polling if the transformer has started (simplified condition)
                                // The original Media3 example uses this condition, which might be more reliable
                                if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                    mainHandler.postDelayed(this, 200) // Update every 200ms - better balance
                                }
                            }
                        }
                    )

                    continuation.invokeOnCancellation {
                        transformer.cancel()
                        outputFile.delete()
                    }
                }
            }
        }

        suspend fun cropVideo(
            context: Context,
            videoPath: String,
            aspectRatio: String
        ): String {
            // File operations on IO thread
            withContext(Dispatchers.IO) {
                require(File(videoPath).exists()) { "Input video file does not exist" }
                require(aspectRatio.matches(Regex("\\d+:\\d+"))) { "Aspect ratio must be in format 'width:height' (e.g., '16:9')" }
            }

            val outputFile = withContext(Dispatchers.IO) {
                File(context.cacheDir, "cropped_video_${System.currentTimeMillis()}.mp4")
                    .apply { if (exists()) delete() }
            }

            // Get video dimensions
            val retriever = MediaMetadataRetriever()
            val (videoWidth, videoHeight) = withContext(Dispatchers.IO) {
                try {
                    retriever.setDataSource(videoPath)
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloat() ?: 0f
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloat() ?: 0f
                    width to height
                } finally {
                    retriever.release()
                }
            }

            // Calculate crop dimensions based on aspect ratio
            val (targetWidth, targetHeight) = aspectRatio.split(":").map { it.toFloat() }
            val targetAspectRatio = targetWidth / targetHeight
            val videoAspectRatio = videoWidth / videoHeight

            // Calculate scale factors to achieve the desired aspect ratio through scaling
            val (scaleWidth, scaleHeight) = if (videoAspectRatio > targetAspectRatio) {
                // Video is wider than target, scale height up to crop sides
                val scale = videoAspectRatio / targetAspectRatio
                1f to scale
            } else {
                // Video is taller than target, scale width up to crop top/bottom
                val scale = targetAspectRatio / videoAspectRatio
                scale to 1f
            }

            // Transformer operations on Main thread
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val mediaItem =
                        MediaItem.Builder().setUri(Uri.fromFile(File(videoPath))).build()

                    val editedMediaItem =
                        EditedMediaItem.Builder(mediaItem)
                            .setEffects(
                                Effects(
                                    emptyList(),
                                    listOf(
                                        ScaleAndRotateTransformation.Builder()
                                            .setScale(scaleWidth, scaleHeight)
                                            .build()
                                    )
                                )
                            )
                            .build()

                    val transformer =
                        Transformer.Builder(context)
                            .addListener(
                                object : Transformer.Listener {
                                    override fun onCompleted(
                                        composition: Composition,
                                        exportResult: ExportResult
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resume(outputFile.absolutePath)
                                        }
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                VideoException(
                                                    "Failed to crop video: ${exportException.message}",
                                                    exportException
                                                )
                                            )
                                        }
                                        outputFile.delete()
                                    }
                                }
                            )
                            .build()

                    transformer.start(editedMediaItem, outputFile.absolutePath)

                    // Set up progress tracking
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post(
                        object : Runnable {
                            override fun run() {
                                val progressState = transformer.getProgress(progressHolder)
                                // Report progress to ProgressManager
                                // Send progress updates more frequently
                                // Always report progress as long as we have a valid progress value
                                if (progressHolder.progress >= 0) {
                                    // Report progress to ProgressManager
                                    ProgressManager.getInstance().reportProgress(progressHolder.progress / 100.0)
                                }
                                
                                // Continue polling if the transformer has started (simplified condition)
                                // The original Media3 example uses this condition, which might be more reliable
                                if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                    mainHandler.postDelayed(this, 200) // Update every 200ms - better balance
                                }
                            }
                        }
                    )

                    continuation.invokeOnCancellation {
                        transformer.cancel()
                        outputFile.delete()
                    }
                }
            }
        }

        suspend fun rotateVideo(context: Context, videoPath: String, rotationDegrees: Float): String {
            // File operations on IO thread
            withContext(Dispatchers.IO) {
                require(File(videoPath).exists()) { "Input video file does not exist" }
                require(rotationDegrees % 90 == 0f) { "Rotation must be a multiple of 90 degrees" }
            }

            val outputFile = withContext(Dispatchers.IO) {
                File(context.cacheDir, "rotated_video_${System.currentTimeMillis()}.mp4")
                    .apply { if (exists()) delete() }
            }

            // Transformer operations on Main thread
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val mediaItem =
                        MediaItem.Builder().setUri(Uri.fromFile(File(videoPath))).build()

                    val effects =
                        Effects(
                            emptyList(),
                            listOf(
                                ScaleAndRotateTransformation.Builder()
                                    .setRotationDegrees(rotationDegrees)
                                    .build()
                            )
                        )

                    val editedMediaItem =
                        EditedMediaItem.Builder(mediaItem).setEffects(effects).build()

                    val transformer =
                        Transformer.Builder(context)
                            .addListener(
                                object : Transformer.Listener {
                                    override fun onCompleted(
                                        composition: Composition,
                                        exportResult: ExportResult
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resume(outputFile.absolutePath)
                                        }
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                VideoException(
                                                    "Failed to rotate video: ${exportException.message}",
                                                    exportException
                                                )
                                            )
                                        }
                                        outputFile.delete()
                                    }
                                }
                            )
                            .build()

                    transformer.start(editedMediaItem, outputFile.absolutePath)

                    // Set up progress tracking
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post(
                        object : Runnable {
                            override fun run() {
                                val progressState = transformer.getProgress(progressHolder)
                                // Report progress to ProgressManager
                                // Send progress updates more frequently
                                // Always report progress as long as we have a valid progress value
                                if (progressHolder.progress >= 0) {
                                    // Report progress to ProgressManager
                                    ProgressManager.getInstance().reportProgress(progressHolder.progress / 100.0)
                                }
                                
                                // Continue polling if the transformer has started (simplified condition)
                                // The original Media3 example uses this condition, which might be more reliable
                                if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                    mainHandler.postDelayed(this, 200) // Update every 200ms - better balance
                                }
                            }
                        }
                    )

                    continuation.invokeOnCancellation {
                        transformer.cancel()
                        outputFile.delete()
                    }
                }
            }
        }

        suspend fun generateThumbnail(
            context: Context,
            videoPath: String,
            positionMs: Long,
            width: Int? = null,
            height: Int? = null,
            quality: Int = 80
        ): String = withContext(Dispatchers.IO) {
            require(File(videoPath).exists()) { "Video file does not exist" }
            require(positionMs >= 0) { "Position must be non-negative" }
            require(quality in 0..100) { "Quality must be between 0 and 100" }
            width?.let { require(it > 0) { "Width must be positive" } }
            height?.let { require(it > 0) { "Height must be positive" } }

            val retriever = MediaMetadataRetriever()
            return@withContext try {
                // ── choose best method to obtain (possibly pre-scaled) Bitmap ─────────
                val bitmap: Bitmap = when {
                    // API 27+  and  at least ONE dimension ⇒ let retriever scale for us
                    Build.VERSION.SDK_INT >= 27 && (width != null || height != null) -> {
                        val primary = width ?: height!!
                        val secondary = Int.MAX_VALUE          // so only 'primary' is respected
                        val retriever = MediaMetadataRetriever().apply { setDataSource(videoPath) }
                        retriever.getScaledFrameAtTime(
                            positionMs * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            primary, secondary
                        ) ?: throw VideoException("Failed to generate thumbnail")
                    }

                    // ── every other case (stretch OR old API) → full frame
                    else -> {
                        val retriever = MediaMetadataRetriever().apply { setDataSource(videoPath) }
                        retriever.getFrameAtTime(
                            positionMs * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        ) ?: throw VideoException("Failed to generate thumbnail")
                    }
                }

                // ── post-process if we still need to scale / stretch ──────────────────
                val finalBitmap: Bitmap = when {
                    // Both given → always stretch / fit EXACTLY WxH
                    width != null && height != null ->
                        Bitmap.createScaledBitmap(bitmap, width, height, /*filter*/ false)

                    // Only width → compute height to keep aspect (when platform didn’t already do it)
                    width != null && (Build.VERSION.SDK_INT < 27 || height == null) -> {
                        val newH = (bitmap.height * width / bitmap.width.toFloat()).roundToInt()
                        Bitmap.createScaledBitmap(bitmap, width, newH, false)
                    }

                    // Only height → compute width to keep aspect
                    height != null && (Build.VERSION.SDK_INT < 27 || width == null) -> {
                        val newW = (bitmap.width * height / bitmap.height.toFloat()).roundToInt()
                        Bitmap.createScaledBitmap(bitmap, newW, height, false)
                    }

                    else -> bitmap   // no resize needed
                }

                val outputFile = File(context.cacheDir, "thumbnail_${System.currentTimeMillis()}.jpg").apply {
                    if (exists()) delete()
                }

                FileOutputStream(outputFile).use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }

                if (finalBitmap != bitmap) finalBitmap.recycle()
                if (bitmap != finalBitmap) bitmap.recycle()

                outputFile.absolutePath
            } catch (e: OutOfMemoryError) {
                throw VideoException("Out of memory while generating thumbnail", e)
            } catch (e: Exception) {
                throw VideoException("Error generating thumbnail: ${e.message}", e)
            } finally {
                retriever.release()
            }
        }


        suspend fun flipVideo(context: Context, videoPath: String, flipDirection: String): String {
            // File operations on IO thread
            withContext(Dispatchers.IO) {
                require(File(videoPath).exists()) { "Input video file does not exist" }
                require(flipDirection.isNotEmpty()) { "Direction must not empty" }
            }

            val outputFile = withContext(Dispatchers.IO) {
                File(context.cacheDir, "flip_video_${System.currentTimeMillis()}.mp4")
                    .apply { if (exists()) delete() }
            }

            // Transformer operations on Main thread
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val mediaItem =
                        MediaItem.Builder().setUri(Uri.fromFile(File(videoPath))).build()

                    val flipEffect = when (flipDirection.lowercase()) {
                        "horizontal" -> ScaleAndRotateTransformation.Builder().setScale(-1f, 1f).build()
                        "vertical" -> ScaleAndRotateTransformation.Builder().setScale(1f, -1f).build()
                        else -> ScaleAndRotateTransformation.Builder().setScale(1f, 1f).build()
                    }

                    val effects =
                        Effects(
                            emptyList(),
                            listOf(
                                flipEffect
                            )
                        )

                    val editedMediaItem =
                        EditedMediaItem.Builder(mediaItem).setEffects(effects).build()

                    val transformer =
                        Transformer.Builder(context)
                            .addListener(
                                object : Transformer.Listener {
                                    override fun onCompleted(
                                        composition: Composition,
                                        exportResult: ExportResult
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resume(outputFile.absolutePath)
                                        }
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                VideoException(
                                                    "Failed to rotate video: ${exportException.message}",
                                                    exportException
                                                )
                                            )
                                        }
                                        outputFile.delete()
                                    }
                                }
                            )
                            .build()

                    transformer.start(editedMediaItem, outputFile.absolutePath)

                    // Set up progress tracking
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post(
                        object : Runnable {
                            override fun run() {
                                val progressState = transformer.getProgress(progressHolder)
                                // Report progress to ProgressManager
                                // Send progress updates more frequently
                                // Always report progress as long as we have a valid progress value
                                if (progressHolder.progress >= 0) {
                                    // Report progress to ProgressManager
                                    ProgressManager.getInstance().reportProgress(progressHolder.progress / 100.0)
                                }

                                // Continue polling if the transformer has started (simplified condition)
                                // The original Media3 example uses this condition, which might be more reliable
                                if (progressState != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                    mainHandler.postDelayed(this, 200) // Update every 200ms - better balance
                                }
                            }
                        }
                    )

                    continuation.invokeOnCancellation {
                        if (transformer.getProgress(progressHolder) != Transformer.PROGRESS_STATE_NOT_STARTED) {
                            transformer.cancel()
                            outputFile.delete()
                        }
                    }
                }
            }
        }
    }
}

class VideoException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/**
 * Data class representing video metadata
 */
data class VideoMetadata(
    val duration: Long, // Duration in milliseconds
    val width: Int,
    val height: Int,
    val title: String?,
    val author: String?,
    val rotation: Int, // 0, 90, 180, or 270 degrees
    val fileSize: Long, // in bytes
    val date: String?
)
