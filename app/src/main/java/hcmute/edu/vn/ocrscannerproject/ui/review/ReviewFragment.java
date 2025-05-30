package hcmute.edu.vn.ocrscannerproject.ui.review;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;
import hcmute.edu.vn.ocrscannerproject.data.ScannedDocumentRepository;
import android.media.ExifInterface;

public class ReviewFragment extends Fragment {

    private static final String TAG = "ReviewFragment";
    private static final String ANONYMOUS_USER = "anonymous";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    
    private TextView tvFileName;
    private ImageButton btnBack;
    private ImageView btnEditName;
    private ViewPager2 viewPagerImages;
    private LinearLayout actionRotate, actionCrop, actionExtractText, actionSave;
    private BottomNavigationView bottomNav;
    private FloatingActionButton fabCamera;
    
    private ArrayList<String> capturedImages = new ArrayList<>();
    private ArrayList<String> processedImages = new ArrayList<>();
    private int currentPageIndex = 0;
    private boolean isProcessing = false;
    
    // Rotation angles for each image
    private List<Integer> rotationAngles = new ArrayList<>();
    private ExecutorService executor;
    
    private TextView tvPageIndicator;
    private ImageButton btnPrevious, btnNext;

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
        executor = Executors.newSingleThreadExecutor();
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
        View view = inflater.inflate(R.layout.fragment_review, container, false);
        
        // Initialize views
        tvFileName = view.findViewById(R.id.tv_file_name);
        btnBack = view.findViewById(R.id.btn_back);
        btnEditName = view.findViewById(R.id.btn_edit_name);
        viewPagerImages = view.findViewById(R.id.view_pager_images);
        
        // Initialize page indicator views
        tvPageIndicator = view.findViewById(R.id.tv_page_indicator);
        btnPrevious = view.findViewById(R.id.btn_previous);
        btnNext = view.findViewById(R.id.btn_next);
        
        actionRotate = view.findViewById(R.id.action_rotate);
        actionCrop = view.findViewById(R.id.action_crop);
        actionExtractText = view.findViewById(R.id.action_extract_text);
        actionSave = view.findViewById(R.id.action_save);
        
        // Set up click listeners
        btnBack.setOnClickListener(v -> {
            showDiscardConfirmationDialog();
        });
        
        // Make the entire filename layout clickable
        View layoutFilename = view.findViewById(R.id.layout_filename);
        layoutFilename.setOnClickListener(v -> showEditFileNameDialog());
        btnEditName.setOnClickListener(v -> showEditFileNameDialog());
        
        setupListeners();
        setupViewPager();
        setupDefaultFileName();
        
        // Pre-process images for better OCR
        processImages();
        
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize UI components
        initializeViews(view);
        setupListeners();
        setupViewPager();
        setupDefaultFileName();
        
        // Hide bottom navigation
        hideBottomNavigation();

        Log.d(TAG, "onViewCreated: Hiding bottom navigation and action bar");
        
        // Pre-process images for better OCR
        processImages();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Hide bottom navigation
        hideBottomNavigation();
        Log.d(TAG, "onResume: Hiding bottom navigation and action bar");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Show bottom navigation when leaving this fragment
        showBottomNavigation();
        Log.d(TAG, "onDestroyView: Showing bottom navigation");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Shutdown executor properly
        if (!executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
    
    private void initializeViews(View view) {
        // Initialize any other views if needed
    }
    
    private void setupListeners() {
        // Back button with discard confirmation
        btnBack.setOnClickListener(v -> {
            showDiscardConfirmationDialog();
        });
        
        // Action buttons
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
            String fileName = tvFileName.getText().toString().trim();
            if (fileName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a file name", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Save document with processed images
            saveDocument(fileName);
        });

        // Crop button
        actionCrop.setOnClickListener(v -> {
            if (capturedImages.isEmpty() || currentPageIndex >= capturedImages.size()) {
                return;
            }
            
            String currentImagePath = capturedImages.get(currentPageIndex);
            // Start crop activity
            startCropActivity(currentImagePath);
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
        
        // Update initial page indicator
        updatePageIndicator(0);
        
        // Setup page change listener
        viewPagerImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPageIndex = position;
                updatePageIndicator(position);
            }
        });
        
        // Setup navigation buttons
        setupNavigationButtons();
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
        tvFileName.setText(defaultFileName);
    }
    
    private void processImages() {
        if (capturedImages.isEmpty() || isProcessing) {
            return;
        }
        
        isProcessing = true;
        processedImages.clear();
        
        try {
            // Process images in background
            executor.execute(() -> {
                try {
                    for (int i = 0; i < capturedImages.size(); i++) {
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        
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
                    
                    if (getActivity() != null && !Thread.currentThread().isInterrupted()) {
                        getActivity().runOnUiThread(() -> {
                            isProcessing = false;
                            if (isAdded()) {
                                Toast.makeText(requireContext(), 
                                        processedImages.size() + " images ready for OCR", 
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in processImages: " + e.getMessage(), e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            isProcessing = false;
                        });
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Task rejected: " + e.getMessage(), e);
            isProcessing = false;
        }
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
            
            args.putString("fileName", tvFileName.getText().toString().trim());
            
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
    
    private void showEditFileNameDialog() {
        // Create dialog with custom layout
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_filename, null);
        builder.setView(dialogView);
        
        // Get reference to EditText in dialog
        TextInputEditText etFilename = dialogView.findViewById(R.id.et_filename);
        // Set current text
        etFilename.setText(tvFileName.getText());
        etFilename.setSelection(etFilename.getText().length());
        
        // Add Cancel and Save buttons
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newFileName = etFilename.getText().toString().trim();
            if (!newFileName.isEmpty()) {
                tvFileName.setText(newFileName);
            }
        });
        
        // Show dialog
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void hideBottomNavigation() {
        // Find and hide the bottom navigation view from activity
        if (getActivity() != null) {
            // Hide action bar
            if (getActivity() instanceof AppCompatActivity) {
                ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    Log.d(TAG, "hideBottomNavigation: Hiding action bar");
                    actionBar.hide();
                } else {
                    Log.d(TAG, "hideBottomNavigation: ActionBar is null");
                }
            }

            bottomNav = getActivity().findViewById(R.id.bottomNavigationView);
            if (bottomNav != null) {
                Log.d(TAG, "hideBottomNavigation: Hiding bottom navigation");
                bottomNav.setVisibility(View.GONE);
            }
            
            // Also hide the floating action button
            fabCamera = getActivity().findViewById(R.id.fab_camera);
            if (fabCamera != null) {
                Log.d(TAG, "hideBottomNavigation: Hiding camera FAB");
                fabCamera.setVisibility(View.GONE);
            }
        } else {
            Log.d(TAG, "hideBottomNavigation: Activity is null");
        }
    }
    
    private void showBottomNavigation() {
        // Show bottom navigation when leaving this fragment
        if (bottomNav != null) {
            Log.d(TAG, "showBottomNavigation: Showing bottom navigation");
            bottomNav.setVisibility(View.VISIBLE);
        }
        
        // Also restore the floating action button
        if (fabCamera != null) {
            Log.d(TAG, "showBottomNavigation: Showing camera FAB");
            fabCamera.setVisibility(View.VISIBLE);
        }
    }
    
    private void updatePageIndicator(int position) {
        int totalPages = capturedImages.size();
        tvPageIndicator.setText(String.format(Locale.getDefault(), "%d/%d", position + 1, totalPages));
        
        // Update navigation buttons state
        btnPrevious.setEnabled(position > 0);
        btnNext.setEnabled(position < totalPages - 1);
        
        // Update alpha for better visual feedback
        btnPrevious.setAlpha(position > 0 ? 1.0f : 0.5f);
        btnNext.setAlpha(position < totalPages - 1 ? 1.0f : 0.5f);
    }
    
    private void setupNavigationButtons() {
        btnPrevious.setOnClickListener(v -> {
            if (currentPageIndex > 0) {
                viewPagerImages.setCurrentItem(currentPageIndex - 1, true);
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentPageIndex < capturedImages.size() - 1) {
                viewPagerImages.setCurrentItem(currentPageIndex + 1, true);
            }
        });
    }
    
    private void startCropActivity(String imagePath) {
        try {
            Uri imageUri;
            if (imagePath.startsWith("content://")) {
                imageUri = Uri.parse(imagePath);
            } else {
                imageUri = Uri.fromFile(new File(imagePath));
            }
            
            // Start UCrop activity
            String destinationFileName = "cropped_" + System.currentTimeMillis() + ".jpg";
            UCrop.of(imageUri, Uri.fromFile(new File(requireContext().getCacheDir(), destinationFileName)))
                    .withAspectRatio(0, 0) // Free form
                    .withMaxResultSize(1920, 1920) // Max resolution
                    .start(requireContext(), this);
        } catch (Exception e) {
            Log.e(TAG, "Error starting crop activity", e);
            Toast.makeText(requireContext(), "Error starting crop activity", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case UCrop.REQUEST_CROP:
                    // Handle crop result
                    handleCropResult(data);
                    break;
            }
        } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
            final Throwable cropError = UCrop.getError(data);
            Log.e(TAG, "Crop error: " + cropError);
            Toast.makeText(requireContext(), "Error cropping image", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleCropResult(Intent data) {
        final Uri resultUri = UCrop.getOutput(data);
        if (resultUri != null) {
            try {
                // Replace the current image with the cropped one
                String croppedPath = getPathFromUri(resultUri);
                if (croppedPath != null && currentPageIndex < capturedImages.size()) {
                    capturedImages.set(currentPageIndex, croppedPath);
                    rotationAngles.set(currentPageIndex, 0); // Reset rotation for cropped image
                    updateViewPager();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling crop result", e);
                Toast.makeText(requireContext(), "Error saving cropped image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateViewPager() {
        ReviewImageAdapter adapter = new ReviewImageAdapter(requireContext(), capturedImages, rotationAngles);
        viewPagerImages.setAdapter(adapter);
        updatePageIndicator(currentPageIndex);
    }

    // Helper methods
    private String currentPhotoPath;

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",        /* suffix */
                storageDir     /* directory */
        );
        
        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private String getPathFromUri(Uri uri) {
        try {
            if (uri.getScheme().equals("file")) {
                return uri.getPath();
            } else if (uri.getScheme().equals("content")) {
                String[] projection = {MediaStore.Images.Media.DATA};
                Cursor cursor = requireContext().getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                        }
                    } finally {
                        cursor.close();
                    }
                }
                // If we can't get the file path, just use the content URI as is
                return uri.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting path from uri", e);
        }
        return null;
    }
    
    /**
     * Adapter class for the ViewPager to display captured images
     */
    private static class ReviewImageAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ReviewImageAdapter.ImageViewHolder> {
        private static final String TAG = "ReviewImageAdapter";
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
            int userRotation = rotationAngles.get(position);
            
            try {
                // Get EXIF orientation
                ExifInterface exif = new ExifInterface(imagePath);
                int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);

                // Calculate total rotation based on EXIF and user rotation
                int totalRotation = userRotation;
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        totalRotation += 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        totalRotation += 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        totalRotation += 270;
                        break;
                }
                
                // Normalize rotation to 0-360
                totalRotation = ((totalRotation % 360) + 360) % 360;

                // Use Glide to load and rotate the image
                RequestOptions options = new RequestOptions()
                    .transform(new RotateTransformation(context, totalRotation));
                
                Glide.with(context)
                    .load(imagePath)
                    .apply(options)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Error loading image: " + e);
                            holder.imageView.setImageResource(R.drawable.ic_menu_report_image);
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(holder.imageView);
                    
            } catch (Exception e) {
                Log.e(TAG, "Error loading image: " + e.getMessage(), e);
                holder.imageView.setImageResource(R.drawable.ic_menu_report_image);
            }
        }
        
        private static class RotateTransformation extends BitmapTransformation {
            private final float rotateRotationAngle;

            RotateTransformation(Context context, float rotateRotationAngle) {
                super();
                this.rotateRotationAngle = rotateRotationAngle;
            }

            @Override
            protected Bitmap transform(@NonNull com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotateRotationAngle);
                return Bitmap.createBitmap(toTransform, 0, 0, toTransform.getWidth(), toTransform.getHeight(), matrix, true);
            }

            @Override
            public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
                messageDigest.update(("rotate" + rotateRotationAngle).getBytes());
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