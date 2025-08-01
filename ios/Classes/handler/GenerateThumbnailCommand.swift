import Flutter
import AVFoundation
import Foundation

class GenerateThumbnailCommand: Command {
    func execute(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let arguments = call.arguments as? [String: Any],
              let videoPath = arguments["videoPath"] as? String,
              let positionMs = arguments["positionMs"] as? NSNumber,
              let quality = arguments["quality"] as? NSNumber else {
            result(FlutterError(
                code: "INVALID_ARGUMENTS",
                message: "Missing required arguments: videoPath, positionMs, or quality",
                details: nil
            ))
            return
        }

        let width  = (arguments["width"]  as? NSNumber)?.intValue
        let height = (arguments["height"] as? NSNumber)?.intValue        
        
        let operationId = OperationManager.shared.generateOperationId()
        
        lazy var workItem: DispatchWorkItem = DispatchWorkItem { 
            if workItem.isCancelled {
                DispatchQueue.main.async {
                    result(nil)
                }
                return
            }

            do {
                let outputPath = try VideoUtils.generateThumbnail(
                    videoPath: videoPath,
                    positionMs: positionMs.int64Value,
                    width: width,
                    height: height,
                    quality: quality.intValue,
                    workItem: workItem
                )

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

            // Cancel operation when completed
            OperationManager.shared.cancelOperation(operationId)
        }

        // Register operation
        OperationManager.shared.registerOperation(id: operationId, workItem: workItem)

        // Start the operation
        DispatchQueue.global(qos: .userInitiated).async(execute: workItem)
    }
}

