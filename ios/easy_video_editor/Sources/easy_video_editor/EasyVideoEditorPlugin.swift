import Flutter
import UIKit

public class EasyVideoEditorPlugin: NSObject, FlutterPlugin, FlutterStreamHandler {
  private let commandHandler = CommandHandler()
  private var progressEventSink: FlutterEventSink?
  
  public static func register(with registrar: FlutterPluginRegistrar) {
    // Method channel for regular method calls
    let channel = FlutterMethodChannel(name: "easy_video_editor", binaryMessenger: registrar.messenger())
    
    // Event channel for progress updates
    let eventChannel = FlutterEventChannel(name: "easy_video_editor/progress", binaryMessenger: registrar.messenger())
    
    let instance = EasyVideoEditorPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
    eventChannel.setStreamHandler(instance)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
     commandHandler.handleCommand(call, result: result)
  }
  
  // MARK: - FlutterStreamHandler
  
  public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
    progressEventSink = events
    ProgressManager.shared.setEventSink(events)
    return nil
  }
  
  public func onCancel(withArguments arguments: Any?) -> FlutterError? {
    progressEventSink = nil
    ProgressManager.shared.setEventSink(nil)
    return nil
  }
}
