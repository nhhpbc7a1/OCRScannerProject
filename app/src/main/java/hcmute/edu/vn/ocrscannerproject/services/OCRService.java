package hcmute.edu.vn.ocrscannerproject.services;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for performing OCR (Optical Character Recognition) on images.
 */
public class OCRService {
    private static final String TAG = "OCRService";
    
    private final TextRecognizer textRecognizer;
    
    /**
     * Initializes the OCR service.
     */
    public OCRService() {
        // Create an instance of the TextRecognizer with Latin script
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }
    
    /**
     * Recognizes text in an image.
     * 
     * @param bitmap The bitmap image to process
     * @param callback The callback to receive the result
     */
    public void recognizeText(Bitmap bitmap, OCRResultCallback callback) {
        if (bitmap == null) {
            callback.onError(new IllegalArgumentException("Bitmap cannot be null"));
            return;
        }
        
        // Create an InputImage from the bitmap
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        
        // Process the image
        textRecognizer.process(image)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text text) {
                        // Extract the recognized text
                        String recognizedText = text.getText();
                        List<TextBlock> textBlocks = new ArrayList<>();
                        
                        // Process the blocks of text
                        for (Text.TextBlock block : text.getTextBlocks()) {
                            Rect boundingBox = block.getBoundingBox();
                            Point[] cornerPoints = convertToPointArray(block.getCornerPoints());
                            String blockText = block.getText();
                            List<TextLine> textLines = new ArrayList<>();
                            
                            // Process the lines in each block
                            for (Text.Line line : block.getLines()) {
                                Rect lineBoundingBox = line.getBoundingBox();
                                Point[] lineCornerPoints = convertToPointArray(line.getCornerPoints());
                                String lineText = line.getText();
                                
                                textLines.add(new TextLine(lineText, lineBoundingBox, lineCornerPoints));
                            }
                            
                            textBlocks.add(new TextBlock(blockText, boundingBox, cornerPoints, textLines));
                        }
                        
                        // Create the result
                        OCRResult result = new OCRResult(recognizedText, textBlocks);
                        
                        // Invoke the callback
                        callback.onSuccess(result);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Error processing image with OCR", e);
                        callback.onError(e);
                    }
                });
    }
    
    /**
     * Recognizes text in an image, returning a Task that can be used with coroutines or other async mechanisms.
     * 
     * @param bitmap The bitmap image to process
     * @return A Task that will complete with the OCR result
     * @throws IOException If there is an error creating the InputImage
     */
    public Task<Text> recognizeTextTask(Bitmap bitmap) throws IOException {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap cannot be null");
        }
        
        // Create an InputImage from the bitmap
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        
        // Process the image and return the Task
        return textRecognizer.process(image);
    }
    
    /**
     * Closes the OCR service, releasing any resources.
     */
    public void close() {
        textRecognizer.close();
    }
    
    /**
     * Converts a Point[] array to an array of Point objects.
     * 
     * @param cornerPoints The array of corner points
     * @return An array of Point objects
     */
    private Point[] convertToPointArray(android.graphics.Point[] cornerPoints) {
        if (cornerPoints == null) {
            return new Point[0];
        }
        
        Point[] points = new Point[cornerPoints.length];
        for (int i = 0; i < cornerPoints.length; i++) {
            points[i] = new Point(cornerPoints[i].x, cornerPoints[i].y);
        }
        
        return points;
    }
    
    /**
     * Callback interface for OCR results.
     */
    public interface OCRResultCallback {
        /**
         * Called when the OCR process completes successfully.
         * 
         * @param result The OCR result
         */
        void onSuccess(OCRResult result);
        
        /**
         * Called when the OCR process fails.
         * 
         * @param e The exception that caused the failure
         */
        void onError(Exception e);
    }
    
    /**
     * Represents the result of an OCR operation.
     */
    public static class OCRResult {
        private final String text;
        private final List<TextBlock> blocks;
        
        /**
         * Constructs a new OCRResult.
         * 
         * @param text The recognized text
         * @param blocks The blocks of text
         */
        public OCRResult(String text, List<TextBlock> blocks) {
            this.text = text;
            this.blocks = blocks;
        }
        
        /**
         * Gets the recognized text.
         * 
         * @return The text
         */
        public String getText() {
            return text;
        }
        
        /**
         * Gets the blocks of text.
         * 
         * @return The blocks
         */
        public List<TextBlock> getBlocks() {
            return blocks;
        }
    }
    
    /**
     * Represents a block of text in the image.
     */
    public static class TextBlock {
        private final String text;
        private final Rect boundingBox;
        private final Point[] cornerPoints;
        private final List<TextLine> lines;
        
        /**
         * Constructs a new TextBlock.
         * 
         * @param text The text in the block
         * @param boundingBox The bounding box of the block
         * @param cornerPoints The corner points of the block
         * @param lines The lines in the block
         */
        public TextBlock(String text, Rect boundingBox, Point[] cornerPoints, List<TextLine> lines) {
            this.text = text;
            this.boundingBox = boundingBox;
            this.cornerPoints = cornerPoints;
            this.lines = lines;
        }
        
        /**
         * Gets the text in the block.
         * 
         * @return The text
         */
        public String getText() {
            return text;
        }
        
        /**
         * Gets the bounding box of the block.
         * 
         * @return The bounding box
         */
        public Rect getBoundingBox() {
            return boundingBox;
        }
        
        /**
         * Gets the corner points of the block.
         * 
         * @return The corner points
         */
        public Point[] getCornerPoints() {
            return cornerPoints;
        }
        
        /**
         * Gets the lines in the block.
         * 
         * @return The lines
         */
        public List<TextLine> getLines() {
            return lines;
        }
    }
    
    /**
     * Represents a line of text in the image.
     */
    public static class TextLine {
        private final String text;
        private final Rect boundingBox;
        private final Point[] cornerPoints;
        
        /**
         * Constructs a new TextLine.
         * 
         * @param text The text in the line
         * @param boundingBox The bounding box of the line
         * @param cornerPoints The corner points of the line
         */
        public TextLine(String text, Rect boundingBox, Point[] cornerPoints) {
            this.text = text;
            this.boundingBox = boundingBox;
            this.cornerPoints = cornerPoints;
        }
        
        /**
         * Gets the text in the line.
         * 
         * @return The text
         */
        public String getText() {
            return text;
        }
        
        /**
         * Gets the bounding box of the line.
         * 
         * @return The bounding box
         */
        public Rect getBoundingBox() {
            return boundingBox;
        }
        
        /**
         * Gets the corner points of the line.
         * 
         * @return The corner points
         */
        public Point[] getCornerPoints() {
            return cornerPoints;
        }
    }
} 