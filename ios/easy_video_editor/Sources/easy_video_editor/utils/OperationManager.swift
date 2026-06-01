class OperationManager {
    static let shared = OperationManager()
    
    internal var operations: [String: DispatchWorkItem] = [:]
    internal let queue = DispatchQueue(label: "com.easyvideoeditor.operation", attributes: .concurrent)
    
    private init() {}

    /// Generate a unique `operationId`  
    func generateOperationId() -> String {
        return UUID().uuidString
    }

    /// Register a new operation with its ID and work item
    func registerOperation(id: String, workItem: DispatchWorkItem) {
        queue.async(flags: .barrier) {
            self.operations[id] = workItem
        }
    }

    /// Remove a completed operation without canceling it.
    func unregisterOperation(_ id: String) {
        queue.async(flags: .barrier) {
            self.operations.removeValue(forKey: id)
        }
    }

    /// Cancel a specific operation by its ID
    func cancelOperation(_ id: String) {
        queue.async(flags: .barrier) {
            if let workItem = self.operations[id] {
                workItem.cancel()
                self.operations.removeValue(forKey: id)
            }
        }
    }

    /// Cancel all operations
    func cancelAllOperations() {
        queue.async(flags: .barrier) {
            for (_, workItem) in self.operations {
                workItem.cancel()
            }
            self.operations.removeAll()
        }
    }
}
