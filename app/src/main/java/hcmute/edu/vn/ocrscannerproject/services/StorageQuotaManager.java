package hcmute.edu.vn.ocrscannerproject.services;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.android.gms.tasks.Tasks;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Manages storage quotas for users.
 */
public class StorageQuotaManager {
    private static final String TAG = "StorageQuotaManager";
    
    // 1GB in bytes
    public static final long MAX_USER_QUOTA_BYTES = 1024 * 1024 * 1024;
    
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;
    private final FirebaseAuth auth;
    private final Map<String, Long> cachedUsage;
    private final Executor executor;
    private final Handler mainHandler;
    
    /**
     * Initializes the StorageQuotaManager.
     */
    public StorageQuotaManager() {
        this.firestore = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.cachedUsage = new HashMap<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Gets the current storage usage for a user.
     * 
     * @param userId The ID of the user
     * @param callback The callback to be invoked with the result
     */
    public void getUserStorageUsage(String userId, final StorageQuotaCallback<Long> callback) {
        // First check if we have the usage cached in Firestore
        DocumentReference userQuotaDoc = firestore.collection("userQuotas").document(userId);
        
        userQuotaDoc.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                    // We have a cached usage
                    Long usedBytes = task.getResult().getLong("usedBytes");
                    Long lastUpdated = task.getResult().getLong("lastUpdated");
                    
                    if (usedBytes != null && lastUpdated != null) {
                        // If the cached usage is recent (less than an hour old), use it
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUpdated < 3600000) { // 1 hour in milliseconds
                            callback.onSuccess(usedBytes);
                            return;
                        }
                    }
                }
                
                // If we don't have a recent cached usage, calculate it
                calculateUserStorageUsage(userId, callback);
            }
        });
    }
    
    /**
     * Calculates the current storage usage for a user by listing all their files in Firebase Storage.
     * 
     * @param userId The ID of the user
     * @param callback The callback to be invoked with the result
     */
    private void calculateUserStorageUsage(String userId, final StorageQuotaCallback<Long> callback) {
        StorageReference userStorageRef = storage.getReference().child("scannedImages").child(userId);
        
        userStorageRef.listAll().addOnCompleteListener(new OnCompleteListener<ListResult>() {
            @Override
            public void onComplete(@NonNull Task<ListResult> task) {
                if (task.isSuccessful()) {
                    ListResult result = task.getResult();
                    if (result.getItems().isEmpty()) {
                        // No files, so usage is 0
                        updateCachedUsage(userId, 0);
                        callback.onSuccess(0L);
                        return;
                    }
                    
                    // Get the size of each file
                    final long[] totalSize = {0};
                    final int[] completedCount = {0};
                    
                    for (StorageReference fileRef : result.getItems()) {
                        fileRef.getMetadata().addOnCompleteListener(new OnCompleteListener<StorageMetadata>() {
                            @Override
                            public void onComplete(@NonNull Task<StorageMetadata> metadataTask) {
                                completedCount[0]++;
                                
                                if (metadataTask.isSuccessful()) {
                                    StorageMetadata metadata = metadataTask.getResult();
                                    totalSize[0] += metadata.getSizeBytes();
                                }
                                
                                // If all files have been processed, return the total
                                if (completedCount[0] == result.getItems().size()) {
                                    updateCachedUsage(userId, totalSize[0]);
                                    callback.onSuccess(totalSize[0]);
                                }
                            }
                        });
                    }
                } else {
                    Log.e(TAG, "Error calculating storage usage", task.getException());
                    callback.onError(task.getException());
                }
            }
        });
    }
    
    /**
     * Updates the cached storage usage for a user in Firestore.
     * 
     * @param userId The ID of the user
     * @param usedBytes The used bytes
     */
    private void updateCachedUsage(String userId, long usedBytes) {
        DocumentReference userQuotaDoc = firestore.collection("userQuotas").document(userId);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("usedBytes", usedBytes);
        updates.put("lastUpdated", System.currentTimeMillis());
        
        userQuotaDoc.set(updates)
                .addOnFailureListener(e -> Log.e(TAG, "Error updating cached usage", e));
    }
    
    /**
     * Checks if a user has enough quota to upload a file of the specified size.
     * 
     * @param userId The ID of the user
     * @param fileSizeBytes The size of the file in bytes
     * @param callback The callback to be invoked with the result
     */
    public void checkQuotaForUpload(String userId, long fileSizeBytes, 
                                   final StorageQuotaCallback<Boolean> callback) {
        getUserStorageUsage(userId, new StorageQuotaCallback<Long>() {
            @Override
            public void onSuccess(Long usedBytes) {
                boolean hasQuota = usedBytes + fileSizeBytes <= MAX_USER_QUOTA_BYTES;
                callback.onSuccess(hasQuota);
            }
            
            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Updates the cached storage usage after a successful upload.
     * 
     * @param userId The ID of the user
     * @param additionalBytes The additional bytes used
     */
    public void updateUsageAfterUpload(String userId, long additionalBytes) {
        DocumentReference userQuotaDoc = firestore.collection("userQuotas").document(userId);
        
        userQuotaDoc.update("usedBytes", FieldValue.increment(additionalBytes))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating usage after upload", e));
    }
    
    /**
     * Updates the cached storage usage after a successful deletion.
     * 
     * @param userId The ID of the user
     * @param deletedBytes The bytes that were deleted
     */
    public void updateUsageAfterDelete(String userId, long deletedBytes) {
        DocumentReference userQuotaDoc = firestore.collection("userQuotas").document(userId);
        
        userQuotaDoc.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                Long currentUsage = task.getResult().getLong("usedBytes");
                if (currentUsage != null) {
                    long newUsage = Math.max(0, currentUsage - deletedBytes);
                    userQuotaDoc.update("usedBytes", newUsage)
                            .addOnFailureListener(e -> Log.e(TAG, "Error updating usage after delete", e));
                }
            }
        });
    }
    
    /**
     * Gets a formatted string representing the user's storage usage.
     * Format: "X.XX MB / 1.00 GB"
     * 
     * @return The formatted storage usage string
     */
    public String getFormattedQuotaUsage() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return "Not signed in";
        }
        
        String uid = user.getUid();
        DocumentReference userQuotaDoc = firestore.collection("userQuotas").document(uid);
        
        try {
            // Get the document synchronously using Tasks API
            Task<DocumentSnapshot> task = userQuotaDoc.get();
            DocumentSnapshot document = Tasks.await(task);
            
            long usedBytes = 0;
            if (document.exists() && document.getLong("usedBytes") != null) {
                usedBytes = document.getLong("usedBytes");
                // Update the cache
                cachedUsage.put(uid, usedBytes);
            }
            
            double usedMB = usedBytes / (1024 * 1024.0);
            return String.format(Locale.US, "%.2f MB / 1.00 GB", usedMB);
        } catch (Exception e) {
            Log.e(TAG, "Error getting formatted quota usage", e);
            return "Error loading usage";
        }
    }
    
    /**
     * Gets the percentage of storage quota used by the current user.
     * 
     * @return The percentage of quota used (0-100)
     */
    public int getQuotaUsagePercentage() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return 0;
        }
        
        String uid = user.getUid();
        DocumentReference userQuotaDoc = firestore.collection("userQuotas").document(uid);
        
        try {
            // Get the document synchronously using Tasks API
            Task<DocumentSnapshot> task = userQuotaDoc.get();
            DocumentSnapshot document = Tasks.await(task);
            
            long usedBytes = 0;
            if (document.exists() && document.getLong("usedBytes") != null) {
                usedBytes = document.getLong("usedBytes");
                // Update the cache
                cachedUsage.put(uid, usedBytes);
            }
            
            return (int) ((usedBytes / (double) MAX_USER_QUOTA_BYTES) * 100);
        } catch (Exception e) {
            Log.e(TAG, "Error getting quota usage percentage", e);
            return 0;
        }
    }
    
    /**
     * Callback interface for storage quota operations.
     * 
     * @param <T> The type of the result
     */
    public interface StorageQuotaCallback<T> {
        /**
         * Called when the operation is successful.
         * 
         * @param result The result of the operation
         */
        void onSuccess(T result);
        
        /**
         * Called when the operation fails.
         * 
         * @param e The exception that caused the failure
         */
        void onError(Exception e);
    }
} 