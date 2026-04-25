package com.ah.acr.messagebox.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.SatTrackEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

    // Time formats
    private static final SimpleDateFormat TIME_RANGE_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
    private static final SimpleDateFormat TIME_SHORT_FMT =
            new SimpleDateFormat("HH:mm", Locale.US);
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);


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
        TextView tvModeIcon;
        TextView tvTrackName;
        LinearLayout layoutStatusBadge;
        TextView tvStatusIcon;
        TextView tvStatus;

        TextView tvTimeRange;
        TextView tvImei;

        TextView tvTrackDuration;
        TextView tvTrackDistance;
        TextView tvTrackPoints;

        ImageButton btnExport;
        ImageButton btnDelete;

        TextView tvTrackStart;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            tvModeIcon = itemView.findViewById(R.id.tvModeIcon);
            tvTrackName = itemView.findViewById(R.id.tvTrackName);
            layoutStatusBadge = itemView.findViewById(R.id.layoutStatusBadge);
            tvStatusIcon = itemView.findViewById(R.id.tvStatusIcon);
            tvStatus = itemView.findViewById(R.id.tvStatus);

            tvTimeRange = itemView.findViewById(R.id.tvTimeRange);
            tvImei = itemView.findViewById(R.id.tvImei);

            tvTrackDuration = itemView.findViewById(R.id.tvTrackDuration);
            tvTrackDistance = itemView.findViewById(R.id.tvTrackDistance);
            tvTrackPoints = itemView.findViewById(R.id.tvTrackPoints);

            btnExport = itemView.findViewById(R.id.btnExport);
            btnDelete = itemView.findViewById(R.id.btnDelete);

            tvTrackStart = itemView.findViewById(R.id.tvTrackStart);
        }

        void bind(SatTrackEntity track, OnTrackActionListener listener) {
            Context ctx = itemView.getContext();

            // Mode icon (by session name)
            String name = track.getName() != null ? track.getName() : "";
            if (name.contains("SOS")) {
                tvModeIcon.setText("🆘");
            } else {
                tvModeIcon.setText("🛰");
            }

            // Session name
            tvTrackName.setText(name);

            // Status badge (ACTIVE/COMPLETED) - Localized
            String status = track.getStatus();
            if ("ACTIVE".equalsIgnoreCase(status)) {
                layoutStatusBadge.setBackgroundResource(R.drawable.bg_badge_active);
                tvStatusIcon.setText("●");
                tvStatusIcon.setTextColor(0xFF00E5D1);
                tvStatus.setText(ctx.getString(R.string.adapter_status_active));
                tvStatus.setTextColor(0xFF00E5D1);
            } else {
                layoutStatusBadge.setBackgroundResource(R.drawable.bg_badge_completed);
                tvStatusIcon.setText("✓");
                tvStatusIcon.setTextColor(0xFF95B0D4);
                tvStatus.setText(ctx.getString(R.string.adapter_status_completed));
                tvStatus.setTextColor(0xFF95B0D4);
            }

            // Time range (localized)
            String timeRange = formatTimeRange(track, ctx);
            tvTimeRange.setText(timeRange);

            // IMEI
            if (track.getImei() != null && !track.getImei().isEmpty()) {
                tvImei.setText("📡 " + track.getImei());
                tvImei.setVisibility(View.VISIBLE);
            } else {
                tvImei.setVisibility(View.GONE);
            }

            // Distance
            double km = track.getTotalDistance() / 1000.0;
            tvTrackDistance.setText(String.format(Locale.US, "%.2f km", km));

            // Duration
            long durationMs = track.getDurationMillis();
            tvTrackDuration.setText(formatDuration(durationMs));

            // Points
            tvTrackPoints.setText(String.format(Locale.US, "%d", track.getPointCount()));

            // Click
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

        /**
         * Format time range (localized for "in progress")
         */
        private String formatTimeRange(SatTrackEntity track, Context ctx) {
            Date start = track.getStartTime();
            Date end = track.getEndTime();

            if (start == null) return "-";

            String startStr = TIME_RANGE_FMT.format(start);

            if (end == null) {
                // Active session - localized "in progress"
                return startStr + " ~ " + ctx.getString(R.string.adapter_time_in_progress);
            }

            // Same day check
            String startDate = DATE_FMT.format(start);
            String endDate = DATE_FMT.format(end);

            if (startDate.equals(endDate)) {
                return startStr + " ~ " + TIME_SHORT_FMT.format(end);
            } else {
                return startStr + " ~ " + TIME_RANGE_FMT.format(end);
            }
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
