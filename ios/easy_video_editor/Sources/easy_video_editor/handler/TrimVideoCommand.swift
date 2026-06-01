import Flutter
import AVFoundation
import Foundation

class TrimVideoCommand: Command {
    func execute(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let arguments = call.arguments as? [String: Any],
              let videoPath = arguments["videoPath"] as? String,
              let startTime = arguments["startTimeMs"] as? NSNumber,
              let endTime = arguments["endTimeMs"] as? NSNumber else {
            result(FlutterError(
                code: "INVALID_ARGUMENTS",
                message: "Missing required arguments: videoPath, startTimeMs, or endTimeMs",
                details: nil
            ))
            return
        }
        
        let startTimeMs = startTime.int64Value
        let endTimeMs = endTime.int64Value

        // Create operation ID
        let operationId = OperationManager.shared.generateOperationId()

        lazy var workItem: DispatchWorkItem = DispatchWorkItem {
            // Check if operation was canceled before starting
            if workItem.isCancelled {
                DispatchQueue.main.async {
                    result(nil)
                }
                return
            }

            do {
                let outputPath = try VideoUtils.trimVideo(
                    videoPath: videoPath,
                    startTimeMs: startTimeMs,
                    endTimeMs: endTimeMs,
                    workItem: workItem
                )

                // Check if operation was canceled after processing
                if workItem.isCancelled {
                    try? FileManager.default.removeItem(atPath: outputPath)
                    DispatchQueue.main.async {
                        result(nil)
                    }
                } else {
                    DispatchQueue.main.async {
                        result(outputPath)
                    }
                }
            } catch {
                // Silently handle errors without showing error message
                DispatchQueue.main.async {
                    result(nil)
                }
            }

            OperationManager.shared.unregisterOperation(operationId)
        }

        // Register workItem to be able to cancel
        OperationManager.shared.registerOperation(id: operationId, workItem: workItem)

        // Run the operation
        DispatchQueue.global(qos: .userInitiated).async(execute: workItem)
    }
}
