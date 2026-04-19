package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.LocationEntity;
import com.ah.acr.messagebox.database.LocationWithAddress;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 트랙 포인트 목록 어댑터 (상세 팝업용)
 * - 단순 ListAdapter가 아닌 일반 RecyclerView.Adapter로 구현
 * - 선택된 위치 관리 기능
 */
public class TrackPointAdapter extends RecyclerView.Adapter<TrackPointAdapter.TrackViewHolder> {

    public interface OnTrackPointClickListener {
        void onTrackPointClick(int position, LocationWithAddress item);
    }

    private final List<LocationWithAddress> items = new ArrayList<>();
    private int selectedPosition = -1;
    private final OnTrackPointClickListener listener;

    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    public TrackPointAdapter(OnTrackPointClickListener listener) {
        this.listener = listener;
    }

    /** 데이터 업데이트 */
    public void setItems(List<LocationWithAddress> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    /** 선택 위치 설정 */
    public void setSelectedPosition(int position) {
        int oldSelection = selectedPosition;
        selectedPosition = position;

        if (oldSelection >= 0) notifyItemChanged(oldSelection);
        if (selectedPosition >= 0) notifyItemChanged(selectedPosition);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public LocationWithAddress getItem(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.adapter_track_point, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        LocationWithAddress item = items.get(position);
        LocationEntity loc = item.getLocation();

        // # 순번 (역순 표시: 최신=1, 오래됨=N)
        // items는 시간 DESC 순으로 들어옴
        holder.tvIndex.setText(String.valueOf(position + 1));

        // Type 배지 및 색상
        int trackMode = loc.getTrackMode();
        if (trackMode == 0x10 || trackMode == 0x11 || trackMode == 4 || trackMode == 5) {
            holder.tvType.setText("SOS");
            holder.tvType.setTextColor(0xFFFF5252);
        } else if (trackMode == 2) {
            holder.tvType.setText("TRACK");
            holder.tvType.setTextColor(0xFF378ADD);
        } else {
            holder.tvType.setText("DATA");
            holder.tvType.setTextColor(0xFF95B0D4);
        }

        // 좌표
        if (loc.getLatitude() != null) {
            holder.tvLat.setText(String.format(Locale.US, "%.4f", loc.getLatitude()));
        } else {
            holder.tvLat.setText("-");
        }

        if (loc.getLongitude() != null) {
            holder.tvLng.setText(String.format(Locale.US, "%.4f", loc.getLongitude()));
        } else {
            holder.tvLng.setText("-");
        }

        // 시간
        if (loc.getCreateAt() != null) {
            holder.tvTime.setText(timeFormat.format(loc.getCreateAt()));
        } else {
            holder.tvTime.setText("--:--");
        }

        // 선택 상태
        holder.itemView.setSelected(position == selectedPosition);

        // 클릭
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackPointClick(position, item);
            }
        });
    }


    static class TrackViewHolder extends RecyclerView.ViewHolder {
        android.widget.TextView tvIndex, tvType, tvLat, tvLng, tvTime;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tv_index);
            tvType = itemView.findViewById(R.id.tv_type);
            tvLat = itemView.findViewById(R.id.tv_lat);
            tvLng = itemView.findViewById(R.id.tv_lng);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}
