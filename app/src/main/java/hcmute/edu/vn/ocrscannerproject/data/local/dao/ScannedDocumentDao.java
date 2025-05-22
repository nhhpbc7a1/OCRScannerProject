package hcmute.edu.vn.ocrscannerproject.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import hcmute.edu.vn.ocrscannerproject.data.local.entity.ScannedDocumentEntity;

/**
 * Data Access Object for the ScannedDocument entity.
 * Provides methods to access and manipulate data in the local database.
 */
@Dao
public interface ScannedDocumentDao {
    
    /**
     * Insert a new scanned document into the database.
     * If a document with the same ID already exists, it will be replaced.
     * 
     * @param document The document to insert
     * @return The row ID of the inserted document
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ScannedDocumentEntity document);
    
    /**
     * Update an existing scanned document in the database.
     * 
     * @param document The document to update
     */
    @Update
    void update(ScannedDocumentEntity document);
    
    /**
     * Delete a scanned document from the database.
     * 
     * @param document The document to delete
     */
    @Delete
    void delete(ScannedDocumentEntity document);
    
    /**
     * Get all scanned documents for a specific user, ordered by timestamp (newest first).
     * 
     * @param userId The ID of the user whose documents to retrieve
     * @return A LiveData list of scanned documents
     */
    @Query("SELECT * FROM scanned_documents WHERE userId = :userId ORDER BY timestamp DESC")
    LiveData<List<ScannedDocumentEntity>> getAllForUser(String userId);
    
    /**
     * Get a specific scanned document by its ID.
     * 
     * @param id The ID of the document to retrieve
     * @return The scanned document, or null if not found
     */
    @Query("SELECT * FROM scanned_documents WHERE id = :id LIMIT 1")
    ScannedDocumentEntity getDocumentById(String id);
    
    /**
     * Search for scanned documents by file name.
     * 
     * @param userId The ID of the user whose documents to search
     * @param query The search query (will be wrapped with '%' for SQL LIKE)
     * @return A LiveData list of matching scanned documents
     */
    @Query("SELECT * FROM scanned_documents WHERE userId = :userId AND fileName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    LiveData<List<ScannedDocumentEntity>> searchByFileName(String userId, String query);
    
    /**
     * Get all scanned documents that have not had their metadata synced to the cloud.
     * 
     * @param userId The ID of the user whose documents to retrieve
     * @return A list of scanned documents with unsynced metadata
     */
    @Query("SELECT * FROM scanned_documents WHERE userId = :userId AND isMetadataSynced = 0")
    List<ScannedDocumentEntity> getDocumentsWithUnsyncedMetadata(String userId);
    
    /**
     * Get all scanned documents that have not had their images synced to the cloud.
     * 
     * @param userId The ID of the user whose documents to retrieve
     * @return A list of scanned documents with unsynced images
     */
    @Query("SELECT * FROM scanned_documents WHERE userId = :userId AND isImageSynced = 0")
    List<ScannedDocumentEntity> getDocumentsWithUnsyncedImages(String userId);
    
    /**
     * Get all scanned documents from the database.
     * 
     * @return A list of all scanned documents
     */
    @Query("SELECT * FROM scanned_documents ORDER BY timestamp DESC")
    List<ScannedDocumentEntity> getAllDocuments();
} 