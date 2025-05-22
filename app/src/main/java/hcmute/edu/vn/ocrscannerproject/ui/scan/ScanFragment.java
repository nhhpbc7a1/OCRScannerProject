package hcmute.edu.vn.ocrscannerproject.ui.scan;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.ocrscannerproject.R;

public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA
    };
    private static final String[] OPTIONAL_PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    
    // UI Components
    private RadioGroup radioGroupMode;
    private RadioButton radioSingle, radioBatch;
    private Button captureButton, btnComplete;
    private CardView cardBatchPreview;
    private TextView tvBatchCount;
    private ImageView imgBatchPreview;
    
    // Batch mode variables
    private boolean isBatchMode = false;
    private int batchCount = 0;
    private ArrayList<String> capturedImagePaths = new ArrayList<>();
    
    // Current photo path
    private String currentPhotoPath;
    
    // Permission and activity result launchers
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
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
                        Log.d(TAG, "All permissions granted, capture button enabled");
                        if (captureButton != null) {
                            captureButton.setEnabled(true);
                        }
                    } else {
                        Log.e(TAG, "Some permissions were denied");
                        Toast.makeText(requireContext(),
                                "Vui lòng cấp quyền trong Settings > Apps > OCRScannerProject > Permissions",
                                Toast.LENGTH_LONG).show();
                        if (captureButton != null) {
                            captureButton.setEnabled(false);
                        }
                    }
                });
        
        // Register camera launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d(TAG, "Camera returned with image: " + currentPhotoPath);
                        if (currentPhotoPath != null) {
                            onImageCaptured(currentPhotoPath);
                        }
                    } else {
                        Log.e(TAG, "Camera returned with error or user cancelled");
                        Toast.makeText(requireContext(), "Camera capture cancelled or failed", Toast.LENGTH_SHORT).show();
                    }
                });
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
        
        // Request camera permissions
        requestCameraPermissions();
        
        setupListeners();
    }
    
    private void initializeViews(View view) {
        radioGroupMode = view.findViewById(R.id.radioGroupMode);
        radioSingle = view.findViewById(R.id.radioSingle);
        radioBatch = view.findViewById(R.id.radioBatch);
        captureButton = view.findViewById(R.id.camera_capture_button);
        btnComplete = view.findViewById(R.id.btnComplete);
        cardBatchPreview = view.findViewById(R.id.cardBatchPreview);
        tvBatchCount = view.findViewById(R.id.tvBatchCount);
        imgBatchPreview = view.findViewById(R.id.imgBatchPreview);
        
        // Disable capture button until permissions are granted
        captureButton.setEnabled(false);
    }
    
    private void setupListeners() {
        // Mode selection listener
        radioGroupMode.setOnCheckedChangeListener((group, checkedId) -> {
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
        });
        
        // Capture button click listener
        captureButton.setOnClickListener(v -> {
            if (allPermissionsGranted()) {
                openDefaultCamera();
            } else {
                Log.d(TAG, "Requesting camera permission");
                requestCameraPermissions();
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
    }
    
    private void requestCameraPermissions() {
        if (allPermissionsGranted()) {
            Log.d(TAG, "All required permissions already granted");
            captureButton.setEnabled(true);
        } else {
            Log.d(TAG, "Requesting required permissions");
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }
    
    private void takePhoto() {
        Log.d(TAG, "Starting takePhoto method");
        
        // Kiểm tra quyền truy cập trước khi tiếp tục
        if (!allPermissionsGranted()) {
            Log.e(TAG, "Permissions not granted, requesting permissions again");
            requestCameraPermissions();
            return;
        }
        
        // Log trạng thái quyền
        for (String permission : REQUIRED_PERMISSIONS) {
            boolean granted = ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Permission status - " + permission + ": " + (granted ? "GRANTED" : "DENIED"));
        }

        try {
            Log.d(TAG, "Creating image file");
            File photoFile = createImageFile();
            
            Uri photoURI = FileProvider.getUriForFile(
                    requireContext(),
                    "hcmute.edu.vn.ocrscannerproject.fileprovider",
                    photoFile);
            
            Log.d(TAG, "Photo URI: " + photoURI.toString());
            
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            
            // Thử dùng phương pháp khác với MIUI
            List<ResolveInfo> resInfoList = requireActivity().getPackageManager()
                    .queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                requireActivity().grantUriPermission(packageName, photoURI, 
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Log.d(TAG, "Granted permission to: " + packageName);
            }
            
            Log.d(TAG, "Launching camera intent");
            
            if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                cameraLauncher.launch(takePictureIntent);
            } else {
                // Fallback approach for some devices
                try {
                    Log.d(TAG, "Using fallback approach for camera");
                    startActivityForResult(takePictureIntent, 1001);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start camera using fallback: " + e.getMessage());
                    Toast.makeText(requireContext(), "Camera app không khả dụng", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating image file: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "General error in takePhoto: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
        
        // Tạo file với định dạng tên đầy đủ thay vì dùng createTempFile
        File image = new File(storageDir, imageFileName + ".jpg");
        
        // Đảm bảo file không tồn tại trước đó
        if (image.exists()) {
            image.delete();
        }
        
        // Tạo file trống
        boolean fileCreated = image.createNewFile();
        Log.d(TAG, "Image file created: " + fileCreated + " at " + image.getAbsolutePath());
        
        // Save a file path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
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
        
        // Kiểm tra quyền STORAGE để log
        for (String permission : OPTIONAL_PERMISSIONS) {
            boolean granted = ContextCompat.checkSelfPermission(
                    requireContext(), permission) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Optional permission " + permission + ": " + (granted ? "GRANTED" : "DENIED"));
        }
        
        Log.d(TAG, "All required permissions granted");
        return true;
    }

    // Phương pháp thay thế sử dụng camera trực tiếp
    private void tryDirectCameraOption() {
        Log.d(TAG, "Trying direct camera option approach");
        
        if (!allPermissionsGranted()) {
            Log.e(TAG, "Required permissions not granted for direct camera approach");
            requestCameraPermissions();
            return;
        }
        
        try {
            // Tạo Intent để mở camera
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            
            // Kiểm tra xem camera có khả dụng không
            if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                Log.d(TAG, "Camera app is available, launching direct camera intent");
                startActivityForResult(takePictureIntent, 1002);
            } else {
                Log.e(TAG, "No camera app available");
                Toast.makeText(requireContext(), "Không tìm thấy ứng dụng camera", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching direct camera: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Không thể mở camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.d(TAG, "onActivityResult called: requestCode=" + requestCode + ", resultCode=" + resultCode 
                + ", data=" + (data != null ? "not null" : "null"));
        
        if (requestCode == 1001) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Camera returned success with full image");
                
                // Kiểm tra file đã được tạo
                if (currentPhotoPath != null) {
                    File photoFile = new File(currentPhotoPath);
                    if (photoFile.exists()) {
                        Log.d(TAG, "Photo file exists: " + currentPhotoPath + ", size: " + photoFile.length() + " bytes");
                        
                        // Kiểm tra xem file có dữ liệu không
                        if (photoFile.length() > 0) {
                            Toast.makeText(requireContext(), "Đã chụp ảnh thành công", Toast.LENGTH_SHORT).show();
                            onImageCaptured(currentPhotoPath);
                        } else {
                            Log.e(TAG, "Photo file exists but is empty");
                            Toast.makeText(requireContext(), "File ảnh trống", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Photo file doesn't exist at path: " + currentPhotoPath);
                        Toast.makeText(requireContext(), "Không tìm thấy ảnh đã chụp", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "currentPhotoPath is null, cannot find saved image");
                    Toast.makeText(requireContext(), "Đường dẫn ảnh trống", Toast.LENGTH_SHORT).show();
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.d(TAG, "Camera capture cancelled by user");
                Toast.makeText(requireContext(), "Đã hủy chụp ảnh", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Camera returned with error: " + resultCode);
                Toast.makeText(requireContext(), "Lỗi khi chụp ảnh", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1002) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getExtras() != null) {
                Log.d(TAG, "Camera returned with thumbnail");
                
                // Xử lý thumbnail từ data
                try {
                    Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                    if (imageBitmap != null) {
                        Log.d(TAG, "Got thumbnail: " + imageBitmap.getWidth() + "x" + imageBitmap.getHeight());
                        
                        // Tạo file mới nếu chưa có
                        if (currentPhotoPath == null) {
                            File photoFile = createImageFile();
                            currentPhotoPath = photoFile.getAbsolutePath();
                        }
                        
                        // Lưu bitmap vào file
                        try (FileOutputStream out = new FileOutputStream(currentPhotoPath)) {
                            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                            Log.d(TAG, "Saved thumbnail to: " + currentPhotoPath);
                            Toast.makeText(requireContext(), "Đã lưu ảnh thu nhỏ", Toast.LENGTH_SHORT).show();
                            onImageCaptured(currentPhotoPath);
                        } catch (Exception e) {
                            Log.e(TAG, "Error saving thumbnail: " + e.getMessage(), e);
                            Toast.makeText(requireContext(), "Lỗi lưu ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "No bitmap in intent data");
                        Toast.makeText(requireContext(), "Không nhận được ảnh từ camera", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing thumbnail: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Camera returned with error or no data");
                Toast.makeText(requireContext(), "Không nhận được dữ liệu từ camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Dọn dẹp tài nguyên nếu cần
        Log.d(TAG, "onDestroyView called, cleaning up resources");
    }

    /**
     * Mở camera mặc định của thiết bị và chụp ảnh
     */
    private void openDefaultCamera() {
        Log.d(TAG, "Opening default camera");

        try {
            // Tạo file để lưu ảnh
            File photoFile = createImageFile();
            currentPhotoPath = photoFile.getAbsolutePath();
            Log.d(TAG, "Photo will be saved to: " + currentPhotoPath);

            // Tạo intent để mở camera
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            
            // Đảm bảo có ứng dụng camera
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                // Thêm URI cho file output
                Uri photoURI = FileProvider.getUriForFile(
                        requireContext(),
                        "hcmute.edu.vn.ocrscannerproject.fileprovider",
                        photoFile);
                
                Log.d(TAG, "Photo URI: " + photoURI);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                
                // Cấp quyền cho camera app
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                
                // Mở camera
                Log.d(TAG, "Starting camera activity with URI");
                startActivityForResult(intent, 1001);
            } else {
                Log.e(TAG, "No camera app available");
                Toast.makeText(requireContext(), "Không tìm thấy ứng dụng camera", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Lỗi mở camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
} 