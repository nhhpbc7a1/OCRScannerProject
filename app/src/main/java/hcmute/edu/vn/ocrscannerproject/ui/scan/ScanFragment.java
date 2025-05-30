package hcmute.edu.vn.ocrscannerproject.ui.scan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.Image;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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
import androidx.camera.core.AspectRatio;
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
import com.google.android.material.button.MaterialButton;
import com.bumptech.glide.Glide;

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
import android.media.ExifInterface;

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
    private ImageButton btnImportFile, btnImportImage, btnDiscard;
    private MaterialButton btnComplete;
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

    private View previewContainer, importFileContainer, importImageContainer, discardContainer, completeContainer;

    private MediaPlayer mediaPlayer;
    private File outputDirectory;
    private List<File> photoList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize photoList
        photoList = new ArrayList<>();
        
        // Initialize output directory
        outputDirectory = new File(requireContext().getFilesDir(), "OCRScanner");
        if (!outputDirectory.exists()) {
            boolean dirCreated = outputDirectory.mkdirs();
            Log.d(TAG, "Output directory created: " + dirCreated + " at " + outputDirectory.getAbsolutePath());
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

        // Initialize MediaPlayer for camera shutter sound
        mediaPlayer = MediaPlayer.create(requireContext(), R.raw.camera_shutter);
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
            bottomNav = getActivity().findViewById(R.id.bottomNavigationView);
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
        
        // Initialize containers
        importFileContainer = view.findViewById(R.id.importFileContainer);
        importImageContainer = view.findViewById(R.id.importImageContainer);
        discardContainer = view.findViewById(R.id.discardContainer);
        completeContainer = view.findViewById(R.id.completeContainer);
        
        // Initialize buttons
        btnClose = view.findViewById(R.id.btnClose);
        btnFlash = view.findViewById(R.id.btnFlash);
        btnImportFile = view.findViewById(R.id.btnImportFile);
        btnImportImage = view.findViewById(R.id.btnImportImage);
        btnDiscard = view.findViewById(R.id.btnDiscard);
        btnComplete = view.findViewById(R.id.btnComplete);
        
        previewContainer = view.findViewById(R.id.previewContainer);
        cardBatchPreview = view.findViewById(R.id.cardBatchPreview);
        tvBatchCount = view.findViewById(R.id.tvBatchCount);
        imgBatchPreview = view.findViewById(R.id.imgBatchPreview);
        previewView = view.findViewById(R.id.previewView);

        // Set Single mode as default
        modeToggleGroup.check(R.id.radioSingle);
        isBatchMode = false;

        // Set initial visibility
        previewContainer.setVisibility(View.GONE);
        discardContainer.setVisibility(View.GONE);
        completeContainer.setVisibility(View.GONE);
    }
    
    private void setupListeners() {
        // Mode selection listener
        modeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
            isBatchMode = checkedId == R.id.radioBatch;
            
                // Update UI based on mode
            if (isBatchMode) {
                if (batchCount > 0) {
                        updateBatchModeUI(true);
                }
            } else {
                    updateBatchModeUI(false);
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
            if (batchCount > 0) {
                // Show discard confirmation dialog
                showDiscardConfirmationDialog();
            } else {
                // Navigate back directly if no images captured
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            }
        });
        
        // Discard button click listener
        btnDiscard.setOnClickListener(v -> {
            showDiscardConfirmationDialog();
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
                        .setTargetRotation(requireView().getDisplay().getRotation())
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setJpegQuality(100)
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
        if (imageCapture == null) return;

        // Create output file to hold the image
        File photoFile = new File(
                outputDirectory,
                new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
                        .format(System.currentTimeMillis()) + ".jpg");

        // Create output options object which contains file + metadata
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(new ImageCapture.Metadata())
                .build();

        // Set up image capture listener
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri savedUri = Uri.fromFile(photoFile);
                        try {
                            // Set EXIF orientation
                            ExifInterface exif = new ExifInterface(photoFile.getAbsolutePath());
                            int rotation = requireView().getDisplay().getRotation();
                            int orientation;
                            switch (rotation) {
                                case Surface.ROTATION_90:
                                    orientation = ExifInterface.ORIENTATION_ROTATE_90;
                                    break;
                                case Surface.ROTATION_180:
                                    orientation = ExifInterface.ORIENTATION_ROTATE_180;
                                    break;
                                case Surface.ROTATION_270:
                                    orientation = ExifInterface.ORIENTATION_ROTATE_270;
                                    break;
                                default:
                                    orientation = ExifInterface.ORIENTATION_NORMAL;
                            }
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientation));
                            exif.saveAttributes();
                        } catch (IOException e) {
                            Log.e(TAG, "Error setting EXIF orientation: " + e.getMessage());
                        }

                        String msg = "Photo capture succeeded: " + savedUri;
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);

                        // Process the captured image
                        onImageCaptured(photoFile.getAbsolutePath());
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exc) {
                        Log.e(TAG, "Photo capture failed: " + exc.getMessage(), exc);
                    }
                }
        );
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
        
        // Load thumbnail using Glide
        if (imgBatchPreview != null) {
            Glide.with(requireContext())
                .load(imagePath)
                .override(1024, 1024) // Limit size for memory efficiency
                .into(imgBatchPreview);
        }
        
        if (isBatchMode) {
            // Increment batch count and update UI
            batchCount++;
            tvBatchCount.setText(String.valueOf(batchCount));
            
            // Update UI for batch mode
            updateBatchModeUI(true);
            
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
        previewContainer.setVisibility(View.GONE);
        discardContainer.setVisibility(View.GONE);
        completeContainer.setVisibility(View.GONE);
        tvBatchCount.setText("0");
        
        // Show import buttons again
        importFileContainer.setVisibility(View.VISIBLE);
        importImageContainer.setVisibility(View.VISIBLE);
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
                R.drawable.ic_flash_on : 
                R.drawable.ic_flash_off);
            
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

        // Set status bar color to black and icons to light
        if (getActivity() != null) {
            Window window = getActivity().getWindow();
            window.setStatusBarColor(Color.BLACK);
            // Make status bar icons light
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // Remove light status bar flag
            decorView.setSystemUiVisibility(flags);
        }
        
        // Hide bottom navigation
        hideBottomNavigation();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Show bottom navigation when leaving this fragment
        showBottomNavigation();

        // Restore original status bar color and light status bar
        if (getActivity() != null) {
            Window window = getActivity().getWindow();
            window.setStatusBarColor(Color.WHITE);
            // Restore light status bar
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decorView.setSystemUiVisibility(flags);
        }
        
        // Shut down camera executor
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        // Release MediaPlayer resources
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void updateBatchModeUI(boolean showBatchUI) {
        // Show/hide import containers
        importFileContainer.setVisibility(showBatchUI ? View.GONE : View.VISIBLE);
        importImageContainer.setVisibility(showBatchUI ? View.GONE : View.VISIBLE);
        
        // Show/hide batch mode containers
        discardContainer.setVisibility(showBatchUI ? View.VISIBLE : View.GONE);
        completeContainer.setVisibility(showBatchUI ? View.VISIBLE : View.GONE);
        
        // Show/hide preview container
        previewContainer.setVisibility(showBatchUI && batchCount > 0 ? View.VISIBLE : View.GONE);
    }

    private void showDiscardConfirmationDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Discard Images")
            .setMessage("Are you sure you want to discard all captured images?")
            .setPositiveButton("Discard", (dialog, which) -> {
                discardImages();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void discardImages() {
        // Reset batch state
        resetBatchState();
        // Update UI to show import buttons and hide batch mode buttons
        updateBatchModeUI(false);
        // Switch back to single mode
        modeToggleGroup.check(R.id.radioSingle);
        isBatchMode = false;
        // Show feedback
        Toast.makeText(requireContext(), "Images discarded", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (imageCapture != null) {
            imageCapture.setTargetRotation(requireView().getDisplay().getRotation());
        }
    }
} 