package hcmute.edu.vn.ocrscannerproject.ui.extract;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.text.Text;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import hcmute.edu.vn.ocrscannerproject.R;

public class TextSelectionManager {
    private final Context context;
    private final Paint textPaint = new Paint();
    private final Paint textBackgroundPaint = new Paint();
    private final Paint selectionPaint = new Paint();
    private final List<Text.TextBlock> textBlocks;
    private PointF selectionStart;
    private PointF selectionEnd;
    private boolean isSelecting;
    private PopupWindow selectionMenu;
    private String selectedText;
    private final float imageScaleX;
    private final float imageScaleY;
    private final float imageTranslateX;
    private final float imageTranslateY;
    private static final float MIN_TEXT_SIZE = 24f;
    private static final float MAX_TEXT_SIZE = 36f;
    private static final float TEXT_SIZE_STEP = 2f;
    private static final float PADDING_HORIZONTAL = 8f;
    private static final float PADDING_VERTICAL = 6f;
    private static final float LINE_SPACING = 4f;
    private static final float TEXT_MARGIN_BOTTOM = 4f;
    private static final float CORNER_RADIUS = 6f;
    private float lastLineBottom = 0;
    private static final float HANDLE_RADIUS = 30f;
    private static final float HANDLE_TOUCH_RADIUS = 50f;
    private static final int HANDLE_COLOR = Color.rgb(33, 150, 243);
    private static final int SELECTION_COLOR = Color.argb(50, 33, 150, 243);
    private static final float HANDLE_STROKE_WIDTH = 4f;
    private static final Paint handlePaint;
    private boolean isMenuShowing = false;
    private DrawnWord startWord = null;
    private DrawnWord endWord = null;
    private boolean hasWindowFocus = true;
    private boolean shouldShowMenu = false;
    private float menuX, menuY;
    private boolean isHandlesDragging = false;
    private boolean isDraggingStartHandle = false;
    private RectF startHandleBounds;
    private RectF endHandleBounds;
    private static final long TAP_TIMEOUT = 200; // milliseconds
    private long touchStartTime;
    private PointF touchStartPoint;
    private boolean isTapSelection = false;
    private PointF currentHandlePosition;
    private static final float SNAP_THRESHOLD = 50f; // Distance threshold for snapping to words
    private static final long WORD_UPDATE_DELAY = 100; // milliseconds
    private long lastWordUpdateTime = 0;
    private DrawnWord lastNearestWord = null;
    private boolean isTranslationEnabled = false;
    private Map<DrawnWord, String> originalTexts = new HashMap<>();
    private Map<DrawnWord, String> translatedTexts = new HashMap<>();
    private FloatingActionButton translateButton;
    private static final int TRANSLATE_BUTTON_MARGIN = 32;
    private static final int TRANSLATE_BUTTON_SIZE = 56;
    private static final String OPENAI_API_KEY = "";
    private static final String OPENAI_API_URL = "https://models.inference.ai.azure.com";
    private static final String MODEL = "gpt-4o";
    private final OkHttpClient client = new OkHttpClient();

    static {
        handlePaint = new Paint();
        handlePaint.setColor(HANDLE_COLOR);
        handlePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        handlePaint.setStrokeWidth(HANDLE_STROKE_WIDTH);
        handlePaint.setAntiAlias(true);
    }

    private class DrawnWord {
        RectF rect;
        RectF fullRect;
        String text;
        float textSize;
        float x;
        float y;

        DrawnWord(String text, RectF rect, RectF fullRect, float textSize, float x, float y) {
            this.text = text;
            this.rect = rect;
            this.fullRect = fullRect;
            this.textSize = textSize;
            this.x = x;
            this.y = y;
        }
    }

    private List<DrawnWord> drawnWords = new ArrayList<>();

    public TextSelectionManager(Context context, List<Text.TextBlock> textBlocks, 
                              float imageScaleX, float imageScaleY,
                              float imageTranslateX, float imageTranslateY) {
        this.context = context;
        this.textBlocks = textBlocks;
        this.imageScaleX = imageScaleX;
        this.imageScaleY = imageScaleY;
        this.imageTranslateX = imageTranslateX;
        this.imageTranslateY = imageTranslateY;

        setupPaints();
        setupTranslateButton();
    }

    private void setupPaints() {
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(MAX_TEXT_SIZE);
        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.LEFT);

        textBackgroundPaint.setColor(Color.WHITE);
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setShadowLayer(6f, 0f, 2f, Color.argb(60, 0, 0, 0));

        selectionPaint.setColor(SELECTION_COLOR);
        selectionPaint.setStyle(Paint.Style.FILL);

        startHandleBounds = new RectF();
        endHandleBounds = new RectF();
    }

    private void setupTranslateButton() {
        translateButton = new FloatingActionButton(context);
        translateButton.setImageResource(R.drawable.ic_translate);
        translateButton.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        translateButton.setImageTintList(ColorStateList.valueOf(Color.rgb(33, 150, 243))); // Blue color
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            TRANSLATE_BUTTON_SIZE,
            TRANSLATE_BUTTON_SIZE
        );
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setMargins(0, 0, TRANSLATE_BUTTON_MARGIN, TRANSLATE_BUTTON_MARGIN);
        translateButton.setLayoutParams(params);
        
        translateButton.setOnClickListener(v -> toggleTranslation());
    }

    /**
     * Returns the translate button for adding to the view hierarchy.
     * @return The FloatingActionButton used for translation
     */
    public FloatingActionButton getTranslateButton() {
        return translateButton;
    }

    private void toggleTranslation() {
        isTranslationEnabled = !isTranslationEnabled;
        
        if (isTranslationEnabled) {
            // Save original texts if not already saved
            if (originalTexts.isEmpty()) {
                for (DrawnWord word : drawnWords) {
                    originalTexts.put(word, word.text);
                }
            }
            // Translate all words if not already translated
            if (translatedTexts.isEmpty()) {
                translateAllWords();
            } else {
                // Use cached translations
                for (DrawnWord word : drawnWords) {
                    String translatedText = translatedTexts.get(word);
                    if (translatedText != null) {
                        word.text = translatedText;
                    }
                }
            }
        } else {
            // Restore original texts
            for (DrawnWord word : drawnWords) {
                String originalText = originalTexts.get(word);
                if (originalText != null) {
                    word.text = originalText;
                }
            }
        }
        
        // Update button appearance
        translateButton.setImageTintList(ColorStateList.valueOf(
            isTranslationEnabled ? Color.GREEN : Color.rgb(33, 150, 243)
        ));

        // Force redraw
        if (context instanceof Activity) {
            View view = ((Activity) context).findViewById(android.R.id.content);
            if (view != null) {
                view.invalidate();
            }
        }
    }

    private void translateAllWords() {
        // Create a list of all words to translate
        List<String> textsToTranslate = drawnWords.stream()
            .map(word -> word.text)
            .collect(Collectors.toList());

        // Join all words with a special separator that won't appear in normal text
        String combinedText = String.join("\n", textsToTranslate);

        // Show loading dialog
        Dialog loadingDialog = new Dialog(context);
        loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        loadingDialog.setContentView(R.layout.dialog_loading);
        loadingDialog.setCancelable(false);
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        final TextView loadingText = loadingDialog.findViewById(R.id.loading_text);
        loadingDialog.show();

        // Create a new thread for translation
        new Thread(() -> {
            final int MAX_ATTEMPTS = 3;
            final long BASE_DELAY = 2000; // Start with 2 seconds
            
            for (int currentAttempt = 0; currentAttempt < MAX_ATTEMPTS; currentAttempt++) {
                try {
                    if (currentAttempt > 0) {
                        // Update loading message to show retry attempt
                        final int attemptNumber = currentAttempt + 1;
                        if (context instanceof Activity) {
                            ((Activity) context).runOnUiThread(() -> {
                                loadingText.setText("Service is busy. Retrying... (" + attemptNumber + "/" + MAX_ATTEMPTS + ")");
                            });
                        }
                        Thread.sleep(BASE_DELAY * (1L << currentAttempt)); // Exponential backoff
                    }

                    String prompt = "Translate the following Vietnamese text to English. Keep each line separate:\n\n" + combinedText;
                    String translatedText = callOpenAI(prompt);
                    String[] translatedWords = translatedText.split("\n");

                    // Update UI on main thread
                    if (context instanceof Activity) {
                        ((Activity) context).runOnUiThread(() -> {
                            loadingDialog.dismiss();
                            
                            // Update the translated texts map
                            for (int i = 0; i < Math.min(drawnWords.size(), translatedWords.length); i++) {
                                DrawnWord word = drawnWords.get(i);
                                String translated = translatedWords[i].trim();
                                translatedTexts.put(word, translated);
                                word.text = translated;
                            }

                            // Force a redraw
                            View view = ((Activity) context).findViewById(android.R.id.content);
                            if (view != null) {
                                view.invalidate();
                            }
                        });
                    }
                    return; // Success, exit the retry loop

                } catch (IOException e) {
                    if (currentAttempt >= MAX_ATTEMPTS - 1 || !e.getMessage().contains("Rate limit exceeded")) {
                        e.printStackTrace();
                        if (context instanceof Activity) {
                            ((Activity) context).runOnUiThread(() -> {
                                loadingDialog.dismiss();
                                String errorMessage;
                                if (e.getMessage().contains("Rate limit exceeded")) {
                                    errorMessage = "The service is currently experiencing high demand.\nPlease try again in a few minutes.";
                                } else {
                                    errorMessage = "Failed to translate text: " + e.getMessage();
                                }
                                showErrorDialog("Translation Error", errorMessage);
                            });
                        }
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (context instanceof Activity) {
                        ((Activity) context).runOnUiThread(() -> {
                            loadingDialog.dismiss();
                            showErrorDialog("Error", "An unexpected error occurred while translating the text.");
                        });
                    }
                    return;
                }
            }
        }).start();
    }

    private String callOpenAI(String prompt) throws IOException {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        JSONObject requestBody = new JSONObject();
        try {
            // Create messages array
            JSONArray messages = new JSONArray();
            
            // Add system message
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a helpful assistant that translates Vietnamese text to English. Maintain the same format and structure of the input text.");
            messages.put(systemMessage);
            
            // Add user message
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.put(userMessage);
            
            // Create request body exactly like marketplace example
            requestBody.put("messages", messages);
            requestBody.put("model", MODEL);
            requestBody.put("temperature", 0.7);
            requestBody.put("top_p", 0.95);
            requestBody.put("max_tokens", 16384);
            requestBody.put("stream", false);

            // Build request with exact path
            String fullUrl = OPENAI_API_URL + "/chat/completions";
            Request request = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    Log.e("API_ERROR", "Error response: " + errorBody);
                    throw new IOException("Unexpected response: " + response + "\nError body: " + errorBody);
                }
                
                String responseBody = response.body().string();
                Log.d("API_RESPONSE", "Response: " + responseBody);
                
                JSONObject jsonResponse = new JSONObject(responseBody);
                return jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
            }
        } catch (JSONException e) {
            Log.e("API_ERROR", "JSON error: " + e.getMessage());
            throw new IOException("Failed to process request/response: " + e.getMessage());
        }
    }

    private void showErrorDialog(String title, String message) {
        Dialog errorDialog = new Dialog(context);
        errorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        errorDialog.setContentView(R.layout.dialog_error);
        
        TextView titleView = errorDialog.findViewById(R.id.error_title);
        TextView messageView = errorDialog.findViewById(R.id.error_message);
        Button btnOk = errorDialog.findViewById(R.id.btn_ok);
        
        titleView.setText(title);
        messageView.setText(message);
        
        btnOk.setOnClickListener(v -> errorDialog.dismiss());
        
        // Set dialog width to match parent with margins
        Window window = errorDialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(layoutParams);
        }
        
        errorDialog.show();
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        hasWindowFocus = hasFocus;
        if (hasFocus && shouldShowMenu) {
            // Delay showing menu to ensure proper drawing
            new android.os.Handler().postDelayed(() -> {
                showSelectionMenu(menuX, menuY);
                shouldShowMenu = false;
            }, 100);
        }
    }

    public void onDraw(Canvas canvas) {
        drawTextBoxes(canvas);
        
        // Draw selection and handles if we have valid selection
        if (startWord != null && endWord != null) {
            drawSelection(canvas);
            if (!isSelecting) {
                drawHandles(canvas);
            }
        }
    }

    private void drawTextBoxes(Canvas canvas) {
        lastLineBottom = 0;
        drawnWords.clear();
        float defaultLineHeight = MAX_TEXT_SIZE + PADDING_VERTICAL * 2;
        float lastLineTop = 0;

        for (Text.TextBlock block : textBlocks) {
            if (block.getBoundingBox() == null) continue;

            for (Text.Line line : block.getLines()) {
                if (line.getBoundingBox() == null) continue;

                RectF lineRect = new RectF(line.getBoundingBox());
                float originalTop = lineRect.top * imageScaleY + imageTranslateY;
                
                // Calculate the minimum spacing needed from the last line
                float minSpacing = (lastLineBottom > 0) ? LINE_SPACING : 0;
                
                // Try to keep the line close to its original position while ensuring no overlap
                lineRect.top = Math.max(originalTop - PADDING_VERTICAL, lastLineBottom + minSpacing);
                
                lineRect.left = lineRect.left * imageScaleX + imageTranslateX - PADDING_HORIZONTAL;
                lineRect.right = lineRect.right * imageScaleX + imageTranslateX + PADDING_HORIZONTAL;
                
                float textSize = calculateAppropriateTextSize(line.getText(), lineRect.width() - PADDING_HORIZONTAL * 2);
                textPaint.setTextSize(textSize);

                float lineHeight = textSize + PADDING_VERTICAL * 2;
                lineRect.bottom = lineRect.top + lineHeight;

                // Ensure minimum spacing between lines while trying to maintain original position
                if (lastLineBottom > 0 && lineRect.top - lastLineBottom < LINE_SPACING) {
                    float adjustment = LINE_SPACING - (lineRect.top - lastLineBottom);
                    lineRect.top += adjustment;
                    lineRect.bottom += adjustment;
                }

                canvas.drawRoundRect(lineRect, CORNER_RADIUS, CORNER_RADIUS, textBackgroundPaint);

                String[] words = line.getText().split("\\s+");
                float startX = lineRect.left + PADDING_HORIZONTAL;
                float baselineY = lineRect.bottom - PADDING_VERTICAL - TEXT_MARGIN_BOTTOM;
                float spaceWidth = textPaint.measureText(" ");

                for (String word : words) {
                    float wordWidth = textPaint.measureText(word);
                    
                    RectF wordRect = new RectF(
                        startX,
                        lineRect.top,
                        startX + wordWidth,
                        lineRect.bottom
                    );

                    RectF fullRect = new RectF(
                        startX,
                        lineRect.top,
                        startX + wordWidth + spaceWidth,
                        lineRect.bottom
                    );

                    drawnWords.add(new DrawnWord(word, wordRect, fullRect, textSize, startX, baselineY));
                    canvas.drawText(word, startX, baselineY, textPaint);
                    startX += wordWidth + spaceWidth;
                }

                lastLineBottom = lineRect.bottom;
                lastLineTop = lineRect.top;
            }
        }
    }

    private float calculateAppropriateTextSize(String text, float maxWidth) {
        float textSize = MAX_TEXT_SIZE;
        textPaint.setTextSize(textSize);
        float textWidth = textPaint.measureText(text);

        while (textWidth > maxWidth && textSize > MIN_TEXT_SIZE) {
            textSize -= TEXT_SIZE_STEP;
            textPaint.setTextSize(textSize);
            textWidth = textPaint.measureText(text);
        }

        return textSize;
    }

    public boolean onTouchEvent(MotionEvent event, Canvas canvas) {
        float x = event.getX();
        float y = event.getY();

        // Check if touching handles first, regardless of menu state
        if (startWord != null && endWord != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (startHandleBounds.contains(x, y)) {
                    isHandlesDragging = true;
                    isDraggingStartHandle = true;
                    currentHandlePosition = new PointF(x, y);
                    lastNearestWord = startWord; // Initialize with current word
                    hideSelectionMenu();
                    return true;
                } else if (endHandleBounds.contains(x, y)) {
                    isHandlesDragging = true;
                    isDraggingStartHandle = false;
                    currentHandlePosition = new PointF(x, y);
                    lastNearestWord = endWord; // Initialize with current word
                    hideSelectionMenu();
                    return true;
                }
            }
        }

        // If menu is showing, handle clicks outside selection and menu
        if (isMenuShowing) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                boolean clickedInSelection = false;
                // Check if clicked in selection area
                for (DrawnWord word : drawnWords) {
                    if (word.fullRect.contains(x, y)) {
                        clickedInSelection = true;
                        break;
                    }
                }
                
                // Only clear if clicked outside selection and menu
                if (!clickedInSelection && !isTouchingMenu(event.getRawX(), event.getRawY())) {
                    hideSelectionMenu();
                    clearSelection();
                }
            }
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartTime = System.currentTimeMillis();
                touchStartPoint = new PointF(x, y);

                // If not touching handles, check for new selection
                DrawnWord touchedWord = findTouchedWordObject(x, y);
                if (touchedWord != null) {
                    startWord = touchedWord;
                    endWord = touchedWord;
                    selectionStart = new PointF(x, y);
                    isSelecting = true;
                    isTapSelection = true;
                    hideSelectionMenu();
                    processSelection();
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isHandlesDragging) {
                    // Always update handle position immediately for smooth movement
                    currentHandlePosition.set(x, y);
                    
                    // Only update word selection periodically
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastWordUpdateTime > WORD_UPDATE_DELAY) {
                        // Find nearest word but don't snap to it yet
                        DrawnWord nearestWord = findNearestWordForHandle(x, y);
                        if (nearestWord != null && nearestWord != lastNearestWord) {
                            if (isDraggingStartHandle) {
                                // Don't allow start handle to go after end handle
                                if (nearestWord.rect.top < endWord.rect.bottom && 
                                    (nearestWord.rect.top != endWord.rect.top || nearestWord.rect.left <= endWord.rect.right)) {
                                    startWord = nearestWord;
                                    lastNearestWord = nearestWord;
                                }
                            } else {
                                // Don't allow end handle to go before start handle
                                if (nearestWord.rect.top > startWord.rect.top || 
                                    (nearestWord.rect.top == startWord.rect.top && nearestWord.rect.right >= startWord.rect.left)) {
                                    endWord = nearestWord;
                                    lastNearestWord = nearestWord;
                                }
                            }
                            processSelection();
                            lastWordUpdateTime = currentTime;
                        }
                    }
                    return true;
                } else if (isSelecting && !isTapSelection) {
                    // Don't start dragging selection unless moved beyond threshold
                    float dx = x - touchStartPoint.x;
                    float dy = y - touchStartPoint.y;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    if (distance > 10) { // 10px movement threshold
                        isTapSelection = false;
                        selectionEnd = new PointF(x, y);
                        updateSelection();
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                long touchDuration = System.currentTimeMillis() - touchStartTime;
                
                if (isHandlesDragging) {
                    // Find the final word to snap to
                    DrawnWord finalWord = findNearestWordForHandle(x, y);
                    if (finalWord != null) {
                        if (isDraggingStartHandle) {
                            if (finalWord.rect.top < endWord.rect.bottom && 
                                (finalWord.rect.top != endWord.rect.top || finalWord.rect.left <= endWord.rect.right)) {
                                startWord = finalWord;
                            }
                        } else {
                            if (finalWord.rect.top > startWord.rect.top || 
                                (finalWord.rect.top == startWord.rect.top && finalWord.rect.right >= startWord.rect.left)) {
                                endWord = finalWord;
                            }
                        }
                    }
                    isHandlesDragging = false;
                    currentHandlePosition = null;
                    processSelection();
                    if (selectedText != null && !selectedText.isEmpty()) {
                        showSelectionMenu(event.getRawX(), event.getRawY());
                    }
                    return true;
                } else if (isSelecting) {
                    isSelecting = false;
                    
                    // If this was a tap (short duration and small movement)
                    if (isTapSelection && touchDuration < TAP_TIMEOUT) {
                        // Keep the single word selection
                        selectionEnd = new PointF(
                            startWord.rect.right,
                            startWord.rect.centerY()
                        );
                    } else {
                        // Complete the drag selection
                        selectionEnd = new PointF(x, y);
                        updateSelection();
                    }
                    
                    if (selectedText != null && !selectedText.isEmpty()) {
                        showSelectionMenu(event.getRawX(), event.getRawY());
                    }
                    return true;
                }
                isTapSelection = false;
                break;
        }
        return false;
    }

    private DrawnWord findTouchedWordObject(float x, float y) {
        for (DrawnWord word : drawnWords) {
            if (word.rect.contains(x, y)) {
                return word;
            }
        }
        return null;
    }

    private DrawnWord findNearestWordForHandle(float x, float y) {
        DrawnWord nearest = null;
        float minDistance = Float.MAX_VALUE;
        float currentLineY = isDraggingStartHandle ? startWord.rect.centerY() : endWord.rect.centerY();

        for (DrawnWord word : drawnWords) {
            // Calculate distance to word center
            float wordCenterX = (word.rect.left + word.rect.right) / 2;
            float wordCenterY = (word.rect.top + word.rect.bottom) / 2;
            float dx = x - wordCenterX;
            float dy = y - wordCenterY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            // Heavily prioritize words on the current line
            if (Math.abs(word.rect.centerY() - currentLineY) < textPaint.getTextSize()) {
                distance *= 0.3f; // Give much more weight to words on the current line
            }
            // Moderately prioritize words on the same line as the touch point
            else if (Math.abs(word.rect.centerY() - y) < textPaint.getTextSize()) {
                distance *= 0.7f; // Give more weight to words on the same line as touch
            }

            if (distance < minDistance) {
                minDistance = distance;
                nearest = word;
            }
        }

        return nearest;
    }

    private void drawSelection(Canvas canvas) {
        if (startWord == null || endWord == null) return;

        // For single word selection
        if (startWord == endWord) {
            canvas.drawRoundRect(startWord.fullRect, CORNER_RADIUS, CORNER_RADIUS, selectionPaint);
            return;
        }

        // For multi-word selection
        boolean isSelectingDown = startWord.rect.top <= endWord.rect.top;
        DrawnWord topWord = isSelectingDown ? startWord : endWord;
        DrawnWord bottomWord = isSelectingDown ? endWord : startWord;

        for (DrawnWord word : drawnWords) {
            boolean shouldHighlight = false;

            if (Math.abs(word.rect.top - topWord.rect.top) < textPaint.getTextSize()) {
                if (isSelectingDown) {
                    shouldHighlight = word.rect.left >= topWord.rect.left;
                } else {
                    shouldHighlight = word.rect.right <= topWord.rect.right;
                }
            }
            else if (Math.abs(word.rect.top - bottomWord.rect.top) < textPaint.getTextSize()) {
                if (isSelectingDown) {
                    shouldHighlight = word.rect.right <= bottomWord.rect.right;
                } else {
                    shouldHighlight = word.rect.left >= bottomWord.rect.left;
                }
            }
            else if (word.rect.top > topWord.rect.top && word.rect.top < bottomWord.rect.top) {
                shouldHighlight = true;
            }

            if (shouldHighlight) {
                canvas.drawRoundRect(word.fullRect, CORNER_RADIUS, CORNER_RADIUS, selectionPaint);
            }
        }

        // Draw handles only if not in selection mode
        if (!isSelecting) {
            drawHandles(canvas);
        }
    }

    private void drawHandles(Canvas canvas) {
        if (isSelecting) return; // Don't draw handles while selecting
        
        // Draw start handle
        float startHandleX, startHandleY;
        if (isHandlesDragging && isDraggingStartHandle && currentHandlePosition != null) {
            // Draw handle at current drag position with smooth interpolation
            startHandleX = currentHandlePosition.x;
            startHandleY = currentHandlePosition.y;
        } else {
            // Draw handle at word position
            startHandleX = startWord.rect.left;
            startHandleY = startWord.rect.bottom + HANDLE_RADIUS;
        }
        
        // Draw handle with border
        handlePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(startHandleX, startHandleY, HANDLE_RADIUS, handlePaint);
        handlePaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(startHandleX, startHandleY, HANDLE_RADIUS - HANDLE_STROKE_WIDTH/2, handlePaint);
        handlePaint.setStyle(Paint.Style.FILL);
        
        // Update start handle bounds for touch detection
        startHandleBounds.set(
            startHandleX - HANDLE_TOUCH_RADIUS,
            startHandleY - HANDLE_TOUCH_RADIUS,
            startHandleX + HANDLE_TOUCH_RADIUS,
            startHandleY + HANDLE_TOUCH_RADIUS
        );

        // Draw end handle
        float endHandleX, endHandleY;
        if (isHandlesDragging && !isDraggingStartHandle && currentHandlePosition != null) {
            // Draw handle at current drag position with smooth interpolation
            endHandleX = currentHandlePosition.x;
            endHandleY = currentHandlePosition.y;
        } else {
            // Draw handle at word position
            endHandleX = endWord.rect.right;
            endHandleY = endWord.rect.bottom + HANDLE_RADIUS;
        }
        
        // Draw handle with border
        handlePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(endHandleX, endHandleY, HANDLE_RADIUS, handlePaint);
        handlePaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(endHandleX, endHandleY, HANDLE_RADIUS - HANDLE_STROKE_WIDTH/2, handlePaint);
        handlePaint.setStyle(Paint.Style.FILL);
        
        // Update end handle bounds for touch detection
        endHandleBounds.set(
            endHandleX - HANDLE_TOUCH_RADIUS,
            endHandleY - HANDLE_TOUCH_RADIUS,
            endHandleX + HANDLE_TOUCH_RADIUS,
            endHandleY + HANDLE_TOUCH_RADIUS
        );
    }

    private void updateSelection() {
        if (selectionStart == null || selectionEnd == null) return;

        // Find words at selection points
        DrawnWord wordAtStart = findNearestWordForHandle(selectionStart.x, selectionStart.y);
        DrawnWord wordAtEnd = findNearestWordForHandle(selectionEnd.x, selectionEnd.y);

        if (wordAtStart != null && wordAtEnd != null) {
            startWord = wordAtStart;
            endWord = wordAtEnd;
            processSelection();
        }
    }

    private void processSelection() {
        if (startWord == null || endWord == null) return;

        // Determine selection direction
        boolean isSelectingDown = startWord.rect.top <= endWord.rect.top;
        DrawnWord topWord = isSelectingDown ? startWord : endWord;
        DrawnWord bottomWord = isSelectingDown ? endWord : startWord;

        List<DrawnWord> selectedWords = new ArrayList<>();

        // For single word selection
        if (startWord == endWord) {
            selectedWords.add(startWord);
        }
        // For multi-word selection
        else {
            // Select words based on their position
            for (DrawnWord word : drawnWords) {
                // Words on the same line as top word
                if (Math.abs(word.rect.top - topWord.rect.top) < textPaint.getTextSize()) {
                    if (isSelectingDown) {
                        if (word.rect.left >= topWord.rect.left) {
                            selectedWords.add(word);
                        }
                    } else {
                        if (word.rect.right <= topWord.rect.right) {
                            selectedWords.add(word);
                        }
                    }
                }
                // Words on the same line as bottom word
                else if (Math.abs(word.rect.top - bottomWord.rect.top) < textPaint.getTextSize()) {
                    if (isSelectingDown) {
                        if (word.rect.right <= bottomWord.rect.right) {
                            selectedWords.add(word);
                        }
                    } else {
                        if (word.rect.left >= bottomWord.rect.left) {
                            selectedWords.add(word);
                        }
                    }
                }
                // Words in between top and bottom words
                else if (word.rect.top > topWord.rect.top && word.rect.top < bottomWord.rect.top) {
                    selectedWords.add(word);
                }
            }
        }

        // Sort selected words by position
        selectedWords.sort((w1, w2) -> {
            if (Math.abs(w1.rect.top - w2.rect.top) > textPaint.getTextSize() / 2) {
                return Float.compare(w1.rect.top, w2.rect.top);
            }
            return Float.compare(w1.rect.left, w2.rect.left);
        });

        // Build selected text
        StringBuilder selectedTextBuilder = new StringBuilder();
        float lastTop = -1;
        String currentLine = "";

        for (DrawnWord word : selectedWords) {
            if (lastTop != -1 && Math.abs(word.rect.top - lastTop) > textPaint.getTextSize() / 2) {
                selectedTextBuilder.append(currentLine.trim()).append("\n");
                currentLine = "";
            }
            currentLine += word.text + " ";
            lastTop = word.rect.top;
        }
        if (!currentLine.isEmpty()) {
            selectedTextBuilder.append(currentLine.trim());
        }

        selectedText = selectedTextBuilder.toString().trim();
    }

    private void showSelectionMenu(float rawX, float rawY) {
        if (selectedText == null || selectedText.isEmpty()) return;

        // If we don't have window focus, save coordinates and show later
        if (!hasWindowFocus) {
            shouldShowMenu = true;
            menuX = rawX;
            menuY = rawY;
            return;
        }

        // Dismiss any existing menu
        hideSelectionMenu();

        View menuView = LayoutInflater.from(context).inflate(R.layout.text_selection_menu, null);
        selectionMenu = new PopupWindow(menuView, 
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                true);
        selectionMenu.setElevation(8f);
        
        // Set touch interceptor to prevent losing selection when touching menu
        menuView.setOnTouchListener((v, event) -> true);
        
        setupMenuButtons(menuView);

        // Position menu above the selection
        float menuY = Math.min(startWord.rect.top, endWord.rect.top) - 150;
        float menuX = (startWord.rect.left + endWord.rect.right) / 2;

        selectionMenu.showAtLocation(menuView, Gravity.NO_GRAVITY, 
                (int) menuX, (int) menuY);
                
        isMenuShowing = true;
    }

    private void setupMenuButtons(View menuView) {
        ImageButton btnCopy = menuView.findViewById(R.id.btn_copy);
        btnCopy.setOnClickListener(v -> {
            copyToClipboard(selectedText);
            hideSelectionMenu();
        });

        ImageButton btnTranslate = menuView.findViewById(R.id.btn_translate);
        btnTranslate.setOnClickListener(v -> {
            translateText(selectedText);
            hideSelectionMenu();
        });

        ImageButton btnShare = menuView.findViewById(R.id.btn_share);
        btnShare.setOnClickListener(v -> {
            shareText(selectedText);
            hideSelectionMenu();
        });
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Selected Text", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void translateText(String text) {
        // Create a list with single text
        List<String> textList = new ArrayList<>();
        textList.add(text);
        String combinedText = String.join("\n", textList);

        // Show loading dialog
        Dialog loadingDialog = new Dialog(context);
        loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        loadingDialog.setContentView(R.layout.dialog_loading);
        loadingDialog.setCancelable(false);
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        final TextView loadingText = loadingDialog.findViewById(R.id.loading_text);
        loadingDialog.show();

        // Create a new thread for translation
        new Thread(() -> {
            try {
                String prompt = "Translate the following Vietnamese text to English:\n\n" + combinedText;
                String translatedText = callOpenAI(prompt);

                // Update UI on main thread
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        Toast.makeText(context, translatedText, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        showErrorDialog("Translation Error", "Failed to translate text: " + e.getMessage());
                    });
                }
            }
        }).start();
    }

    private void shareText(String text) {
        Toast.makeText(context, "Share coming soon!", Toast.LENGTH_SHORT).show();
    }

    private void clearSelection() {
        selectionStart = null;
        selectionEnd = null;
        startWord = null;
        endWord = null;
        selectedText = null;
        isMenuShowing = false;
        shouldShowMenu = false;
    }

    public void hideSelectionMenu() {
        if (selectionMenu != null && selectionMenu.isShowing()) {
            selectionMenu.dismiss();
        }
        isMenuShowing = false;
        shouldShowMenu = false;
    }

    private boolean isTouchingMenu(float rawX, float rawY) {
        if (selectionMenu == null || !selectionMenu.isShowing()) return false;
        
        // Get menu location and size
        int[] location = new int[2];
        View menuView = selectionMenu.getContentView();
        menuView.getLocationOnScreen(location);
        
        return rawX >= location[0] && 
               rawX <= location[0] + menuView.getWidth() &&
               rawY >= location[1] && 
               rawY <= location[1] + menuView.getHeight();
    }
} 