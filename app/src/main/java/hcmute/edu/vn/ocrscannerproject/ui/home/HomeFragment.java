package hcmute.edu.vn.ocrscannerproject.ui.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.adapter.ScannedDocumentAdapter;
import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;
import hcmute.edu.vn.ocrscannerproject.data.ScannedDocumentRepository;

public class HomeFragment extends Fragment implements ScannedDocumentAdapter.OnDocumentClickListener {

    private static final String TAG = "HomeFragment";
    private static final String ANONYMOUS_USER = "anonymous";

    private SearchView searchView;
    private Button btnImportFile, btnImportImage;
    private Spinner spinnerFilter;
    private ImageButton btnSort, btnViewMode, btnSelect;
    private RecyclerView recyclerDocuments;
    
    private boolean isGridView = false;
    private List<String> filterOptions = Arrays.asList("All", "PDF", "TXT", "Image", "Excel", "Word");
    private enum SortType {
        NAME_ASC, NAME_DESC, DATE_NEWEST, DATE_OLDEST
    }

    private SortType currentSortType = SortType.DATE_NEWEST;
    private ScannedDocumentRepository documentRepository;
    private ScannedDocumentAdapter documentAdapter;
    private List<ScannedDocument> documents = new ArrayList<>();

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize document repository
        documentRepository = ScannedDocumentRepository.getInstance(requireContext());
        
        // Initialize UI components
        initializeViews(view);
        setupListeners();
        setupFilterSpinner();
        setupRecyclerView();
        loadDocuments();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Reload documents when returning to this fragment
        loadDocuments();
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Đăng ký launcher để chọn ảnh từ thư viện
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            // Chuyển ảnh đã chọn sang ReviewFragment
                            sendImageToReview(selectedImageUri);
                        } else {
                            Toast.makeText(requireContext(), "Không thể lấy ảnh đã chọn", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            
        // Đăng ký launcher để chọn file
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri selectedFileUri = result.getData().getData();
                        if (selectedFileUri != null) {
                            String fileName = getFileName(selectedFileUri);
                            Toast.makeText(requireContext(), "Đã chọn file: " + fileName, Toast.LENGTH_SHORT).show();
                            
                            // Xử lý file được chọn
                            processSelectedFile(selectedFileUri, fileName);
                        } else {
                            Toast.makeText(requireContext(), "Không thể lấy file đã chọn", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
    
    private void initializeViews(View view) {
        searchView = view.findViewById(R.id.search_view);
        btnImportFile = view.findViewById(R.id.btn_import_file);
        btnImportImage = view.findViewById(R.id.btn_import_image);
        spinnerFilter = view.findViewById(R.id.spinner_filter);
        btnSort = view.findViewById(R.id.btn_sort);
        btnViewMode = view.findViewById(R.id.btn_view_mode);
        btnSelect = view.findViewById(R.id.btn_select);
        recyclerDocuments = view.findViewById(R.id.recycler_documents);
    }

    private void setupListeners() {
        // Search functionality
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchDocuments(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    loadDocuments(); // Reset to show all documents
                }
                return true;
            }
        });
        
        // Import file button
        btnImportFile.setOnClickListener(v -> {
            openFilePicker();
        });
        
        // Import image button
        btnImportImage.setOnClickListener(v -> {
            openImagePicker();
        });

        // View mode toggle (list/grid)
        btnViewMode.setOnClickListener(v -> {
            isGridView = !isGridView;
            setupRecyclerView();
            loadDocuments();
            
            // Update icon
            btnViewMode.setImageResource(isGridView ? 
                    android.R.drawable.ic_dialog_dialer : // Grid icon
                    android.R.drawable.ic_menu_sort_by_size); // List icon
        });
        
        // Sort button
        btnSort.setOnClickListener(v -> {
            // Cycle through sort types
            switch (currentSortType) {
                case DATE_NEWEST:
                    currentSortType = SortType.DATE_OLDEST;
                    Toast.makeText(requireContext(), "Sort: Oldest first", Toast.LENGTH_SHORT).show();
                    break;
                case DATE_OLDEST:
                    currentSortType = SortType.NAME_ASC;
                    Toast.makeText(requireContext(), "Sort: Name A-Z", Toast.LENGTH_SHORT).show();
                    break;
                case NAME_ASC:
                    currentSortType = SortType.NAME_DESC;
                    Toast.makeText(requireContext(), "Sort: Name Z-A", Toast.LENGTH_SHORT).show();
                    break;
                case NAME_DESC:
                    currentSortType = SortType.DATE_NEWEST;
                    Toast.makeText(requireContext(), "Sort: Newest first", Toast.LENGTH_SHORT).show();
                    break;
            }
            sortDocuments();
        });


        // Select button
        btnSelect.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Select functionality", Toast.LENGTH_SHORT).show();
            // TODO: Implement multi-select
        });
    }

    private void sortDocuments() {
        if (documents == null || documents.isEmpty()) return;

        switch (currentSortType) {
            case NAME_ASC:
                documents.sort((d1, d2) -> d1.getFileName().compareToIgnoreCase(d2.getFileName()));
                break;
            case NAME_DESC:
                documents.sort((d1, d2) -> d2.getFileName().compareToIgnoreCase(d1.getFileName()));
                break;
            case DATE_NEWEST:
                documents.sort((d1, d2) -> Long.compare(d2.getCreatedAt(), d1.getCreatedAt()));
                break;
            case DATE_OLDEST:
                documents.sort((d1, d2) -> Long.compare(d1.getCreatedAt(), d2.getCreatedAt()));
                break;
        }

        updateUI();
    }

    private void setupFilterSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, filterOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(adapter);
        
        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedFilter = filterOptions.get(position);
                filterDocuments(selectedFilter);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }
    
    private void setupRecyclerView() {
        if (isGridView) {
            recyclerDocuments.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        } else {
            recyclerDocuments.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
        
        documentAdapter = new ScannedDocumentAdapter(requireContext(), documents);
        documentAdapter.setOnDocumentClickListener(this);
        recyclerDocuments.setAdapter(documentAdapter);
    }
    
    private void loadDocuments() {
        documents = documentRepository.getAllDocuments();
        updateUI();
    }
    
    private void searchDocuments(String query) {
        if (query != null && !query.isEmpty()) {
            documents = documentRepository.searchDocuments(query);
        } else {
            documents = documentRepository.getAllDocuments();
        }
        updateUI();
    }
    
    private void filterDocuments(String filter) {
        documents = documentRepository.filterDocumentsByType(filter);
        updateUI();
    }
    
    private void updateUI() {
        if (documentAdapter != null) {
            documentAdapter.updateDocuments(documents);
        }
        
        // Show empty state if needed
        if (documents.isEmpty()) {
            Toast.makeText(requireContext(), "No documents found", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onDocumentClick(ScannedDocument document) {
        // Navigate to document details
        Bundle args = new Bundle();
        args.putString("documentId", document.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_homeFragment_to_documentDetailsFragment, args);
    }
    
    @Override
    public void onDocumentLongClick(ScannedDocument document) {
        // Show options menu (delete, share, etc.)
        Toast.makeText(requireContext(), "Long click: " + document.getFileName(), Toast.LENGTH_SHORT).show();
    }

    /**
     * Mở giao diện chọn ảnh từ thư viện
     */
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        
        // Thêm các provider khác như Google Drive, Photos, v.v. (nếu có)
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png"});
        
        try {
            imagePickerLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "Không tìm thấy ứng dụng để chọn ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Chuyển ảnh đã chọn sang ReviewFragment
     */
    private void sendImageToReview(Uri imageUri) {
        try {
            // Chuyển URI thành đường dẫn file
            String imagePath = getImagePathFromUri(imageUri);
            
            if (imagePath != null) {
                // Tạo mảng String[] chứa đường dẫn ảnh
                String[] imagePathsArray = new String[]{imagePath};
                
                // Tạo bundle và chuyển sang ReviewFragment
                Bundle args = new Bundle();
                args.putStringArray("capturedImages", imagePathsArray);
                
                // Chuyển hướng đến ReviewFragment
                Navigation.findNavController(requireView())
                        .navigate(R.id.action_homeFragment_to_reviewFragment, args);
            } else {
                Toast.makeText(requireContext(), "Không thể xử lý ảnh đã chọn", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending image to review: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Chuyển đổi Uri thành đường dẫn file
     */
    private String getImagePathFromUri(Uri uri) {
        try {
            // Kiểm tra xem URI có phải là file trực tiếp không
            if ("file".equals(uri.getScheme())) {
                return uri.getPath();
            }
            
            // Xử lý URI từ content provider
            if ("content".equals(uri.getScheme())) {
                // Đối với một số thiết bị, có thể lấy đường dẫn trực tiếp
                String[] projection = {MediaStore.Images.Media.DATA};
                Cursor cursor = null;
                
                try {
                    cursor = requireContext().getContentResolver().query(uri, projection, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                        String path = cursor.getString(columnIndex);
                        if (path != null) {
                            return path;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error getting real path: " + e.getMessage());
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                
                // Nếu không lấy được đường dẫn trực tiếp, tạo một bản sao của ảnh
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String fileName = "IMG_" + timeStamp + ".jpg";
                
                File outputDir = new File(requireContext().getFilesDir(), "imported_images");
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
            }
            
            // Nếu không phải URI file hoặc content, trả về null
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error processing image URI: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Mở giao diện chọn file
     */
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        // Cho phép chọn nhiều loại file khác nhau
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "application/msword", "application/vnd.ms-excel", 
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain", "image/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        try {
            filePickerLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "Không tìm thấy ứng dụng để chọn file", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Xử lý file được chọn từ giao diện hệ thống
     */
    private void processSelectedFile(Uri fileUri, String fileName) {
        try {
            // Tạo bản sao của file trong bộ nhớ ứng dụng
            String localFilePath = copyFileToAppStorage(fileUri, fileName);
            
            if (localFilePath != null) {
                // Tạo document mới từ file đã chọn
                ScannedDocument document = createDocumentFromFile(localFilePath, fileName);
                
                if (document != null) {
                    // Lưu document vào repository
                    documentRepository.addDocument(document);
                    
                    // Cập nhật danh sách documents
                    loadDocuments();
                    
                    Toast.makeText(requireContext(), "Đã lưu file: " + fileName, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), "Không thể sao chép file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing file: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Lỗi xử lý file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Sao chép file từ URI vào bộ nhớ ứng dụng
     */
    private String copyFileToAppStorage(Uri uri, String fileName) {
        try {
            // Tạo thư mục lưu trữ nếu chưa tồn tại
            File outputDir = new File(requireContext().getFilesDir(), "imported_files");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            // Tạo file đích với tên phù hợp
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String extension = getFileExtension(fileName);
            String newFileName = "FILE_" + timeStamp + (extension.isEmpty() ? "" : "." + extension);
            
            File outputFile = new File(outputDir, newFileName);
            
            // Sao chép nội dung file
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
            Log.e(TAG, "Error copying file: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Tạo document từ file đã chọn
     */
    private ScannedDocument createDocumentFromFile(String filePath, String originalFileName) {
        try {
            // Tạo tên document từ tên file gốc
            String title = originalFileName;
            
            // Loại bỏ phần mở rộng nếu có
            int lastDotPos = title.lastIndexOf(".");
            if (lastDotPos > 0) {
                title = title.substring(0, lastDotPos);
            }
            
            // Xác định loại document dựa trên phần mở rộng
            String extension = getFileExtension(originalFileName).toLowerCase();
            String type = "File";
            
            switch (extension) {
                case "pdf":
                    type = "PDF";
                    break;
                case "doc":
                case "docx":
                    type = "Word";
                    break;
                case "xls":
                case "xlsx":
                    type = "Excel";
                    break;
                case "txt":
                    type = "TXT";
                    break;
                case "jpg":
                case "jpeg":
                case "png":
                    type = "Image";
                    break;
            }
            
            // Tạo document mới
            ScannedDocument document = new ScannedDocument(title, ANONYMOUS_USER, filePath);
            document.setType(type);
            
            return document;
        } catch (Exception e) {
            Log.e(TAG, "Error creating document: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Lấy phần mở rộng của file
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        
        int lastDotPos = fileName.lastIndexOf(".");
        if (lastDotPos >= 0 && lastDotPos < fileName.length() - 1) {
            return fileName.substring(lastDotPos + 1);
        }
        
        return "";
    }

    /**
     * Lấy tên file từ URI
     */
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename: " + e.getMessage());
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
} 