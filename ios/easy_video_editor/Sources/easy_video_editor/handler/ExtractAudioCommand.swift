import Flutter
import AVFoundation
import Foundation

class ExtractAudioCommand: Command {
    func execute(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let arguments = call.arguments as? [String: Any],
              let videoPath = arguments["videoPath"] as? String else {
            result(FlutterError(
                code: "INVALID_ARGUMENTS",
                message: "Missing required argument: videoPath",
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
                let outputPath = try VideoUtils.extractAudio(videoPath: videoPath, workItem: workItem)

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

        // Register operation
        OperationManager.shared.registerOperation(id: operationId, workItem: workItem)

        // Start the operation
        DispatchQueue.global(qos: .userInitiated).async(execute: workItem)
    }
}
