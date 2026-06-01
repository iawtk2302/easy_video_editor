import Flutter
import AVFoundation

class MergeVideosCommand: Command {
    func execute(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let arguments = call.arguments as? [String: Any],
              let videoPaths = arguments["videoPaths"] as? [String], !videoPaths.isEmpty else {
            result(FlutterError(
                code: "INVALID_ARGUMENTS",
                message: "Missing or empty videoPaths",
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
                let outputPath = try VideoUtils.mergeVideos(videoPaths: videoPaths, workItem: workItem)

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

        // Start the operation
        DispatchQueue.global(qos: .userInitiated).async(execute: workItem)
    }
}
