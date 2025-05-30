package hcmute.edu.vn.ocrscannerproject.ui.details;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;
import hcmute.edu.vn.ocrscannerproject.data.ScannedDocumentRepository;

public class TextContentFragment extends Fragment {
    private static final String ARG_DOCUMENT_ID = "documentId";
    
    private TextView tvContent;
    private FloatingActionButton fabCopy;
    private String documentId;
    private ScannedDocument document;
    private ScannedDocumentRepository repository;

    public static TextContentFragment newInstance(String documentId) {
        TextContentFragment fragment = new TextContentFragment();
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
        return inflater.inflate(R.layout.fragment_text_content, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        tvContent = view.findViewById(R.id.tv_content);
        fabCopy = view.findViewById(R.id.fab_copy);
        
        loadDocument();
        setupListeners();
    }
    
    private void loadDocument() {
        if (documentId != null) {
            document = repository.getDocumentById(documentId);
            if (document != null && document.getExtractedText() != null) {
                tvContent.setText(document.getExtractedText());
            } else {
                tvContent.setText(R.string.no_text_content);
            }
        }
    }
    
    private void setupListeners() {
        fabCopy.setOnClickListener(v -> copyTextToClipboard());
    }
    
    private void copyTextToClipboard() {
        String content = tvContent.getText().toString();
        if (content.isEmpty() || content.equals(getString(R.string.no_text_content))) {
            Toast.makeText(requireContext(), "No text to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ClipboardManager clipboard = (ClipboardManager) 
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Document Text", content);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(requireContext(), "Text copied to clipboard", Toast.LENGTH_SHORT).show();
    }
} 