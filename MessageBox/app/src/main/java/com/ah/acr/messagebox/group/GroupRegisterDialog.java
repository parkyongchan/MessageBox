package com.ah.acr.messagebox.group;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.MainActivity;
import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.AddressViewModelFactory;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.fragment.app.FragmentActivity;

/**
 * 그룹 등록 다이얼로그.
 *
 * 연락처(체크박스 다중선택)로 구성원 선택 → "등록 전송"
 *   → MsgEntity(받는이=그룹번호10자리, 타이틀=~M:<msgId>:address, 본문=구성원들) 보낼편지함 추가
 *   → 기존 "Send pending"으로 위성 송신 → 서버 ~A: 회신 시 GroupStore 확정.
 */
public class GroupRegisterDialog extends Dialog {

    private final FragmentActivity activity;
    private final GroupStore store;
    private GroupContactAdapter adapter;
    private GroupStore.Group editing;   // 수정 모드면 기존 그룹, 신규면 null

    private TextView tvGroupNo, tvCount, tvEmpty;
    private EditText etName;

    public GroupRegisterDialog(@NonNull FragmentActivity activity) {
        this(activity, null);
    }

    /** 수정 모드: 기존 그룹 전달 */
    public GroupRegisterDialog(@NonNull FragmentActivity activity, GroupStore.Group editGroup) {
        super(activity);
        this.activity = activity;
        this.store = new GroupStore(activity);
        this.editing = editGroup;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_group_register, null);
        setContentView(root);
        if (getWindow() != null) {
            getWindow().setLayout(
                    (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        tvGroupNo = root.findViewById(R.id.tv_group_no);
        etName = root.findViewById(R.id.et_group_name);
        tvCount = root.findViewById(R.id.tv_selected_count);
        tvEmpty = root.findViewById(R.id.tv_empty);
        RecyclerView rv = root.findViewById(R.id.rv_contacts);
        Button btnCancel = root.findViewById(R.id.btn_cancel);
        Button btnRegister = root.findViewById(R.id.btn_register);

        // 그룹번호 결정 (신규=다음 번호, 수정=기존)
        final String groupNo = (editing != null) ? editing.groupNo : store.nextGroupNo();
        tvGroupNo.setText(shortNo(groupNo) + "번");
        if (editing != null && editing.name != null) etName.setText(editing.name);

        // 연락처 어댑터
        adapter = new GroupContactAdapter();
        adapter.setOnSelectionChanged(count ->
                tvCount.setText("선택: " + count + "명"));
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        // 연락처 로드 (Room ViewModel)
        AddressViewModel addrVm = new ViewModelProvider(activity,
                new AddressViewModelFactory(activity.getApplication()))
                .get(AddressViewModel.class);
        addrVm.getAllAddress().observe(activity, list -> {
            List<AddressEntity> contacts = (list != null) ? list : new ArrayList<>();
            adapter.setItems(contacts);
            tvEmpty.setVisibility(contacts.isEmpty() ? View.VISIBLE : View.GONE);
            // 수정 모드: 기존 구성원 미리 체크
            if (editing != null) adapter.setPreselected(editing.members);
        });

        btnCancel.setOnClickListener(v -> dismiss());
        btnRegister.setOnClickListener(v -> doRegister(groupNo));
    }

    private void doRegister(String groupNo) {
        List<String> members = adapter.getSelectedImeis();
        if (members.isEmpty()) {
            toast("구성원을 1명 이상 선택하세요.");
            return;
        }

        // 메모 = 구성원 IMEI들 (',' 구분)
        String memo = TextUtils.join(",", members);
        // 10K 검증
        if (memo.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 10 * 1024) {
            toast("구성원이 너무 많습니다 (10K 초과).");
            return;
        }

        // ACK msgId 발급 (기존 단문 ACK 재사용)
        int msgId = ((MainActivity) activity).nextAckMsgId();
        String title = "~M:" + msgId + ":address";
        // 타이틀 20byte 한도 점검
        if (title.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 20) {
            toast("등록 ID가 너무 큽니다. 잠시 후 다시 시도하세요.");
            return;
        }

        // MsgEntity 생성 (받는이=그룹번호10자리, 타이틀=~M:<id>:address, 본문=구성원)
        MsgEntity newMsg = new MsgEntity(
                0, true, groupNo,
                title,
                memo,
                new Date(),
                null, null,
                false, false, false
        );

        MsgViewModel msgVm = new ViewModelProvider(activity).get(MsgViewModel.class);
        msgVm.insert(newMsg, success -> {
            if (Boolean.TRUE.equals(success)) {
                // GroupStore에 PENDING 저장 (ACK 오면 확정)
                GroupStore.Group g = (editing != null) ? editing : new GroupStore.Group();
                g.groupNo = groupNo;
                g.name = etName.getText().toString().trim();
                g.members = new ArrayList<>(members);
                g.confirmed = false;
                g.pendingMsgId = msgId;
                store.upsert(g);

                activity.runOnUiThread(() -> {
                    toast(shortNo(groupNo) + "번 그룹 등록 전송 대기. 'Send pending'으로 전송하세요.");
                    dismiss();
                });
            }
            return null;
        });
    }

    private String shortNo(String groupNo) {
        try { return String.valueOf(Long.parseLong(groupNo)); }
        catch (Exception e) { return groupNo; }
    }

    private void toast(String s) {
        Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show();
    }
}