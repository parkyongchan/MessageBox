package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.SatTrackEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SatTrackAdapter extends RecyclerView.Adapter<SatTrackAdapter.TrackViewHolder> {

    public interface OnTrackActionListener {
        void onTrackClick(SatTrackEntity track);
        void onTrackDelete(SatTrackEntity track);
        void onTrackExport(SatTrackEntity track);
    }

    private List<SatTrackEntity> tracks = new ArrayList<>();
    private final OnTrackActionListener listener;

    private static final SimpleDateFormat START_FMT =
            new SimpleDateFormat("MM/dd HH:mm:ss", Locale.US);


    public SatTrackAdapter(OnTrackActionListener listener) {
        this.listener = listener;
    }


    public void submitList(List<SatTrackEntity> newList) {
        tracks.clear();
        if (newList != null) {
            tracks.addAll(newList);
        }
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sat_track, parent, false);
        return new TrackViewHolder(view);
    }


    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        SatTrackEntity track = tracks.get(position);
        holder.bind(track, listener);
    }


    @Override
    public int getItemCount() {
        return tracks.size();
    }


    // ═══════════════════════════════════════════════════════
    // ViewHolder
    // ═══════════════════════════════════════════════════════

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        TextView tvTrackName;
        TextView tvImei;
        TextView tvTrackDistance;
        TextView tvTrackDuration;
        TextView tvTrackPoints;
        TextView tvTrackStart;
        ImageButton btnExport;
        ImageButton btnDelete;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTrackName = itemView.findViewById(R.id.tvTrackName);
            tvImei = itemView.findViewById(R.id.tvImei);
            tvTrackDistance = itemView.findViewById(R.id.tvTrackDistance);
            tvTrackDuration = itemView.findViewById(R.id.tvTrackDuration);
            tvTrackPoints = itemView.findViewById(R.id.tvTrackPoints);
            tvTrackStart = itemView.findViewById(R.id.tvTrackStart);
            btnExport = itemView.findViewById(R.id.btnExport);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(SatTrackEntity track, OnTrackActionListener listener) {
            tvTrackName.setText(track.getName());

            // IMEI
            if (track.getImei() != null && !track.getImei().isEmpty()) {
                tvImei.setText("📡 " + track.getImei());
                tvImei.setVisibility(View.VISIBLE);
            } else {
                tvImei.setVisibility(View.GONE);
            }

            double km = track.getTotalDistance() / 1000.0;
            tvTrackDistance.setText(String.format(Locale.US, "%.2f km", km));

            long durationMs = track.getDurationMillis();
            tvTrackDuration.setText(formatDuration(durationMs));

            tvTrackPoints.setText(String.format(Locale.US, "%d pts", track.getPointCount()));

            if (track.getStartTime() != null) {
                tvTrackStart.setText("Started: " + START_FMT.format(track.getStartTime()));
            } else {
                tvTrackStart.setText("");
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTrackClick(track);
            });

            btnExport.setOnClickListener(v -> {
                if (listener != null) listener.onTrackExport(track);
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onTrackDelete(track);
            });
        }

        private String formatDuration(long ms) {
            if (ms <= 0) return "00:00:00";
            long seconds = ms / 1000;
            long hours = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            long secs = seconds % 60;
            return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs);
        }
    }
}
