package hcmute.edu.vn.ocrscannerproject.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.util.Date;

import hcmute.edu.vn.ocrscannerproject.data.local.converter.DateConverter;

/**
 * Room Entity representing a ScannedDocument in the local database.
 */
@Entity(tableName = "scanned_documents")
@TypeConverters(DateConverter.class)
public class ScannedDocumentEntity {
    
    @PrimaryKey
    @NonNull
    private String id;
    
    private String localImagePath;
    private String cloudImageUrl;
    private String recognizedText;
    private String summaryText;
    private Date timestamp;
    private String language;
    private String fileName;
    private String userId;
    private boolean isMetadataSynced;
    private boolean isImageSynced;
    
    /**
     * Default constructor for Room
     */
    public ScannedDocumentEntity() {
    }
    
    /**
     * Full constructor for all properties
     */
    public ScannedDocumentEntity(@NonNull String id, String localImagePath, String cloudImageUrl,
                                 String recognizedText, String summaryText, Date timestamp,
                                 String language, String fileName, String userId,
                                 boolean isMetadataSynced, boolean isImageSynced) {
        this.id = id;
        this.localImagePath = localImagePath;
        this.cloudImageUrl = cloudImageUrl;
        this.recognizedText = recognizedText;
        this.summaryText = summaryText;
        this.timestamp = timestamp;
        this.language = language;
        this.fileName = fileName;
        this.userId = userId;
        this.isMetadataSynced = isMetadataSynced;
        this.isImageSynced = isImageSynced;
    }
    
    @NonNull
    public String getId() {
        return id;
    }
    
    public void setId(@NonNull String id) {
        this.id = id;
    }
    
    public String getLocalImagePath() {
        return localImagePath;
    }
    
    public void setLocalImagePath(String localImagePath) {
        this.localImagePath = localImagePath;
    }
    
    public String getCloudImageUrl() {
        return cloudImageUrl;
    }
    
    public void setCloudImageUrl(String cloudImageUrl) {
        this.cloudImageUrl = cloudImageUrl;
    }
    
    public String getRecognizedText() {
        return recognizedText;
    }
    
    public void setRecognizedText(String recognizedText) {
        this.recognizedText = recognizedText;
    }
    
    public String getSummaryText() {
        return summaryText;
    }
    
    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }
    
    public Date getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public boolean isMetadataSynced() {
        return isMetadataSynced;
    }
    
    public void setMetadataSynced(boolean metadataSynced) {
        isMetadataSynced = metadataSynced;
    }
    
    public boolean isImageSynced() {
        return isImageSynced;
    }
    
    public void setImageSynced(boolean imageSynced) {
        isImageSynced = imageSynced;
    }
} 