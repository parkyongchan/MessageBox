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

/**
 * 채팅방 메시지 어댑터 (카카오톡 스타일)
 *
 * ⭐ 카카오톡 스타일:
 *   - 송신 (내 메시지): 우측 정렬, 노랑 말풍선 (#FEE500), 검정 글자
 *   - 수신 (상대 메시지): 좌측 정렬, 흰색 말풍선 (#FFFFFF), 검정 글자 + 아바타
 *
 *   말풍선 꼬리:
 *     - 송신: 우측 하단 각진 꼬리 (bottomRightRadius=4dp)
 *     - 수신: 좌측 상단 각진 꼬리 (topLeftRadius=4dp)
 */
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

        // Avatar: FrameLayout wrapping ImageView + TextView
        private final FrameLayout layoutAvatar;
        private final ImageView imgAvatar;
        private final TextView textAvatar;

        private final TextView textSender;
        private final TextView textTitle;
        private final TextView textMsg;
        private final ImageView imgPhoto;   // [3-D-2] 사진 썸네일

        // 좌측 시간 영역 (송신)
        private final LinearLayout layoutTimeLeft;
        private final ImageView imgPendingLeft;
        private final TextView textTimeLeft;

        // 우측 시간 (수신)
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
            imgPhoto       = view.findViewById(R.id.img_bubble_photo);

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
                avatarPath = item.getAddress().getAvatarPath();
            }

            // 표시 이름
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
                // ═══════════════════════════════════════════
                // 📤 송신: 우측 정렬 + 노랑 말풍선
                // ═══════════════════════════════════════════

                // 루트 우측 정렬
                layoutRoot.setGravity(android.view.Gravity.END);

                // 아바타 숨김
                layoutAvatar.setVisibility(View.GONE);

                // 발신자 이름 숨김
                textSender.setVisibility(View.GONE);

                // ⭐ 노랑 말풍선 배경
                layoutBox.setBackgroundResource(R.drawable.bg_bubble_send);

                // 글자: 검정 (노랑 배경 위)
                textMsg.setTextColor(0xFF000000);

                // 제목 색상 (노랑 위에 강조)
                textTitle.setTextColor(0xFFE74C3C);

                // 시간 좌측 표시
                layoutTimeLeft.setVisibility(View.VISIBLE);
                textTimeLeft.setText(time);
                textTimeRight.setVisibility(View.GONE);

                // 전송 상태 3단계: 미전송 / 보냄 ✓ / 전달됨 ✓✓
                // ⭐ v7: ACK 상태 기반 (ackState 0=없음/1=서버도착V/2=상대도착VV)
                //   ACK off면 ackState가 0으로 유지되어 V 안 뜸. isSend와 분리.
                int ackState = item.getMsg().getAckState();
                if (ackState >= 2) {
                    imgPendingLeft.setVisibility(View.GONE);
                    textTimeLeft.setText("\u2713\u2713 " + time);   // ✓✓ 상대도착
                    textTimeLeft.setTextColor(0xFF00B8A0);  // 민트
                } else if (ackState == 1) {
                    imgPendingLeft.setVisibility(View.GONE);
                    textTimeLeft.setText("\u2713 " + time);         // ✓ 서버도착
                    textTimeLeft.setTextColor(0xFF7A8FA8);  // 회색
                } else {
                    // ackState==0: V 없음 (ACK off거나 아직 서버확인 전) → 시간만
                    imgPendingLeft.setVisibility(View.GONE);
                    textTimeLeft.setText(time);
                    textTimeLeft.setTextColor(0xFF7A8FA8);  // 회색
                }

            } else {
                // ═══════════════════════════════════════════
                // 📥 수신: 좌측 정렬 + 흰색 말풍선
                // ═══════════════════════════════════════════

                // 루트 좌측 정렬
                layoutRoot.setGravity(android.view.Gravity.START);

                // 아바타 표시
                layoutAvatar.setVisibility(View.VISIBLE);
                textAvatar.setVisibility(View.GONE);

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

                // 발신자 이름 표시
                textSender.setVisibility(View.VISIBLE);
                textSender.setText(name);

                // ⭐ 흰색 말풍선 배경
                layoutBox.setBackgroundResource(R.drawable.bg_bubble_receive);

                // 글자: 검정 (흰색 배경 위)
                textMsg.setTextColor(0xFF000000);

                // 제목 색상 (흰 배경 위에 강조)
                textTitle.setTextColor(0xFF0066CC);

                // 시간 우측 표시
                layoutTimeLeft.setVisibility(View.GONE);
                imgPendingLeft.setVisibility(View.GONE);

                textTimeRight.setVisibility(View.VISIBLE);
                textTimeRight.setText(time);
                textTimeRight.setTextColor(0xFF7A8FA8);  // 회색
            }

            // 제목 (공통)
            String title = item.getMsg().getTitle();
            if (title != null && !title.trim().isEmpty()) {
                textTitle.setVisibility(View.VISIBLE);
                textTitle.setText(title);
            } else {
                textTitle.setVisibility(View.GONE);
            }

            // 본문 (공통)
            // [3-D-2 썸네일 분기] title이 [IMG]면 경로(msg)로 사진 미리보기, 아니면 텍스트
            String _bodyVal = item.getMsg().getMsg();
            String _titleVal = item.getMsg().getTitle();
            boolean _isImg = (_titleVal != null && _titleVal.startsWith("[IMG]") && _bodyVal != null && !_bodyVal.trim().isEmpty());
            // [mediaBubble] title로 종류 판정: [IMG]=썸네일+클릭, [FILE]/[VOICE]=아이콘텍스트+클릭. 경로/URI(content://) 둘 다 지원.
            boolean _isVoice = (_titleVal != null && _titleVal.startsWith("[VOICE]"));
            boolean _isFile  = (_titleVal != null && _titleVal.startsWith("[FILE]"));
            if (_isImg) {
                android.graphics.Bitmap _bmp = loadBitmapUniversal(imgPhoto.getContext(), _bodyVal);
                if (_bmp != null) {
                    imgPhoto.setImageBitmap(_bmp);
                    imgPhoto.setVisibility(View.VISIBLE);
                    textMsg.setVisibility(View.GONE);
                    final String _loc = _bodyVal;
                    imgPhoto.setOnClickListener(v -> openMediaUniversal(v.getContext(), _loc, "image/*"));
                } else {
                    imgPhoto.setVisibility(View.GONE);
                    textMsg.setVisibility(View.VISIBLE);
                    textMsg.setText("[IMG] " + _bodyVal);
                }
            } else if (_isVoice || _isFile) {
                imgPhoto.setVisibility(View.GONE);
                textMsg.setVisibility(View.VISIBLE);
                String _icon = _isVoice ? "\uD83C\uDFA4 " : "\uD83D\uDCC4 ";
                String _nameOnly = _bodyVal;
                int _sl = (_bodyVal != null) ? Math.max(_bodyVal.lastIndexOf('/'), _bodyVal.lastIndexOf("%2F")) : -1;
                if (_sl >= 0 && _bodyVal != null) _nameOnly = _bodyVal.substring(Math.min(_sl + 1, _bodyVal.length()));
                String _disp = (_titleVal != null && _titleVal.length() > (_isVoice ? 7 : 6))
                        ? _titleVal.substring(_isVoice ? 7 : 6) : _nameOnly;
                textMsg.setText(_icon + (_disp == null || _disp.isEmpty() ? (_isVoice ? "Voice" : "File") : _disp) + "  \u25B6");
                final String _loc2 = _bodyVal;
                final String _mime = _isVoice ? "audio/*" : "*/*";
                textMsg.setOnClickListener(v -> openMediaUniversal(v.getContext(), _loc2, _mime));
            } else {
                imgPhoto.setVisibility(View.GONE);
                textMsg.setVisibility(View.VISIBLE);
                textMsg.setText(_bodyVal != null ? _bodyVal : "");
            }
            // [copy] Long-press bubble text to copy
            final String _copyText = item.getMsg().getMsg() != null ? item.getMsg().getMsg() : "";
            textMsg.setOnLongClickListener(view -> {
                if (_copyText.isEmpty()) return false;
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        view.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("message", _copyText));
                android.widget.Toast.makeText(view.getContext(), "Copied", android.widget.Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    /** [mediaBubble] content:// URI 또는 파일경로 둘 다에서 Bitmap 로드 (수신=URI, 송신=경로). 실패 시 null. */
    private static android.graphics.Bitmap loadBitmapUniversal(android.content.Context ctx, String loc) {
        if (loc == null || loc.trim().isEmpty()) return null;
        try {
            if (loc.startsWith("content://")) {
                android.net.Uri u = android.net.Uri.parse(loc);
                try (java.io.InputStream is = ctx.getContentResolver().openInputStream(u)) {
                    return android.graphics.BitmapFactory.decodeStream(is);
                }
            } else {
                return android.graphics.BitmapFactory.decodeFile(loc);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** [mediaBubble] content:// URI 또는 파일경로를 시스템 앱으로 열기 (음성=재생, 파일/이미지=뷰어). */
    private static void openMediaUniversal(android.content.Context ctx, String loc, String mime) {
        if (loc == null || loc.trim().isEmpty()) {
            android.widget.Toast.makeText(ctx, "\uACBD\uB85C \uC5C6\uC74C", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.net.Uri uri;
            if (loc.startsWith("content://")) {
                uri = android.net.Uri.parse(loc);
            } else {
                java.io.File f = new java.io.File(loc);
                uri = androidx.core.content.FileProvider.getUriForFile(
                        ctx, ctx.getPackageName() + ".fileprovider", f);
            }
            android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            it.setDataAndType(uri, mime);
            it.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(it);
        } catch (Exception ex) {
            android.widget.Toast.makeText(ctx, "\uC5F4\uAE30 \uC2E4\uD328: " + ex.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
