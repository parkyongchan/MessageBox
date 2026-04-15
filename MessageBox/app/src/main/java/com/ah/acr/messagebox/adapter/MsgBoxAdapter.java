package com.ah.acr.messagebox.adapter;

import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.InboxMsg;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.database.MsgWithAddress;
import com.ah.acr.messagebox.databinding.AdapterMsgInboxBinding;
import com.ah.acr.messagebox.databinding.AdapterMsgboxBinding;

import java.text.SimpleDateFormat;
import java.util.List;

public class MsgBoxAdapter extends ListAdapter<MsgWithAddress, MsgBoxAdapter.MsgViewHolder> {

    public interface OnMsgClickListener {
        void onMessageClick(MsgEntity msg);
        void onMsgDeleteClick(MsgEntity msg);
    }

    private final OnMsgClickListener onMsgClickListener;
    public MsgBoxAdapter(OnMsgClickListener onMsgClickListener) {
        super(new MsgDiffCallback());
        this.onMsgClickListener = onMsgClickListener;
    }

    @NonNull
    @Override
    public MsgViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterMsgboxBinding binding = AdapterMsgboxBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);

        return new MsgViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MsgViewHolder holder, int position) {
        holder.bind(getItem(position), onMsgClickListener);
    }


    public static class MsgViewHolder extends RecyclerView.ViewHolder {
        private final AdapterMsgboxBinding binding;
        private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");



        public MsgViewHolder(AdapterMsgboxBinding binding) {
            super(binding.getRoot());
            this.binding = binding;



        }

        public void bind(MsgWithAddress msg, OnMsgClickListener onMsgClickListener) {

            // send message

            if (msg.getMsg().isSendMsg()){
                binding.imageReceived.setImageResource(R.drawable.ic_sent);

                if (!msg.getMsg().isSend()) binding.getRoot().setBackgroundColor(Color.LTGRAY);
                else binding.getRoot().setBackgroundColor(Color.WHITE);
                //if (msg.isSend() && !msg.isDeviceSend()) binding.getRoot().setBackgroundColor(Color.CYAN);
                //if (msg.isDeviceSend()) binding.getRoot().setBackgroundColor(Color.WHITE);

            } else {  // recevice message
                binding.imageReceived.setImageResource(R.drawable.ic_received);

                if (!msg.getMsg().isRead()) binding.getRoot().setBackgroundColor(Color.CYAN);
                else binding.getRoot().setBackgroundColor(Color.WHITE);
            }

            //String bodyMsg = msg.getMsg().getMsg().trim();
            //if (bodyMsg.length() > 15) binding.textTitle.setText(bodyMsg.substring(0,15));
            //else binding.textTitle.setText(bodyMsg);

            binding.textTitle.setText(msg.getMsg().getTitle());
            binding.textName.setText(msg.getAddress()==null? msg.getMsg().getCodeNum(): msg.getAddress().getNumbersNic());
            binding.textCodeNum.setText(msg.getMsg().getCodeNum());
            binding.textTime.setText(sdf.format(msg.getMsg().getCreateAt()));

            binding.getRoot().setOnClickListener(v -> onMsgClickListener.onMessageClick(msg.getMsg()));
            binding.buttonDelete.setOnClickListener(v -> onMsgClickListener.onMsgDeleteClick(msg.getMsg()));
        }
    }
}

class MsgDiffCallback extends DiffUtil.ItemCallback<MsgWithAddress> {
    @Override
    public boolean areItemsTheSame(@NonNull MsgWithAddress oldItem, @NonNull MsgWithAddress newItem) {
        return oldItem.getMsg().getId() == newItem.getMsg().getId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull MsgWithAddress oldItem, @NonNull MsgWithAddress newItem) {
        return oldItem.equals(newItem);
    }
}
