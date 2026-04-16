package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.MsgWithAddress;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class ChatRoomAdapter extends ListAdapter<MsgWithAddress, ChatRoomAdapter.BubbleViewHolder> {

    private static final int TYPE_SEND    = 1;
    private static final int TYPE_RECEIVE = 2;

    public ChatRoomAdapter() {
        super(new DiffUtil.ItemCallback<MsgWithAddress>() {
            @Override
            public boolean areItemsTheSame(@NonNull MsgWithAddress o, @NonNull MsgWithAddress n) {
                return o.getMsg().getId() == n.getMsg().getId();
            }
            @Override
            public boolean areContentsTheSame(@NonNull MsgWithAddress o, @NonNull MsgWithAddress n) {
                return o.equals(n);
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getMsg().isSendMsg() ? TYPE_SEND : TYPE_RECEIVE;
    }

    @NonNull
    @Override
    public BubbleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bubble, parent, false);
        return new BubbleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BubbleViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class BubbleViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout layoutRoot;
        private final LinearLayout layoutContent;
        private final LinearLayout layoutRow;
        private final LinearLayout layoutBox;
        private final TextView textAvatar;
        private final TextView textSender;
        private final TextView textTitle;
        private final TextView textMsg;
        private final TextView textTimeLeft;
        private final TextView textTimeRight;
        private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

        BubbleViewHolder(View view) {
            super(view);
            layoutRoot    = view.findViewById(R.id.layout_bubble_root);
            layoutContent = view.findViewById(R.id.layout_bubble_content);
            layoutRow     = view.findViewById(R.id.layout_bubble_row);
            layoutBox     = view.findViewById(R.id.layout_bubble_box);
            textAvatar    = view.findViewById(R.id.text_bubble_avatar);
            textSender    = view.findViewById(R.id.text_bubble_sender);
            textTitle     = view.findViewById(R.id.text_bubble_title);
            textMsg       = view.findViewById(R.id.text_bubble_msg);
            textTimeLeft  = view.findViewById(R.id.text_bubble_time_left);
            textTimeRight = view.findViewById(R.id.text_bubble_time_right);
        }

        void bind(MsgWithAddress item) {
            boolean isSend = item.getMsg().isSendMsg();

            // 연락처 이름
            String name = item.getAddress() != null && item.getAddress().getNumbersNic() != null
                    ? item.getAddress().getNumbersNic()
                    : (item.getMsg().getCodeNum() != null ? item.getMsg().getCodeNum() : "?");

            // 이니셜 (첫 글자)
            String initial = name.length() > 0 ? name.substring(0, 1).toUpperCase() : "?";

            // 시간
            String time = item.getMsg().getCreateAt() != null
                    ? sdf.format(item.getMsg().getCreateAt()) : "";

            if (isSend) {
                // ── 발신: 오른쪽 정렬 ──
                layoutRoot.setGravity(android.view.Gravity.END);
                textAvatar.setVisibility(View.GONE);
                textSender.setVisibility(View.GONE);
                layoutBox.setBackgroundColor(0xFF003D3A); // 어두운 청록색
                textMsg.setTextColor(0xFF00E5D1);
                textTimeLeft.setVisibility(View.VISIBLE);
                textTimeLeft.setText(time);
                textTimeRight.setVisibility(View.GONE);
                // 미전송 표시
                if (!item.getMsg().isSend()) {
                    textTimeLeft.setText(time + "\n⏳");
                    textTimeLeft.setTextColor(0xFFFFB300);
                } else {
                    textTimeLeft.setTextColor(0xFF4A5F78);
                }

            } else {
                // ── 수신: 왼쪽 정렬 ──
                layoutRoot.setGravity(android.view.Gravity.START);
                textAvatar.setVisibility(View.VISIBLE);
                textAvatar.setText(initial);
                textSender.setVisibility(View.VISIBLE);
                textSender.setText(name);
                layoutBox.setBackgroundColor(0xFF1A2F50); // 어두운 파란색
                textMsg.setTextColor(0xFFFFFFFF);
                textTimeLeft.setVisibility(View.GONE);
                textTimeRight.setVisibility(View.VISIBLE);
                textTimeRight.setText(time);
                textTimeRight.setTextColor(0xFF4A5F78);
            }

            // 제목
            String title = item.getMsg().getTitle();
            if (title != null && !title.trim().isEmpty()) {
                textTitle.setVisibility(View.VISIBLE);
                textTitle.setText(title);
            } else {
                textTitle.setVisibility(View.GONE);
            }

            // 본문
            textMsg.setText(item.getMsg().getMsg() != null ? item.getMsg().getMsg() : "");
        }
    }
}