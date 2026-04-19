package com.ah.acr.messagebox.adapter;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.MsgWithAddress;
import com.ah.acr.messagebox.util.AvatarHelper;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class ChatRoomAdapter extends ListAdapter<MsgWithAddress, ChatRoomAdapter.BubbleViewHolder> {

    private static final int TYPE_SEND    = 1;
    private static final int TYPE_RECEIVE = 2;

    private static final int AVATAR_SIZE_DP = 36;

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

        // ⭐ Avatar: FrameLayout wrapping ImageView + TextView
        private final FrameLayout layoutAvatar;
        private final ImageView imgAvatar;
        private final TextView textAvatar;

        private final TextView textSender;
        private final TextView textTitle;
        private final TextView textMsg;

        // 좌측 시간 영역
        private final LinearLayout layoutTimeLeft;
        private final ImageView imgPendingLeft;
        private final TextView textTimeLeft;

        // 우측 시간 (수신 메시지용)
        private final TextView textTimeRight;

        private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

        BubbleViewHolder(View view) {
            super(view);
            layoutRoot     = view.findViewById(R.id.layout_bubble_root);
            layoutContent  = view.findViewById(R.id.layout_bubble_content);
            layoutRow      = view.findViewById(R.id.layout_bubble_row);
            layoutBox      = view.findViewById(R.id.layout_bubble_box);

            // Avatar views
            layoutAvatar   = view.findViewById(R.id.layout_bubble_avatar);
            imgAvatar      = view.findViewById(R.id.img_bubble_avatar);
            textAvatar     = view.findViewById(R.id.text_bubble_avatar);

            textSender     = view.findViewById(R.id.text_bubble_sender);
            textTitle      = view.findViewById(R.id.text_bubble_title);
            textMsg        = view.findViewById(R.id.text_bubble_msg);

            layoutTimeLeft = view.findViewById(R.id.layout_bubble_time_left);
            imgPendingLeft = view.findViewById(R.id.img_bubble_pending_left);
            textTimeLeft   = view.findViewById(R.id.text_bubble_time_left);

            textTimeRight  = view.findViewById(R.id.text_bubble_time_right);
        }

        void bind(MsgWithAddress item) {
            boolean isSend = item.getMsg().isSendMsg();

            // 연락처 정보
            String imei = item.getMsg().getCodeNum();
            String nickname = null;
            String avatarPath = null;

            if (item.getAddress() != null) {
                nickname = item.getAddress().getNumbersNic();
                avatarPath = item.getAddress().getAvatarPath();  // ⭐ NEW
            }

            // 표시 이름 (닉네임 > IMEI > "?")
            String name;
            if (nickname != null && !nickname.trim().isEmpty()) {
                name = nickname;
            } else if (imei != null && !imei.trim().isEmpty()) {
                name = imei;
            } else {
                name = "?";
            }

            // 시간
            String time = item.getMsg().getCreateAt() != null
                    ? sdf.format(item.getMsg().getCreateAt()) : "";

            if (isSend) {
                // ── 발신: 오른쪽 정렬 ──
                layoutRoot.setGravity(android.view.Gravity.END);

                // ⭐ 아바타 전체 숨김 (발신 메시지는 내 것이므로 아바타 불필요)
                layoutAvatar.setVisibility(View.GONE);

                textSender.setVisibility(View.GONE);
                layoutBox.setBackgroundColor(0xFF003D3A);
                textMsg.setTextColor(0xFF00E5D1);

                // 좌측 시간 영역 표시
                layoutTimeLeft.setVisibility(View.VISIBLE);
                textTimeLeft.setText(time);
                textTimeRight.setVisibility(View.GONE);

                // 미전송 시 모래시계 ImageView 표시
                if (!item.getMsg().isSend()) {
                    imgPendingLeft.setVisibility(View.VISIBLE);
                    textTimeLeft.setTextColor(0xFFFFB300);
                } else {
                    imgPendingLeft.setVisibility(View.GONE);
                    textTimeLeft.setTextColor(0xFF4A5F78);
                }

            } else {
                // ── 수신: 왼쪽 정렬 ──
                layoutRoot.setGravity(android.view.Gravity.START);

                // ⭐ 아바타 표시 (AvatarHelper 사용)
                layoutAvatar.setVisibility(View.VISIBLE);
                textAvatar.setVisibility(View.GONE);  // Bitmap 사용 시 TextView 숨김

                try {
                    Bitmap avatarBitmap = AvatarHelper.loadOrCreate(
                            itemView.getContext(),
                            imei,
                            nickname,
                            avatarPath,
                            AVATAR_SIZE_DP
                    );
                    imgAvatar.setImageBitmap(avatarBitmap);
                } catch (Exception e) {
                    // Fallback: show TextView with initial
                    imgAvatar.setImageDrawable(null);
                    textAvatar.setVisibility(View.VISIBLE);
                    textAvatar.setText(
                            AvatarHelper.getInitial(imei, nickname)
                    );
                }

                textSender.setVisibility(View.VISIBLE);
                textSender.setText(name);
                layoutBox.setBackgroundColor(0xFF1A2F50);
                textMsg.setTextColor(0xFFFFFFFF);

                // 좌측 시간 영역 숨김
                layoutTimeLeft.setVisibility(View.GONE);
                imgPendingLeft.setVisibility(View.GONE);

                // 우측 시간만 표시
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
