package com.example.easy_video_editor.handler

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.example.easy_video_editor.command.Command
import com.example.easy_video_editor.utils.OperationManager
import com.example.easy_video_editor.utils.VideoUtils
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TrimVideoCommand(private val context: Context) : Command {
    @UnstableApi
    override fun execute(call: MethodCall, result: MethodChannel.Result) {
        val videoPath = call.argument<String>("videoPath")
        val startTime = call.argument<Number>("startTimeMs")?.toLong()
        val endTime = call.argument<Number>("endTimeMs")?.toLong()

        if (videoPath == null || startTime == null || endTime == null) {
            result.error(
                "INVALID_ARGUMENTS",
                "Missing required arguments: videoPath, startTimeMs, or endTimeMs",
                null
            )
            return
        }

        // Create a new scope that's tied only to this method call
        val methodScope = CoroutineScope(Dispatchers.Main + Job())
        
        // Register with operation manager for cancellation support
        val operationId = OperationManager.generateOperationId()
        OperationManager.registerOperation(operationId, methodScope)
        
        methodScope.launch {
            try {
                val outputPath = VideoUtils.trimVideo(
                    context = context,
                    videoPath = videoPath,
                    startTimeMs = startTime,
                    endTimeMs = endTime
                )
                result.success(outputPath)
            } catch (e: Exception) {
                // Check if the error is due to user cancellation
                if (e.message?.contains("canceled by user") == true) {
                    // This is a user cancellation, so just return success with a null path
                    result.success(null)
                } else {
                    // This is a genuine error, return it
                    result.error("TRIM_ERROR", e.message, null)
                }
            } finally {
                OperationManager.unregisterOperation(operationId)
                methodScope.cancel()
            }
        }
    }
}
