package com.ah.acr.messagebox.adapter;

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

    // ⭐ v4 UI-2026-04-23: 다양한 시간 포맷
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
        // 상단
        TextView tvModeIcon;
        TextView tvTrackName;
        LinearLayout layoutStatusBadge;
        TextView tvStatusIcon;
        TextView tvStatus;

        // 시간 & IMEI
        TextView tvTimeRange;
        TextView tvImei;

        // 통계
        TextView tvTrackDuration;
        TextView tvTrackDistance;
        TextView tvTrackPoints;

        // 액션
        ImageButton btnExport;
        ImageButton btnDelete;

        // Fallback (숨김)
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
            // ⭐ 모드 아이콘 (세션명으로 판별)
            String name = track.getName() != null ? track.getName() : "";
            if (name.contains("SOS")) {
                tvModeIcon.setText("🆘");
            } else {
                tvModeIcon.setText("🛰");
            }

            // 세션명
            tvTrackName.setText(name);

            // ⭐ 상태 뱃지 (ACTIVE/COMPLETED)
            String status = track.getStatus();
            if ("ACTIVE".equalsIgnoreCase(status)) {
                layoutStatusBadge.setBackgroundResource(R.drawable.bg_badge_active);
                tvStatusIcon.setText("●");
                tvStatusIcon.setTextColor(0xFF00E5D1); // 민트
                tvStatus.setText("ACTIVE");
                tvStatus.setTextColor(0xFF00E5D1);
            } else {
                layoutStatusBadge.setBackgroundResource(R.drawable.bg_badge_completed);
                tvStatusIcon.setText("✓");
                tvStatusIcon.setTextColor(0xFF95B0D4); // 회색
                tvStatus.setText("COMPLETED");
                tvStatus.setTextColor(0xFF95B0D4);
            }

            // ⭐ 시간 범위 표시
            String timeRange = formatTimeRange(track);
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

            // 클릭
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
         * 시간 범위 포매팅
         * - 활성: "2026-04-23 16:00 ~ 진행중"
         * - 완료 (같은 날): "2026-04-23 16:00 ~ 17:15"
         * - 완료 (다른 날): "2026-04-23 16:00 ~ 04/24 02:15"
         * - startTime 없음: "-"
         */
        private String formatTimeRange(SatTrackEntity track) {
            Date start = track.getStartTime();
            Date end = track.getEndTime();

            if (start == null) return "-";

            String startStr = TIME_RANGE_FMT.format(start);

            if (end == null) {
                // 활성 세션 (완료 안 됨)
                return startStr + " ~ 진행중";
            }

            // 같은 날인지 체크
            String startDate = DATE_FMT.format(start);
            String endDate = DATE_FMT.format(end);

            if (startDate.equals(endDate)) {
                // 같은 날 → 시작 전체 + 종료 시간만
                return startStr + " ~ " + TIME_SHORT_FMT.format(end);
            } else {
                // 다른 날 → 둘 다 날짜 포함
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
