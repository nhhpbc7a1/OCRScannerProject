package hcmute.edu.vn.ocrscannerproject.ui.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.mlkit.vision.text.Text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.ocrscannerproject.R;

public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {
    private static final String TAG = "ImagePagerAdapter";
    private final Context context;
    private final List<String> imagePaths;
    private final Map<Integer, Text> recognizedTexts;
    private final Map<Integer, Bitmap> overlayBitmaps;
    private final Map<Integer, Integer> imageRotations;
    private final Map<Integer, Bitmap> rotatedBitmaps; // Store rotated bitmaps
    private OnImageLoadedListener onImageLoadedListener;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface OnImageLoadedListener {
        void onImageLoaded(int position, Bitmap bitmap);
    }

    public void setOnImageLoadedListener(OnImageLoadedListener listener) {
        this.onImageLoadedListener = listener;
    }

    public ImagePagerAdapter(Context context, List<String> imagePaths) {
        this.context = context;
        this.imagePaths = imagePaths;
        this.recognizedTexts = new HashMap<>();
        this.overlayBitmaps = new HashMap<>();
        this.imageRotations = new HashMap<>();
        this.rotatedBitmaps = new HashMap<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image_text, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imagePath = imagePaths.get(position);
        
        // Set a loading placeholder
        holder.imageView.setImageResource(R.drawable.ic_image_loading);
        
        // Load and process image in background
        executor.execute(() -> {
            try {
                // Load bitmap
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    // Store original bitmap
                    rotatedBitmaps.put(position, bitmap);
                    
                    // Create overlay if we have recognized text
                    final Bitmap displayBitmap;
                    if (recognizedTexts.containsKey(position)) {
                        displayBitmap = createOverlayBitmap(bitmap, position);
                        if (displayBitmap != null) {
                            overlayBitmaps.put(position, displayBitmap);
                        }
                    } else {
                        displayBitmap = bitmap;
                    }
                    
                    // Update UI on main thread
                    mainHandler.post(() -> {
                        if (holder.getAdapterPosition() == position) {
                            holder.imageView.setImageBitmap(displayBitmap);
                            setupTouchListener(holder.imageView, bitmap, position);
                            if (onImageLoadedListener != null) {
                                onImageLoadedListener.onImageLoaded(position, bitmap);
                            }
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        if (holder.getAdapterPosition() == position) {
                            holder.imageView.setImageResource(R.drawable.ic_image_error);
                        }
                    });
                    Log.e(TAG, "Failed to decode bitmap: " + imagePath);
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (holder.getAdapterPosition() == position) {
                        holder.imageView.setImageResource(R.drawable.ic_image_error);
                    }
                });
                Log.e(TAG, "Error loading image: " + e.getMessage(), e);
            }
        });
    }

    private void setupTouchListener(ImageView imageView, Bitmap bitmap, int position) {
        imageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float scaledX = event.getX() * (float)bitmap.getWidth() / v.getWidth();
                float scaledY = event.getY() * (float)bitmap.getHeight() / v.getHeight();
                handleTextSelection(position, scaledX, scaledY);
            }
            return true;
        });
    }

    @Override
    public void onViewRecycled(@NonNull ImageViewHolder holder) {
        super.onViewRecycled(holder);
        holder.imageView.setImageBitmap(null);
        holder.imageView.setOnTouchListener(null);
    }

    @Override
    public int getItemCount() {
        return imagePaths.size();
    }

    public void setRecognizedText(int position, Text text) {
        Log.d(TAG, "Setting recognized text for position " + position + " with " + 
              (text != null ? text.getTextBlocks().size() : 0) + " blocks");
        recognizedTexts.put(position, text);
        
        // Update overlay in background
        executor.execute(() -> {
            Bitmap originalBitmap = rotatedBitmaps.get(position);
            if (originalBitmap != null) {
                Bitmap overlay = createOverlayBitmap(originalBitmap, position);
                if (overlay != null) {
                    overlayBitmaps.put(position, overlay);
                    mainHandler.post(() -> notifyItemChanged(position));
                }
            }
        });
    }

    private Bitmap createOverlayBitmap(Bitmap original, int position) {
        Text recognizedText = recognizedTexts.get(position);
        if (recognizedText == null) return null;

        Bitmap overlay = original.copy(original.getConfig(), true);
        Canvas canvas = new Canvas(overlay);
        
        Paint textBlockPaint = new Paint();
        textBlockPaint.setStyle(Paint.Style.STROKE);
        textBlockPaint.setColor(Color.BLUE);
        textBlockPaint.setStrokeWidth(4);
        
        Paint textBackgroundPaint = new Paint();
        textBackgroundPaint.setColor(Color.WHITE);
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setAlpha(128);

        for (Text.TextBlock block : recognizedText.getTextBlocks()) {
            if (block.getBoundingBox() != null) {
                Rect blockRect = block.getBoundingBox();
                RectF drawRect = new RectF(blockRect);

                // Draw semi-transparent white background
                canvas.drawRect(drawRect, textBackgroundPaint);
                
                // Draw blue border
                canvas.drawRect(drawRect, textBlockPaint);
            }
        }
        
        return overlay;
    }

    private void handleTextSelection(int position, float x, float y) {
        Text recognizedText = recognizedTexts.get(position);
        if (recognizedText == null) return;
        
        // No need to transform coordinates since the image and text blocks are already correctly oriented
        // Find the text block that was tapped
        for (Text.TextBlock block : recognizedText.getTextBlocks()) {
            if (block.getBoundingBox() != null && 
                block.getBoundingBox().contains((int)x, (int)y)) {
                // Copy text to clipboard
                copyToClipboard(block.getText());
                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
                break;
            }
        }
    }

    public void copyAllText(int position) {
        Text recognizedText = recognizedTexts.get(position);
        if (recognizedText != null) {
            StringBuilder allText = new StringBuilder();
            for (Text.TextBlock block : recognizedText.getTextBlocks()) {
                allText.append(block.getText()).append("\n");
            }
            copyToClipboard(allText.toString());
            Toast.makeText(context, "All text copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) 
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Extracted Text", text);
        clipboard.setPrimaryClip(clip);
    }

    public Text getRecognizedText(int position) {
        return recognizedTexts.get(position);
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);
        }
    }
} 