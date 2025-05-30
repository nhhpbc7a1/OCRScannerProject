package hcmute.edu.vn.ocrscannerproject.ui.extract;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;
import hcmute.edu.vn.ocrscannerproject.data.ScannedDocumentRepository;
import hcmute.edu.vn.ocrscannerproject.ui.adapter.ImagePagerAdapter;
import hcmute.edu.vn.ocrscannerproject.MainActivity;

public class ExtractTextFragment extends Fragment {

    private static final String TAG = "ExtractTextFragment";
    private static final String ANONYMOUS_USER = "anonymous";
    
    private ImageButton btnBack;
    
    private ArrayList<String> processedImages = new ArrayList<>();
    private String fileName;
    private TextRecognizer textRecognizer;
    
    private ViewPager2 viewPagerImages;
    private TextView tvPageIndicator;
    private ImageButton btnPrevious, btnNext, btnCopyAll;
    private ImagePagerAdapter adapter;
    private int currentPage = 0;
    private String extractedText = "";
    private final Executor executor = Executors.newSingleThreadExecutor();
    private static final String OPENAI_API_KEY =""; // Replace with your API key
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private final OkHttpClient client = new OkHttpClient();
    private View actionSavePdf, actionFormatText, actionSummarize;

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
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_extract_text, container, false);
        
        // Initialize views
        viewPagerImages = view.findViewById(R.id.view_pager_images);
        tvPageIndicator = view.findViewById(R.id.tv_page_indicator);
        btnPrevious = view.findViewById(R.id.btn_previous);
        btnNext = view.findViewById(R.id.btn_next);
        btnBack = view.findViewById(R.id.btn_back);
        btnCopyAll = view.findViewById(R.id.btn_copy_all);
        actionSavePdf = view.findViewById(R.id.action_save_pdf);
        actionFormatText = view.findViewById(R.id.action_format_text);
        actionSummarize = view.findViewById(R.id.action_summarize);
        
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupViewPager();
        setupListeners();

        // Hide bottom navigation
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideBottomNav();
        }

        // Set status bar color to black and icons to light
        if (getActivity() != null) {
            Window window = getActivity().getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
            // Make status bar icons light
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // Remove light status bar flag
            decorView.setSystemUiVisibility(flags);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Hide bottom navigation
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideBottomNav();
        }

        // Set status bar color to black and icons to light
        if (getActivity() != null) {
            Window window = getActivity().getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
            // Make status bar icons light
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // Remove light status bar flag
            decorView.setSystemUiVisibility(flags);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Show bottom navigation when leaving fragment
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showBottomNav();
        }
        
        // Restore status bar when leaving fragment
        if (getActivity() != null) {
            Log.d(TAG, "onDestroyView: Restoring status bar color");
            // Restore original status bar color and light status bar
            Window window = getActivity().getWindow();
            window.setStatusBarColor(Color.WHITE);
            // Restore light status bar
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decorView.setSystemUiVisibility(flags);
        }
        
        // Close text recognizer
        textRecognizer.close();
    }
    
    private void setupViewPager() {
        adapter = new ImagePagerAdapter(requireContext(), processedImages);
        viewPagerImages.setAdapter(adapter);
        
        // Set up image loaded listener for OCR
        adapter.setOnImageLoadedListener((position, bitmap) -> {
            // Only perform OCR if we haven't already processed this position
            if (adapter.getRecognizedText(position) == null) {
                // Perform OCR on the bitmap
                InputImage image = InputImage.fromBitmap(bitmap, 0);
            
            textRecognizer.process(image)
                .addOnSuccessListener(text -> {
                    if (isAdded()) {  // Check if fragment is still attached
                        // Update the adapter with recognized text blocks
                        adapter.setRecognizedText(position, text);
                            // Store the extracted text
                            extractedText = text.getText();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {  // Check if fragment is still attached
                        Toast.makeText(requireContext(), "Error processing image: " + e.getMessage(), 
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        
        viewPagerImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updatePageIndicator();
            }
        });
        
        updatePageIndicator();
    }
    
    private void setupListeners() {
        // Back button
        btnBack.setOnClickListener(v -> {
            Navigation.findNavController(requireView()).popBackStack();
        });
        
        btnPrevious.setOnClickListener(v -> {
            if (currentPage > 0) {
                viewPagerImages.setCurrentItem(currentPage - 1);
            }
        });
        
        btnNext.setOnClickListener(v -> {
            if (currentPage < processedImages.size() - 1) {
                viewPagerImages.setCurrentItem(currentPage + 1);
            }
        });
        
        btnCopyAll.setOnClickListener(v -> {
            adapter.copyAllText(currentPage);
            Toast.makeText(requireContext(), "All text copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // New action listeners
        actionSavePdf.setOnClickListener(v -> saveToPdf());
        actionFormatText.setOnClickListener(v -> formatText());
        actionSummarize.setOnClickListener(v -> summarizeText());

        // Save all action
        View actionSaveAll = getView().findViewById(R.id.action_save_all);
        if (actionSaveAll != null) {
            actionSaveAll.setOnClickListener(v -> showSaveDialog());
        }
    }
    
    private void updatePageIndicator() {
        String text = String.format("%d/%d", currentPage + 1, processedImages.size());
        tvPageIndicator.setText(text);
        
        btnPrevious.setEnabled(currentPage > 0);
        btnNext.setEnabled(currentPage < processedImages.size() - 1);
    }

    private void saveToPdf() {
        if (adapter == null || adapter.getRecognizedText(currentPage) == null) {
            Toast.makeText(getContext(), "No text to save", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            try {
                // Create directory if it doesn't exist
                File directory = new File(getContext().getFilesDir(), "documents");
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                // Create PDF file with full path
                String pdfFileName = fileName + ".pdf";
                File pdfFile = new File(directory, pdfFileName);
                OutputStream output = new FileOutputStream(pdfFile);
                
                // Create PDF document with A4 size
                Document document = new Document(PageSize.A4);
                PdfWriter writer = PdfWriter.getInstance(document, output);
                document.open();

                for (int i = 0; i < processedImages.size(); i++) {
                    if (i > 0) document.newPage();
                    
                    // Add image with high quality
                    Bitmap bitmap = BitmapFactory.decodeFile(processedImages.get(i));
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    Image image = Image.getInstance(stream.toByteArray());
                    
                    // Scale image to fit page while maintaining aspect ratio
                    float ratio = Math.min(
                        document.getPageSize().getWidth() / image.getWidth(),
                        document.getPageSize().getHeight() / image.getHeight()
                    );
                    image.scaleAbsolute(image.getWidth() * ratio, image.getHeight() * ratio);
                    
                    // Center image on page
                    image.setAbsolutePosition(
                        (document.getPageSize().getWidth() - image.getScaledWidth()) / 2,
                        (document.getPageSize().getHeight() - image.getScaledHeight()) / 2
                    );
                    document.add(image);
                    
                    // Add searchable text layer
                    Text recognizedText = adapter.getRecognizedText(i);
        if (recognizedText != null) {
                        PdfContentByte canvas = writer.getDirectContent();
                        BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                        canvas.setFontAndSize(bf, 1);
                        canvas.setRGBColorFill(0, 0, 0);
                        canvas.setTextRenderingMode(PdfContentByte.TEXT_RENDER_MODE_INVISIBLE);
                        
                        for (Text.TextBlock block : recognizedText.getTextBlocks()) {
                            canvas.beginText();
                            float x = block.getBoundingBox().left * ratio + 
                                (document.getPageSize().getWidth() - image.getScaledWidth()) / 2;
                            float y = document.getPageSize().getHeight() - 
                                (block.getBoundingBox().top * ratio + 
                                (document.getPageSize().getHeight() - image.getScaledHeight()) / 2);
                            canvas.setTextMatrix(x, y);
                            canvas.showText(block.getText());
                            canvas.endText();
                        }
                    }
                }

                document.close();
                output.close();

                // Create a new ScannedDocument for the PDF
                List<String> imagePaths = new ArrayList<>(processedImages);
                ScannedDocument pdfDocument = new ScannedDocument(
                    fileName,                    // title
                    ANONYMOUS_USER,             // userId
                    pdfFile.getAbsolutePath()   // filePath
                );
                pdfDocument.setType("PDF");     // Set document type to PDF
                pdfDocument.setLocalImagePaths(imagePaths);  // Set original image paths

                // Save to repository
                ScannedDocumentRepository repository = ScannedDocumentRepository.getInstance(requireContext());
                repository.addDocument(pdfDocument);

                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "PDF saved and added to documents", Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Failed to save PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showResultDialog(String title, String content) {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_text_result);
        dialog.getWindow().setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );

        TextView titleView = dialog.findViewById(R.id.dialog_title);
        TextView contentView = dialog.findViewById(R.id.dialog_content);
        Button btnSaveDoc = dialog.findViewById(R.id.btn_save_doc);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);

        titleView.setText(title);
        contentView.setText(content);

        btnSaveDoc.setOnClickListener(v -> {
            saveToDoc(content);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void formatText() {
        // Get all extracted text from all pages
        StringBuilder allText = new StringBuilder();
        for (int i = 0; i < processedImages.size(); i++) {
            Text recognizedText = adapter.getRecognizedText(i);
            if (recognizedText != null) {
                if (i > 0) allText.append("\n\n");
                allText.append(recognizedText.getText());
            }
        }

        String textToFormat = allText.toString().trim();
        if (textToFormat.isEmpty()) {
            Toast.makeText(getContext(), "No text to format", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading dialog
        Dialog loadingDialog = new Dialog(requireContext());
        loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        loadingDialog.setContentView(R.layout.dialog_loading);
        loadingDialog.setCancelable(false);
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        final TextView loadingText = loadingDialog.findViewById(R.id.loading_text);
        loadingDialog.show();

        executor.execute(() -> {
            final int MAX_ATTEMPTS = 3;
            final long BASE_DELAY = 2000; // Start with 2 seconds
            
            for (int currentAttempt = 0; currentAttempt < MAX_ATTEMPTS; currentAttempt++) {
                try {
                    if (currentAttempt > 0) {
                        // Update loading message to show retry attempt
                        final int attemptNumber = currentAttempt + 1;
                        getActivity().runOnUiThread(() -> {
                            loadingText.setText("Service is busy. Retrying... (" + attemptNumber + "/" + MAX_ATTEMPTS + ")");
                        });
                        Thread.sleep(BASE_DELAY * (1L << currentAttempt)); // Exponential backoff
                    }

                    String prompt = "Format and connect the following text into a coherent paragraph, " +
                                  "fixing any formatting, spelling, and grammar issues while maintaining " +
                                  "the original meaning: \n\n" + textToFormat;
                    String formattedText = callOpenAI(prompt);
                    
                    getActivity().runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        showEditableResultDialog("AI Formatted Text", formattedText);
                    });
                    return; // Success, exit the retry loop
                    
                } catch (IOException e) {
                    if (currentAttempt >= MAX_ATTEMPTS - 1 || !e.getMessage().contains("Rate limit exceeded")) {
                        e.printStackTrace();
                        getActivity().runOnUiThread(() -> {
                            loadingDialog.dismiss();
                            String errorMessage;
                            if (e.getMessage().contains("Rate limit exceeded")) {
                                errorMessage = "The service is currently experiencing high demand.\nPlease try again in a few minutes.";
                            } else {
                                errorMessage = "Failed to format text: " + e.getMessage();
                            }
                            showErrorDialog("Format Error", errorMessage);
                        });
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    getActivity().runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        showErrorDialog("Error", "An unexpected error occurred while formatting the text.");
                    });
                    return;
                }
            }
        });
    }

    private void showErrorDialog(String title, String message) {
        Dialog errorDialog = new Dialog(requireContext());
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

    private String callOpenAI(String prompt) throws IOException {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("model", "gpt-3.5-turbo");
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            requestBody.put("messages", new JSONObject[]{message});
        } catch (Exception e) {
            throw new IOException("Failed to create request body: " + e.getMessage());
        }

        Request request = new Request.Builder()
            .url(OPENAI_API_URL)
            .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 429) {
                throw new IOException("Rate limit exceeded");
            }
            
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response: " + response);
            }
            
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            return jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
                
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to process OpenAI response: " + e.getMessage());
        }
    }

    private void summarizeText() {
        if (extractedText.isEmpty()) {
            Toast.makeText(getContext(), "No text to summarize", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            try {
                String prompt = "Summarize the main points of the following text: " + extractedText;
                String summary = callOpenAI(prompt);
                
                getActivity().runOnUiThread(() -> {
                    showResultDialog("Summary", summary);
                });
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Failed to summarize text", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void saveToDoc(String content) {
        executor.execute(() -> {
            try {
                File textFile = new File(getContext().getFilesDir(), fileName + ".txt");
                FileOutputStream out = new FileOutputStream(textFile);
                out.write(content.getBytes());
                out.close();

                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Text file saved successfully", Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Failed to save text file", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showSaveDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_save_document);

        // Make dialog fill width with margins
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(layoutParams);
        }

        EditText etDocumentName = dialog.findViewById(R.id.et_document_name);
        EditText etDocumentContent = dialog.findViewById(R.id.et_document_content);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_save);

        // Set default document name
        String defaultName = fileName.isEmpty() ? 
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date()) : fileName;
        etDocumentName.setText(defaultName);

        // Get all extracted text from all pages
        StringBuilder allText = new StringBuilder();
        for (int i = 0; i < processedImages.size(); i++) {
            Text recognizedText = adapter.getRecognizedText(i);
            if (recognizedText != null) {
                if (i > 0) allText.append("\n\n");
                allText.append(recognizedText.getText());
            }
        }
        etDocumentContent.setText(allText.toString());

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String documentName = etDocumentName.getText().toString().trim();
            String documentContent = etDocumentContent.getText().toString().trim();
            
            if (documentName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a document name", 
                    Toast.LENGTH_SHORT).show();
                return;
            }

            if (documentContent.isEmpty()) {
                Toast.makeText(requireContext(), "Document content cannot be empty", 
                    Toast.LENGTH_SHORT).show();
                return;
            }

            saveDocument(documentName, documentContent);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveDocument(String documentName, String documentContent) {
        // Create new ScannedDocument with required parameters
        ScannedDocument document = new ScannedDocument(
            documentName,           // title
            ANONYMOUS_USER,        // userId
            processedImages,       // localImagePaths
            documentContent        // extractedText
        );

        // Save document
        ScannedDocumentRepository repository = 
            ScannedDocumentRepository.getInstance(requireContext());
        repository.addDocument(document);

        Toast.makeText(requireContext(), "Document saved successfully", 
            Toast.LENGTH_SHORT).show();
    }

    private void showEditableResultDialog(String title, String content) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_save_document);

        // Make dialog fill width with margins
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(layoutParams);
        }

        TextView titleView = dialog.findViewById(R.id.tv_title);
        EditText etDocumentName = dialog.findViewById(R.id.et_document_name);
        EditText etDocumentContent = dialog.findViewById(R.id.et_document_content);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_save);

        titleView.setText(title);

        // Set default document name with "Formatted_" prefix
        String defaultName = "Formatted_" + (fileName.isEmpty() ? 
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date()) : fileName);
        etDocumentName.setText(defaultName);

        // Set the formatted content
        etDocumentContent.setText(content);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String documentName = etDocumentName.getText().toString().trim();
            String documentContent = etDocumentContent.getText().toString().trim();
            
            if (documentName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a document name", 
                    Toast.LENGTH_SHORT).show();
                return;
            }

            if (documentContent.isEmpty()) {
                Toast.makeText(requireContext(), "Document content cannot be empty", 
                    Toast.LENGTH_SHORT).show();
                return;
            }

            saveDocument(documentName, documentContent);
            dialog.dismiss();
        });

        dialog.show();
    }
} 