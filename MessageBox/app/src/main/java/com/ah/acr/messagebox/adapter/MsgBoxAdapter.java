package com.ah.acr.messagebox.adapter;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgWithAddress;
import com.ah.acr.messagebox.util.AvatarHelper;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MsgBoxAdapter extends ListAdapter<MsgWithAddress, MsgBoxAdapter.MsgViewHolder> {

    private static final int AVATAR_SIZE_DP = 48;

    public interface OnMsgClickListener {
        void onMessageClick(MsgEntity msg);
        void onMsgDeleteClick(MsgEntity msg);
        void onLongClick();
    }

    private final OnMsgClickListener onMsgClickListener;
    private boolean isCheckMode = false;
    private final Set<Integer> checkedIds = new HashSet<>();
    private Map<String, Integer> mUnsentMap = new HashMap<>();

    public MsgBoxAdapter(OnMsgClickListener onMsgClickListener) {
        super(new MsgDiffCallback());
        this.onMsgClickListener = onMsgClickListener;
    }

    public void setUnsentMap(Map<String, Integer> unsentMap) {
        this.mUnsentMap = unsentMap != null ? unsentMap : new HashMap<>();
        notifyDataSetChanged();
    }

    public void setCheckMode(boolean checkMode) {
        this.isCheckMode = checkMode;
        if (!checkMode) checkedIds.clear();
        notifyDataSetChanged();
    }

    public boolean isCheckMode() { return isCheckMode; }

    public Set<Integer> getCheckedIds() { return checkedIds; }

    public void checkAll() {
        for (int i = 0; i < getCurrentList().size(); i++) {
            checkedIds.add(getCurrentList().get(i).getMsg().getId());
        }
        notifyDataSetChanged();
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
        holder.bind(getItem(position), onMsgClickListener, isCheckMode, checkedIds, mUnsentMap);
    }

    public static class MsgViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;

        // ⭐ Avatar: FrameLayout + ImageView + TextView fallback
        private final FrameLayout layoutAvatar;
        private final ImageView imgAvatar;
        private final TextView textAvatar;

        private final TextView textName;
        private final TextView textLastMsg;
        private final TextView textTime;
        private final TextView textDirection;
        private final TextView unsentBadge;
        private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());

        public MsgViewHolder(View itemView) {
            super(itemView);
            checkBox      = itemView.findViewById(R.id.checkbox_msg);

            // Avatar views
            layoutAvatar  = itemView.findViewById(R.id.layout_chat_avatar);
            imgAvatar     = itemView.findViewById(R.id.img_chat_avatar);
            textAvatar    = itemView.findViewById(R.id.text_chat_avatar);

            textName      = itemView.findViewById(R.id.text_chat_name);
            textLastMsg   = itemView.findViewById(R.id.text_chat_last_msg);
            textTime      = itemView.findViewById(R.id.text_chat_time);
            textDirection = itemView.findViewById(R.id.text_chat_direction);
            unsentBadge   = itemView.findViewById(R.id.text_unsent_badge);
        }

        public void bind(MsgWithAddress item, OnMsgClickListener listener,
                         boolean isCheckMode, Set<Integer> checkedIds,
                         Map<String, Integer> unsentMap) {
            int msgId = item.getMsg().getId();

            // 체크박스
            checkBox.setVisibility(isCheckMode ? View.VISIBLE : View.GONE);
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(checkedIds.contains(msgId));
            checkBox.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) checkedIds.add(msgId);
                else checkedIds.remove(msgId);
            });

            // 연락처 정보
            String imei = item.getMsg().getCodeNum();
            String nickname = null;
            String avatarPath = null;

            if (item.getAddress() != null) {
                nickname = item.getAddress().getNumbersNic();
                avatarPath = item.getAddress().getAvatarPath();  // ⭐ NEW
            }

            // 표시 이름
            String displayName;
            if (nickname != null && !nickname.trim().isEmpty()) {
                displayName = nickname;
            } else if (imei != null && !imei.trim().isEmpty()) {
                displayName = imei;
            } else {
                displayName = "?";
            }
            textName.setText(displayName);

            // ⭐ 아바타 생성 (AvatarHelper 사용)
            try {
                Bitmap avatarBitmap = AvatarHelper.loadOrCreate(
                        itemView.getContext(),
                        imei,
                        nickname,
                        avatarPath,
                        AVATAR_SIZE_DP
                );
                imgAvatar.setImageBitmap(avatarBitmap);
                textAvatar.setVisibility(View.GONE);
            } catch (Exception e) {
                // Fallback: TextView with initial
                imgAvatar.setImageDrawable(null);
                textAvatar.setVisibility(View.VISIBLE);
                textAvatar.setText(
                        AvatarHelper.getInitial(imei, nickname)
                );
            }

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

            // 미전송 배지
            String codeNum = item.getMsg().getCodeNum();
            int unsentCount = (unsentMap != null && unsentMap.containsKey(codeNum))
                    ? unsentMap.get(codeNum) : 0;
            if (unsentCount > 0 && !isCheckMode) {
                unsentBadge.setVisibility(View.VISIBLE);
                unsentBadge.setText(String.valueOf(unsentCount));
            } else {
                unsentBadge.setVisibility(View.GONE);
            }

            // 클릭
            itemView.setOnClickListener(v -> {
                if (isCheckMode) {
                    boolean newState = !checkBox.isChecked();
                    checkBox.setChecked(newState);
                } else {
                    if (listener != null) listener.onMessageClick(item.getMsg());
                }
            });

            // 롱클릭
            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onLongClick();
                return true;
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
