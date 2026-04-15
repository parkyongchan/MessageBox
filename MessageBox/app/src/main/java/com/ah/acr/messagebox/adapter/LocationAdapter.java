package com.ah.acr.messagebox.adapter;

import android.graphics.Color;
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
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.databinding.AdapterLocationBinding;
import com.ah.acr.messagebox.databinding.AdapterMsgboxBinding;

import java.text.SimpleDateFormat;

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
        private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


        public LocationViewHolder(AdapterLocationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(LocationWithAddress locationAddr, OnLocationClickListener onLocationClickListener) {
            LocationEntity location = locationAddr.getLocation();
            //receive location
            if (location.isIncomeLoc()) {
                binding.tvDatetime.setText( sdf.format(location.getCreateAt()));
                binding.tvSender.setText(locationAddr.getAddress()==null?location.getCodeNum(): locationAddr.getAddress().getNumbersNic());
                binding.tvLatitude.setText(location.getLatitude().toString());
                binding.tvLongitude.setText(location.getLongitude().toString());
                binding.tvAltitude.setText(location.getAltitude()==null?"0":location.getAltitude().toString());
                binding.tvDirection.setText(location.getDirection()==null?"0":location.getDirection().toString());
                binding.tvSpeed.setText(location.getSpeed()==null?"0":location.getSpeed().toString());

                if (locationAddr.getLocation().getTrackMode() == 0x10) {
                    binding.layAltitude.setVisibility(View.GONE);
                    binding.layDirection.setVisibility(View.GONE);
                    binding.laySpeed.setVisibility(View.GONE);
//                    binding.tvAltitude.setVisibility(View.GONE);
//                    binding.tvDirection.setVisibility(View.GONE);
//                    binding.tvSpeed.setVisibility(View.GONE);

                    binding.tvSender.setBackgroundColor(Color.RED);

                } else if (locationAddr.getLocation().getTrackMode() == 0x11) {
                    binding.layAltitude.setVisibility(View.GONE);
                    binding.layDirection.setVisibility(View.GONE);
                    binding.laySpeed.setVisibility(View.GONE);
//                    binding.tvAltitude.setVisibility(View.GONE);
//                    binding.tvDirection.setVisibility(View.GONE);
//                    binding.tvSpeed.setVisibility(View.GONE);
                }
            }

            binding.getRoot().setOnClickListener(v -> onLocationClickListener.onLocationClick(location));
            binding.btnDel.setOnClickListener(v -> onLocationClickListener.onLocationDeleteClick(location));
            binding.btnCopy.setOnClickListener(v->onLocationClickListener.onLocationCopyClick(location));
            binding.btnMap.setOnClickListener(v->onLocationClickListener.onLocationMapClick(location));
            binding.btnAddressEdit.setOnClickListener(v-> onLocationClickListener.onAddressClick(locationAddr));
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
