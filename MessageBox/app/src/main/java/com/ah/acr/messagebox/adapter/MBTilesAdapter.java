package com.ah.acr.messagebox.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MBTiles file list adapter
 */
public class MBTilesAdapter extends RecyclerView.Adapter<MBTilesAdapter.MBTilesViewHolder> {

    public interface OnMBTilesActionListener {
        void onDeleteClick(File file);
    }

    private final List<File> items = new ArrayList<>();
    private final OnMBTilesActionListener listener;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public MBTilesAdapter(OnMBTilesActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<File> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public int getTotalCount() {
        return items.size();
    }

    public long getTotalSize() {
        long total = 0;
        for (File f : items) total += f.length();
        return total;
    }

    @NonNull
    @Override
    public MBTilesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.adapter_mbtiles_item, parent, false);
        return new MBTilesViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MBTilesViewHolder holder, int position) {
        File file = items.get(position);
        Context ctx = holder.itemView.getContext();

        // File name
        holder.tvFileName.setText(file.getName());

        // Size
        holder.tvFileSize.setText(formatFileSize(file.length()));

        // ⭐ BUGFIX: Localized "Added: ..." (was Korean "추가:")
        holder.tvFileDate.setText(ctx.getString(
                R.string.maps_added_label,
                dateFormat.format(new Date(file.lastModified()))));

        // Delete button
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(file);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }


    /** Format file size */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }


    static class MBTilesViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName, tvFileSize, tvFileDate;
        ImageButton btnDelete;

        MBTilesViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileSize = itemView.findViewById(R.id.tv_file_size);
            tvFileDate = itemView.findViewById(R.id.tv_file_date);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
