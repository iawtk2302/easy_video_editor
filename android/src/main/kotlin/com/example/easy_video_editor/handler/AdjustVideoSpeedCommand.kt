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

class AdjustVideoSpeedCommand(private val context: Context) : Command {
    @UnstableApi
    override fun execute(call: MethodCall, result: MethodChannel.Result) {
        val videoPath = call.argument<String>("videoPath")
        val speedMultiplier = call.argument<Number>("speed")?.toFloat()

        if (videoPath == null || speedMultiplier == null) {
            result.error(
                "INVALID_ARGUMENTS",
                "Missing required arguments: videoPath or speedMultiplier",
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
                val outputPath = VideoUtils.adjustVideoSpeed(
                    context = context,
                    videoPath = videoPath,
                    speedMultiplier = speedMultiplier
                )
                result.success(outputPath)
            } catch (e: Exception) {
                result.error("ADJUST_SPEED_ERROR", e.message, null)
            } finally {
                OperationManager.unregisterOperation(operationId)
                methodScope.cancel()
            }
        }
    }
} 
