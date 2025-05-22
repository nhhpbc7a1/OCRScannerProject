package hcmute.edu.vn.ocrscannerproject.ui.settings;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.text.DecimalFormat;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import hcmute.edu.vn.ocrscannerproject.core.entities.User;
import hcmute.edu.vn.ocrscannerproject.data.repository.ScannedDocumentRepository;
import hcmute.edu.vn.ocrscannerproject.services.AuthService;

/**
 * ViewModel for managing settings screen data and operations.
 */
public class SettingsViewModel extends AndroidViewModel {
    
    private static final String TAG = "SettingsViewModel";
    private static final String PREF_START_WITH_CAMERA = "pref_start_with_camera";
    private static final String PREF_FILE_NAME_FORMAT = "pref_file_name_format";
    private static final String PREF_SAVE_LOCATION = "pref_save_location";
    
    private final AuthService authService;
    private final ScannedDocumentRepository repository;
    private final Executor executor;
    private final Handler mainHandler;
    private final SharedPreferences preferences;
    
    private final MutableLiveData<String> userName = new MutableLiveData<>();
    private final MutableLiveData<String> userEmail = new MutableLiveData<>();
    private final MutableLiveData<String> storageUsage = new MutableLiveData<>();
    private final MutableLiveData<Integer> storagePercentage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSyncing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isCleaningCache = new MutableLiveData<>(false);
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();
    
    // New LiveData for settings
    private final MutableLiveData<Boolean> startWithCamera = new MutableLiveData<>();
    private final MutableLiveData<String> fileNameFormat = new MutableLiveData<>();
    private final MutableLiveData<String> saveLocation = new MutableLiveData<>();
    
    // Last calculated storage sizes
    private String appStorageSize = "0 B";
    private String cacheSize = "0 B";
    
    public SettingsViewModel(@NonNull Application application) {
        super(application);
        authService = new AuthService();
        repository = new ScannedDocumentRepository(application);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        preferences = PreferenceManager.getDefaultSharedPreferences(application);
        
        // Load initial data
        loadUserData();
        refreshStorageInfo();
        loadSettings();
    }
    
    /**
     * Loads settings from SharedPreferences.
     */
    private void loadSettings() {
        startWithCamera.setValue(preferences.getBoolean(PREF_START_WITH_CAMERA, false));
        fileNameFormat.setValue(preferences.getString(PREF_FILE_NAME_FORMAT, "Scan_yyyyMMdd_HHmmss"));
        saveLocation.setValue(preferences.getString(PREF_SAVE_LOCATION, 
                getApplication().getExternalFilesDir(null) + "/OCRScanner"));
    }
    
    /**
     * Loads the user data from auth service.
     */
    public void loadUserData() {
        User currentUser = authService.getCurrentUser();
        if (currentUser != null) {
            userName.setValue(currentUser.getDisplayName());
            userEmail.setValue(currentUser.getEmail());
        } else {
            userName.setValue("Guest User");
            userEmail.setValue("Not signed in");
        }
    }
    
    /**
     * Refreshes the storage usage information.
     */
    public void refreshStorageInfo() {
        executor.execute(() -> {
            try {
                // Calculate storage usage for cloud and local storage
                String usage = repository.getFormattedQuotaUsage();
                int percentage = repository.getQuotaUsagePercentage();
                
                // Calculate app storage size
                calculateAppStorageSize();
                
                mainHandler.post(() -> {
                    storageUsage.setValue(usage);
                    storagePercentage.setValue(percentage);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing storage info", e);
                mainHandler.post(() -> 
                    toastMessage.setValue("Failed to refresh storage information")
                );
            }
        });
    }
    
    /**
     * Calculates the app storage size and cache size.
     */
    private void calculateAppStorageSize() {
        Context context = getApplication().getApplicationContext();
        
        // Calculate application storage
        File appDir = context.getFilesDir();
        long appSize = getFolderSize(appDir);
        appStorageSize = formatSize(appSize);
        
        // Calculate cache size
        File cacheDir = context.getCacheDir();
        long cacheDirSize = getFolderSize(cacheDir);
        
        // Add external cache if available
        File externalCacheDir = context.getExternalCacheDir();
        if (externalCacheDir != null) {
            cacheDirSize += getFolderSize(externalCacheDir);
        }
        
        cacheSize = formatSize(cacheDirSize);
    }
    
    /**
     * Gets the app storage size.
     * 
     * @return The app storage size in human-readable format
     */
    public String getAppStorageSize() {
        return appStorageSize;
    }
    
    /**
     * Gets the cache size.
     * 
     * @return The cache size in human-readable format
     */
    public String getCacheSize() {
        return cacheSize;
    }
    
    /**
     * Recursively gets the size of a folder.
     * 
     * @param folder The folder to get the size of
     * @return The size of the folder in bytes
     */
    private long getFolderSize(File folder) {
        long size = 0;
        if (folder != null && folder.exists()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        size += getFolderSize(file);
                    } else {
                        size += file.length();
                    }
                }
            }
        }
        return size;
    }
    
    /**
     * Formats a size in bytes to a human-readable string.
     * 
     * @param size The size in bytes
     * @return A human-readable string
     */
    private String formatSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        
        // Keep it within array bounds
        digitGroups = Math.min(digitGroups, units.length - 1);
        
        DecimalFormat df = new DecimalFormat("#,##0.##");
        return df.format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
    
    /**
     * Checks if the app has camera permission.
     * 
     * @param context The context
     * @return True if the permission is granted, false otherwise
     */
    public boolean hasCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Checks if the app has storage permission.
     * 
     * @param context The context
     * @return True if the permission is granted, false otherwise
     */
    public boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 and above
            return Environment.isExternalStorageManager();
        } else {
            // For Android 9 and below
            return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Checks if the app has microphone permission.
     * 
     * @param context The context
     * @return True if the permission is granted, false otherwise
     */
    public boolean hasMicrophonePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Syncs local data with Firebase.
     */
    public void syncData() {
        if (Boolean.TRUE.equals(isSyncing.getValue())) {
            return;
        }
        
        isSyncing.setValue(true);
        executor.execute(() -> {
            try {
                repository.syncDocuments();
                refreshStorageInfo();
                mainHandler.post(() -> {
                    isSyncing.setValue(false);
                    toastMessage.setValue("Documents synchronized successfully");
                });
            } catch (Exception e) {
                Log.e(TAG, "Error syncing data", e);
                mainHandler.post(() -> {
                    isSyncing.setValue(false);
                    toastMessage.setValue("Failed to sync documents: " + e.getMessage());
                });
            }
        });
    }
    
    /**
     * Signs out the current user.
     */
    public void signOut() {
        authService.signOut();
        toastMessage.setValue("Signed out successfully");
        loadUserData();
    }
    
    /**
     * Cleans the app cache by deleting temporary files.
     */
    public void cleanCache() {
        if (Boolean.TRUE.equals(isCleaningCache.getValue())) {
            return;
        }
        
        isCleaningCache.setValue(true);
        executor.execute(() -> {
            try {
                Context context = getApplication().getApplicationContext();
                // Clear app cache
                clearCache(context.getCacheDir());
                
                // Clear external cache if available
                File externalCacheDir = context.getExternalCacheDir();
                if (externalCacheDir != null) {
                    clearCache(externalCacheDir);
                }
                
                mainHandler.post(() -> {
                    isCleaningCache.setValue(false);
                    toastMessage.setValue("Cache cleaned successfully");
                });
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning cache", e);
                mainHandler.post(() -> {
                    isCleaningCache.setValue(false);
                    toastMessage.setValue("Failed to clean cache: " + e.getMessage());
                });
            }
        });
    }
    
    /**
     * Sets whether to start with camera.
     * 
     * @param enable True to enable, false to disable
     */
    public void setStartWithCamera(boolean enable) {
        preferences.edit().putBoolean(PREF_START_WITH_CAMERA, enable).apply();
        startWithCamera.setValue(enable);
    }
    
    /**
     * Sets the file name format.
     * 
     * @param format The file name format
     */
    public void setFileNameFormat(String format) {
        preferences.edit().putString(PREF_FILE_NAME_FORMAT, format).apply();
        fileNameFormat.setValue(format);
    }
    
    /**
     * Sets the save location.
     * 
     * @param location The save location
     */
    public void setSaveLocation(String location) {
        preferences.edit().putString(PREF_SAVE_LOCATION, location).apply();
        saveLocation.setValue(location);
    }
    
    /**
     * Helper method to recursively delete files in a directory.
     * 
     * @param dir The directory to clear
     * @return True if the directory was cleared successfully, false otherwise
     */
    private boolean clearCache(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            for (File child : dir.listFiles()) {
                if (child.isDirectory()) {
                    clearCache(child);
                } else {
                    child.delete();
                }
            }
            return true;
        }
        return false;
    }
    
    /**
     * Gets the user name live data.
     * 
     * @return The user name live data
     */
    public LiveData<String> getUserName() {
        return userName;
    }
    
    /**
     * Gets the user email live data.
     * 
     * @return The user email live data
     */
    public LiveData<String> getUserEmail() {
        return userEmail;
    }
    
    /**
     * Gets the storage usage live data.
     * 
     * @return The storage usage live data
     */
    public LiveData<String> getStorageUsage() {
        return storageUsage;
    }
    
    /**
     * Gets the storage percentage live data.
     * 
     * @return The storage percentage live data
     */
    public LiveData<Integer> getStoragePercentage() {
        return storagePercentage;
    }
    
    /**
     * Gets the is syncing live data.
     * 
     * @return The is syncing live data
     */
    public LiveData<Boolean> getIsSyncing() {
        return isSyncing;
    }
    
    /**
     * Gets the is cleaning cache live data.
     * 
     * @return The is cleaning cache live data
     */
    public LiveData<Boolean> getIsCleaningCache() {
        return isCleaningCache;
    }
    
    /**
     * Gets the toast message live data.
     * 
     * @return The toast message live data
     */
    public LiveData<String> getToastMessage() {
        return toastMessage;
    }
    
    /**
     * Gets the start with camera live data.
     * 
     * @return The start with camera live data
     */
    public LiveData<Boolean> getStartWithCamera() {
        return startWithCamera;
    }
    
    /**
     * Gets the file name format live data.
     * 
     * @return The file name format live data
     */
    public LiveData<String> getFileNameFormat() {
        return fileNameFormat;
    }
    
    /**
     * Gets the save location live data.
     * 
     * @return The save location live data
     */
    public LiveData<String> getSaveLocation() {
        return saveLocation;
    }
} 