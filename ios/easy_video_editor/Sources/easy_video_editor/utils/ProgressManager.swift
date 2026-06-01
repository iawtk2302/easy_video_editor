import Flutter

// TEMPORARY: Will be removed when ProgressManager.swift is properly added to the Xcode project
// This implementation sends progress data to Flutter's event channel
class ProgressManager {
    static let shared = ProgressManager()
    private var eventSink: Any?
    
    func reportProgress(_ progress: Double) {
        guard let eventSink = eventSink as? FlutterEventSink else { return }
        
        // Ensure progress is between 0 and 1
        let normalizedProgress = min(max(progress, 0), 1.0)
        
        // Send progress to Flutter on the main thread
        DispatchQueue.main.async {
            eventSink(normalizedProgress)
        }
    }
    
    func setEventSink(_ sink: Any?) {
        eventSink = sink
    }
}
