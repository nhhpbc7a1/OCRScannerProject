package hcmute.edu.vn.ocrscannerproject.ui.extract;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;
import hcmute.edu.vn.ocrscannerproject.data.ScannedDocumentRepository;

public class ExtractTextFragment extends Fragment {

    private static final String TAG = "ExtractTextFragment";
    private static final String ANONYMOUS_USER = "anonymous";
    
    private ImageButton btnBack;
    private Spinner spinnerLanguage;
    private EditText etExtractedText;
    private Button btnCopy, btnSave;
    private ProgressBar progressExtract;
    
    private ArrayList<String> processedImages = new ArrayList<>();
    private String fileName;
    private List<String> languages = Arrays.asList("Auto", "English", "Vietnamese", "Japanese", "Korean", "Chinese");
    private String selectedLanguage = "Auto";
    
    private TextRecognizer textRecognizer;
    private LanguageIdentifier languageIdentifier;
    private final Executor executor = Executors.newSingleThreadExecutor();
    
    // Map to translate human language names to ML Kit language codes
    private final Map<String, String> languageCodeMap = new HashMap<String, String>() {{
        put("English", "en");
        put("Vietnamese", "vi");
        put("Japanese", "ja");
        put("Korean", "ko");
        put("Chinese", "zh");
    }};

    public static ExtractTextFragment newInstance(String[] imagePaths, String fileName) {
        ExtractTextFragment fragment = new ExtractTextFragment();
        Bundle args = new Bundle();
        args.putStringArray("processedImages", imagePaths);
        args.putString("fileName", fileName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            // Nhận dữ liệu dưới dạng String[] và chuyển thành ArrayList
            String[] imagePathsArray = getArguments().getStringArray("processedImages");
            if (imagePathsArray != null) {
                processedImages = new ArrayList<>(Arrays.asList(imagePathsArray));
            } else {
                processedImages = new ArrayList<>();
            }
            fileName = getArguments().getString("fileName", "");
        }
        
        // Initialize ML Kit text recognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        languageIdentifier = LanguageIdentification.getClient();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_extract_text, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        setupLanguageSpinner();
        setupListeners();
        
        // Start OCR if we have images
        if (!processedImages.isEmpty()) {
            performOCR(selectedLanguage);
        } else {
            etExtractedText.setText("No images available for text extraction");
        }
    }
    
    private void initializeViews(View view) {
        btnBack = view.findViewById(R.id.btn_back);
        spinnerLanguage = view.findViewById(R.id.spinner_language);
        etExtractedText = view.findViewById(R.id.et_extracted_text);
        btnCopy = view.findViewById(R.id.btn_copy);
        btnSave = view.findViewById(R.id.btn_save);
        
        // Add a ProgressBar if not already in layout
        progressExtract = view.findViewById(R.id.progress_extract);
        if (progressExtract == null) {
            progressExtract = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
            ViewGroup layout = (ViewGroup) etExtractedText.getParent();
            layout.addView(progressExtract);
        }
    }
    
    private void setupLanguageSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), 
                android.R.layout.simple_spinner_item,
                languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapter);
        
        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String previousLanguage = selectedLanguage;
                selectedLanguage = languages.get(position);
                
                // Only re-run OCR if language changed and it's not "Auto"
                if (!previousLanguage.equals(selectedLanguage) && !selectedLanguage.equals("Auto")) {
                    performOCR(selectedLanguage);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Default to Auto
            }
        });
    }
    
    private void setupListeners() {
        // Back button
        btnBack.setOnClickListener(v -> {
            Navigation.findNavController(requireView()).popBackStack();
        });
        
        // Copy button
        btnCopy.setOnClickListener(v -> {
            copyToClipboard(etExtractedText.getText().toString());
        });
        
        // Save button
        btnSave.setOnClickListener(v -> {
            saveExtractedText();
        });
    }
    
    private void performOCR(String language) {
        if (processedImages.isEmpty()) {
            return;
        }
        
        // Show processing
        progressExtract.setVisibility(View.VISIBLE);
        etExtractedText.setText("Processing...");
        btnCopy.setEnabled(false);
        btnSave.setEnabled(false);
        
        executor.execute(() -> {
            final StringBuilder extractedTextBuilder = new StringBuilder();
            int processedCount = 0;
            
            for (String imagePath : processedImages) {
                try {
                    // Load image from path or URI
                    InputImage image = getInputImage(imagePath);
                    if (image == null) continue;

                    // Process with ML Kit text recognizer
                    Task<Text> result = textRecognizer.process(image);
                    
                    // Wait for the task to complete
                    while (!result.isComplete()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    
                    if (result.isSuccessful()) {
                        Text resultText = result.getResult();
                        String recognizedText = resultText.getText();
                        
                        // If language filter is set, perform language identification
                        if (!language.equals("Auto") && !recognizedText.isEmpty()) {
                            // Get ML Kit language code from our map
                            String languageCode = languageCodeMap.get(language);
                            if (languageCode != null) {
                                // We'd ideally filter by language, but for now just append all text
                                // Language filtering would require more complex processing
                            }
                        }
                        
                        // Add text from this image
                        if (!recognizedText.isEmpty()) {
                            if (extractedTextBuilder.length() > 0) {
                                extractedTextBuilder.append("\n\n----- Page ").append(processedCount + 1).append(" -----\n\n");
                            }
                            extractedTextBuilder.append(recognizedText);
                            processedCount++;
                        }
                    }
                    
                    // Update progress on UI thread
                    int finalProcessedCount = processedCount;
                    requireActivity().runOnUiThread(() -> {
                        int progress = (int) ((float) finalProcessedCount / processedImages.size() * 100);
                        progressExtract.setProgress(progress);
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error processing image: " + e.getMessage(), e);
                }
            }
            
            // Final results on UI thread
            requireActivity().runOnUiThread(() -> {
                progressExtract.setVisibility(View.GONE);
                btnCopy.setEnabled(true);
                btnSave.setEnabled(true);
                
                if (extractedTextBuilder.length() > 0) {
                    etExtractedText.setText(extractedTextBuilder.toString());
                } else {
                    etExtractedText.setText("No text detected in images");
                }
            });
        });
    }
    
    private InputImage getInputImage(String imagePath) {
        try {
            // Try to parse as URI first
            if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) {
                return InputImage.fromFilePath(requireContext(), Uri.parse(imagePath));
            } 
            
            // Then try direct file path
            File imageFile = new File(imagePath);
            if (imageFile.exists()) {
                // Load bitmap from file to avoid OutOfMemoryError for large images
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2; // Downsample by factor of 2
                
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
                if (bitmap != null) {
                    return InputImage.fromBitmap(bitmap, 0);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "Error creating input image: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Extracted Text", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), "Text copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    private void saveExtractedText() {
        String text = etExtractedText.getText().toString();
        if (text.isEmpty() || text.equals("Processing...")) {
            Toast.makeText(requireContext(), "No text to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Get the repository instance
            ScannedDocumentRepository repository = ScannedDocumentRepository.getInstance(requireContext());
            
            // Check if we have images
            if (processedImages.isEmpty()) {
                Toast.makeText(requireContext(), "No images to save with text", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Kiểm tra tính hợp lệ của tất cả các ảnh
            List<String> validImagePaths = new ArrayList<>();
            for (String path : processedImages) {
                if (path != null && !path.isEmpty()) {
                    if ((path.startsWith("content://") || path.startsWith("file://")) || new File(path).exists()) {
                        validImagePaths.add(path);
                    }
                }
            }
            
            if (validImagePaths.isEmpty()) {
                Toast.makeText(requireContext(), "No valid images to save with text", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create and save the document with extracted text and all images
            ScannedDocument document = new ScannedDocument(fileName, ANONYMOUS_USER, validImagePaths);
            document.setExtractedText(text);
            document.setType("Text");
            repository.addDocument(document);
            
            Toast.makeText(requireContext(), 
                    "Document saved with " + validImagePaths.size() + " image(s) and extracted text", 
                    Toast.LENGTH_SHORT).show();
            
            // Navigate back to home
            Navigation.findNavController(requireView()).popBackStack(R.id.homeFragment, false);
        } catch (Exception e) {
            Log.e(TAG, "Error saving document with extracted text: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Error saving document: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Close the language identifier
        if (languageIdentifier != null) {
            languageIdentifier.close();
        }
    }
} 