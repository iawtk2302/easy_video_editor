import Flutter
import Foundation

class GetFrameCommand: Command {
    func execute(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let arguments = call.arguments as? [String: Any],
              let videoPath = arguments["videoPath"] as? String,
              let positionMs = arguments["positionMs"] as? NSNumber else {
            result(FlutterError(
                code: "INVALID_ARGUMENTS",
                message: "Missing required arguments: videoPath or positionMs",
                details: nil
            ))
            return
        }

        let width = (arguments["width"] as? NSNumber)?.intValue
        let height = (arguments["height"] as? NSNumber)?.intValue

        let exactFrame: Bool = {
            if let b = arguments["exactFrame"] as? Bool { return b }
            if let n = arguments["exactFrame"] as? NSNumber { return n.boolValue }
            return false
        }()

        let operationId = OperationManager.shared.generateOperationId()

        lazy var workItem: DispatchWorkItem = DispatchWorkItem {
            if workItem.isCancelled {
                DispatchQueue.main.async {
                    result(nil)
                }
                return
            }

            do {
                let frame = try VideoUtils.getFrame(
                    videoPath: videoPath,
                    positionMs: positionMs.int64Value,
                    width: width,
                    height: height,
                    exactFrame: exactFrame
                )

                if workItem.isCancelled {
                    DispatchQueue.main.async {
                        result(nil)
                    }
                    return
                }

                DispatchQueue.main.async {
                    result(frame)
                }
            } catch {
                DispatchQueue.main.async {
                    result(FlutterError(
                        code: "FRAME_ERROR",
                        message: error.localizedDescription,
                        details: nil
                    ))
                }
            }

            OperationManager.shared.unregisterOperation(operationId)
        }

        OperationManager.shared.registerOperation(id: operationId, workItem: workItem)
        DispatchQueue.global(qos: .userInitiated).async(execute: workItem)
    }
}
