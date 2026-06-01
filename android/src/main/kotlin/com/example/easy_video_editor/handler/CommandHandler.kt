package com.example.easy_video_editor.handler

import android.content.Context
import com.example.easy_video_editor.command.Command
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

enum class MethodName(val methodName: String) {
    TRIM_VIDEO("trimVideo"),
    MERGE_VIDEOS("mergeVideos"),
    EXTRACT_AUDIO("extractAudio"),
    ADJUST_VIDEO_SPEED("adjustVideoSpeed"),
    REMOVE_AUDIO("removeAudio"),
    CROP_VIDEO("cropVideo"),
    ROTATE_VIDEO("rotateVideo"),
    GENERATE_THUMBNAIL("generateThumbnail"),
    GET_FRAME("getFrame"),
    COMPRESS_VIDEO("compressVideo"),
    CANCEL_OPERATION("cancelOperation"),
    GET_VIDEO_METADATA("getVideoMetadata"),
    FLIP_VIDEO("flipVideo");

    companion object {
        fun fromString(method: String): MethodName? {
            return values().find { it.methodName == method }
        }
    }
}

class CommandHandler (private val context: Context) {
    private val handlers: Map<MethodName, Command> = mapOf(
        MethodName.TRIM_VIDEO to TrimVideoCommand(context),
        MethodName.MERGE_VIDEOS to MergeVideosCommand(context),
        MethodName.EXTRACT_AUDIO to ExtractAudioCommand(context),
        MethodName.ADJUST_VIDEO_SPEED to AdjustVideoSpeedCommand(context),
        MethodName.REMOVE_AUDIO to RemoveAudioCommand(context),
        MethodName.CROP_VIDEO to CropVideoCommand(context),
        MethodName.ROTATE_VIDEO to RotateVideoCommand(context),
        MethodName.GENERATE_THUMBNAIL to GenerateThumbnailCommand(context),
        MethodName.GET_FRAME to GetFrameCommand(context),
        MethodName.COMPRESS_VIDEO to CompressVideoCommand(context),
        MethodName.CANCEL_OPERATION to CancelOperationCommand(),
        MethodName.GET_VIDEO_METADATA to GetVideoMetadataCommand(context),
        MethodName.FLIP_VIDEO to FlipVideoCommand(context),
    )

    fun handleCommand(call: MethodCall, result: MethodChannel.Result) {
        val methodName = MethodName.fromString(call.method)
        if (methodName == null) {
            result.notImplemented()
            return
        }
        handlers[methodName]?.execute(call, result) ?: result.notImplemented()
    }
}
