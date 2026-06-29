package com.ah.acr.messagebox.group;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 그룹 목록 어댑터. 상태 뱃지(✓확정/⏳대기) + 재전송/수정/삭제.
 */
public class GroupListAdapter extends RecyclerView.Adapter<GroupListAdapter.VH> {

    public interface Listener {
        void onResend(GroupStore.Group g);
        void onEdit(GroupStore.Group g);
        void onDelete(GroupStore.Group g);
    }

    private final List<GroupStore.Group> items = new ArrayList<>();
    private final Listener listener;

    public GroupListAdapter(Listener l) {
        this.listener = l;
    }

    public void setItems(List<GroupStore.Group> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        GroupStore.Group g = items.get(position);

        h.tvLabel.setText(g.getDisplayLabel());
        h.tvCount.setText((g.members == null ? 0 : g.members.size()) + " members");

        if (g.confirmed) {
            h.tvStatus.setText("✓ Confirmed");
            h.tvStatus.setTextColor(0xFF00E5D1);
        } else {
            h.tvStatus.setText("⏳ Pending");
            h.tvStatus.setTextColor(0xFFFFB300);
        }

        h.btnResend.setOnClickListener(v -> { if (listener != null) listener.onResend(g); });
        h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(g); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(g); });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvLabel, tvStatus, tvCount;
        Button btnResend, btnEdit, btnDelete;
        VH(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tv_group_label);
            tvStatus = v.findViewById(R.id.tv_status);
            tvCount = v.findViewById(R.id.tv_member_count);
            btnResend = v.findViewById(R.id.btn_resend);
            btnEdit = v.findViewById(R.id.btn_edit_group);
            btnDelete = v.findViewById(R.id.btn_delete_group);
        }
    }
}
