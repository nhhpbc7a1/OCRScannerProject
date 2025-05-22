package hcmute.edu.vn.ocrscannerproject.ui.review;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;
import hcmute.edu.vn.ocrscannerproject.data.ScannedDocumentRepository;

public class ReviewFragment extends Fragment {

    private static final String TAG = "ReviewFragment";
    private static final String ANONYMOUS_USER = "anonymous";
    
    private ImageButton btnBack;
    private EditText etFileName;
    private ViewPager2 viewPagerImages;
    private TabLayout indicatorDots;
    private LinearLayout actionAddImage, actionRotate, actionCrop, actionFilter, actionExtractText, actionSave;
    
    private ArrayList<String> capturedImages = new ArrayList<>();
    private ArrayList<String> processedImages = new ArrayList<>();
    private int currentPageIndex = 0;
    private boolean isProcessing = false;
    
    // Rotation angles for each image
    private List<Integer> rotationAngles = new ArrayList<>();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public static ReviewFragment newInstance(String[] imagePaths) {
        ReviewFragment fragment = new ReviewFragment();
        Bundle args = new Bundle();
        args.putStringArray("capturedImages", imagePaths);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            // Nhận dữ liệu dưới dạng String[] và chuyển thành ArrayList
            String[] imagePathsArray = getArguments().getStringArray("capturedImages");
            if (imagePathsArray != null) {
                capturedImages = new ArrayList<>(Arrays.asList(imagePathsArray));
            } else {
                capturedImages = new ArrayList<>();
            }
            
            // Initialize rotation angles to 0 for each image
            for (int i = 0; i < capturedImages.size(); i++) {
                rotationAngles.add(0);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        setupListeners();
        setupViewPager();
        setupDefaultFileName();
        
        // Pre-process images for better OCR
        processImages();
    }
    
    private void initializeViews(View view) {
        btnBack = view.findViewById(R.id.btn_back);
        etFileName = view.findViewById(R.id.et_file_name);
        viewPagerImages = view.findViewById(R.id.view_pager_images);
        indicatorDots = view.findViewById(R.id.indicator_dots);
        
        actionAddImage = view.findViewById(R.id.action_add_image);
        actionRotate = view.findViewById(R.id.action_rotate);
        actionCrop = view.findViewById(R.id.action_crop);
        actionFilter = view.findViewById(R.id.action_filter);
        actionExtractText = view.findViewById(R.id.action_extract_text);
        actionSave = view.findViewById(R.id.action_save);
    }
    
    private void setupListeners() {
        // Back button with discard confirmation
        btnBack.setOnClickListener(v -> {
            showDiscardConfirmationDialog();
        });
        
        // Action buttons
        actionAddImage.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Add image", Toast.LENGTH_SHORT).show();
            // Open gallery or camera to add more images
        });
        
        actionRotate.setOnClickListener(v -> {
            if (capturedImages.isEmpty() || currentPageIndex >= capturedImages.size()) {
                return;
            }
            
            // Rotate the current image by 90 degrees
            int currentAngle = rotationAngles.get(currentPageIndex);
            int newAngle = (currentAngle + 90) % 360;
            rotationAngles.set(currentPageIndex, newAngle);
            
            // Update the view pager adapter
            updateImageInAdapter(currentPageIndex);
            
            Toast.makeText(requireContext(), "Image rotated", Toast.LENGTH_SHORT).show();
        });
        
        actionCrop.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Crop image", Toast.LENGTH_SHORT).show();
            // Crop current image
        });
        
        actionFilter.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Apply filter", Toast.LENGTH_SHORT).show();
            // Apply filter to current image
        });
        
        actionExtractText.setOnClickListener(v -> {
            if (isProcessing) {
                Toast.makeText(requireContext(), "Still processing images...", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (processedImages.isEmpty()) {
                Toast.makeText(requireContext(), "Processing images for OCR...", Toast.LENGTH_SHORT).show();
                processImages();
                return;
            }
            
            Toast.makeText(requireContext(), "Extracting text...", Toast.LENGTH_SHORT).show();
            navigateToTextExtraction();
        });
        
        actionSave.setOnClickListener(v -> {
            String fileName = etFileName.getText().toString().trim();
            if (fileName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a file name", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Save document with processed images
            saveDocument(fileName);
        });
    }
    
    private void setupViewPager() {
        // If there are no images, don't set up view pager
        if (capturedImages.isEmpty()) {
            return;
        }
        
        // Create an adapter for the view pager
        ReviewImageAdapter adapter = new ReviewImageAdapter(requireContext(), capturedImages, rotationAngles);
        viewPagerImages.setAdapter(adapter);
        
        // Setup page change listener
        viewPagerImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPageIndex = position;
            }
        });
        
        // Setup dots indicator for multiple images
        if (capturedImages.size() > 1) {
            new TabLayoutMediator(indicatorDots, viewPagerImages, (tab, position) -> {
                // Just create the tab, no custom view needed
            }).attach();
            indicatorDots.setVisibility(View.VISIBLE);
        } else {
            indicatorDots.setVisibility(View.GONE);
        }
    }
    
    private void updateImageInAdapter(int position) {
        if (viewPagerImages.getAdapter() instanceof ReviewImageAdapter) {
            ReviewImageAdapter adapter = (ReviewImageAdapter) viewPagerImages.getAdapter();
            adapter.notifyItemChanged(position);
        }
    }
    
    private void setupDefaultFileName() {
        // Generate default file name with current date/time
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String defaultFileName = "Scan_" + sdf.format(new Date());
        etFileName.setText(defaultFileName);
    }
    
    private void processImages() {
        if (capturedImages.isEmpty() || isProcessing) {
            return;
        }
        
        isProcessing = true;
        processedImages.clear();
        
        // Process images in background
        executor.execute(() -> {
            for (int i = 0; i < capturedImages.size(); i++) {
                String imagePath = capturedImages.get(i);
                int rotation = rotationAngles.get(i);
                
                try {
                    // Load and process image
                    String processedPath = preprocessImageForOCR(imagePath, rotation);
                    if (processedPath != null) {
                        processedImages.add(processedPath);
                    } else {
                        // If processing failed, use original
                        processedImages.add(imagePath);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing image: " + e.getMessage(), e);
                    processedImages.add(imagePath); // Use original on error
                }
            }
            
            requireActivity().runOnUiThread(() -> {
                isProcessing = false;
                Toast.makeText(requireContext(), 
                        processedImages.size() + " images ready for OCR", 
                        Toast.LENGTH_SHORT).show();
            });
        });
    }
    
    private String preprocessImageForOCR(String imagePath, int rotation) {
        try {
            // Load bitmap
            Bitmap bitmap;
            
            if (imagePath.startsWith("content://")) {
                bitmap = MediaStore.Images.Media.getBitmap(
                        requireContext().getContentResolver(), Uri.parse(imagePath));
            } else {
                bitmap = BitmapFactory.decodeFile(imagePath);
            }
            
            if (bitmap == null) {
                return null;
            }
            
            // Apply rotation if needed
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), 
                        matrix, true);
            }
            
            // Save processed image
            File outputDir = new File(requireContext().getCacheDir(), "ocr_processed");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            String fileName = "processed_" + System.currentTimeMillis() + ".jpg";
            File outputFile = new File(outputDir, fileName);
            
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            }
            
            return outputFile.getAbsolutePath();
            
        } catch (IOException e) {
            Log.e(TAG, "Error preprocessing image: " + e.getMessage(), e);
            return null;
        }
    }
    
    private void showDiscardConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Discard changes?")
                .setMessage("Are you sure you want to discard the current scan?")
                .setPositiveButton("Discard", (dialog, which) -> {
                    // Navigate back
                    Navigation.findNavController(requireView()).popBackStack();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void navigateToTextExtraction() {
        try {
            // Create a bundle with the necessary data for text extraction
            Bundle args = new Bundle();
            
            // Chuyển đổi ArrayList<String> thành String[] trước khi truyền
            ArrayList<String> imagesToUse = processedImages.isEmpty() ? capturedImages : processedImages;
            
            if (imagesToUse.isEmpty()) {
                Toast.makeText(requireContext(), "No images to process", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Đảm bảo tất cả các đường dẫn ảnh đều hợp lệ
            ArrayList<String> validImages = new ArrayList<>();
            for (String path : imagesToUse) {
                if (path != null && !path.isEmpty()) {
                    if (path.startsWith("content://") || new File(path).exists()) {
                        validImages.add(path);
                    } else {
                        Log.w(TAG, "Invalid image path: " + path);
                    }
                }
            }
            
            if (validImages.isEmpty()) {
                Toast.makeText(requireContext(), "No valid images to process", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String[] imagePathsArray = validImages.toArray(new String[0]);
            args.putStringArray("processedImages", imagePathsArray);
            
            args.putString("fileName", etFileName.getText().toString().trim());
            
            // Navigate to text extraction fragment
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_reviewFragment_to_extractTextFragment, args);
        } catch (Exception e) {
            Log.e(TAG, "Navigation error: " + e.getMessage(), e);
            Toast.makeText(requireContext(), 
                    "Navigation error: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
        }
    }
    
    private void saveDocument(String fileName) {
        try {
            // Get the repository instance
            ScannedDocumentRepository repository = ScannedDocumentRepository.getInstance(requireContext());
            
            // Lấy danh sách ảnh để sử dụng
            ArrayList<String> imagesToUse = processedImages.isEmpty() ? capturedImages : processedImages;
            if (imagesToUse.isEmpty()) {
                Toast.makeText(requireContext(), "No images to save", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Danh sách đường dẫn ảnh hợp lệ
            List<String> validImagePaths = new ArrayList<>();
            
            // Lặp qua tất cả các ảnh và xử lý chúng
            for (String path : imagesToUse) {
                if (path != null && !path.isEmpty()) {
                    if (path.startsWith("content://")) {
                        // Nếu là URI content, tạo một bản sao cục bộ
                        String localPath = copyContentUriToFile(Uri.parse(path));
                        if (localPath != null) {
                            validImagePaths.add(localPath);
                        }
                    } else if (new File(path).exists()) {
                        validImagePaths.add(path);
                    }
                }
            }
            
            if (validImagePaths.isEmpty()) {
                Toast.makeText(requireContext(), "No valid images to save", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create and save the document with all valid images
            ScannedDocument document = new ScannedDocument(fileName, ANONYMOUS_USER, validImagePaths);
            document.setType("Image");
            repository.addDocument(document);
            
            Toast.makeText(requireContext(), 
                    "Document saved with " + validImagePaths.size() + " image(s): " + fileName, 
                    Toast.LENGTH_SHORT).show();
            
            // Navigate back to home
            Navigation.findNavController(requireView()).popBackStack();
        } catch (Exception e) {
            Log.e(TAG, "Error saving document: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Error saving document: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Tạo một bản sao cục bộ của ảnh từ URI content
     */
    private String copyContentUriToFile(Uri uri) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "IMG_" + timeStamp + ".jpg";
            
            File outputDir = new File(requireContext().getFilesDir(), "saved_images");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            File outputFile = new File(outputDir, fileName);
            
            // Sao chép ảnh từ URI vào file
            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                
                if (inputStream == null) {
                    return null;
                }
                
                byte[] buffer = new byte[4 * 1024]; // 4kb buffer
                int read;
                
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                
                outputStream.flush();
                return outputFile.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error copying content URI to file: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Adapter class for the ViewPager to display captured images
     */
    private static class ReviewImageAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ReviewImageAdapter.ImageViewHolder> {
        private final Context context;
        private final List<String> imagePaths;
        private final List<Integer> rotationAngles;
        
        public ReviewImageAdapter(Context context, List<String> imagePaths, List<Integer> rotationAngles) {
            this.context = context;
            this.imagePaths = imagePaths;
            this.rotationAngles = rotationAngles;
        }
        
        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(context);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new ImageViewHolder(imageView);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            String imagePath = imagePaths.get(position);
            int rotation = rotationAngles.get(position);
            
            try {
                Bitmap bitmap;
                if (imagePath.startsWith("content://")) {
                    bitmap = MediaStore.Images.Media.getBitmap(
                            context.getContentResolver(), Uri.parse(imagePath));
                } else {
                    bitmap = BitmapFactory.decodeFile(imagePath);
                }
                
                if (bitmap != null && rotation != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(rotation);
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), 
                            matrix, true);
                }
                
                holder.imageView.setImageBitmap(bitmap);
            } catch (IOException e) {
                Log.e(TAG, "Error loading image: " + e.getMessage(), e);
                holder.imageView.setImageResource(R.drawable.ic_menu_report_image);
            }
        }
        
        @Override
        public int getItemCount() {
            return imagePaths.size();
        }
        
        static class ImageViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            final ImageView imageView;
            
            public ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                this.imageView = (ImageView) itemView;
            }
        }
    }
} 