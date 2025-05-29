package hcmute.edu.vn.ocrscannerproject.ui.scan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.ocrscannerproject.R;

public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA
    };
    private static final String[] STORAGE_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    
    // UI Components
    private com.google.android.material.button.MaterialButtonToggleGroup modeToggleGroup;
    private com.google.android.material.button.MaterialButton btnSingle, btnBatch;
    private FloatingActionButton captureButton;
    private ImageButton btnImportFile, btnImportImage;
    private Button btnComplete;
    private CardView cardBatchPreview;
    private TextView tvBatchCount;
    private ImageView imgBatchPreview;
    private PreviewView previewView;
    private BottomNavigationView bottomNav;
    private FloatingActionButton fabCamera;
    private ImageButton btnClose, btnFlash;
    private Camera camera;
    private boolean isFlashEnabled = false;
    
    // Batch mode variables
    private boolean isBatchMode = false;
    private int batchCount = 0;
    private ArrayList<String> capturedImagePaths = new ArrayList<>();
    
    // CameraX variables
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    
    // Permission and activity result launchers
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private ActivityResultLauncher<String> getImageLauncher;
    private ActivityResultLauncher<String> getDocumentLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide the action bar
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }
        }
        
        // Register permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    // Log each permission result
                    for (String permission : permissions.keySet()) {
                        boolean granted = permissions.get(permission);
                        Log.d(TAG, "Permission " + permission + ": " + (granted ? "GRANTED" : "DENIED"));
                    }
                    
                    boolean allGranted = true;
                    for (Boolean granted : permissions.values()) {
                        allGranted = allGranted && granted;
                    }
                    
                    if (allGranted) {
                        Log.d(TAG, "All permissions granted, starting camera");
                        startCamera();
                    } else {
                        Log.e(TAG, "Some permissions were denied");
                        Toast.makeText(requireContext(),
                                "Vui lòng cấp quyền trong Settings > Apps > OCRScannerProject > Permissions",
                                Toast.LENGTH_LONG).show();
                    }
                });
                
        // Register image picker launcher
        getImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri result) {
                        if (result != null) {
                            handleSelectedMedia(result, "image");
                        }
                    }
                });
        
        // Register document picker launcher
        getDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri result) {
                        if (result != null) {
                            handleSelectedMedia(result, "document");
                        }
                    }
                });
                
        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize UI components
        initializeViews(view);
        
        // Hide bottom navigation
        hideBottomNavigation();
        
        // Request camera permissions
        requestCameraPermissions();
        
        setupListeners();
    }
    
    private void hideBottomNavigation() {
        // Find and hide the bottom navigation view from activity
        if (getActivity() != null) {
            bottomNav = getActivity().findViewById(R.id.view_bottom_navigation);
            if (bottomNav != null) {
                bottomNav.setVisibility(View.GONE);
            }
            
            // Also hide the floating action button
            fabCamera = getActivity().findViewById(R.id.fab_camera);
            if (fabCamera != null) {
                fabCamera.setVisibility(View.GONE);
            }
        }
    }
    
    private void showBottomNavigation() {
        // Show bottom navigation when leaving this fragment
        if (bottomNav != null) {
            bottomNav.setVisibility(View.VISIBLE);
        }
        
        // Also restore the floating action button
        if (fabCamera != null) {
            fabCamera.setVisibility(View.VISIBLE);
        }
    }
    
    private void initializeViews(View view) {
        modeToggleGroup = view.findViewById(R.id.radioGroupMode);
        btnSingle = view.findViewById(R.id.radioSingle);
        btnBatch = view.findViewById(R.id.radioBatch);
        captureButton = view.findViewById(R.id.camera_capture_button);
        btnImportFile = view.findViewById(R.id.btnImportFile);
        btnImportImage = view.findViewById(R.id.btnImportImage);
        btnComplete = view.findViewById(R.id.btnComplete);
        cardBatchPreview = view.findViewById(R.id.cardBatchPreview);
        tvBatchCount = view.findViewById(R.id.tvBatchCount);
        imgBatchPreview = view.findViewById(R.id.imgBatchPreview);
        previewView = view.findViewById(R.id.previewView);
        btnClose = view.findViewById(R.id.btnClose);
        btnFlash = view.findViewById(R.id.btnFlash);
    }
    
    private void setupListeners() {
        // Mode selection listener
        modeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
            isBatchMode = checkedId == R.id.radioBatch;
            
            // Show/hide batch UI elements based on mode
            if (isBatchMode) {
                if (batchCount > 0) {
                    cardBatchPreview.setVisibility(View.VISIBLE);
                    btnComplete.setVisibility(View.VISIBLE);
                }
            } else {
                cardBatchPreview.setVisibility(View.GONE);
                btnComplete.setVisibility(View.GONE);
            }
            
            Toast.makeText(requireContext(), 
                    "Mode: " + (isBatchMode ? "Batch" : "Single"), 
                    Toast.LENGTH_SHORT).show();
            }
        });
        
        // Capture button click listener
        captureButton.setOnClickListener(v -> {
            if (allPermissionsGranted()) {
                captureImage();
            } else {
                Log.d(TAG, "Requesting camera permission");
                requestCameraPermissions();
            }
        });
        
        // Import file button click listener
        btnImportFile.setOnClickListener(v -> {
            if (hasStoragePermissions()) {
                openDocumentPicker();
            } else {
                requestStoragePermissions();
            }
        });
        
        // Import image button click listener
        btnImportImage.setOnClickListener(v -> {
            if (hasStoragePermissions()) {
                openImagePicker();
            } else {
                requestStoragePermissions();
            }
        });
        
        // Complete button click listener (for batch mode)
        btnComplete.setOnClickListener(v -> {
            if (batchCount > 0) {
                // Navigate to review fragment with multiple images
                navigateToReview();
            } else {
                Toast.makeText(requireContext(), "No images captured", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Close button click listener
        btnClose.setOnClickListener(v -> {
            // Navigate back
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });
        
        // Flash button click listener
        btnFlash.setOnClickListener(v -> {
            toggleFlash();
        });
    }
    
    private void requestCameraPermissions() {
        if (allPermissionsGranted()) {
            Log.d(TAG, "All required permissions already granted");
            startCamera();
        } else {
            Log.d(TAG, "Requesting required permissions");
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }
    
    private void requestStoragePermissions() {
        requestPermissionLauncher.launch(STORAGE_PERMISSIONS);
    }
    
    private boolean hasStoragePermissions() {
        for (String permission : STORAGE_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    private void openImagePicker() {
        getImageLauncher.launch("image/*");
    }
    
    private void openDocumentPicker() {
        getDocumentLauncher.launch("application/pdf");
    }
    
    private void handleSelectedMedia(Uri uri, String type) {
        try {
            // Create a temporary file to store the selected media
            File outputFile = createImageFile();
            String outputPath = outputFile.getAbsolutePath();
            
            // Copy the content from URI to the file
            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                
                if (inputStream == null) {
                    Toast.makeText(requireContext(), 
                            "Không thể đọc file", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                outputStream.flush();
                Log.d(TAG, type + " saved to: " + outputPath);
                
                // Handle the file like a captured image
                onImageCaptured(outputPath);
                
            } catch (IOException e) {
                Log.e(TAG, "Error copying file: " + e.getMessage(), e);
                Toast.makeText(requireContext(), 
                        "Lỗi khi lưu file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating file: " + e.getMessage(), e);
            Toast.makeText(requireContext(), 
                    "Lỗi khi tạo file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(requireContext());
                
        cameraProviderFuture.addListener(() -> {
            try {
                // Camera provider is now guaranteed to be available
                cameraProvider = cameraProviderFuture.get();
                
                // Set up the view finder use case to display camera preview
                Preview preview = new Preview.Builder().build();
                
                // Select back camera as a default
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();
                
                // Set up the capture use case to allow users to take photos
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();
                
                // Unbind use cases before rebinding
                cameraProvider.unbindAll();
                
                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(),
                        cameraSelector,
                        preview,
                        imageCapture);
                
                // Connect the preview use case to the previewView
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                
                Log.d(TAG, "Camera started successfully");
                
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage(), e);
                Toast.makeText(requireContext(), 
                        "Không thể khởi tạo camera: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
    
    private void captureImage() {
        if (imageCapture == null) {
            Log.e(TAG, "Cannot capture image, imageCapture is null");
            Toast.makeText(requireContext(), "Camera chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create output file to hold the image
        File photoFile;
        try {
            photoFile = createImageFile();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create image file: " + e.getMessage(), e);
            Toast.makeText(requireContext(), 
                    "Không thể tạo file ảnh: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        
        String currentPhotoPath = photoFile.getAbsolutePath();
        Log.d(TAG, "Image will be saved to: " + currentPhotoPath);
        
        // Create output options object which contains file + metadata
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions
                .Builder(photoFile)
                .build();
        
        // Set up image capture listener
        imageCapture.takePicture(
                outputOptions, 
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.d(TAG, "Image saved successfully: " + currentPhotoPath);
                        Toast.makeText(requireContext(), "Đã chụp ảnh thành công", Toast.LENGTH_SHORT).show();
                        onImageCaptured(currentPhotoPath);
                    }
                    
                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Error capturing image: " + exception.getMessage(), exception);
                        Toast.makeText(requireContext(),
                                "Lỗi khi chụp ảnh: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "OCR_" + timeStamp;
        
        // Sử dụng bộ nhớ trong phạm vi ứng dụng thay vì bộ nhớ ngoài
        File storageDir = new File(requireContext().getFilesDir(), "OCRScanner");
        
        // Đảm bảo thư mục tồn tại
        if (!storageDir.exists()) {
            boolean dirCreated = storageDir.mkdirs();
            Log.d(TAG, "Directory created: " + dirCreated + " at " + storageDir.getAbsolutePath());
        } else {
            Log.d(TAG, "Directory already exists at " + storageDir.getAbsolutePath());
        }
        
        // Tạo file với định dạng tên đầy đủ
        File image = new File(storageDir, imageFileName + ".jpg");
        
        // Đảm bảo file không tồn tại trước đó
        if (image.exists()) {
            image.delete();
        }
        
        // Tạo file trống
        boolean fileCreated = image.createNewFile();
        Log.d(TAG, "Image file created: " + fileCreated + " at " + image.getAbsolutePath());
        
        return image;
    }
    
    private void onImageCaptured(String imagePath) {
        Log.d(TAG, "Image captured: " + imagePath);
        
        // Debug file ảnh
        debugImageFile(imagePath);
        
        // Add captured image to the list
        capturedImagePaths.add(imagePath);
        
        // Load thumbnail for preview
        try {
            Bitmap thumbnail = BitmapFactory.decodeFile(imagePath);
            if (thumbnail != null && imgBatchPreview != null) {
                Log.d(TAG, "Loaded thumbnail: " + thumbnail.getWidth() + "x" + thumbnail.getHeight());
                imgBatchPreview.setImageBitmap(thumbnail);
            } else {
                Log.e(TAG, "Failed to load thumbnail from: " + imagePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading thumbnail: " + e.getMessage(), e);
        }
        
        if (isBatchMode) {
            // Increment batch count and update UI
            batchCount++;
            tvBatchCount.setText(String.valueOf(batchCount));
            
            // Show batch preview components
            cardBatchPreview.setVisibility(View.VISIBLE);
            btnComplete.setVisibility(View.VISIBLE);
            
            Toast.makeText(requireContext(), 
                    "Đã chụp ảnh " + batchCount + " vào batch", 
                    Toast.LENGTH_SHORT).show();
        } else {
            // Single mode - navigate directly to review
            Toast.makeText(requireContext(), "Đang chuyển đến màn hình xem lại...", Toast.LENGTH_SHORT).show();
            navigateToReview();
        }
    }
    
    private void navigateToReview() {
        try {
            // Kiểm tra danh sách ảnh
            if (capturedImagePaths.isEmpty()) {
                Log.e(TAG, "No images to review");
                Toast.makeText(requireContext(), "Không có ảnh để xem lại", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Log tất cả các đường dẫn ảnh
            Log.d(TAG, "Images to review: " + capturedImagePaths.size());
            for (int i = 0; i < capturedImagePaths.size(); i++) {
                Log.d(TAG, "Image " + i + ": " + capturedImagePaths.get(i));
                debugImageFile(capturedImagePaths.get(i));
            }
            
            // Create a bundle with the necessary data
            Bundle args = new Bundle();
            
            // Chuyển đổi ArrayList<String> thành String[] trước khi truyền
            String[] imagePathsArray = capturedImagePaths.toArray(new String[0]);
            args.putStringArray("capturedImages", imagePathsArray);
            
            Log.d(TAG, "Navigating to review with " + imagePathsArray.length + " images");
            
            // Navigate to review fragment
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_scanFragment_to_reviewFragment, args);
            
            // Reset batch state
            resetBatchState();
        } catch (Exception e) {
            Log.e(TAG, "Navigation error: " + e.getMessage(), e);
            Toast.makeText(requireContext(), 
                    "Lỗi chuyển màn hình: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
        }
    }
    
    private void resetBatchState() {
        batchCount = 0;
        capturedImagePaths.clear();
        cardBatchPreview.setVisibility(View.GONE);
        btnComplete.setVisibility(View.GONE);
        tvBatchCount.setText("0");
    }
    
    private boolean allPermissionsGranted() {
        // Chỉ kiểm tra quyền CAMERA là bắt buộc
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Required permission not granted: " + permission);
                return false;
            }
        }
        
        Log.d(TAG, "All required permissions granted");
        return true;
    }

    /**
     * Phương thức debug để kiểm tra file ảnh
     */
    private void debugImageFile(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            Log.e(TAG, "DEBUG: Image path is null or empty");
            return;
        }
        
        File file = new File(imagePath);
        if (file.exists()) {
            Log.d(TAG, "DEBUG: File exists at " + imagePath);
            Log.d(TAG, "DEBUG: File size: " + file.length() + " bytes");
            Log.d(TAG, "DEBUG: File can read: " + file.canRead());
            Log.d(TAG, "DEBUG: File can write: " + file.canWrite());
            Log.d(TAG, "DEBUG: File last modified: " + new Date(file.lastModified()));
            
            // Thử đọc file để xem có vấn đề gì không
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(imagePath, options);
                Log.d(TAG, "DEBUG: Image dimensions: " + options.outWidth + "x" + options.outHeight);
                
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    Log.e(TAG, "DEBUG: Invalid image dimensions, file might be corrupted");
                }
            } catch (Exception e) {
                Log.e(TAG, "DEBUG: Error reading image file: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "DEBUG: File does NOT exist at " + imagePath);
            
            // Kiểm tra thư mục
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                Log.d(TAG, "DEBUG: Parent directory exists: " + parentDir.exists());
                if (parentDir.exists()) {
                    Log.d(TAG, "DEBUG: Files in directory: " + Arrays.toString(parentDir.list()));
                }
            }
        }
    }
    
    private void toggleFlash() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isFlashEnabled = !isFlashEnabled;
            camera.getCameraControl().enableTorch(isFlashEnabled);
            
            // Update flash icon
            btnFlash.setImageResource(isFlashEnabled ? 
                android.R.drawable.ic_menu_revert : 
                android.R.drawable.ic_menu_camera);
            
            // Show feedback
            Toast.makeText(requireContext(),
                    "Flash " + (isFlashEnabled ? "On" : "Off"),
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(),
                    "Flash not available on this device",
                    Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Hide the action bar when returning to this fragment
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }
        }
        
        // Also hide bottom navigation
        hideBottomNavigation();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Show bottom navigation when leaving this fragment
        showBottomNavigation();
        
        // Restore action bar
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
        }
        
        // Shut down camera executor
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
} 