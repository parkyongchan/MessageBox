package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.databinding.AdapterAddressBinding;
import com.ah.acr.messagebox.databinding.AdapterMsgboxBinding;

import java.text.SimpleDateFormat;

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
        private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


        public AddressViewHolder(AdapterAddressBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

        }

        public void bind(AddressEntity addr, OnAddressClickListener onAddressClickListener) {
            binding.textName.setText(addr.getNumbersNic());
            binding.textNumbers.setText(addr.getNumbers());
            binding.textTime.setText(  sdf.format(addr.getCreateAt()));

            binding.getRoot().setOnClickListener(v -> onAddressClickListener.onAddressClick(addr));
            binding.buttonDelete.setOnClickListener(v -> onAddressClickListener.onAddressDeleteClick(addr));

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
