package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgWithAddress;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MsgBoxAdapter extends ListAdapter<MsgWithAddress, MsgBoxAdapter.MsgViewHolder> {

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

    // 미전송 카운트 맵 업데이트
    public void setUnsentMap(Map<String, Integer> unsentMap) {
        this.mUnsentMap = unsentMap != null ? unsentMap : new HashMap<>();
        notifyDataSetChanged();
    }

    // 체크모드 토글
    public void setCheckMode(boolean checkMode) {
        this.isCheckMode = checkMode;
        if (!checkMode) checkedIds.clear();
        notifyDataSetChanged();
    }

    public boolean isCheckMode() { return isCheckMode; }

    // 체크된 ID 목록 반환
    public Set<Integer> getCheckedIds() { return checkedIds; }

    // 전체 선택
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
        private final TextView textAvatar;
        private final TextView textName;
        private final TextView textLastMsg;
        private final TextView textTime;
        private final TextView textDirection;
        private final TextView unsentBadge;
        private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());

        public MsgViewHolder(View itemView) {
            super(itemView);
            checkBox     = itemView.findViewById(R.id.checkbox_msg);
            textAvatar   = itemView.findViewById(R.id.text_chat_avatar);
            textName     = itemView.findViewById(R.id.text_chat_name);
            textLastMsg  = itemView.findViewById(R.id.text_chat_last_msg);
            textTime     = itemView.findViewById(R.id.text_chat_time);
            textDirection = itemView.findViewById(R.id.text_chat_direction);
            unsentBadge  = itemView.findViewById(R.id.text_unsent_badge);
        }

        public void bind(MsgWithAddress item, OnMsgClickListener listener,
                         boolean isCheckMode, Set<Integer> checkedIds,
                         Map<String, Integer> unsentMap) {
            int msgId = item.getMsg().getId();

            // 체크박스 표시/숨김
            checkBox.setVisibility(isCheckMode ? View.VISIBLE : View.GONE);
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(checkedIds.contains(msgId));
            checkBox.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) checkedIds.add(msgId);
                else checkedIds.remove(msgId);
            });

            // 연락처 이름
            String name = item.getAddress() != null && item.getAddress().getNumbersNic() != null
                    ? item.getAddress().getNumbersNic()
                    : item.getMsg().getCodeNum();
            textName.setText(name);

            // 이니셜 아바타
            String initial = (name != null && name.length() > 0)
                    ? name.substring(0, 1).toUpperCase() : "?";
            textAvatar.setText(initial);

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