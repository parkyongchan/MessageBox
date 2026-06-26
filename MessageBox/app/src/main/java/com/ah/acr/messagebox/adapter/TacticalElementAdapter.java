package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ah.acr.messagebox.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** TAC 상세 — 전술 그룹의 요소(마커/라인/메저)를 표 행으로. */
public class TacticalElementAdapter extends RecyclerView.Adapter<TacticalElementAdapter.VH> {

    public static class Row {
        public String cls;     // MARKER / LINE / MEAS
        public String id;      // 마커 id 또는 "-"
        public String affil;   // 소속명 또는 "-"
        public double lat, lon;
        public Row(String cls, String id, String affil, double lat, double lon) {
            this.cls = cls; this.id = id; this.affil = affil; this.lat = lat; this.lon = lon;
        }
    }

    public interface OnRowClick { void onRowClick(Row row); }

    private final List<Row> rows = new ArrayList<>();
    private final OnRowClick listener;

    public TacticalElementAdapter(OnRowClick listener) { this.listener = listener; }

    public void submit(List<Row> list) {
        rows.clear();
        if (list != null) rows.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tactical_element, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = rows.get(position);
        h.cls.setText(r.cls);
        h.id.setText(r.id);
        h.affil.setText(r.affil);
        h.lat.setText(String.format(Locale.US, "%.5f", r.lat));
        h.lon.setText(String.format(Locale.US, "%.5f", r.lon));
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onRowClick(r); });
    }

    @Override
    public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView cls, id, affil, lat, lon;
        VH(@NonNull View v) {
            super(v);
            cls = v.findViewById(R.id.el_class);
            id = v.findViewById(R.id.el_id);
            affil = v.findViewById(R.id.el_affil);
            lat = v.findViewById(R.id.el_lat);
            lon = v.findViewById(R.id.el_lon);
        }
    }
}