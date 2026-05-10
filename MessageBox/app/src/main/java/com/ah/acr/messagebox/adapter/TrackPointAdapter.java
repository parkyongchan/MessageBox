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
 * 트랙 포인트 목록 어댑터 (상세 팝업)
 * - 단순 ListAdapter가 아닌 일반 RecyclerView.Adapter로 구현
 * - 선택된 위치 관리 기능
 * - ⭐ trackMode 분류: 0x10=SOS, 0x11/0x12/0x13=Track
 *
 * [2026-05-10] displayReversed 플래그 추가
 *   - false (기본): 정순 표시 (#1=position 0=화면 맨 위, 가장 오래됨)
 *   - true: 역순 표시 (화면 맨 위=최신, 번호는 원본 시간순 기준 #1=가장 오래됨)
 *   - 클릭 콜백/setSelectedPosition은 항상 mTrackPoints 인덱스(정순) 기준
 */
public class TrackPointAdapter extends RecyclerView.Adapter<TrackPointAdapter.TrackViewHolder> {

    public interface OnTrackPointClickListener {
        void onTrackPointClick(int position, LocationWithAddress item);
    }

    private final List<LocationWithAddress> items = new ArrayList<>();
    private int selectedPosition = -1;
    private boolean displayReversed = false;  // ⭐ 목록만 역순 표시 (번호는 원본 기준)
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

    /**
     * ⭐ 목록 표시만 역순 (최신이 위). 번호는 원본 시간순 기준 유지(#1=가장 오래됨)
     *
     * Fragment에서 mTrackPoints를 reverse한 리스트를 setItems로 넘긴 후
     * setDisplayReversed(true)를 호출하면 어댑터가 자동으로:
     *   - 번호를 (items.size() - position)으로 표시 → 화면 맨 위가 #N(최신)
     *   - 클릭 시 mTrackPoints 정순 인덱스(items.size() - 1 - position)로 변환해서 콜백
     *   - setSelectedPosition도 정순 인덱스를 받아 어댑터 position으로 변환
     */
    public void setDisplayReversed(boolean reversed) {
        this.displayReversed = reversed;
        notifyDataSetChanged();
    }

    /**
     * 선택 위치 설정.
     * ⭐ 받는 trackIndex는 항상 mTrackPoints 정순 인덱스 (재생 인덱스).
     * displayReversed=true면 어댑터 position으로 자동 변환해서 하이라이트.
     */
    public void setSelectedPosition(int trackIndex) {
        int oldSelection = selectedPosition;
        selectedPosition = (displayReversed && trackIndex >= 0)
                ? (items.size() - 1 - trackIndex)
                : trackIndex;
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

    @Override
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

        // ⭐ # 순번:
        //   displayReversed=false (정순): position 0 = #1 (가장 오래됨, 화면 맨 위)
        //   displayReversed=true (역순):  position 0 = #N (최신, 화면 맨 위)
        //   어느 경우든 #1은 항상 시간상 가장 오래된 점을 의미
        int displayNumber = displayReversed
                ? (items.size() - position)
                : (position + 1);
        holder.tvIndex.setText(String.valueOf(displayNumber));

        // ⭐ Type 분류:
        //   SOS:   0x10 (진짜 SOS), 4, 5 (legacy SOS)
        //   TRACK: 0x11 (CAR), 0x12 (UAV), 0x13 (UAT), 2 (legacy Track)
        //   DATA:  기타
        int trackMode = loc.getTrackMode();
        if (trackMode == 0x10 || trackMode == 4 || trackMode == 5) {
            holder.tvType.setText("SOS");
            holder.tvType.setTextColor(0xFFFF5252);
        } else if (trackMode == 0x11 || trackMode == 0x12
                || trackMode == 0x13 || trackMode == 2) {
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

        // ⭐ 클릭: displayReversed=true면 mTrackPoints 정순 인덱스로 변환해서 콜백
        //   Fragment의 onTrackPointClick(position, item)에서 mTrackPoints.get(position) 호출하므로
        //   여기서 미리 변환해두면 Fragment 코드 수정 불필요
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int actualIndex = displayReversed
                        ? (items.size() - 1 - position)
                        : position;
                listener.onTrackPointClick(actualIndex, item);
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
