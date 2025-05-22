package hcmute.edu.vn.ocrscannerproject.ui.details;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;
import hcmute.edu.vn.ocrscannerproject.data.ScannedDocumentRepository;

public class DocumentDetailsFragment extends Fragment {

    private static final String TAG = "DocumentDetailsFragment";
    private static final String ARG_DOCUMENT_ID = "documentId";
    
    private ViewPager2 viewPagerImages;
    private TabLayout tabIndicator;
    private TextView tvDocumentTitle;
    private TextView tvImageCount;
    private TextView tvDocumentContent;
    
    private String documentId;
    private ScannedDocumentRepository documentRepository;
    private ScannedDocument currentDocument;

    public static DocumentDetailsFragment newInstance(String documentId) {
        DocumentDetailsFragment fragment = new DocumentDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DOCUMENT_ID, documentId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            documentId = getArguments().getString(ARG_DOCUMENT_ID);
        }
        
        // Get repository instance
        documentRepository = ScannedDocumentRepository.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_document_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewPagerImages = view.findViewById(R.id.viewpager_document_images);
        tabIndicator = view.findViewById(R.id.tab_indicator);
        tvDocumentTitle = view.findViewById(R.id.tv_document_title);
        tvImageCount = view.findViewById(R.id.tv_image_count);
        tvDocumentContent = view.findViewById(R.id.tv_document_content);
        
        // Load document details using documentId
        loadDocumentDetails();
    }
    
    private void loadDocumentDetails() {
        if (documentId == null || documentId.isEmpty()) {
            showError("Document ID is missing");
            return;
        }
        
        try {
            // Get document from repository
            currentDocument = documentRepository.getDocumentById(documentId);
            
            if (currentDocument == null) {
                showError("Document not found");
                return;
            }
            
            // Set document title
            tvDocumentTitle.setText(currentDocument.getFileName());
            
            // Set document content if available
            String extractedText = currentDocument.getExtractedText();
            if (extractedText != null && !extractedText.isEmpty()) {
                tvDocumentContent.setText(extractedText);
            } else {
                tvDocumentContent.setText("No text has been extracted yet.");
            }
            
            // Load document images
            List<String> imagePaths = currentDocument.getLocalImagePaths();
            if (imagePaths != null && !imagePaths.isEmpty()) {
                // Set image count text
                tvImageCount.setText(imagePaths.size() + " image" + (imagePaths.size() > 1 ? "s" : ""));
                
                // Setup ViewPager with images
                setupImageViewPager(imagePaths);
            } else {
                tvImageCount.setText("No images");
                viewPagerImages.setVisibility(View.GONE);
                tabIndicator.setVisibility(View.GONE);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading document details: " + e.getMessage(), e);
            showError("Error loading document: " + e.getMessage());
        }
    }
    
    private void setupImageViewPager(List<String> imagePaths) {
        if (imagePaths.isEmpty()) {
            return;
        }
        
        DocumentImageAdapter adapter = new DocumentImageAdapter(requireContext(), imagePaths);
        viewPagerImages.setAdapter(adapter);
        
        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabIndicator, viewPagerImages,
                (tab, position) -> {
                    // No text for tab
                }).attach();
        
        // Only show TabLayout if we have multiple images
        if (imagePaths.size() <= 1) {
            tabIndicator.setVisibility(View.GONE);
        }
    }
    
    private void showError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        Log.e(TAG, message);
    }
    
    /**
     * Adapter for showing document images in ViewPager
     */
    private static class DocumentImageAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<DocumentImageAdapter.ImageViewHolder> {
        private final Context context;
        private final List<String> imagePaths;
        
        public DocumentImageAdapter(Context context, List<String> imagePaths) {
            this.context = context;
            this.imagePaths = imagePaths;
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
            
            try {
                Bitmap bitmap;
                if (imagePath.startsWith("content://")) {
                    bitmap = MediaStore.Images.Media.getBitmap(
                            context.getContentResolver(), android.net.Uri.parse(imagePath));
                } else {
                    bitmap = BitmapFactory.decodeFile(imagePath);
                }
                
                if (bitmap != null) {
                    holder.imageView.setImageBitmap(bitmap);
                } else {
                    holder.imageView.setImageResource(R.drawable.ic_menu_report_image);
                    Log.e("DocumentDetails", "Could not decode bitmap from: " + imagePath);
                }
            } catch (IOException | IllegalArgumentException e) {
                Log.e("DocumentDetails", "Error loading image: " + e.getMessage(), e);
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