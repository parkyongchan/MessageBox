package com.ah.acr.messagebox.group;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;

import java.util.List;

/**
 * 그룹 관리 목록 다이얼로그.
 * 등록된 그룹들(✓확정/⏳대기) 표시 + 추가/재전송/수정/삭제.
 */
public class GroupListDialog extends Dialog {

    private final FragmentActivity activity;
    private final GroupStore store;
    private GroupListAdapter adapter;
    private TextView tvEmpty;

    public GroupListDialog(@NonNull FragmentActivity activity) {
        super(activity);
        this.activity = activity;
        this.store = new GroupStore(activity);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_group_list, null);
        setContentView(root);
        if (getWindow() != null) {
            getWindow().setLayout(
                    (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        tvEmpty = root.findViewById(R.id.tv_empty_groups);
        RecyclerView rv = root.findViewById(R.id.rv_groups);
        Button btnAdd = root.findViewById(R.id.btn_add_group);
        Button btnClose = root.findViewById(R.id.btn_close);

        adapter = new GroupListAdapter(new GroupListAdapter.Listener() {
            @Override public void onResend(GroupStore.Group g) { resend(g); }
            @Override public void onEdit(GroupStore.Group g) { edit(g); }
            @Override public void onDelete(GroupStore.Group g) { confirmDelete(g); }
        });
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> {
            new GroupRegisterDialog(activity).show();
            dismiss();   // 등록 다이얼로그로 이동 (등록 후 다시 목록 열면 갱신됨)
        });
        btnClose.setOnClickListener(v -> dismiss());

        refresh();
    }

    private void refresh() {
        List<GroupStore.Group> groups = store.loadAll();
        adapter.setItems(groups);
        tvEmpty.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void resend(GroupStore.Group g) {
        GroupSender.send(activity, g, (ok, msg) -> {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            refresh();
        });
    }

    private void edit(GroupStore.Group g) {
        new GroupRegisterDialog(activity, g).show();
        dismiss();
    }

    private void confirmDelete(GroupStore.Group g) {
        new AlertDialog.Builder(getContext())
                .setTitle("그룹 삭제")
                .setMessage(g.shortNo() + "번 그룹을 삭제할까요?")
                .setPositiveButton("삭제", (d, w) -> {
                    store.delete(g.groupNo);
                    refresh();
                })
                .setNegativeButton("취소", null)
                .show();
    }
}