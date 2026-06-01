import Flutter
import AVFoundation
import Foundation

class CompressVideoCommand: Command {
    func execute(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let arguments = call.arguments as? [String: Any],
              let videoPath = arguments["videoPath"] as? String,
              let targetHeight = arguments["targetHeight"] as? Int else {
            result(FlutterError(
                code: "INVALID_ARGUMENTS",
                message: "Missing required arguments: videoPath or targetHeight",
                details: nil
            ))
            return
        }
        
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
                let outputPath = try VideoUtils.compressVideo(
                    videoPath: videoPath,
                    targetHeight: targetHeight,
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

        // Register work item with operation manager for possible cancellation
        OperationManager.shared.registerOperation(id: operationId, workItem: workItem)

        // Start the operation
        DispatchQueue.global(qos: .userInitiated).async(execute: workItem)
    }
}
