package hcmute.edu.vn.ocrscannerproject.data.local.converter;

import androidx.room.TypeConverter;

import java.util.Date;

/**
 * Type converter for Room to convert between Date and Long (timestamp).
 */
public class DateConverter {
    
    /**
     * Converts a Date object to a Long timestamp (milliseconds since epoch).
     * 
     * @param date The Date to convert
     * @return The timestamp as a Long, or null if date is null
     */
    @TypeConverter
    public static Long fromDate(Date date) {
        return date == null ? null : date.getTime();
    }
    
    /**
     * Converts a Long timestamp (milliseconds since epoch) to a Date object.
     * 
     * @param timestamp The timestamp to convert
     * @return The Date object, or null if timestamp is null
     */
    @TypeConverter
    public static Date toDate(Long timestamp) {
        return timestamp == null ? null : new Date(timestamp);
    }
} 