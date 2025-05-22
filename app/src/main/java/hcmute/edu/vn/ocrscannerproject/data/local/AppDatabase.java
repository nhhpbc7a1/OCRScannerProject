package hcmute.edu.vn.ocrscannerproject.data.local;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import hcmute.edu.vn.ocrscannerproject.data.local.dao.ScannedDocumentDao;
import hcmute.edu.vn.ocrscannerproject.data.local.entity.ScannedDocumentEntity;

/**
 * The Room database for the application.
 */
@Database(entities = {ScannedDocumentEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "ocr_scanner_db";
    private static volatile AppDatabase INSTANCE;
    
    /**
     * Get the DAO for ScannedDocument entities.
     * 
     * @return The ScannedDocumentDao
     */
    public abstract ScannedDocumentDao scannedDocumentDao();
    
    /**
     * Get a singleton instance of the AppDatabase.
     * 
     * @param context The application context
     * @return The singleton instance of AppDatabase
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
} 