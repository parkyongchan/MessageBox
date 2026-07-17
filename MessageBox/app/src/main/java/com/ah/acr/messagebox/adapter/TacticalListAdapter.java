package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.TacticalStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** TAC 목록 어댑터 — 장비별 최신 전술 1건씩 표시. */
public class TacticalListAdapter extends RecyclerView.Adapter<TacticalListAdapter.VH> {

    public interface OnTacticalClickListener {
        void onTacticalClick(TacticalStore.Entry entry);   // 항목 클릭 → 지도 이동
        void onTacticalDetail(TacticalStore.Entry entry);  // 상세 아이콘 클릭
    }

    private final List<TacticalStore.Entry> items = new ArrayList<>();
    private final OnTacticalClickListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public TacticalListAdapter(OnTacticalClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<TacticalStore.Entry> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tactical, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        TacticalStore.Entry e = items.get(position);
        String from = (e.data != null && e.data.fromImei != null && !e.data.fromImei.isEmpty())
                ? e.data.fromImei : "Control";
        // [SURV] 생존 데이터 판별 (markers 중 cat="S")
        boolean _isSurv = false;
        if (e.data != null && e.data.markers != null) {
            for (com.ah.acr.messagebox.TacticalParser.TMarker _m : e.data.markers) { if ("S".equals(_m.cat)) { _isSurv = true; break; } }
        }
        h.from.setText((_isSurv ? "SURVIVAL — " : "TACTICAL — ") + from);

        int mCnt = (e.data != null && e.data.markers != null) ? e.data.markers.size() : 0;
        int lCnt = (e.data != null && e.data.lines != null) ? e.data.lines.size() : 0;
        int rCnt = (e.data != null && e.data.measures != null) ? e.data.measures.size() : 0;
        String _sum = "markers " + mCnt + ", lines " + lCnt + ", measures " + rCnt;
        if (_isSurv) _sum += "\n\u25B6 TAP TWICE ON MAP TO OPEN GUIDE";
        h.summary.setText(_sum);
        // [SURV-color] \ub300\ud45c survType \uc0c9\uc73c\ub85c \uc138\ub85c \ubc14 (\uc704\ud5d8 \uc6b0\uc120)
        if (h.bar != null) {
            int repType = -1; boolean disaster = false;
            if (e.data != null && e.data.markers != null) {
                for (com.ah.acr.messagebox.TacticalParser.TMarker _m : e.data.markers) {
                    if (_m.survDisaster >= 0) disaster = true;
                    if (_m.survType == 4) { repType = 4; break; }
                    if (repType < 0 && _m.survType >= 0) repType = _m.survType;
                }
            }
            if (repType >= 0) h.bar.setBackgroundColor(
                com.ah.acr.messagebox.TacticalMarkerIcon.survColor(repType, disaster));
        }

        h.time.setText(sdf.format(new java.util.Date(e.recvAt)));

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onTacticalClick(e); });
        h.detail.setOnClickListener(v -> { if (listener != null) listener.onTacticalDetail(e); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView from, summary, time;
        final View bar;
        final ImageView detail;
        VH(@NonNull View v) {
            super(v);
            from = v.findViewById(R.id.tac_item_from);
            summary = v.findViewById(R.id.tac_item_summary);
            time = v.findViewById(R.id.tac_item_time);
            detail = v.findViewById(R.id.tac_item_detail);
            bar = v.findViewById(R.id.tac_item_bar);
        }
    }
}