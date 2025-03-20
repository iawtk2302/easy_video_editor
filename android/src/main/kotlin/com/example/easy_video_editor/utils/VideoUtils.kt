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
import androidx.media3.effect.Presentation
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
                    
                    // Get file size
                    val fileSize = videoFile.length()
                    
                    VideoMetadata(
                        duration = duration,
                        width = width,
                        height = height,
                        title = title,
                        author = author,
                        rotation = rotation,
                        fileSize = fileSize
                    )
                } finally {
                    retriever.release()
                }
            }
        }
        
        
        suspend fun compressVideo(
            context: Context,
            videoPath: String,
            targetHeight: Int = 720, // Default to 720p
            bitrateMultiplier: Float = 0.5f // Reduce bitrate to 50% of original by default
        ): String {
            withContext(Dispatchers.IO) {
                require(File(videoPath).exists()) { "Input video file does not exist" }
                require(bitrateMultiplier in 0.1f..1.0f) { "Bitrate multiplier must be between 0.1 and 1.0" }
                require(targetHeight > 0) { "Target height must be positive" }
            }

            val outputFile = withContext(Dispatchers.IO) {
                File(context.cacheDir, "compressed_video_${System.currentTimeMillis()}.mp4")
                    .apply { if (exists()) delete() }
            }

            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val mediaItem = MediaItem.fromUri(Uri.fromFile(File(videoPath)))
                    
                    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                        .setEffects(Effects(
                             emptyList(),
                            listOf(Presentation.createForHeight(targetHeight))
                        ))
                        .build()

                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
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
                                                "Failed to compress video: ${exportException.message}",
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

                    continuation.invokeOnCancellation {
                        transformer.cancel()
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
                            .setRemoveAudio(true) // Xoá âm thanh
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
        ): String =
            withContext(Dispatchers.IO) {
                require(File(videoPath).exists()) { "Video file does not exist" }
                require(positionMs >= 0) { "Position must be non-negative" }
                require(quality in 0..100) { "Quality must be between 0 and 100" }
                width?.let { require(it > 0) { "Width must be positive" } }
                height?.let { require(it > 0) { "Height must be positive" } }

                val retriever = MediaMetadataRetriever()
                return@withContext try {
                    retriever.setDataSource(videoPath)

                    // Get video duration to validate position
                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                        ?: throw VideoException("Could not determine video duration")
                    require(positionMs <= durationMs) { "Position exceeds video duration" }

                    val bitmap =
                        retriever.getFrameAtTime(
                            positionMs * 1000, // Convert to microseconds
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                            ?: throw VideoException("Failed to generate thumbnail")

                    val scaledBitmap =
                        if (width != null && height != null) {
                            bitmap.scale(width, height)
                        } else {
                            bitmap
                        }

                    val outputFile =
                        File(context.cacheDir, "thumbnail_${System.currentTimeMillis()}.jpg")
                            .apply { if (exists()) delete() } // Delete if exists

                    FileOutputStream(outputFile).use { out ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    }

                    if (scaledBitmap != bitmap) scaledBitmap.recycle()
                    bitmap.recycle()

                    outputFile.absolutePath
                } catch (e: Exception) {
                    throw VideoException("Error generating thumbnail: ${e.message}", e)
                } finally {
                    retriever.release()
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
    val fileSize: Long // in bytes
)
