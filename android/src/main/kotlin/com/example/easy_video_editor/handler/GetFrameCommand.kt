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

class GetFrameCommand(private val context: Context) : Command {
    @UnstableApi
    override fun execute(call: MethodCall, result: MethodChannel.Result) {
        val videoPath = call.argument<String>("videoPath")
        val position = call.argument<Number>("positionMs")?.toLong()
        val width = call.argument<Number>("width")?.toInt()
        val height = call.argument<Number>("height")?.toInt()
        val exactFrame = call.argument<Boolean>("exactFrame") ?: false

        if (videoPath == null || position == null) {
            result.error(
                "INVALID_ARGUMENTS",
                "Missing required arguments: videoPath or position",
                null
            )
            return
        }

        val methodScope = CoroutineScope(Dispatchers.Main + Job())
        val operationId = OperationManager.generateOperationId()
        OperationManager.registerOperation(operationId, methodScope)

        methodScope.launch {
            try {
                result.success(
                    VideoUtils.getFrame(
                        videoPath = videoPath,
                        positionMs = position,
                        width = width,
                        height = height,
                        exactFrame = exactFrame
                    )
                )
            } catch (e: Exception) {
                result.error("FRAME_ERROR", e.message, null)
            } finally {
                OperationManager.unregisterOperation(operationId)
                methodScope.cancel()
            }
        }
    }
}
