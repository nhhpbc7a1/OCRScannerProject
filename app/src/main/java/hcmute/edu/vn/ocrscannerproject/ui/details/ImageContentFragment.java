package hcmute.edu.vn.ocrscannerproject.ui.details;

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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.List;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;
import hcmute.edu.vn.ocrscannerproject.data.ScannedDocumentRepository;

public class ImageContentFragment extends Fragment {
    private static final String ARG_DOCUMENT_ID = "documentId";
    private static final String TAG = "ImageContentFragment";
    
    private ViewPager2 viewPagerImages;
    private TextView tvPageIndicator;
    private ImageButton btnPrevious, btnNext;
    private String documentId;
    private ScannedDocument document;
    private ScannedDocumentRepository repository;
    private int currentPosition = 0;

    public static ImageContentFragment newInstance(String documentId) {
        ImageContentFragment fragment = new ImageContentFragment();
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
        repository = ScannedDocumentRepository.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_image_content, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewPagerImages = view.findViewById(R.id.viewpager_images);
        tvPageIndicator = view.findViewById(R.id.tv_page_indicator);
        btnPrevious = view.findViewById(R.id.btn_previous);
        btnNext = view.findViewById(R.id.btn_next);
        
        setupListeners();
        loadDocument();
    }
    
    private void setupListeners() {
        btnPrevious.setOnClickListener(v -> {
            if (currentPosition > 0) {
                viewPagerImages.setCurrentItem(currentPosition - 1);
            }
        });
        
        btnNext.setOnClickListener(v -> {
            if (currentPosition < viewPagerImages.getAdapter().getItemCount() - 1) {
                viewPagerImages.setCurrentItem(currentPosition + 1);
            }
        });
        
        viewPagerImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updatePageIndicator();
                updateNavigationButtons();
            }
        });
    }
    
    private void loadDocument() {
        if (documentId != null) {
            document = repository.getDocumentById(documentId);
            if (document != null) {
                setupImagePager();
            }
        }
    }
    
    private void setupImagePager() {
        List<String> imagePaths = document.getLocalImagePaths();
        if (imagePaths != null && !imagePaths.isEmpty()) {
            DocumentImageAdapter adapter = new DocumentImageAdapter(requireContext(), imagePaths);
            viewPagerImages.setAdapter(adapter);
            updatePageIndicator();
            updateNavigationButtons();
        }
    }
    
    private void updatePageIndicator() {
        if (viewPagerImages.getAdapter() != null) {
            int totalPages = viewPagerImages.getAdapter().getItemCount();
            tvPageIndicator.setText(String.format("%d/%d", currentPosition + 1, totalPages));
        }
    }
    
    private void updateNavigationButtons() {
        if (viewPagerImages.getAdapter() != null) {
            int totalPages = viewPagerImages.getAdapter().getItemCount();
            btnPrevious.setEnabled(currentPosition > 0);
            btnNext.setEnabled(currentPosition < totalPages - 1);
            btnPrevious.setAlpha(currentPosition > 0 ? 1.0f : 0.5f);
            btnNext.setAlpha(currentPosition < totalPages - 1 ? 1.0f : 0.5f);
        }
    }
    
    private static class DocumentImageAdapter extends RecyclerView.Adapter<DocumentImageAdapter.ImageViewHolder> {
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
                            context.getContentResolver(), Uri.parse(imagePath));
                } else {
                    bitmap = BitmapFactory.decodeFile(imagePath);
                }
                
                if (bitmap != null) {
                    holder.imageView.setImageBitmap(bitmap);
                } else {
                    holder.imageView.setImageResource(R.drawable.ic_menu_report_image);
                    Log.e(TAG, "Could not decode bitmap from: " + imagePath);
                }
            } catch (IOException | IllegalArgumentException e) {
                Log.e(TAG, "Error loading image: " + e.getMessage(), e);
                holder.imageView.setImageResource(R.drawable.ic_menu_report_image);
            }
        }

        @Override
        public int getItemCount() {
            return imagePaths.size();
        }
        
        static class ImageViewHolder extends RecyclerView.ViewHolder {
            final ImageView imageView;
            
            public ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                this.imageView = (ImageView) itemView;
            }
        }
    }
} 