package hcmute.edu.vn.ocrscannerproject.ui.details;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.mlkit.vision.text.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;
import hcmute.edu.vn.ocrscannerproject.data.ScannedDocumentRepository;

public class DocumentDetailsFragment extends Fragment {

    private static final String TAG = "DocumentDetailsFragment";
    private static final String ARG_DOCUMENT_ID = "documentId";
    
    private ViewPager2 viewPagerContent;
    private TabLayout tabLayout;
    private TextView tvDocumentTitle, tvCreatedDate, tvFileType;
    private String documentId;
    private ScannedDocumentRepository documentRepository;
    private ScannedDocument currentDocument;
    private DocumentContentPagerAdapter pagerAdapter;

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
        
        initViews(view);
        setupListeners(view);
        loadDocument();
    }
    
    private void initViews(View view) {
        viewPagerContent = view.findViewById(R.id.viewpager_content);
        tabLayout = view.findViewById(R.id.tab_layout);
        tvDocumentTitle = view.findViewById(R.id.tv_document_title);
        tvCreatedDate = view.findViewById(R.id.tv_created_date);
        tvFileType = view.findViewById(R.id.tv_file_type);
    }
    
    private void setupListeners(View view) {
        view.findViewById(R.id.btn_back).setOnClickListener(v -> requireActivity().onBackPressed());
        view.findViewById(R.id.btn_open).setOnClickListener(v -> openDocument());
        view.findViewById(R.id.btn_share).setOnClickListener(v -> shareDocument());
        view.findViewById(R.id.btn_delete).setOnClickListener(v -> deleteDocument());
    }
    
    private void loadDocument() {
        if (documentId != null) {
            currentDocument = documentRepository.getDocumentById(documentId);
            if (currentDocument != null) {
                setupDocumentDetails();
                setupContentPager();
            }
        }
    }
    
    private void setupDocumentDetails() {
        tvDocumentTitle.setText(currentDocument.getFileName());
        
        // Format and set creation date
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        String createdDate = sdf.format(currentDocument.getTimestamp());
        tvCreatedDate.setText("Created: " + createdDate);
        
        // Set file type
        tvFileType.setText("Type: " + currentDocument.getType());
    }
    
    private void setupContentPager() {
        pagerAdapter = new DocumentContentPagerAdapter(requireActivity(), currentDocument);
        viewPagerContent.setAdapter(pagerAdapter);
        
        // Setup tabs
        new TabLayoutMediator(tabLayout, viewPagerContent, (tab, position) -> {
            tab.setText(position == 0 ? "Text" : "Images");
        }).attach();
    }
    
    private void openDocument() {
        if (currentDocument == null) return;
        
        String filePath = currentDocument.getLocalImagePath();
        String type = currentDocument.getType();
        
        if (filePath == null || type == null) return;
        
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".provider",
                new File(filePath));
                
            if ("PDF".equals(type)) {
                intent.setDataAndType(uri, "application/pdf");
            } else if ("DOC".equals(type) || "DOCX".equals(type)) {
                intent.setDataAndType(uri, "application/msword");
            } else {
                intent.setDataAndType(uri, "image/*");
            }
            
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "No app found to open this file", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(requireContext(), "Error accessing file", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void shareDocument() {
        if (currentDocument == null) return;
        
        String filePath = currentDocument.getLocalImagePath();
        if (filePath == null) return;
        
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".provider",
                new File(filePath));
                
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("*/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "Share document"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "No app found to share this file", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(requireContext(), "Error accessing file", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void deleteDocument() {
        if (currentDocument == null) return;
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Document")
            .setMessage("Are you sure you want to delete this document?")
            .setPositiveButton("Delete", (dialog, which) -> {
                documentRepository.deleteDocument(currentDocument.getId());
                requireActivity().onBackPressed();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}

class DocumentContentPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
    private final ScannedDocument document;
    private static final int NUM_PAGES = 2;

    public DocumentContentPagerAdapter(FragmentActivity activity, ScannedDocument document) {
        super(activity);
        this.document = document;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return TextContentFragment.newInstance(document.getId());
        } else {
            return ImageContentFragment.newInstance(document.getId());
        }
    }

    @Override
    public int getItemCount() {
        return NUM_PAGES;
    }
} 