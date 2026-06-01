import Foundation
import AVFoundation

extension Int64 {
    /// Convert milliseconds to CMTime
    var toCMTime: CMTime {
        let seconds = Double(self) / 1000.0
        return CMTime(seconds: seconds, preferredTimescale: 1000)
    }
    
    /// Convert milliseconds to TimeInterval
    var toTimeInterval: TimeInterval {
        return TimeInterval(self) / 1000.0
    }
}

extension CMTime {
    /// Convert CMTime to milliseconds
    var toMilliseconds: Int64 {
        return Int64(CMTimeGetSeconds(self) * 1000)
    }
} 