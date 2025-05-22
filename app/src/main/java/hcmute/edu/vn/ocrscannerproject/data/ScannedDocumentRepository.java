package hcmute.edu.vn.ocrscannerproject.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;

/**
 * Repository for managing ScannedDocument data locally using SharedPreferences.
 */
public class ScannedDocumentRepository {
    private static final String TAG = "ScannedDocRepository";
    private static final String PREF_NAME = "scanned_document_prefs";
    private static final String KEY_DOCUMENTS = "scanned_documents";
    
    private static ScannedDocumentRepository instance;
    private final SharedPreferences preferences;
    private final Gson gson;
    private List<ScannedDocument> documents;
    
    private ScannedDocumentRepository(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadDocuments();
    }
    
    /**
     * Gets the singleton instance of the repository.
     * 
     * @param context The application context
     * @return The repository instance
     */
    public static synchronized ScannedDocumentRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ScannedDocumentRepository(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Loads documents from SharedPreferences.
     */
    private void loadDocuments() {
        String json = preferences.getString(KEY_DOCUMENTS, null);
        if (json != null) {
            try {
                Type type = new TypeToken<ArrayList<ScannedDocument>>(){}.getType();
                documents = gson.fromJson(json, type);
            } catch (Exception e) {
                Log.e(TAG, "Error loading documents: " + e.getMessage());
                documents = new ArrayList<>();
            }
        } else {
            documents = new ArrayList<>();
        }
    }
    
    /**
     * Saves documents to SharedPreferences.
     */
    private void saveDocuments() {
        try {
            String json = gson.toJson(documents);
            preferences.edit().putString(KEY_DOCUMENTS, json).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving documents: " + e.getMessage());
        }
    }
    
    /**
     * Gets all documents.
     * 
     * @return A list of all documents
     */
    public List<ScannedDocument> getAllDocuments() {
        return new ArrayList<>(documents);
    }
    
    /**
     * Gets a document by its ID.
     * 
     * @param id The ID of the document to get
     * @return The document, or null if not found
     */
    public ScannedDocument getDocumentById(String id) {
        for (ScannedDocument document : documents) {
            if (document.getId().equals(id)) {
                return document;
            }
        }
        return null;
    }
    
    /**
     * Adds a new document to the repository.
     * 
     * @param document The document to add
     */
    public void addDocument(ScannedDocument document) {
        // Generate an ID if not already set
        if (document.getId() == null || document.getId().isEmpty()) {
            document.setId(UUID.randomUUID().toString());
        }
        
        documents.add(0, document); // Add to beginning of list (newest first)
        saveDocuments();
    }
    
    /**
     * Updates an existing document in the repository.
     * 
     * @param document The document to update
     */
    public void updateDocument(ScannedDocument document) {
        for (int i = 0; i < documents.size(); i++) {
            if (documents.get(i).getId().equals(document.getId())) {
                documents.set(i, document);
                saveDocuments();
                return;
            }
        }
    }
    
    /**
     * Deletes a document from the repository.
     * 
     * @param id The ID of the document to delete
     */
    public void deleteDocument(String id) {
        for (int i = 0; i < documents.size(); i++) {
            if (documents.get(i).getId().equals(id)) {
                documents.remove(i);
                saveDocuments();
                return;
            }
        }
    }
    
    /**
     * Searches for documents by title or extracted text.
     * 
     * @param query The search query
     * @return A list of matching documents
     */
    public List<ScannedDocument> searchDocuments(String query) {
        List<ScannedDocument> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        for (ScannedDocument document : documents) {
            if (document.getFileName().toLowerCase().contains(lowerQuery) || 
                    (document.getRecognizedText() != null && 
                     document.getRecognizedText().toLowerCase().contains(lowerQuery))) {
                results.add(document);
            }
        }
        
        return results;
    }
    
    /**
     * Filters documents by type.
     * 
     * @param filter The type to filter by, or "All" for all documents
     * @return A list of filtered documents
     */
    public List<ScannedDocument> filterDocumentsByType(String filter) {
        if (filter == null || filter.isEmpty() || filter.equals("All")) {
            return new ArrayList<>(documents);
        }
        
        List<ScannedDocument> results = new ArrayList<>();
        for (ScannedDocument document : documents) {
            if (document.getType().equals(filter)) {
                results.add(document);
            }
        }
        
        return results;
    }
} 