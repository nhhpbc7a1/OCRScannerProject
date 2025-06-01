package hcmute.edu.vn.ocrscannerproject.ui.extract;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.List;

public class TextSelectionImageView extends AppCompatImageView {
    private TextSelectionManager selectionManager;
    private List<Text.TextBlock> textBlocks = new ArrayList<>();
    private float[] matrixValues = new float[9];

    public TextSelectionImageView(@NonNull Context context) {
        super(context);
        init();
    }

    public TextSelectionImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TextSelectionImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Enable drawing cache for better performance
        setDrawingCacheEnabled(true);
    }

    public void setRecognizedText(Text recognizedText) {
        if (recognizedText != null) {
            this.textBlocks = recognizedText.getTextBlocks();
            updateSelectionManager();
        }
    }

    private void updateSelectionManager() {
        if (getDrawable() == null) return;

        // Get the current image matrix
        Matrix matrix = getImageMatrix();
        matrix.getValues(matrixValues);

        // Calculate scale factors
        float scaleX = matrixValues[Matrix.MSCALE_X];
        float scaleY = matrixValues[Matrix.MSCALE_Y];
        
        // Get translation values
        float translateX = matrixValues[Matrix.MTRANS_X];
        float translateY = matrixValues[Matrix.MTRANS_Y];

        // Create new selection manager with current scale and translation
        selectionManager = new TextSelectionManager(getContext(), textBlocks, 
                scaleX, scaleY, translateX, translateY);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (selectionManager != null) {
            selectionManager.onDraw(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (selectionManager != null) {
            if (selectionManager.onTouchEvent(event, new Canvas())) {
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateSelectionManager();
    }

    public void clearSelection() {
        if (selectionManager != null) {
            selectionManager.hideSelectionMenu();
        }
        invalidate();
    }
} 