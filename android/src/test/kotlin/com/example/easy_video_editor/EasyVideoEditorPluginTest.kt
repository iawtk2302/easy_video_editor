package com.example.easy_video_editor

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import com.example.easy_video_editor.utils.OperationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.mockito.Mockito

/*
 * This demonstrates a simple unit test of the Kotlin portion of this plugin's implementation.
 *
 * Once you have built the plugin's example app, you can run these tests from the command
 * line by running `./gradlew testDebugUnitTest` in the `example/android/` directory, or
 * you can run them directly from IDEs that support JUnit such as Android Studio.
 */

internal class EasyVideoEditorPluginTest {
  @Test
  fun onMethodCall_getPlatformVersion_returnsExpectedValue() {
    val plugin = EasyVideoEditorPlugin()

    val call = MethodCall("getPlatformVersion", null)
    val mockResult: MethodChannel.Result = Mockito.mock(MethodChannel.Result::class.java)
    plugin.onMethodCall(call, mockResult)

    Mockito.verify(mockResult).success("Android " + android.os.Build.VERSION.RELEASE)
  }

  @Test
  fun unregisterOperation_removesCompletedScopeWithoutCancelingIt() {
    OperationManager.cancelAllOperations()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val operationId = OperationManager.generateOperationId()

    OperationManager.registerOperation(operationId, scope)
    OperationManager.unregisterOperation(operationId)

    assertEquals(0, OperationManager.activeOperationCount)
    assertFalse(OperationManager.cancelOperation(operationId))
    scope.cancel()
  }

  @Test
  fun cancelOperation_removesInactiveScope() {
    OperationManager.cancelAllOperations()
    val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    val operationId = OperationManager.generateOperationId()

    OperationManager.registerOperation(operationId, scope)
    scope.cancel()

    assertFalse(OperationManager.cancelOperation(operationId))
    assertEquals(0, OperationManager.activeOperationCount)
  }
}
