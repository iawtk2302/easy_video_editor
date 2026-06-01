import Foundation
import Flutter

class GetVideoMetadataCommand: Command {
    func execute(call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let videoPath = args["videoPath"] as? String else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing required argument: videoPath", details: nil))
            return
        }
        
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let metadata = try VideoUtils.getVideoMetadata(videoPath: videoPath)
                DispatchQueue.main.async {
                    result(metadata)
                }
            } catch {
                DispatchQueue.main.async {
                    result(FlutterError(code: "METADATA_ERROR", message: "Failed to get video metadata: \(error.localizedDescription)", details: nil))
                }
            }
        }
    }
}
