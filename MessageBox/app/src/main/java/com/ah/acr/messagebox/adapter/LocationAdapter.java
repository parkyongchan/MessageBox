package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.LocationEntity;
import com.ah.acr.messagebox.database.LocationWithAddress;
import com.ah.acr.messagebox.databinding.AdapterLocationBinding;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * ⭐ trackMode 분류 수정:
 * - 0x10, 4, 5    → SOS (긴급)
 * - 0x11, 0x12, 0x13, 2 → TRACK (Tracking)
 * - 기타          → DATA
 */
public class LocationAdapter extends ListAdapter<LocationWithAddress, LocationAdapter.LocationViewHolder> {

    public interface OnLocationClickListener {
        void onLocationClick(LocationEntity location);
        void onLocationDeleteClick(LocationEntity location);
        void onLocationCopyClick(LocationEntity location);
        void onLocationMapClick(LocationEntity location);
        void onAddressClick(LocationWithAddress location);
    }

    private final OnLocationClickListener onLocationClickListener;

    public LocationAdapter(OnLocationClickListener onLocationClickListener) {
        super(new LocationDiffCallback());
        this.onLocationClickListener = onLocationClickListener;
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterLocationBinding binding = AdapterLocationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LocationViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        holder.bind(getItem(position), onLocationClickListener);
    }


    public static class LocationViewHolder extends RecyclerView.ViewHolder {
        private final AdapterLocationBinding binding;
        private final SimpleDateFormat sdfTime =
                new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        public LocationViewHolder(AdapterLocationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(LocationWithAddress locationAddr, OnLocationClickListener onLocationClickListener) {
            LocationEntity location = locationAddr.getLocation();

            // 이름 처리 (주소록 > IMEI)
            String displayName;
            if (locationAddr.getAddress() != null && locationAddr.getAddress().getNumbersNic() != null) {
                displayName = locationAddr.getAddress().getNumbersNic();
            } else {
                displayName = location.getCodeNum() != null ? location.getCodeNum() : "Unknown";
            }

            binding.tvSender.setText(displayName);
            binding.tvImei.setText(location.getCodeNum() != null ? location.getCodeNum() : "");

            // ⭐ 이니셜 아바타
            String initial = !displayName.isEmpty()
                    ? displayName.substring(0, 1).toUpperCase()
                    : "?";
            binding.textAvatarInitial.setText(initial);

            // 시간
            if (location.getCreateAt() != null) {
                binding.tvDatetime.setText(sdfTime.format(location.getCreateAt()));
            } else {
                binding.tvDatetime.setText("--:--");
            }

            // 좌표
            binding.tvLatitude.setText(formatCoord(location.getLatitude()));
            binding.tvLongitude.setText(formatCoord(location.getLongitude()));

            // ⭐ Track Mode에 따른 배지 처리 (수정됨)
            // - 0x10, 4, 5    → SOS (긴급)
            // - 0x11, 0x12, 0x13, 2 → TRACK (Tracking)
            // - 기타          → DATA
            int trackMode = location.getTrackMode();
            if (trackMode == 0x10 || trackMode == 4 || trackMode == 5) {
                // 🚨 SOS 모드 (긴급)
                binding.badgeType.setText("SOS");
                binding.badgeType.setBackgroundResource(R.drawable.bg_badge_sos);
                binding.badgeType.setTextColor(0xFFFF5252);
                binding.layDetail.setVisibility(View.GONE);
            } else if (trackMode == 0x11 || trackMode == 0x12
                    || trackMode == 0x13 || trackMode == 2) {
                // 🚗 Track 모드 (0x11=CAR, 0x12=UAV, 0x13=UAT, 2=legacy)
                binding.badgeType.setText("TRACK");
                binding.badgeType.setBackgroundResource(R.drawable.bg_badge_track);
                binding.badgeType.setTextColor(0xFF00E5D1);
                // 상세 정보 표시
                showDetailInfo(location);
            } else {
                // 📍 일반 데이터
                binding.badgeType.setText("DATA");
                binding.badgeType.setBackgroundResource(R.drawable.bg_badge_data);
                binding.badgeType.setTextColor(0xFF95B0D4);
                // 상세 정보 조건부 표시
                showDetailInfo(location);
            }

            // 클릭 리스너
            binding.getRoot().setOnClickListener(v ->
                    onLocationClickListener.onLocationClick(location));
            binding.btnDel.setOnClickListener(v ->
                    onLocationClickListener.onLocationDeleteClick(location));
            binding.btnCopy.setOnClickListener(v ->
                    onLocationClickListener.onLocationCopyClick(location));
            binding.btnMap.setOnClickListener(v ->
                    onLocationClickListener.onLocationMapClick(location));
            binding.btnAddressEdit.setOnClickListener(v ->
                    onLocationClickListener.onAddressClick(locationAddr));
        }

        /** 상세 정보 (고도/방향/속도) 표시 */
        private void showDetailInfo(LocationEntity location) {
            boolean hasDetail = location.getAltitude() != null
                    || location.getDirection() != null
                    || location.getSpeed() != null;

            if (hasDetail) {
                binding.layDetail.setVisibility(View.VISIBLE);

                // 고도
                if (location.getAltitude() != null) {
                    binding.tvAltitude.setText(location.getAltitude() + "m");
                    binding.layAltitude.setVisibility(View.VISIBLE);
                } else {
                    binding.layAltitude.setVisibility(View.GONE);
                }

                // 방향
                if (location.getDirection() != null) {
                    binding.tvDirection.setText(location.getDirection() + "°");
                    binding.layDirection.setVisibility(View.VISIBLE);
                } else {
                    binding.layDirection.setVisibility(View.GONE);
                }

                // 속도
                if (location.getSpeed() != null) {
                    binding.tvSpeed.setText(location.getSpeed() + "km/h");
                    binding.laySpeed.setVisibility(View.VISIBLE);
                } else {
                    binding.laySpeed.setVisibility(View.GONE);
                }
            } else {
                binding.layDetail.setVisibility(View.GONE);
            }
        }

        /** 좌표 포맷 (소수점 6자리) */
        private String formatCoord(Double coord) {
            return coord != null
                    ? String.format(Locale.US, "%.6f", coord)
                    : "0.000000";
        }

    }


}


class LocationDiffCallback extends DiffUtil.ItemCallback<LocationWithAddress> {
    @Override
    public boolean areItemsTheSame(@NonNull LocationWithAddress oldItem, @NonNull LocationWithAddress newItem) {
        return oldItem.getLocation().getId() == newItem.getLocation().getId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull LocationWithAddress oldItem, @NonNull LocationWithAddress newItem) {
        return oldItem.equals(newItem);
    }
}
