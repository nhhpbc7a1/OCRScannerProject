package hcmute.edu.vn.ocrscannerproject.core.entities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents a document that has been scanned by the user.
 * Contains information about the image, extracted text, and sync status.
 */
public class ScannedDocument {
    private String id;
    private String localImagePath;
    private List<String> localImagePaths;
    private String cloudImageUrl;
    private List<String> cloudImageUrls;
    private String recognizedText;
    private String summaryText;
    private Date timestamp;
    private String language;
    private String fileName;
    private String userId; // Owner of this document
    private boolean isMetadataSynced;
    private boolean isImageSynced;
    private String type; // PDF, TXT, Image, etc.

    /**
     * Constructs a new ScannedDocument with the specified file name and user ID.
     * Other properties will be set through their respective setters.
     * 
     * @param fileName The name of the file
     * @param userId The ID of the user who owns this document
     */
    public ScannedDocument(String fileName, String userId) {
        this.fileName = fileName;
        this.userId = userId;
        this.timestamp = new Date(); // Default to current time
        this.isMetadataSynced = false;
        this.isImageSynced = false;
        this.localImagePaths = new ArrayList<>();
        this.cloudImageUrls = new ArrayList<>();
        this.type = "Image"; // Default type
    }
    
    /**
     * Constructs a new ScannedDocument with a single image path
     * 
     * @param fileName The name of the file
     * @param userId The ID of the user who owns this document
     * @param localImagePath The local path to the image
     */
    public ScannedDocument(String fileName, String userId, String localImagePath) {
        this(fileName, userId);
        this.localImagePath = localImagePath;
        this.localImagePaths.add(localImagePath);
        this.type = "Image";
    }
    
    /**
     * Constructs a new ScannedDocument with multiple image paths
     * 
     * @param fileName The name of the file
     * @param userId The ID of the user who owns this document
     * @param localImagePaths List of local paths to images
     */
    public ScannedDocument(String fileName, String userId, List<String> localImagePaths) {
        this(fileName, userId);
        this.localImagePaths = new ArrayList<>(localImagePaths);
        if (!localImagePaths.isEmpty()) {
            this.localImagePath = localImagePaths.get(0);
        }
        this.type = "Image";
    }
    
    /**
     * Constructs a new ScannedDocument with a single image path and extracted text
     * 
     * @param fileName The name of the file
     * @param userId The ID of the user who owns this document
     * @param localImagePath The local path to the image
     * @param recognizedText The text extracted from the image
     */
    public ScannedDocument(String fileName, String userId, String localImagePath, String recognizedText) {
        this(fileName, userId);
        this.localImagePath = localImagePath;
        this.localImagePaths.add(localImagePath);
        this.recognizedText = recognizedText;
        this.type = "TXT";
    }
    
    /**
     * Constructs a new ScannedDocument with multiple image paths and extracted text
     * 
     * @param fileName The name of the file
     * @param userId The ID of the user who owns this document
     * @param localImagePaths List of local paths to images
     * @param recognizedText The text extracted from the images
     */
    public ScannedDocument(String fileName, String userId, List<String> localImagePaths, String recognizedText) {
        this(fileName, userId);
        this.localImagePaths = new ArrayList<>(localImagePaths);
        if (!localImagePaths.isEmpty()) {
            this.localImagePath = localImagePaths.get(0);
        }
        this.recognizedText = recognizedText;
        this.type = "TXT";
    }

    /**
     * Gets the unique identifier for this document.
     * 
     * @return The document ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this document.
     * 
     * @param id The document ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the local path to the scanned image file.
     * 
     * @return The local image path
     */
    public String getLocalImagePath() {
        return localImagePath;
    }

    /**
     * Sets the local path to the scanned image file.
     * 
     * @param localImagePath The local image path
     */
    public void setLocalImagePath(String localImagePath) {
        this.localImagePath = localImagePath;
        
        // Cập nhật cả danh sách ảnh nếu chưa có
        if (localImagePaths == null) {
            localImagePaths = new ArrayList<>();
        }
        
        // Thêm ảnh vào danh sách nếu chưa tồn tại
        if (!localImagePaths.contains(localImagePath)) {
            localImagePaths.add(localImagePath);
        }
    }
    
    /**
     * Gets the list of local paths to the scanned image files.
     * 
     * @return The list of local image paths
     */
    public List<String> getLocalImagePaths() {
        return localImagePaths;
    }
    
    /**
     * Sets the list of local paths to the scanned image files.
     * 
     * @param localImagePaths The list of local image paths
     */
    public void setLocalImagePaths(List<String> localImagePaths) {
        this.localImagePaths = new ArrayList<>(localImagePaths);
        if (!localImagePaths.isEmpty()) {
            this.localImagePath = localImagePaths.get(0);
        }
    }
    
    /**
     * Adds a local image path to the list.
     * 
     * @param imagePath The local image path to add
     */
    public void addLocalImagePath(String imagePath) {
        if (this.localImagePaths == null) {
            this.localImagePaths = new ArrayList<>();
        }
        if (!this.localImagePaths.contains(imagePath)) {
            this.localImagePaths.add(imagePath);
        }
        
        // Cập nhật localImagePath nếu đây là ảnh đầu tiên
        if (this.localImagePath == null || this.localImagePath.isEmpty()) {
            this.localImagePath = imagePath;
        }
    }
    
    /**
     * Gets the number of images in this document.
     * 
     * @return The number of images
     */
    public int getImageCount() {
        return localImagePaths != null ? localImagePaths.size() : 0;
    }

    /**
     * Gets the URL of the image stored in the cloud.
     * 
     * @return The cloud image URL
     */
    public String getCloudImageUrl() {
        return cloudImageUrl;
    }

    /**
     * Sets the URL of the image stored in the cloud.
     * 
     * @param cloudImageUrl The cloud image URL
     */
    public void setCloudImageUrl(String cloudImageUrl) {
        this.cloudImageUrl = cloudImageUrl;
        
        // Cập nhật cả danh sách URL ảnh nếu chưa có
        if (cloudImageUrls == null) {
            cloudImageUrls = new ArrayList<>();
        }
        
        // Thêm URL vào danh sách nếu chưa tồn tại
        if (!cloudImageUrls.contains(cloudImageUrl)) {
            cloudImageUrls.add(cloudImageUrl);
        }
    }
    
    /**
     * Gets the list of URLs of images stored in the cloud.
     * 
     * @return The list of cloud image URLs
     */
    public List<String> getCloudImageUrls() {
        return cloudImageUrls;
    }
    
    /**
     * Sets the list of URLs of images stored in the cloud.
     * 
     * @param cloudImageUrls The list of cloud image URLs
     */
    public void setCloudImageUrls(List<String> cloudImageUrls) {
        this.cloudImageUrls = new ArrayList<>(cloudImageUrls);
        if (!cloudImageUrls.isEmpty()) {
            this.cloudImageUrl = cloudImageUrls.get(0);
        }
    }
    
    /**
     * Adds a cloud image URL to the list.
     * 
     * @param cloudImageUrl The cloud image URL to add
     */
    public void addCloudImageUrl(String cloudImageUrl) {
        if (this.cloudImageUrls == null) {
            this.cloudImageUrls = new ArrayList<>();
        }
        if (!this.cloudImageUrls.contains(cloudImageUrl)) {
            this.cloudImageUrls.add(cloudImageUrl);
        }
        
        // Cập nhật cloudImageUrl nếu đây là URL đầu tiên
        if (this.cloudImageUrl == null || this.cloudImageUrl.isEmpty()) {
            this.cloudImageUrl = cloudImageUrl;
        }
    }

    /**
     * Gets the text that was recognized from the image.
     * 
     * @return The recognized text
     */
    public String getRecognizedText() {
        return recognizedText;
    }

    /**
     * Sets the text that was recognized from the image.
     * 
     * @param recognizedText The recognized text
     */
    public void setRecognizedText(String recognizedText) {
        this.recognizedText = recognizedText;
    }

    /**
     * Gets the AI-generated summary of the recognized text.
     * 
     * @return The summary text
     */
    public String getSummaryText() {
        return summaryText;
    }

    /**
     * Sets the AI-generated summary of the recognized text.
     * 
     * @param summaryText The summary text
     */
    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    /**
     * Gets the timestamp when this document was created.
     * 
     * @return The creation timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp when this document was created.
     * 
     * @param timestamp The creation timestamp
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets the language of the recognized text.
     * 
     * @return The language code
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Sets the language of the recognized text.
     * 
     * @param language The language code
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Gets the name of the file.
     * 
     * @return The file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the name of the file.
     * 
     * @param fileName The file name
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets the ID of the user who owns this document.
     * 
     * @return The user ID
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * Sets the ID of the user who owns this document.
     * 
     * @param userId The user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Checks if the metadata for this document has been synced to the cloud.
     * 
     * @return True if metadata is synced, false otherwise
     */
    public boolean isMetadataSynced() {
        return isMetadataSynced;
    }

    /**
     * Sets whether the metadata for this document has been synced to the cloud.
     * 
     * @param isMetadataSynced True if metadata is synced, false otherwise
     */
    public void setMetadataSynced(boolean isMetadataSynced) {
        this.isMetadataSynced = isMetadataSynced;
    }

    /**
     * Checks if the image for this document has been synced to the cloud.
     * 
     * @return True if image is synced, false otherwise
     */
    public boolean isImageSynced() {
        return isImageSynced;
    }

    /**
     * Sets whether the image for this document has been synced to the cloud.
     * 
     * @param isImageSynced True if image is synced, false otherwise
     */
    public void setImageSynced(boolean isImageSynced) {
        this.isImageSynced = isImageSynced;
    }
    
    /**
     * Gets the type of this document (PDF, TXT, Image, etc.).
     * 
     * @return The document type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Sets the type of this document (PDF, TXT, Image, etc.).
     * 
     * @param type The document type
     */
    public void setType(String type) {
        this.type = type;
    }
    
    /**
     * Gets the extracted text from the document.
     * This is an alias for getRecognizedText() for compatibility with the old Document class.
     * 
     * @return The extracted text
     */
    public String getExtractedText() {
        return recognizedText;
    }
    
    /**
     * Sets the extracted text for the document.
     * This is an alias for setRecognizedText() for compatibility with the old Document class.
     * 
     * @param extractedText The extracted text
     */
    public void setExtractedText(String extractedText) {
        this.recognizedText = extractedText;
    }
    
    /**
     * Gets the creation date of the document.
     * This is an alias for getTimestamp() for compatibility with the old Document class.
     * 
     * @return The creation date
     */
    public Date getCreatedDate() {
        return timestamp;
    }
    
    /**
     * Sets the creation date of the document.
     * This is an alias for setTimestamp() for compatibility with the old Document class.
     * 
     * @param createdDate The creation date
     */
    public void setCreatedDate(Date createdDate) {
        this.timestamp = createdDate;
    }
    
    /**
     * Gets the image path of the document.
     * This is an alias for getLocalImagePath() for compatibility with the old Document class.
     * 
     * @return The image path
     */
    public String getImagePath() {
        return localImagePath;
    }
    
    /**
     * Sets the image path of the document.
     * This is an alias for setLocalImagePath() for compatibility with the old Document class.
     * 
     * @param imagePath The image path
     */
    public void setImagePath(String imagePath) {
        setLocalImagePath(imagePath);
    }
    
    /**
     * Gets the image paths of the document.
     * This is an alias for getLocalImagePaths() for compatibility with the old Document class.
     * 
     * @return The image paths
     */
    public List<String> getImagePaths() {
        return localImagePaths;
    }
    
    /**
     * Sets the image paths of the document.
     * This is an alias for setLocalImagePaths() for compatibility with the old Document class.
     * 
     * @param imagePaths The image paths
     */
    public void setImagePaths(List<String> imagePaths) {
        setLocalImagePaths(imagePaths);
    }
    
    /**
     * Adds an image path to the document.
     * This is an alias for addLocalImagePath() for compatibility with the old Document class.
     * 
     * @param imagePath The image path to add
     */
    public void addImagePath(String imagePath) {
        addLocalImagePath(imagePath);
    }
} 