package org.osmdroid.tileprovider.cachemanager;

import android.os.Build;
import android.util.Log;

import org.osmdroid.api.IMapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TaskCoordinator provides thread-safe task management for CacheManager operations.
 * It handles task registration, tracking, grouping, and bulk cancellation with
 * statistics collection.
 * 
 * Features:
 * - Thread-safe task registration and tracking
 * - Task grouping for bulk operations
 * - Efficient bulk cancellation (parallel on API 24+)
 * - Task statistics collection and reporting
 * 
 * @author osmdroid
 * @since 6.2.0
 */
public class TaskCoordinator {
    
    private static final String TAG = "TaskCoordinator";
    
    // Thread-safe collections for task management
    private final Set<CacheManager.CacheManagerTask> activeTasks;
    private final ConcurrentHashMap<String, TaskGroup> taskGroups;
    
    // Statistics tracking
    private final AtomicInteger totalTasksCreated;
    private final AtomicInteger totalTasksCompleted;
    private final AtomicInteger totalTasksCancelled;
    private final AtomicInteger totalTasksFailed;
    private final AtomicLong totalTilesProcessed;
    
    /**
     * Creates a new TaskCoordinator instance.
     */
    public TaskCoordinator() {
        // Use CopyOnWriteArraySet for safe concurrent iteration during cancellation
        this.activeTasks = new CopyOnWriteArraySet<>();
        this.taskGroups = new ConcurrentHashMap<>();
        
        // Initialize statistics counters
        this.totalTasksCreated = new AtomicInteger(0);
        this.totalTasksCompleted = new AtomicInteger(0);
        this.totalTasksCancelled = new AtomicInteger(0);
        this.totalTasksFailed = new AtomicInteger(0);
        this.totalTilesProcessed = new AtomicLong(0);
    }
    
    /**
     * Registers a task for tracking.
     * 
     * @param task The task to register
     * @return A unique task ID for this task
     */
    public String registerTask(CacheManager.CacheManagerTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        
        activeTasks.add(task);
        totalTasksCreated.incrementAndGet();
        
        String taskId = generateTaskId();
        Log.d(TAG, "Registered task: " + taskId + ", active tasks: " + activeTasks.size());
        
        return taskId;
    }
    
    /**
     * Registers a task with a specific group ID for bulk operations.
     * 
     * @param task The task to register
     * @param groupId The group ID to associate with this task
     * @return A unique task ID for this task
     */
    public String registerTask(CacheManager.CacheManagerTask task, String groupId) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be null or empty");
        }
        
        String taskId = registerTask(task);
        
        // Add task to group
        TaskGroup group = taskGroups.computeIfAbsent(groupId, k -> new TaskGroup(groupId));
        group.addTask(task);
        
        Log.d(TAG, "Registered task: " + taskId + " in group: " + groupId);
        
        return taskId;
    }
    
    /**
     * Unregisters a task from tracking.
     * 
     * @param task The task to unregister
     */
    public void unregisterTask(CacheManager.CacheManagerTask task) {
        if (task == null) {
            return;
        }
        
        boolean removed = activeTasks.remove(task);
        if (removed) {
            Log.d(TAG, "Unregistered task, remaining active tasks: " + activeTasks.size());
        }
        
        // Remove from any groups
        for (TaskGroup group : taskGroups.values()) {
            group.removeTask(task);
        }
    }
    
    /**
     * Marks a task as completed and updates statistics.
     * 
     * @param task The completed task
     * @param tilesProcessed Number of tiles processed by this task
     */
    public void markTaskCompleted(CacheManager.CacheManagerTask task, long tilesProcessed) {
        unregisterTask(task);
        totalTasksCompleted.incrementAndGet();
        totalTilesProcessed.addAndGet(tilesProcessed);
        
        Log.d(TAG, "Task completed. Total completed: " + totalTasksCompleted.get());
    }
    
    /**
     * Marks a task as cancelled and updates statistics.
     * 
     * @param task The cancelled task
     */
    public void markTaskCancelled(CacheManager.CacheManagerTask task) {
        unregisterTask(task);
        totalTasksCancelled.incrementAndGet();
        
        Log.d(TAG, "Task cancelled. Total cancelled: " + totalTasksCancelled.get());
    }
    
    /**
     * Marks a task as failed and updates statistics.
     * 
     * @param task The failed task
     * @param tilesProcessed Number of tiles processed before failure
     */
    public void markTaskFailed(CacheManager.CacheManagerTask task, long tilesProcessed) {
        unregisterTask(task);
        totalTasksFailed.incrementAndGet();
        totalTilesProcessed.addAndGet(tilesProcessed);
        
        Log.d(TAG, "Task failed. Total failed: " + totalTasksFailed.get());
    }
    
    /**
     * Cancels all active tasks.
     * Uses parallel cancellation on API 24+ for better performance.
     * 
     * @param mayInterruptIfRunning Whether to interrupt running tasks
     * @return Number of tasks cancelled
     */
    public int cancelAllTasks(boolean mayInterruptIfRunning) {
        int count = activeTasks.size();
        
        if (count == 0) {
            return 0;
        }
        
        Log.d(TAG, "Cancelling all tasks: " + count);
        
        // API 24+: Use parallel streams for faster cancellation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activeTasks.parallelStream().forEach(task -> {
                try {
                    task.cancel(mayInterruptIfRunning);
                } catch (Exception e) {
                    Log.w(TAG, "Error cancelling task", e);
                }
            });
        } else {
            // API 23+: Use enhanced for-each (CopyOnWriteArraySet is safe for concurrent iteration)
            for (CacheManager.CacheManagerTask task : activeTasks) {
                try {
                    task.cancel(mayInterruptIfRunning);
                } catch (Exception e) {
                    Log.w(TAG, "Error cancelling task", e);
                }
            }
        }
        
        activeTasks.clear();
        totalTasksCancelled.addAndGet(count);
        
        return count;
    }
    
    /**
     * Cancels all tasks in a specific group.
     * 
     * @param groupId The group ID
     * @param mayInterruptIfRunning Whether to interrupt running tasks
     * @return Number of tasks cancelled
     */
    public int cancelTaskGroup(String groupId, boolean mayInterruptIfRunning) {
        if (groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be null or empty");
        }
        
        TaskGroup group = taskGroups.get(groupId);
        if (group == null) {
            Log.d(TAG, "No task group found with ID: " + groupId);
            return 0;
        }
        
        int count = group.cancelAll(mayInterruptIfRunning);
        totalTasksCancelled.addAndGet(count);
        
        // Remove the group
        taskGroups.remove(groupId);
        
        Log.d(TAG, "Cancelled task group: " + groupId + ", tasks cancelled: " + count);
        
        return count;
    }
    
    /**
     * Gets the number of currently active tasks.
     * 
     * @return Number of active tasks
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }
    
    /**
     * Gets the list of all task group IDs.
     * 
     * @return List of group IDs
     */
    public List<String> getTaskGroupIds() {
        return new ArrayList<>(taskGroups.keySet());
    }
    
    /**
     * Gets the number of tasks in a specific group.
     * 
     * @param groupId The group ID
     * @return Number of tasks in the group, or 0 if group doesn't exist
     */
    public int getTaskGroupSize(String groupId) {
        TaskGroup group = taskGroups.get(groupId);
        return group != null ? group.size() : 0;
    }
    
    /**
     * Gets comprehensive statistics about task execution.
     * 
     * @return TaskStatistics object containing all statistics
     */
    public TaskStatistics getStatistics() {
        return new TaskStatistics(
            totalTasksCreated.get(),
            totalTasksCompleted.get(),
            totalTasksCancelled.get(),
            totalTasksFailed.get(),
            activeTasks.size(),
            totalTilesProcessed.get(),
            taskGroups.size()
        );
    }
    
    /**
     * Resets all statistics counters.
     * Does not affect active tasks.
     */
    public void resetStatistics() {
        totalTasksCreated.set(0);
        totalTasksCompleted.set(0);
        totalTasksCancelled.set(0);
        totalTasksFailed.set(0);
        totalTilesProcessed.set(0);
        
        Log.d(TAG, "Statistics reset");
    }
    
    /**
     * Clears all task groups.
     * Does not cancel tasks, only removes group associations.
     */
    public void clearTaskGroups() {
        taskGroups.clear();
        Log.d(TAG, "All task groups cleared");
    }
    
    /**
     * Generates a unique task ID.
     * 
     * @return A unique task ID string
     */
    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + totalTasksCreated.get();
    }
    
    /**
     * Inner class representing a group of related tasks.
     */
    private static class TaskGroup {
        private final String groupId;
        private final Set<CacheManager.CacheManagerTask> tasks;
        
        TaskGroup(String groupId) {
            this.groupId = groupId;
            this.tasks = new CopyOnWriteArraySet<>();
        }
        
        void addTask(CacheManager.CacheManagerTask task) {
            tasks.add(task);
        }
        
        void removeTask(CacheManager.CacheManagerTask task) {
            tasks.remove(task);
        }
        
        int size() {
            return tasks.size();
        }
        
        int cancelAll(boolean mayInterruptIfRunning) {
            int count = tasks.size();
            
            // API 24+: Use parallel streams for faster cancellation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tasks.parallelStream().forEach(task -> {
                    try {
                        task.cancel(mayInterruptIfRunning);
                    } catch (Exception e) {
                        Log.w(TAG, "Error cancelling task in group: " + groupId, e);
                    }
                });
            } else {
                for (CacheManager.CacheManagerTask task : tasks) {
                    try {
                        task.cancel(mayInterruptIfRunning);
                    } catch (Exception e) {
                        Log.w(TAG, "Error cancelling task in group: " + groupId, e);
                    }
                }
            }
            
            tasks.clear();
            return count;
        }
    }
    
    /**
     * Statistics data class containing task execution metrics.
     */
    public static class TaskStatistics {
        private final int totalTasksCreated;
        private final int totalTasksCompleted;
        private final int totalTasksCancelled;
        private final int totalTasksFailed;
        private final int activeTaskCount;
        private final long totalTilesProcessed;
        private final int taskGroupCount;
        
        TaskStatistics(int totalTasksCreated, int totalTasksCompleted, 
                      int totalTasksCancelled, int totalTasksFailed,
                      int activeTaskCount, long totalTilesProcessed,
                      int taskGroupCount) {
            this.totalTasksCreated = totalTasksCreated;
            this.totalTasksCompleted = totalTasksCompleted;
            this.totalTasksCancelled = totalTasksCancelled;
            this.totalTasksFailed = totalTasksFailed;
            this.activeTaskCount = activeTaskCount;
            this.totalTilesProcessed = totalTilesProcessed;
            this.taskGroupCount = taskGroupCount;
        }
        
        public int getTotalTasksCreated() {
            return totalTasksCreated;
        }
        
        public int getTotalTasksCompleted() {
            return totalTasksCompleted;
        }
        
        public int getTotalTasksCancelled() {
            return totalTasksCancelled;
        }
        
        public int getTotalTasksFailed() {
            return totalTasksFailed;
        }
        
        public int getActiveTaskCount() {
            return activeTaskCount;
        }
        
        public long getTotalTilesProcessed() {
            return totalTilesProcessed;
        }
        
        public int getTaskGroupCount() {
            return taskGroupCount;
        }
        
        /**
         * Gets the success rate as a percentage (0-100).
         * 
         * @return Success rate percentage
         */
        public double getSuccessRate() {
            int finished = totalTasksCompleted + totalTasksFailed;
            if (finished == 0) {
                return 0.0;
            }
            return (totalTasksCompleted * 100.0) / finished;
        }
        
        @Override
        public String toString() {
            return "TaskStatistics{" +
                    "created=" + totalTasksCreated +
                    ", completed=" + totalTasksCompleted +
                    ", cancelled=" + totalTasksCancelled +
                    ", failed=" + totalTasksFailed +
                    ", active=" + activeTaskCount +
                    ", tilesProcessed=" + totalTilesProcessed +
                    ", groups=" + taskGroupCount +
                    ", successRate=" + String.format("%.2f%%", getSuccessRate()) +
                    '}';
        }
    }
}
