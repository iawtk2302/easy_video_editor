import Flutter
import Foundation

extension Notification.Name {
    static let operationCanceled = Notification.Name("operationCanceled")
}

/**
 * Command for canceling currently running operations
 */
class CancelOperationCommand: Command {
    func execute(call: FlutterMethodCall, result: @escaping FlutterResult) {
        OperationManager.shared.queue.async(flags: .barrier) {
            for (_, workItem) in OperationManager.shared.operations {
                workItem.cancel()
            }
            OperationManager.shared.operations.removeAll()
            NotificationCenter.default.post(name: .operationCanceled, object: nil)
            DispatchQueue.main.async {
                result(nil)
            }
        }
    }
}
