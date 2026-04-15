package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgWithAddress;

import java.text.SimpleDateFormat;
import java.util.Locale;

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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_room, parent, false);
        return new MsgViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MsgViewHolder holder, int position) {
        holder.bind(getItem(position), onMsgClickListener);
    }

    public static class MsgViewHolder extends RecyclerView.ViewHolder {
        private final TextView textName;
        private final TextView textLastMsg;
        private final TextView textTime;
        private final TextView textDirection;
        private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());

        public MsgViewHolder(View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_chat_name);
            textLastMsg = itemView.findViewById(R.id.text_chat_last_msg);
            textTime = itemView.findViewById(R.id.text_chat_time);
            textDirection = itemView.findViewById(R.id.text_chat_direction);
        }

        public void bind(MsgWithAddress item, OnMsgClickListener listener) {
            // 연락처 이름 (별명 없으면 번호 표시)
            String name = item.getAddress() != null && item.getAddress().getNumbersNic() != null
                    ? item.getAddress().getNumbersNic()
                    : item.getMsg().getCodeNum();
            textName.setText(name);

            // 마지막 메시지
            String lastMsg = item.getMsg().getMsg() != null ? item.getMsg().getMsg() : "";
            if (lastMsg.length() > 30) lastMsg = lastMsg.substring(0, 30) + "...";
            textLastMsg.setText(lastMsg);

            // 시간
            if (item.getMsg().getCreateAt() != null) {
                textTime.setText(sdf.format(item.getMsg().getCreateAt()));
            }

            // 송수신 방향
            if (item.getMsg().isSendMsg()) {
                textDirection.setText("▶ 발신");
                textDirection.setTextColor(0xFF00E5D1);
            } else {
                textDirection.setText("◀ 수신");
                textDirection.setTextColor(0xFFFFB300);
            }

            // 클릭
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onMessageClick(item.getMsg());
            });
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