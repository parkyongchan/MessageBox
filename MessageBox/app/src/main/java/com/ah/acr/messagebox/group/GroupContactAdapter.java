package com.ah.acr.messagebox.group;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.AddressEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 그룹 구성원 선택용 연락처 어댑터 (체크박스 다중선택).
 * 기존 AddressEntity 목록을 받아 IMEI(numbers)를 선택.
 */
public class GroupContactAdapter extends RecyclerView.Adapter<GroupContactAdapter.VH> {

    private final List<AddressEntity> items = new ArrayList<>();
    private final Set<String> selectedImeis = new HashSet<>();   // 선택된 IMEI
    private OnSelectionChanged listener;

    public interface OnSelectionChanged {
        void onChanged(int count);
    }

    public void setOnSelectionChanged(OnSelectionChanged l) {
        this.listener = l;
    }

    /** 연락처 목록 세팅 */
    public void setItems(List<AddressEntity> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    /** 미리 선택할 IMEI들 (그룹 수정 시 기존 구성원) */
    public void setPreselected(List<String> imeis) {
        selectedImeis.clear();
        if (imeis != null) selectedImeis.addAll(imeis);
        notifyDataSetChanged();
        notifyCount();
    }

    /** 선택된 IMEI 목록 반환 */
    public List<String> getSelectedImeis() {
        return new ArrayList<>(selectedImeis);
    }

    public int getSelectedCount() {
        return selectedImeis.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_contact, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AddressEntity a = items.get(position);
        String imei = a.getNumbers();
        String nick = a.getNumbersNic();

        h.tvNick.setText((nick != null && !nick.trim().isEmpty()) ? nick : imei);
        h.tvImei.setText(imei);
        h.cb.setChecked(selectedImeis.contains(imei));

        h.itemView.setOnClickListener(v -> {
            if (selectedImeis.contains(imei)) {
                selectedImeis.remove(imei);
                h.cb.setChecked(false);
            } else {
                selectedImeis.add(imei);
                h.cb.setChecked(true);
            }
            notifyCount();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void notifyCount() {
        if (listener != null) listener.onChanged(selectedImeis.size());
    }

    static class VH extends RecyclerView.ViewHolder {
        CheckBox cb;
        TextView tvNick, tvImei;
        VH(@NonNull View v) {
            super(v);
            cb = v.findViewById(R.id.cb_select);
            tvNick = v.findViewById(R.id.tv_nick);
            tvImei = v.findViewById(R.id.tv_imei);
        }
    }
}