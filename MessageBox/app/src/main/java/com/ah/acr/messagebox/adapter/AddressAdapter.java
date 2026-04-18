package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.databinding.AdapterAddressBinding;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class AddressAdapter extends ListAdapter<AddressEntity, AddressAdapter.AddressViewHolder> {

    public interface OnAddressClickListener {
        void onAddressClick(AddressEntity msg);
        void onAddressDeleteClick(AddressEntity msg);
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
            String name = addr.getNumbersNic() != null ? addr.getNumbersNic() : "?";

            // 이름, 번호, 시간
            binding.textName.setText(name);
            binding.textNumbers.setText(addr.getNumbers());
            binding.textTime.setText(addr.getCreateAt() != null
                    ? sdf.format(addr.getCreateAt()) : "");

            // ⭐ 이니셜 아바타 (첫 글자 대문자)
            String initial = !name.isEmpty()
                    ? name.substring(0, 1).toUpperCase() : "?";
            binding.textAvatarInitial.setText(initial);

            // ⭐ 아바타 박스 클릭 리스너 (이미지 편집 - 향후 구현)
            binding.frameAvatar.setOnClickListener(v -> {
                // TODO: 이미지 편집/업로드 기능 구현 예정
                Toast.makeText(v.getContext(),
                        "Image upload coming soon",
                        Toast.LENGTH_SHORT).show();
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