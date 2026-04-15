package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.MsgWithAddress;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatRoomAdapter extends RecyclerView.Adapter<ChatRoomAdapter.ViewHolder> {

    private List<MsgWithAddress> items = new ArrayList<>();
    private OnChatRoomClickListener listener;

    public interface OnChatRoomClickListener {
        void onChatRoomClick(MsgWithAddress item);
    }

    public void setOnChatRoomClickListener(OnChatRoomClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<MsgWithAddress> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_room, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MsgWithAddress item = items.get(position);

        // 연락처 이름 (별명 없으면 번호 표시)
        String name = item.getAddress() != null && item.getAddress().getNumbersNic() != null
                ? item.getAddress().getNumbersNic()
                : item.getMsg().getCodeNum();
        holder.textName.setText(name);

        // 마지막 메시지
        String lastMsg = item.getMsg().getMsg() != null ? item.getMsg().getMsg() : "";
        if (lastMsg.length() > 30) lastMsg = lastMsg.substring(0, 30) + "...";
        holder.textLastMsg.setText(lastMsg);

        // 시간
        if (item.getMsg().getCreateAt() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
            holder.textTime.setText(sdf.format(item.getMsg().getCreateAt()));
        }

        // 송수신 방향 표시
        if (item.getMsg().isSendMsg()) {
            holder.textDirection.setText("▶ 발신");
            holder.textDirection.setTextColor(0xFF00E5D1);
        } else {
            holder.textDirection.setText("◀ 수신");
            holder.textDirection.setTextColor(0xFFFFB300);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onChatRoomClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textName, textLastMsg, textTime, textDirection;

        ViewHolder(View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_chat_name);
            textLastMsg = itemView.findViewById(R.id.text_chat_last_msg);
            textTime = itemView.findViewById(R.id.text_chat_time);
            textDirection = itemView.findViewById(R.id.text_chat_direction);
        }
    }
}