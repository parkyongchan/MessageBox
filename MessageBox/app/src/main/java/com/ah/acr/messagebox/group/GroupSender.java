package com.ah.acr.messagebox.group;

import android.text.TextUtils;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ah.acr.messagebox.MainActivity;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 그룹 등록/재전송 공통 전송 헬퍼.
 *
 * group(groupNo/name/members 세팅됨)을 받아:
 *   msgId 발급 → ~M:<id>:address 전송(보낼편지함) → GroupStore에 PENDING 저장(confirmed=false).
 * 등록 다이얼로그와 목록의 재전송이 동일 경로를 탄다.
 */
public class GroupSender {

    public interface Result {
        void onResult(boolean ok, String message);
    }

    public static void send(FragmentActivity activity, GroupStore.Group group, Result cb) {
        if (group.members == null || group.members.isEmpty()) {
            cb.onResult(false, "구성원이 없습니다.");
            return;
        }
        String memo = TextUtils.join(",", group.members);
        if (memo.getBytes(StandardCharsets.UTF_8).length > 10 * 1024) {
            cb.onResult(false, "구성원이 너무 많습니다 (10K 초과).");
            return;
        }
        int msgId = ((MainActivity) activity).nextAckMsgId();
        String title = "~M:" + msgId + ":address";
        if (title.getBytes(StandardCharsets.UTF_8).length > 20) {
            cb.onResult(false, "등록 ID가 너무 큽니다. 잠시 후 다시 시도하세요.");
            return;
        }

        MsgEntity newMsg = new MsgEntity(
                0, true, group.groupNo,
                title, memo,
                new Date(), null, null,
                false, false, false
        );

        final int fMsgId = msgId;
        MsgViewModel msgVm = new ViewModelProvider(activity).get(MsgViewModel.class);
        GroupStore store = new GroupStore(activity);

        msgVm.insert(newMsg, success -> {
            if (Boolean.TRUE.equals(success)) {
                group.confirmed = false;        // 재전송이면 다시 대기 상태
                group.pendingMsgId = fMsgId;
                store.upsert(group);
                activity.runOnUiThread(() -> cb.onResult(true,
                        group.shortNo() + "번 그룹 전송 대기. 'Send pending'으로 전송하세요."));
            } else {
                activity.runOnUiThread(() -> cb.onResult(false, "전송 저장 실패"));
            }
            return null;
        });
    }
}