package hcmute.edu.vn.ocrscannerproject.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.ocrscannerproject.R;
import hcmute.edu.vn.ocrscannerproject.core.entities.ScannedDocument;

public class ScannedDocumentAdapter extends RecyclerView.Adapter<ScannedDocumentAdapter.DocumentViewHolder> {

    private Context context;
    private List<ScannedDocument> documents;
    private OnDocumentClickListener listener;
    
    // Interface for click events
    public interface OnDocumentClickListener {
        void onDocumentClick(ScannedDocument document);
        void onDocumentLongClick(ScannedDocument document);
    }
    
    public ScannedDocumentAdapter(Context context, List<ScannedDocument> documents) {
        this.context = context;
        this.documents = documents;
    }
    
    public void setOnDocumentClickListener(OnDocumentClickListener listener) {
        this.listener = listener;
    }
    
    public void updateDocuments(List<ScannedDocument> newDocuments) {
        this.documents = newDocuments;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_document, parent, false);
        return new DocumentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentViewHolder holder, int position) {
        ScannedDocument document = documents.get(position);
        
        // Set document title
        holder.tvTitle.setText(document.getFileName());
        
        // Set document date
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        holder.tvDate.setText(dateFormat.format(document.getTimestamp()));
        
        // Set document type
        holder.tvType.setText(document.getType());
        
        // Load thumbnail if available
        if (document.getLocalImagePath() != null && !document.getLocalImagePath().isEmpty()) {
            File imageFile = new File(document.getLocalImagePath());
            if (imageFile.exists()) {
                Bitmap thumbnail = BitmapFactory.decodeFile(document.getLocalImagePath());
                if (thumbnail != null) {
                    holder.imgThumbnail.setImageBitmap(thumbnail);
                } else {
                    // Set default image if thumbnail couldn't be loaded
                    holder.imgThumbnail.setImageResource(R.drawable.ic_menu_report_image);
                }
            } else {
                // Set default image if file doesn't exist
                holder.imgThumbnail.setImageResource(R.drawable.ic_menu_report_image);
            }
        } else {
            // Set default image if no path
            holder.imgThumbnail.setImageResource(R.drawable.ic_menu_report_image);
        }
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDocumentClick(document);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onDocumentLongClick(document);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return documents != null ? documents.size() : 0;
    }

    public static class DocumentViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail;
        TextView tvTitle;
        TextView tvDate;
        TextView tvType;
        
        public DocumentViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.img_document_thumbnail);
            tvTitle = itemView.findViewById(R.id.tv_document_title);
            tvDate = itemView.findViewById(R.id.tv_document_date);
            tvType = itemView.findViewById(R.id.tv_document_type);
        }
    }
} 