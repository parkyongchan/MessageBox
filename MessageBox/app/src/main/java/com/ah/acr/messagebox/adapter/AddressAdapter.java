package com.ah.acr.messagebox.adapter;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.databinding.AdapterAddressBinding;
import com.ah.acr.messagebox.util.AvatarHelper;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class AddressAdapter extends ListAdapter<AddressEntity, AddressAdapter.AddressViewHolder> {

    private static final int AVATAR_SIZE_DP = 48;

    public interface OnAddressClickListener {
        void onAddressClick(AddressEntity msg);
        void onAddressDeleteClick(AddressEntity msg);
        // ⭐ NEW: Avatar edit callback
        void onAvatarEditClick(AddressEntity addr);
    }

    private final OnAddressClickListener onAddressClickListener;

    public AddressAdapter(OnAddressClickListener onAddressClickListener) {
        super(new AddressDiffCallback());
        this.onAddressClickListener = onAddressClickListener;
    }

    @NonNull
    @Override
    public AddressViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterAddressBinding binding = AdapterAddressBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AddressViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AddressViewHolder holder, int position) {
        holder.bind(getItem(position), onAddressClickListener);
    }


    public static class AddressViewHolder extends RecyclerView.ViewHolder {
        private final AdapterAddressBinding binding;
        private final SimpleDateFormat sdf =
                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        public AddressViewHolder(AdapterAddressBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(AddressEntity addr, OnAddressClickListener onAddressClickListener) {
            String imei = addr.getNumbers();
            String nickname = addr.getNumbersNic();
            String avatarPath = addr.getAvatarPath();

            String displayName = (nickname != null && !nickname.trim().isEmpty())
                    ? nickname
                    : (imei != null ? imei : "?");

            // 이름, 번호, 시간
            binding.textName.setText(displayName);
            binding.textNumbers.setText(imei);
            binding.textTime.setText(addr.getCreateAt() != null
                    ? sdf.format(addr.getCreateAt()) : "");

            // 아바타 생성 (AvatarHelper 사용)
            try {
                Bitmap avatarBitmap = AvatarHelper.loadOrCreate(
                        binding.getRoot().getContext(),
                        imei,
                        nickname,
                        avatarPath,
                        AVATAR_SIZE_DP
                );
                binding.imgAvatar.setImageBitmap(avatarBitmap);
                binding.imgAvatar.setVisibility(View.VISIBLE);
                binding.textAvatarInitial.setVisibility(View.GONE);
            } catch (Exception e) {
                // Fallback: TextView with initial
                binding.imgAvatar.setImageDrawable(null);
                binding.imgAvatar.setVisibility(View.GONE);
                binding.textAvatarInitial.setVisibility(View.VISIBLE);
                binding.textAvatarInitial.setText(
                        AvatarHelper.getInitial(imei, nickname)
                );
            }

            // ⭐ 아바타 박스 클릭 → Fragment로 전달
            binding.frameAvatar.setOnClickListener(v -> {
                if (onAddressClickListener != null) {
                    onAddressClickListener.onAvatarEditClick(addr);
                }
            });

            // 아이템 클릭 (편집 다이얼로그)
            binding.getRoot().setOnClickListener(v ->
                    onAddressClickListener.onAddressClick(addr));

            // 삭제 버튼
            binding.buttonDelete.setOnClickListener(v ->
                    onAddressClickListener.onAddressDeleteClick(addr));
        }
    }
}


class AddressDiffCallback extends DiffUtil.ItemCallback<AddressEntity> {
    @Override
    public boolean areItemsTheSame(@NonNull AddressEntity oldItem, @NonNull AddressEntity newItem) {
        return oldItem.getId() == newItem.getId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull AddressEntity oldItem, @NonNull AddressEntity newItem) {
        return oldItem.equals(newItem);
    }
}
