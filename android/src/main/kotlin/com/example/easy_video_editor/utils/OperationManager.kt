package com.example.easy_video_editor.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton class that manages active operations and allows for cancellation
 */
object OperationManager {
    private val activeScopes = ConcurrentHashMap<String, CoroutineScope>()
    
    /**
     * Generates a unique operation ID
     */
    fun generateOperationId(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * Registers an operation with its associated coroutine scope
     * 
     * @param id Unique identifier for the operation
     * @param scope Coroutine scope associated with the operation
     */
    fun registerOperation(id: String, scope: CoroutineScope) {
        activeScopes[id] = scope
    }

    /**
     * Removes a completed operation without canceling its scope.
     *
     * Completion cleanup should not go through [cancelOperation], because cancellation
     * is user intent and can trigger cancellation handlers after work has succeeded.
     */
    fun unregisterOperation(id: String) {
        activeScopes.remove(id)
    }

    internal val activeOperationCount: Int
        get() = activeScopes.size
    
    /**
     * Cancels a specific operation
     * 
     * @param id Identifier of the operation to cancel
     * @return true if the operation was found and canceled, false otherwise
     */
    fun cancelOperation(id: String): Boolean {
        val scope = activeScopes.remove(id)
        if (scope != null && scope.isActive) {
            scope.cancel("Operation canceled by user")
            return true
        }
        return false
    }
    
    /**
     * Cancels all active operations
     * 
     * @return true if any operations were canceled, false if no operations were active
     */
    fun cancelAllOperations(): Boolean {
        if (activeScopes.isEmpty()) {
            return false
        }
        
        var anyCanceled = false
        activeScopes.forEach { (id, scope) ->
            if (scope.isActive) {
                scope.cancel("All operations canceled by user")
                anyCanceled = true
            }
            activeScopes.remove(id)
        }
        
        return anyCanceled
    }
}
