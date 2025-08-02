import AVFoundation
import UIKit
import Foundation
import AVKit

enum VideoError: Error {
    case fileNotFound
    case invalidParameters
    case exportFailed(String)
    case thumbnailGenerationFailed
    case invalidAsset
    case invalidTimeRange
    case invalidPath
}

class VideoUtils {
    
    // MARK: - Helper Methods
    private static func exportWithCancellation(exportSession: AVAssetExportSession, workItem: DispatchWorkItem?) throws {
        // Set up periodic cancellation check
        var isCancelled = false
        let checkInterval: TimeInterval = 0.1 // Check every 100ms
        
        // Start export
        exportSession.exportAsynchronously {}
        
        // Wait for export completion or cancellation
        while exportSession.status == .waiting || exportSession.status == .exporting {
            if let workItem = workItem, workItem.isCancelled {
                exportSession.cancelExport()
                isCancelled = true
                break
            }
            
            // Report progress if available
            if exportSession.status == .exporting {
                let progress = exportSession.progress
                // Send progress updates to Flutter
                ProgressManager.shared.reportProgress(Double(progress))
            }
            
            Thread.sleep(forTimeInterval: checkInterval)
        }
        
        // Handle cancellation
        if isCancelled {
            if let outputURL = exportSession.outputURL {
                try? FileManager.default.removeItem(at: outputURL)
            }
            throw VideoError.exportFailed("Export cancelled")
        }
        
        // Check export status
        if let error = exportSession.error {
            throw VideoError.exportFailed(error.localizedDescription)
        }
        
        guard exportSession.status == .completed else {
            throw VideoError.exportFailed("Export failed with status: \(exportSession.status.rawValue)")
        }
    }
    
    // MARK: - Trim Video
    static func trimVideo(videoPath: String, startTimeMs: Int64, endTimeMs: Int64, workItem: DispatchWorkItem? = nil) throws -> String {
        let url = URL(fileURLWithPath: videoPath)
        
        let asset = AVAsset(url: url)
        let duration = asset.duration.toMilliseconds
        
        // Validate time range
        guard startTimeMs >= 0,
              endTimeMs > startTimeMs,
              endTimeMs <= duration else {
            throw VideoError.invalidTimeRange
        }
        
        let startTime = startTimeMs.toCMTime
        let endTime = endTimeMs.toCMTime
        let timeRange = CMTimeRange(start: startTime, end: endTime)
        
        // Create export session
        guard let exportSession = AVAssetExportSession(
            asset: asset,
            presetName: AVAssetExportPresetHighestQuality
        ) else {
            throw VideoError.invalidAsset
        }
        
        // Generate output path
        let outputPath = NSTemporaryDirectory() + UUID().uuidString + ".mp4"
        let outputURL = URL(fileURLWithPath: outputPath)
        
        // Configure export session
        exportSession.outputURL = outputURL
        exportSession.outputFileType = .mp4
        exportSession.timeRange = timeRange
        
        // Export with cancellation support
        try exportWithCancellation(exportSession: exportSession, workItem: workItem)
        return outputPath
    }
    
    // MARK: - Merge Videos
    static func mergeVideos(videoPaths: [String], workItem: DispatchWorkItem? = nil) throws -> String {
        guard !videoPaths.isEmpty else {
            throw VideoError.invalidParameters
        }
        
        for path in videoPaths {
            guard FileManager.default.fileExists(atPath: path) else {
                throw VideoError.fileNotFound
            }
        }
        
        let composition = AVMutableComposition()
        guard let compositionVideoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid),
              let compositionAudioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw VideoError.exportFailed("Failed to create composition tracks")
        }
        
        var currentTime = CMTime.zero
        
        for path in videoPaths {
            let asset = AVAsset(url: URL(fileURLWithPath: path))
            let duration = asset.duration
            
            if let videoTrack = asset.tracks(withMediaType: .video).first {
                try compositionVideoTrack.insertTimeRange(
                    CMTimeRange(start: .zero, duration: duration),
                    of: videoTrack,
                    at: currentTime
                )
            }
            
            if let audioTrack = asset.tracks(withMediaType: .audio).first {
                try compositionAudioTrack.insertTimeRange(
                    CMTimeRange(start: .zero, duration: duration),
                    of: audioTrack,
                    at: currentTime
                )
            }
            
            currentTime = CMTimeAdd(currentTime, duration)
        }
        
        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent("merged_video_\(Date().timeIntervalSince1970).mp4")
        
        return try export(composition: composition, outputURL: outputURL, workItem: workItem)
    }
    
    // MARK: - Extract Audio
    static func extractAudio(videoPath: String, workItem: DispatchWorkItem? = nil) throws -> String {
        guard FileManager.default.fileExists(atPath: videoPath) else {
            throw VideoError.fileNotFound
        }
        
        let asset = AVAsset(url: URL(fileURLWithPath: videoPath))
        let composition = AVMutableComposition()
        
        guard let audioTrack = asset.tracks(withMediaType: .audio).first,
              let compositionAudioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw VideoError.exportFailed("Failed to get audio track")
        }
        
        try compositionAudioTrack.insertTimeRange(
            CMTimeRange(start: .zero, duration: asset.duration),
            of: audioTrack,
            at: .zero
        )
        
        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent("extracted_audio_\(Date().timeIntervalSince1970).m4a")
        
        guard let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetAppleM4A) else {
            throw VideoError.invalidAsset
        }
        
        exportSession.outputURL = outputURL
        exportSession.outputFileType = .m4a
        
        try exportWithCancellation(exportSession: exportSession, workItem: workItem)
        return outputURL.path
    }
    
    // MARK: - Adjust Video Speed
    static func adjustVideoSpeed(videoPath: String, speed: Float, workItem: DispatchWorkItem? = nil) throws -> String {
        guard FileManager.default.fileExists(atPath: videoPath) else {
            throw VideoError.fileNotFound
        }
        guard speed > 0 else {
            throw VideoError.invalidParameters
        }
        
        let asset = AVAsset(url: URL(fileURLWithPath: videoPath))
        let composition = AVMutableComposition()
        
        // Create tracks
        guard let compositionVideoTrack = composition.addMutableTrack(
                withMediaType: .video,
                preferredTrackID: kCMPersistentTrackID_Invalid),
              let compositionAudioTrack = composition.addMutableTrack(
                withMediaType: .audio,
                preferredTrackID: kCMPersistentTrackID_Invalid),
              let videoTrack = asset.tracks(withMediaType: .video).first,
              let audioTrack = asset.tracks(withMediaType: .audio).first else {
            throw VideoError.invalidAsset
        }
        
        // Get original duration in milliseconds
        let originalDurationMs = asset.duration.toMilliseconds
        
        // Calculate new duration
        let scaledDurationMs = Int64(Double(originalDurationMs) / Double(speed))
        let timeRange = CMTimeRange(start: .zero, duration: asset.duration)
        
        do {
            // Insert video and audio tracks
            try compositionVideoTrack.insertTimeRange(timeRange, of: videoTrack, at: .zero)
            try compositionAudioTrack.insertTimeRange(timeRange, of: audioTrack, at: .zero)
            
            // Scale tracks to new duration
            compositionVideoTrack.scaleTimeRange(timeRange, toDuration: scaledDurationMs.toCMTime)
            compositionAudioTrack.scaleTimeRange(timeRange, toDuration: scaledDurationMs.toCMTime)
            
            // Export
            let outputPath = NSTemporaryDirectory() + UUID().uuidString + ".mp4"
            let outputURL = URL(fileURLWithPath: outputPath)
            
            guard let exportSession = AVAssetExportSession(
                asset: composition,
                presetName: AVAssetExportPresetHighestQuality
            ) else {
                throw VideoError.invalidAsset
            }
            
            exportSession.outputURL = outputURL
            exportSession.outputFileType = .mp4
            
            try exportWithCancellation(exportSession: exportSession, workItem: workItem)
            return outputPath
        } catch {
            throw VideoError.exportFailed(error.localizedDescription)
        }
    }
    
    // MARK: - Remove Audio
    static func removeAudioFromVideo(videoPath: String, workItem: DispatchWorkItem? = nil) throws -> String {
        guard FileManager.default.fileExists(atPath: videoPath) else {
            throw VideoError.fileNotFound
        }
        
        let asset = AVAsset(url: URL(fileURLWithPath: videoPath))
        let composition = AVMutableComposition()
        
        guard let videoTrack = asset.tracks(withMediaType: .video).first,
              let compositionVideoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw VideoError.exportFailed("Failed to get video track")
        }
        
        try compositionVideoTrack.insertTimeRange(
            CMTimeRange(start: .zero, duration: asset.duration),
            of: videoTrack,
            at: .zero
        )
        
        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent("muted_video_\(Date().timeIntervalSince1970).mp4")
        
        return try export(composition: composition, outputURL: outputURL, workItem: workItem)
    }
    
    // MARK: - Scale Video
    static func scaleVideo(videoPath: String, width: Float, height: Float, workItem: DispatchWorkItem? = nil) throws -> String {
        guard FileManager.default.fileExists(atPath: videoPath) else {
            throw VideoError.fileNotFound
        }
        guard width > 0, height > 0 else {
            throw VideoError.invalidParameters
        }
        
        let asset = AVAsset(url: URL(fileURLWithPath: videoPath))
        let composition = AVMutableComposition()
        let videoComposition = AVMutableVideoComposition()
        
        guard let videoTrack = asset.tracks(withMediaType: .video).first,
              let audioTrack = asset.tracks(withMediaType: .audio).first,
              let compositionVideoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid),
              let compositionAudioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw VideoError.exportFailed("Failed to get tracks")
        }
        
        try compositionVideoTrack.insertTimeRange(
            CMTimeRange(start: .zero, duration: asset.duration),
            of: videoTrack,
            at: .zero
        )
        try compositionAudioTrack.insertTimeRange(
            CMTimeRange(start: .zero, duration: asset.duration),
            of: audioTrack,
            at: .zero
        )
        
        let naturalSize = videoTrack.naturalSize
        let transform = videoTrack.preferredTransform
        
        videoComposition.renderSize = CGSize(
            width: naturalSize.width * CGFloat(width),
            height: naturalSize.height * CGFloat(height)
        )
        videoComposition.frameDuration = CMTime(value: 1, timescale: 30)
        
        let instruction = AVMutableVideoCompositionInstruction()
        instruction.timeRange = CMTimeRange(start: .zero, duration: asset.duration)
        
        let layerInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: compositionVideoTrack)
        layerInstruction.setTransform(transform, at: .zero)
        
        instruction.layerInstructions = [layerInstruction]
        videoComposition.instructions = [instruction]
        
        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent("scaled_video_\(Date().timeIntervalSince1970).mp4")
        
        return try export(composition: composition, outputURL: outputURL, videoComposition: videoComposition, workItem: workItem)
    }
    
    // MARK: - Rotate Video
    static func rotateVideo(videoPath: String, rotationDegrees: Float, workItem: DispatchWorkItem? = nil) throws -> String {
        guard FileManager.default.fileExists(atPath: videoPath) else {
            throw VideoError.fileNotFound
        }
        guard rotationDegrees.truncatingRemainder(dividingBy: 90) == 0 else {
            throw VideoError.invalidParameters
        }

        let asset = AVAsset(url: URL(fileURLWithPath: videoPath))
        let composition = AVMutableComposition()
        let videoComposition = AVMutableVideoComposition()

        guard let videoTrack = asset.tracks(withMediaType: .video).first else {
            throw VideoError.exportFailed("No video track found")
        }

        // Add video track to composition
        guard let compositionVideoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw VideoError.exportFailed("Failed to create composition track")
        }
        try compositionVideoTrack.insertTimeRange(
            CMTimeRange(start: .zero, duration: asset.duration),
            of: videoTrack,
            at: .zero
        )

        // Add audio track if present
        if let audioTrack = asset.tracks(withMediaType: .audio).first,
           let compositionAudioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
            try compositionAudioTrack.insertTimeRange(
                CMTimeRange(start: .zero, duration: asset.duration),
                of: audioTrack,
                at: .zero
            )
        }

        // Get size and transform
        let naturalSize = videoTrack.naturalSize
        let originalTransform = videoTrack.preferredTransform

        // Determine final render size and transform
        let radians = CGFloat(rotationDegrees) * .pi / 180
        
        // Combine the track's preferred transform with the additional rotation
        var transform = originalTransform.concatenating(CGAffineTransform(rotationAngle: radians))
        
        // After rotation the video frame might be shifted out of origin (0,0). Calculate
        // the bounding box of the rotated frame so we can translate it back so that the
        // top-left of the video is at (0,0) and the content fully fits the renderSize.
        let originalRect = CGRect(origin: .zero, size: naturalSize)
        let rotatedRect = originalRect.applying(transform)
        
        // The bounding box can have negative origin values. Translate in by the negative
        // origin to move the video into the positive quadrant.
        let translateX = -rotatedRect.origin.x.rounded(.toNearestOrEven)
        let translateY = -rotatedRect.origin.y.rounded(.toNearestOrEven)
        transform = transform.concatenating(
            CGAffineTransform(translationX: translateX,
                              y: translateY)
        )

        // Final render size
        var finalWidth = Int(abs(rotatedRect.width).rounded())
        var finalHeight = Int(abs(rotatedRect.height).rounded())

        // H.264 requires dimensions to be divisible by 2 – make them even to prevent green edges
        if finalWidth % 2 != 0 { finalWidth += 1 }
        if finalHeight % 2 != 0 { finalHeight += 1 }

        videoComposition.renderSize = CGSize(width: finalWidth, height: finalHeight)

        // Set up video composition parameters
        videoComposition.frameDuration = CMTime(value: 1, timescale: 30)
        videoComposition.renderScale = 1.0

        let instruction = AVMutableVideoCompositionInstruction()
        instruction.timeRange = CMTimeRange(start: .zero, duration: asset.duration)

        let layerInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: compositionVideoTrack)
        layerInstruction.setTransform(transform, at: .zero)

        instruction.layerInstructions = [layerInstruction]
        videoComposition.instructions = [instruction]

        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent("rotated_video_\(Date().timeIntervalSince1970).mp4")

        return try export(composition: composition, outputURL: outputURL, videoComposition: videoComposition, workItem: workItem)
    }
    
    // MARK: - Flip Video
    static func flipVideo(videoPath: String, flipDirection: String, workItem: DispatchWorkItem? = nil) throws -> String {
        // Validate input parameters
        guard FileManager.default.fileExists(atPath: videoPath) else {
            throw VideoError.fileNotFound
        }
        let direction = flipDirection.lowercased()
        guard direction == "horizontal" || direction == "vertical" else {
            throw VideoError.invalidParameters
        }

        let asset = AVAsset(url: URL(fileURLWithPath: videoPath))
        guard let videoTrack = asset.tracks(withMediaType: .video).first else {
            throw VideoError.invalidAsset
        }

        // Create composition and insert video track
        let composition = AVMutableComposition()
        guard let compositionVideoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw VideoError.exportFailed("Failed to create composition video track")
        }
        try compositionVideoTrack.insertTimeRange(
            CMTimeRange(start: .zero, duration: asset.duration),
            of: videoTrack,
            at: .zero
        )

        // Add audio track if present
        if let audioTrack = asset.tracks(withMediaType: .audio).first,
           let compositionAudioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
            try compositionAudioTrack.insertTimeRange(
                CMTimeRange(start: .zero, duration: asset.duration),
                of: audioTrack,
                at: .zero
            )
        }

        // Prepare video composition with flip transform
        let videoComposition = AVMutableVideoComposition()
        videoComposition.frameDuration = CMTime(value: 1, timescale: 30)

        let instruction = AVMutableVideoCompositionInstruction()
        instruction.timeRange = CMTimeRange(start: .zero, duration: asset.duration)

        let layerInstruction = AVMutableVideoCompositionLayerInstruction(assetTrack: compositionVideoTrack)

        let naturalSize = videoTrack.naturalSize

        // Build flip transform
        let flipTransform = direction == "horizontal"
            ? CGAffineTransform(scaleX: -1, y: 1)
            : CGAffineTransform(scaleX: 1, y: -1)

        // Combine original transform with flip
        var transform = videoTrack.preferredTransform.concatenating(flipTransform)

        // Calculate bounding box after transform
        let originalRect = CGRect(origin: .zero, size: naturalSize)
        let flippedRect = originalRect.applying(transform)

        // Translate to ensure video fits the render size
        let translateX = -flippedRect.origin.x.rounded(.toNearestOrEven)
        let translateY = -flippedRect.origin.y.rounded(.toNearestOrEven)
        transform = transform.concatenating(
            CGAffineTransform(translationX: translateX,
                              y: translateY)
        )

        // Final render size
        var finalWidth = Int(abs(flippedRect.width).rounded())
        var finalHeight = Int(abs(flippedRect.height).rounded())

        // H.264 requires dimensions to be divisible by 2 – make them even to prevent green edges
        if finalWidth % 2 != 0 { finalWidth += 1 }
        if finalHeight % 2 != 0 { finalHeight += 1 }

        videoComposition.renderSize = CGSize(width: finalWidth, height: finalHeight)

        layerInstruction.setTransform(transform, at: .zero)
        instruction.layerInstructions = [layerInstruction]
        videoComposition.instructions = [instruction]

        // Output path
        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent("flipped_video_\(Date().timeIntervalSince1970).mp4")

        return try export(composition: composition, outputURL: outputURL, videoComposition: videoComposition, workItem: workItem)
    }
    
    // MARK: - Crop Video
    static func cropVideo(videoPath: String, aspectRatio: String, workItem: DispatchWorkItem? = nil) throws -> String {
        guard FileManager.default.fileExists(atPath: videoPath) else {
            throw VideoError.fileNotFound
        }
        
        // Validate aspect ratio format (e.g., "16:9")
        let components = aspectRatio.split(separator: ":")
        guard components.count == 2,
              let targetWidth = Float(components[0]),
              let targetHeight = Float(components[1]),
              targetWidth > 0, targetHeight > 0 else {
            throw VideoError.invalidParameters
        }
        
        let targetAspectRatio = targetWidth / targetHeight
        
        // Load video asset
        let asset = AVAsset(url: URL(fileURLWithPath: videoPath))
        guard let videoTrack = asset.tracks(withMediaType: .video).first else {
            throw VideoError.invalidAsset
        }
        
        // Get video dimensions
        let videoSize = videoTrack.naturalSize
        let videoAspectRatio = Float(videoSize.width / videoSize.height)
        
        // Calculate crop rectangle
        let cropRect: CGRect
        if videoAspectRatio > targetAspectRatio {
            // Video is wider than target, crop sides
            let newWidth = videoSize.height * CGFloat(targetAspectRatio)
            let x = (videoSize.width - newWidth) / 2
            cropRect = CGRect(x: x, y: 0, width: newWidth, height: videoSize.height)
        } else {
            // Video is taller than target, crop top/bottom
            let newHeight = videoSize.width / CGFloat(targetAspectRatio)
            let y = (videoSize.height - newHeight) / 2
            cropRect = CGRect(x: 0, y: y, width: videoSize.width, height: newHeight)
        }
        
        // Create composition
        let composition = AVMutableComposition()
        guard let compositionVideoTrack = composition.addMutableTrack(
            withMediaType: .video,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) else {
            throw VideoError.exportFailed("Failed to create composition video track")
        }

        // Add audio track if available
        var compositionAudioTrack: AVMutableCompositionTrack?
        if let audioTrack = asset.tracks(withMediaType: .audio).first {
            compositionAudioTrack = composition.addMutableTrack(
                withMediaType: .audio,
                preferredTrackID: kCMPersistentTrackID_Invalid
            )
            try compositionAudioTrack?.insertTimeRange(
                CMTimeRange(start: .zero, duration: asset.duration),
                of: audioTrack,
                at: .zero
            )
        }
        
        // Add video track
        try compositionVideoTrack.insertTimeRange(
            CMTimeRange(start: .zero, duration: asset.duration),
            of: videoTrack,
            at: .zero
        )
        
        // Create video instruction
        let instruction = AVMutableVideoCompositionInstruction()
        instruction.timeRange = CMTimeRange(start: .zero, duration: asset.duration)
        
        // Create layer instruction
        let transformer = AVMutableVideoCompositionLayerInstruction(assetTrack: compositionVideoTrack)
        let transform = videoTrack.preferredTransform
            .concatenating(CGAffineTransform(translationX: -cropRect.origin.x, y: -cropRect.origin.y))
        transformer.setTransform(transform, at: .zero)
        instruction.layerInstructions = [transformer]
        
        // Create video composition
        let videoComposition = AVMutableVideoComposition()
        videoComposition.renderSize = cropRect.size
        videoComposition.frameDuration = CMTime(value: 1, timescale: 30)
        videoComposition.instructions = [instruction]
        
        // Export
        let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent("cropped_video_\(Date().timeIntervalSince1970).mp4")
        
        guard let exportSession = AVAssetExportSession(
            asset: composition,
            presetName: AVAssetExportPresetHighestQuality
        ) else {
            throw VideoError.exportFailed("Failed to create export session")
        }
        
        exportSession.outputURL = outputURL
        exportSession.outputFileType = .mp4
        exportSession.videoComposition = videoComposition
        
        try exportWithCancellation(exportSession: exportSession, workItem: workItem)
        return outputURL.path
    }

    // MARK: - Compress Video
    static func compressVideo(videoPath: String, targetHeight: Int = 720, workItem: DispatchWorkItem? = nil) throws -> String {
        guard FileManager.default.fileExists(atPath: videoPath) else {
            throw VideoError.fileNotFound
        }
        
        let asset = AVAsset(url: URL(fileURLWithPath: videoPath))
        guard asset.tracks(withMediaType: .video).first != nil else {
            throw VideoError.invalidAsset
        }
        
        // Generate output path
        let outputPath = NSTemporaryDirectory() + "compressed_video_\(Date().timeIntervalSince1970).mp4"
        let outputURL = URL(fileURLWithPath: outputPath)
        
        // Select preset based on target height
        let preset = getExportPresetForHeight(targetHeight)
        
        // Create export session
        guard let exportSession = AVAssetExportSession(
            asset: asset,
            presetName: preset
        ) else {
            throw VideoError.invalidAsset
        }
        
        // Configure export session
        exportSession.outputURL = outputURL
        exportSession.outputFileType = .mp4
        
        try exportWithCancellation(exportSession: exportSession, workItem: workItem)
        return outputPath
    }
    
    private static func getExportPresetForHeight(_ height: Int) -> String {
        switch height {
        case ..<480:
            return AVAssetExportPresetLowQuality
        case 480:
            return AVAssetExportPreset640x480
        case 720:
            return AVAssetExportPreset1280x720
        case 1080:
            return AVAssetExportPreset1920x1080
        case 2160:
            return AVAssetExportPreset3840x2160
        default:
            return AVAssetExportPreset1280x720
        }
    }

    // MARK: - Get Video Metadata
    static func getVideoMetadata(videoPath: String) throws -> [String: Any] {
        guard FileManager.default.fileExists(atPath: videoPath) else {
            throw VideoError.fileNotFound
        }

        let url = URL(fileURLWithPath: videoPath)
        let asset = AVAsset(url: url)

        // Load required properties asynchronously
        let durationMs = Int64(asset.duration.seconds * 1000)

        // Get video track for dimensions and orientation
        guard let videoTrack = asset.tracks(withMediaType: .video).first else {
            throw VideoError.invalidAsset
        }

        let naturalSize = videoTrack.naturalSize

        // Get transform for rotation
        let transform = videoTrack.preferredTransform
        let rotation: Int

        // Determine rotation from transform matrix
        if transform.a == 0 && transform.b == 1.0 && transform.c == -1.0 && transform.d == 0 {
            rotation = 90
        } else if transform.a == 0 && transform.b == -1.0 && transform.c == 1.0 && transform.d == 0 {
            rotation = 270
        } else if transform.a == -1.0 && transform.b == 0 && transform.c == 0 && transform.d == -1.0 {
            rotation = 180
        } else {
            rotation = 0
        }

        // Get file attributes for size and creation date
        let fileSize: Int64
        var creationDateString: String? = nil

        do {
            let attributes = try FileManager.default.attributesOfItem(atPath: videoPath)
            fileSize = attributes[.size] as? Int64 ?? 0

            if let creationDate = attributes[.creationDate] as? Date {
                let formatter = ISO8601DateFormatter()
                creationDateString = formatter.string(from: creationDate)
            }
        } catch {
            fileSize = 0
            creationDateString = nil
        }

        // Get metadata items for title and author
        let metadata = asset.commonMetadata
        var title: String? = nil
        var author: String? = nil

        for item in metadata {
            if item.commonKey?.rawValue == "title" {
                title = item.stringValue
            } else if item.commonKey?.rawValue == "creator" {
                author = item.stringValue
            }
        }

        // Build metadata dictionary
        return [
            "duration": durationMs,
            "width": Int(naturalSize.width),
            "height": Int(naturalSize.height),
            "title": title as Any,
            "author": author as Any,
            "rotation": rotation,
            "fileSize": fileSize,
            "date": creationDateString as Any
        ]
    }

    
    // MARK: - Generate Thumbnail
    static func generateThumbnail(videoPath: String, positionMs: Int64, width: Int? = nil, height: Int? = nil, quality: Int = 80, workItem: DispatchWorkItem? = nil) throws -> String {
        // Validate input parameters
        guard FileManager.default.fileExists(atPath: videoPath) else {
            throw VideoError.fileNotFound
        }
        guard quality >= 0 && quality <= 100 else {
            throw VideoError.invalidParameters
        }
        if let width = width {
            guard width > 0 else {
                throw VideoError.invalidParameters
            }
        }
        if let height = height {
            guard height > 0 else {
                throw VideoError.invalidParameters
            }
        }
        
        let asset = AVAsset(url: URL(fileURLWithPath: videoPath))
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true

        // Tell AVFoundation how large the bitmap may be
        if width != nil || height != nil {
            switch (width, height) {
            case let (w?, h?):
                generator.maximumSize = CGSize(width: w, height: h)      // both → bounding box
            case let (w?, nil):
                generator.maximumSize = CGSize(width: CGFloat(w), height: 0.0) // width only
            case let (nil, h?):
                generator.maximumSize = CGSize(width: 0.0, height: CGFloat(h)) // height only
            default: break
            }
        }        
        
        // Convert milliseconds to CMTime
        let time = positionMs.toCMTime
        
        // Validate time range
        let duration = asset.duration.toMilliseconds
        guard positionMs >= 0 && positionMs <= duration else {
            throw VideoError.invalidTimeRange
        }
        
        do {
            let imageRef = try generator.copyCGImage(at: time, actualTime: nil)
            var image = UIImage(cgImage: imageRef)
            
            let outputURL = FileManager.default.temporaryDirectory.appendingPathComponent("thumbnail_\(Date().timeIntervalSince1970).jpg")
            
            guard let data = image.jpegData(compressionQuality: CGFloat(quality) / 100),
                  (try? data.write(to: outputURL)) != nil else {
                throw VideoError.thumbnailGenerationFailed
            }
            
            return outputURL.path
        } catch {
            throw VideoError.thumbnailGenerationFailed
        }
    }
    
    // MARK: - Helper Methods
    private static func export(composition: AVComposition, outputURL: URL, videoComposition: AVVideoComposition? = nil, workItem: DispatchWorkItem? = nil) throws -> String {
        guard let exportSession = AVAssetExportSession(
            asset: composition,
            presetName: AVAssetExportPresetHighestQuality
        ) else {
            throw VideoError.exportFailed("Failed to create export session")
        }
        
        exportSession.outputURL = outputURL
        exportSession.outputFileType = .mp4
        if let videoComposition = videoComposition {
            exportSession.videoComposition = videoComposition
        }
        
        try exportWithCancellation(exportSession: exportSession, workItem: workItem)
        return outputURL.path
    }
}
