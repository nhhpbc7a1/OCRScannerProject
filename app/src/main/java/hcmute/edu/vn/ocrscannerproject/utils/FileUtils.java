package hcmute.edu.vn.ocrscannerproject.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility class for file operations.
 */
public class FileUtils {
    private static final String TAG = "FileUtils";
    
    /**
     * Creates a temporary file for storing camera images.
     * 
     * @param context The application context
     * @return The created file
     * @throws IOException If the file could not be created
     */
    public static File createTempImageFile(Context context) throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }
    
    /**
     * Creates a file for storing scanned document images.
     * 
     * @param context The application context
     * @param documentId The ID of the document
     * @return The created file
     * @throws IOException If the file could not be created
     */
    public static File createDocumentImageFile(Context context, String documentId) throws IOException {
        // Create a file in the app's private directory
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }
        
        String imageFileName = "DOC_" + documentId + ".jpg";
        File imageFile = new File(storageDir, imageFileName);
        if (!imageFile.exists()) {
            imageFile.createNewFile();
        }
        
        return imageFile;
    }
    
    /**
     * Formats a timestamp using the specified format.
     * 
     * @param timestamp The timestamp to format
     * @param format The format string (default: YYYY-MM-DD-HH-mm)
     * @return The formatted timestamp
     */
    public static String formatTimestamp(Date timestamp, String format) {
        if (format == null || format.isEmpty()) {
            format = "yyyy-MM-dd-HH-mm";
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(timestamp);
    }
    
    /**
     * Saves a bitmap to a file.
     * 
     * @param bitmap The bitmap to save
     * @param file The file to save to
     * @param quality The quality (0-100) to use when saving
     * @return True if the save was successful, false otherwise
     */
    public static boolean saveBitmapToFile(Bitmap bitmap, File file, int quality) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving bitmap to file: " + file.getAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * Creates a copy of a file from a Uri.
     * 
     * @param context The application context
     * @param sourceUri The source Uri
     * @param destinationFile The destination file
     * @return True if the copy was successful, false otherwise
     */
    public static boolean copyUriToFile(Context context, Uri sourceUri, File destinationFile) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
             OutputStream outputStream = new FileOutputStream(destinationFile)) {
            
            if (inputStream == null) {
                return false;
            }
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copying Uri to file: " + sourceUri.toString(), e);
            return false;
        }
    }
    
    /**
     * Gets a bitmap from a Uri.
     * 
     * @param context The application context
     * @param imageUri The image Uri
     * @return The bitmap, or null if it could not be loaded
     */
    public static Bitmap getBitmapFromUri(Context context, Uri imageUri) {
        try {
            return MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);
        } catch (IOException e) {
            Log.e(TAG, "Error getting bitmap from Uri: " + imageUri.toString(), e);
            return null;
        }
    }
    
    /**
     * Gets the file size in bytes.
     * 
     * @param file The file
     * @return The size in bytes, or 0 if the file does not exist
     */
    public static long getFileSize(File file) {
        if (file != null && file.exists()) {
            return file.length();
        }
        return 0;
    }
    
    /**
     * Gets the file extension from a path.
     * 
     * @param path The file path
     * @return The file extension, or an empty string if there is no extension
     */
    public static String getFileExtension(String path) {
        if (path != null && path.contains(".")) {
            return path.substring(path.lastIndexOf(".") + 1);
        }
        return "";
    }
} 