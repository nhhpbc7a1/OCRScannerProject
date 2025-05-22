package hcmute.edu.vn.ocrscannerproject.data.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.ArrayList;

import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;
import hcmute.edu.vn.ocrscannerproject.data.local.AppDatabase;
import hcmute.edu.vn.ocrscannerproject.data.local.dao.ScannedDocumentDao;
import hcmute.edu.vn.ocrscannerproject.data.local.entity.ScannedDocumentEntity;
import hcmute.edu.vn.ocrscannerproject.services.StorageQuotaManager;
import hcmute.edu.vn.ocrscannerproject.core.entities.User;

/**
 * Repository for managing ScannedDocument data from multiple sources (local database and Firebase).
 */
public class ScannedDocumentRepository {
    private static final String TAG = "ScannedDocRepository";
    
    private final ScannedDocumentDao scannedDocumentDao;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;
    private final FirebaseAuth auth;
    private final Context context;
    private final Executor executor;
    private final StorageQuotaManager quotaManager;
    
    /**
     * Constructor for the repository.
     * 
     * @param context The application context
     */
    public ScannedDocumentRepository(Context context) {
        this.context = context.getApplicationContext();
        this.scannedDocumentDao = AppDatabase.getInstance(context).scannedDocumentDao();
        this.firestore = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.executor = Executors.newSingleThreadExecutor();
        this.quotaManager = new StorageQuotaManager();
    }
    
    /**
     * Gets the current authenticated user.
     * 
     * @return The current FirebaseUser, or null if not authenticated
     */
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }
    
    /**
     * Gets the storage quota manager.
     * 
     * @return The StorageQuotaManager
     */
    public StorageQuotaManager getQuotaManager() {
        return quotaManager;
    }
    
    /**
     * Gets all scanned documents for the current user.
     * 
     * @return A LiveData list of ScannedDocumentEntity objects
     */
    public LiveData<List<ScannedDocumentEntity>> getAllDocuments() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            return scannedDocumentDao.getAllForUser(user.getUid());
        }
        return null;
    }
    
    /**
     * Searches for documents by file name.
     * 
     * @param query The search query
     * @return A LiveData list of matching ScannedDocumentEntity objects
     */
    public LiveData<List<ScannedDocumentEntity>> searchDocuments(String query) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            return scannedDocumentDao.searchByFileName(user.getUid(), query);
        }
        return null;
    }
    
    /**
     * Gets a document by its ID.
     * 
     * @param documentId The ID of the document to get
     * @return The document, or null if not found
     */
    public ScannedDocument getDocumentById(String documentId) {
        ScannedDocumentEntity entity = scannedDocumentDao.getDocumentById(documentId);
        if (entity != null) {
            return convertEntityToDocument(entity);
        }
        return null;
    }
    
    /**
     * Saves a new scanned document to the local database and to Firebase if the user is authenticated.
     * 
     * @param document The document to save
     * @param callback The callback to be invoked when the save operation completes
     */
    public void saveDocument(ScannedDocument document, DocumentCallback<ScannedDocument> callback) {
        FirebaseUser user = auth.getCurrentUser();
        String userId = (user != null) ? user.getUid() : "anonymous";
        
        // Generate a unique ID if not already set
        if (document.getId() == null || document.getId().isEmpty()) {
            document.setId(UUID.randomUUID().toString());
        }
        
        // Create the entity from the document
        ScannedDocumentEntity entity = convertDocumentToEntity(document);
        
        // Save to local database
        executor.execute(() -> {
            try {
                scannedDocumentDao.insert(entity);
                
                // If user is authenticated, save to Firebase
                if (user != null) {
                    try {
                        saveToFirebase(document);
                        callback.onSuccess(document);
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving to Firebase", e);
                        callback.onError(e);
                    }
                } else {
                    callback.onSuccess(document);
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Updates a document in the local database and Firebase if the user is signed in.
     * 
     * @param document The document to update
     */
    public void updateDocument(ScannedDocument document) throws Exception {
        // Update locally
        ScannedDocumentEntity entity = convertDocumentToEntity(document);
        scannedDocumentDao.update(entity);
        
        // Update in Firebase if user is signed in
        User currentUser = getAppUser();
        if (currentUser != null && document.getUserId().equals(currentUser.getUserId())) {
            updateInFirebase(document);
        }
    }
    
    /**
     * Deletes a document from the local database and from Firebase if the user is authenticated.
     * 
     * @param document The document to delete
     * @param callback The callback to be invoked when the delete operation completes
     */
    public void deleteDocument(ScannedDocument document, DocumentCallback<Void> callback) {
        // Create the entity from the document
        ScannedDocumentEntity entity = convertDocumentToEntity(document);
        
        // Delete from local database
        executor.execute(() -> {
            try {
                scannedDocumentDao.delete(entity);
                
                // Delete local image file if it exists
                if (document.getLocalImagePath() != null && !document.getLocalImagePath().isEmpty()) {
                    File localFile = new File(document.getLocalImagePath());
                    if (localFile.exists()) {
                        localFile.delete();
                    }
                }
                
                // If user is authenticated, delete from Firebase
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    try {
                        deleteFromFirebase(document);
                        callback.onSuccess(null);
                    } catch (Exception e) {
                        Log.e(TAG, "Error deleting from Firebase", e);
                        callback.onError(e);
                    }
                } else {
                    callback.onSuccess(null);
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * Synchronizes documents between local storage and Firebase.
     * This may take some time for large document collections.
     */
    public void syncDocuments() throws Exception {
        User currentUser = getAppUser();
        if (currentUser == null) {
            throw new IllegalStateException("User is not signed in");
        }

        // First, sync metadata from local to Firebase
        List<ScannedDocument> localDocuments = getAllDocumentsLocal();
        for (ScannedDocument document : localDocuments) {
            if (!document.isMetadataSynced()) {
                saveToFirebase(document);
            }
        }

        // Then, sync document images from local to Firebase if they're not yet synced
        for (ScannedDocument document : localDocuments) {
            if (!document.isImageSynced() && document.getLocalImagePath() != null) {
                File imageFile = new File(document.getLocalImagePath());
                if (imageFile.exists()) {
                    long fileSize = imageFile.length();
                    
                    // Check if the user has enough quota
                    boolean hasQuota = true; // Assume true by default to avoid blocking sync
                    try {
                        StorageQuotaManager.StorageQuotaCallback<Boolean> callback = new StorageQuotaManager.StorageQuotaCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                // Do nothing here, this is just used to capture the value
                            }
                            
                            @Override
                            public void onError(Exception e) {
                                // Do nothing here, errors will be handled by the caller
                            }
                        };
                        
                        Task<Boolean> task = Tasks.forResult(true);
                        quotaManager.checkQuotaForUpload(currentUser.getUserId(), fileSize, callback);
                        boolean result = Tasks.await(task);
                        hasQuota = result;
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking quota", e);
                    }
                    
                    if (hasQuota) {
                        // Upload the image to Firebase Storage
                        uploadImage(document.getId(), imageFile, fileSize);
                    }
                }
            }
        }

        // Finally, sync any documents from Firebase that aren't in local storage
        // Query all documents for this user from Firestore
        QuerySnapshot querySnapshot = Tasks.await(
            firestore.collection("scannedDocuments")
                .whereEqualTo("userId", currentUser.getUserId())
                .get()
        );
        
        // For each document in Firestore
        for (DocumentSnapshot docSnapshot : querySnapshot.getDocuments()) {
            String remoteDocId = docSnapshot.getId();
            
            // Check if this document exists locally
            boolean existsLocally = false;
            for (ScannedDocument localDoc : localDocuments) {
                if (localDoc.getId().equals(remoteDocId)) {
                    existsLocally = true;
                    break;
                }
            }
            
            // If not, download it
            if (!existsLocally) {
                try {
                    Map<String, Object> data = docSnapshot.getData();
                    if (data != null) {
                        ScannedDocument doc = new ScannedDocument(
                            (String) data.getOrDefault("fileName", "Unnamed Document"), 
                            currentUser.getUserId()
                        );
                        
                        doc.setId(remoteDocId);
                        doc.setRecognizedText((String) data.getOrDefault("recognizedText", ""));
                        doc.setSummaryText((String) data.getOrDefault("summaryText", ""));
                        
                        Timestamp timestamp = (Timestamp) data.get("timestamp");
                        if (timestamp != null) {
                            doc.setTimestamp(timestamp.toDate());
                        }
                        
                        doc.setLanguage((String) data.getOrDefault("language", ""));
                        doc.setCloudImageUrl((String) data.getOrDefault("cloudImageUrl", ""));
                        doc.setMetadataSynced(true);
                        
                        // If there's a cloud image, download it
                        if (doc.getCloudImageUrl() != null && !doc.getCloudImageUrl().isEmpty()) {
                            File localFile = File.createTempFile("download_" + remoteDocId, ".jpg");
                            StorageReference imageRef = storage.getReferenceFromUrl(doc.getCloudImageUrl());
                            
                            Tasks.await(imageRef.getFile(localFile));
                            doc.setLocalImagePath(localFile.getAbsolutePath());
                            doc.setImageSynced(true);
                        }
                        
                        // Save the document locally
                        saveLocally(doc);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error downloading document: " + remoteDocId, e);
                    // Continue with the next document
                }
            }
        }
    }
    
    /**
     * Saves a document to Firebase Firestore.
     * 
     * @param document The document to save
     */
    private void saveToFirebase(ScannedDocument document) throws Exception {
        User currentUser = getAppUser();
        if (currentUser == null || !document.getUserId().equals(currentUser.getUserId())) {
            throw new IllegalStateException("User not authorized to save this document");
        }
        
        // Create a map of fields to save
        Map<String, Object> docData = new HashMap<>();
        docData.put("fileName", document.getFileName());
        docData.put("recognizedText", document.getRecognizedText());
        docData.put("summaryText", document.getSummaryText());
        docData.put("timestamp", document.getTimestamp());
        docData.put("language", document.getLanguage());
        docData.put("userId", document.getUserId());
        docData.put("cloudImageUrl", document.getCloudImageUrl());
        
        // Save the document to Firestore
        DocumentReference docRef;
        if (document.getId() != null && !document.getId().isEmpty()) {
            // Use the existing ID
            docRef = firestore.collection("scannedDocuments").document(document.getId());
            Tasks.await(docRef.set(docData));
        } else {
            // Let Firestore generate a new ID
            docRef = firestore.collection("scannedDocuments").document();
            document.setId(docRef.getId());
            docData.put("id", document.getId());
            Tasks.await(docRef.set(docData));
            
            // Update the local document with the new ID
            ScannedDocumentEntity entity = convertDocumentToEntity(document);
            scannedDocumentDao.update(entity);
        }
        
        // Mark the document's metadata as synced
        document.setMetadataSynced(true);
        ScannedDocumentEntity entity = convertDocumentToEntity(document);
        scannedDocumentDao.update(entity);
    }
    
    /**
     * Updates a document in Firebase Firestore.
     * 
     * @param document The document to update
     */
    private void updateInFirebase(ScannedDocument document) throws Exception {
        User currentUser = getAppUser();
        if (currentUser == null || !document.getUserId().equals(currentUser.getUserId())) {
            throw new IllegalStateException("User not authorized to update this document");
        }
        
        // Create a map of fields to update
        Map<String, Object> docData = new HashMap<>();
        docData.put("fileName", document.getFileName());
        docData.put("recognizedText", document.getRecognizedText());
        docData.put("summaryText", document.getSummaryText());
        docData.put("timestamp", document.getTimestamp());
        docData.put("language", document.getLanguage());
        docData.put("userId", document.getUserId());
        docData.put("cloudImageUrl", document.getCloudImageUrl());
        
        // Update the document in Firestore
        DocumentReference docRef = firestore.collection("scannedDocuments").document(document.getId());
        Tasks.await(docRef.update(docData));
        
        // Mark the document's metadata as synced
        document.setMetadataSynced(true);
        ScannedDocumentEntity entity = convertDocumentToEntity(document);
        scannedDocumentDao.update(entity);
    }
    
    /**
     * Deletes a document from Firebase Firestore and its image from Firebase Storage.
     * 
     * @param document The document to delete
     */
    private void deleteFromFirebase(ScannedDocument document) throws Exception {
        User currentUser = getAppUser();
        if (currentUser == null || !document.getUserId().equals(currentUser.getUserId())) {
            throw new IllegalStateException("User not authorized to delete this document");
        }
        
        // If there's a cloud image URL, get the file size for quota update
        long fileSize = 0;
        if (document.getCloudImageUrl() != null && !document.getCloudImageUrl().isEmpty()) {
            StorageReference storageRef = storage.getReferenceFromUrl(document.getCloudImageUrl());
            try {
                StorageMetadata metadata = Tasks.await(storageRef.getMetadata());
                fileSize = metadata.getSizeBytes();
            } catch (Exception e) {
                Log.e(TAG, "Error getting file size", e);
                // Continue with deletion even if we can't get the file size
            }
        }
        
        // Delete from Firestore
        DocumentReference docRef = firestore.collection("scannedDocuments").document(document.getId());
        Tasks.await(docRef.delete());
        
        // If there's a cloud image URL, delete the image from Storage
        if (document.getCloudImageUrl() != null && !document.getCloudImageUrl().isEmpty()) {
            StorageReference storageRef = storage.getReferenceFromUrl(document.getCloudImageUrl());
            try {
                Tasks.await(storageRef.delete());
                
                // Update quota usage
                if (fileSize > 0) {
                    quotaManager.updateUsageAfterDelete(currentUser.getUserId(), fileSize);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting image from Storage", e);
                // Continue even if image deletion fails
            }
        }
    }
    
    /**
     * Uploads an image to Firebase Storage.
     * 
     * @param documentId The ID of the document
     * @param imageFile The file to upload
     * @param fileSize The size of the file in bytes
     * @return The URL of the uploaded image
     */
    private String uploadImage(String documentId, File imageFile, long fileSize) throws Exception {
        User currentUser = getAppUser();
        if (currentUser == null) {
            throw new IllegalStateException("User not signed in");
        }
        
        // Create storage reference
        StorageReference imageRef = storage.getReference()
                .child("scannedImages")
                .child(currentUser.getUserId())
                .child(documentId + ".jpg");
        
        // Upload file
        UploadTask uploadTask = imageRef.putFile(Uri.fromFile(imageFile));
        
        // Wait for upload to complete
        Tasks.await(uploadTask);
        
        // Get download URL
        String imageUrl = Tasks.await(imageRef.getDownloadUrl()).toString();
        
        // Update document with cloud URL and sync status
        ScannedDocument document = getDocumentById(documentId);
        if (document != null) {
            document.setCloudImageUrl(imageUrl);
            document.setImageSynced(true);
            try {
                updateDocument(document);
            } catch (Exception e) {
                Log.e(TAG, "Error updating document after upload", e);
            }
        }
        
        // Update user's storage quota
        quotaManager.updateUsageAfterUpload(currentUser.getUserId(), fileSize);
        
        return imageUrl;
    }
    
    /**
     * Converts a ScannedDocument to a ScannedDocumentEntity.
     * 
     * @param document The document to convert
     * @return The converted entity
     */
    private ScannedDocumentEntity convertDocumentToEntity(ScannedDocument document) {
        return new ScannedDocumentEntity(
                document.getId(),
                document.getLocalImagePath(),
                document.getCloudImageUrl(),
                document.getRecognizedText(),
                document.getSummaryText(),
                document.getTimestamp(),
                document.getLanguage(),
                document.getFileName(),
                document.getUserId(),
                document.isMetadataSynced(),
                document.isImageSynced()
        );
    }
    
    /**
     * Converts a ScannedDocumentEntity to a ScannedDocument.
     * 
     * @param entity The entity to convert
     * @return The converted document
     */
    private ScannedDocument convertEntityToDocument(ScannedDocumentEntity entity) {
        ScannedDocument document = new ScannedDocument(entity.getFileName(), entity.getUserId());
        document.setId(entity.getId());
        document.setLocalImagePath(entity.getLocalImagePath());
        document.setCloudImageUrl(entity.getCloudImageUrl());
        document.setRecognizedText(entity.getRecognizedText());
        document.setSummaryText(entity.getSummaryText());
        document.setTimestamp(entity.getTimestamp());
        document.setLanguage(entity.getLanguage());
        document.setMetadataSynced(entity.isMetadataSynced());
        document.setImageSynced(entity.isImageSynced());
        return document;
    }
    
    /**
     * Gets the formatted storage usage string for the current user
     * @return A formatted string representing the storage usage (e.g., "45.2 MB / 1 GB")
     */
    public String getFormattedQuotaUsage() {
        return quotaManager.getFormattedQuotaUsage();
    }

    /**
     * Gets the storage usage percentage for the current user
     * @return The percentage of storage quota used (0-100)
     */
    public int getQuotaUsagePercentage() {
        return quotaManager.getQuotaUsagePercentage();
    }
    
    /**
     * Callback interface for document operations.
     * 
     * @param <T> The type of the result
     */
    public interface DocumentCallback<T> {
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

    /**
     * Gets all documents from local database.
     * 
     * @return A list of all documents in the local database
     */
    private List<ScannedDocument> getAllDocumentsLocal() {
        List<ScannedDocumentEntity> entities = scannedDocumentDao.getAllDocuments();
        List<ScannedDocument> documents = new ArrayList<>();
        
        for (ScannedDocumentEntity entity : entities) {
            documents.add(convertEntityToDocument(entity));
        }
        
        return documents;
    }

    /**
     * Saves a document to the local database only.
     * 
     * @param document The document to save
     */
    private void saveLocally(ScannedDocument document) {
        ScannedDocumentEntity entity = convertDocumentToEntity(document);
        scannedDocumentDao.insert(entity);
    }

    /**
     * Gets the current signed-in user as app User model.
     * 
     * @return The current user, or null if not signed in
     */
    private User getAppUser() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null) {
            return new User(
                    firebaseUser.getUid(),
                    firebaseUser.getEmail(),
                    firebaseUser.getDisplayName(),
                    firebaseUser.isAnonymous()
            );
        }
        return null;
    }
} 