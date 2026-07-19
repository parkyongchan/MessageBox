package com.ah.acr.messagebox.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.WeatherStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** [CLIMATE] 날씨 목록 어댑터. WeatherStore.Weather 표시. */
public class WeatherListAdapter extends RecyclerView.Adapter<WeatherListAdapter.VH> {

    public interface OnWeatherClickListener {
        void onWeatherClick(WeatherStore.Weather w);   // 항목 클릭 → 지도/상세
    }

    private final List<WeatherStore.Weather> items = new ArrayList<>();
    private final OnWeatherClickListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public WeatherListAdapter(OnWeatherClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<WeatherStore.Weather> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
        android.util.Log.d("WX-ADAPTER", "submit size=" + items.size());
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        android.util.Log.d("WX-ADAPTER", "onCreateVH");
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_weather, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        WeatherStore.Weather w = items.get(position);
        String _wc = com.ah.acr.messagebox.WeatherStore.currentText(w);
        h.title.setText((w.marine ? "Marine" : "Land") + (_wc.isEmpty() ? "" : " \u00b7 " + _wc));
        h.bar.setBackgroundColor(w.marine ? 0xFF0077CC : 0xFF00C9FF);

        // 요약: 육상/해상 다른 필드
        StringBuilder s = new StringBuilder();
        if (w.marine) {
            appendF(s, "Wave", w.fields.get("WH"), "m");
            appendF(s, "SST", w.fields.get("SST"), "\u00b0");
            appendF(s, "Wind", w.fields.get("WS"), "");
        } else {
            appendF(s, "Temp", w.fields.get("T"), "\u00b0");
            appendF(s, "Feels", w.fields.get("FL"), "\u00b0");
            appendF(s, "Rain", w.fields.get("P"), "mm");
            appendF(s, "Wind", w.fields.get("WS"), "");
        }
        if (!w.forecast7.isEmpty()) s.append("  \u00b7 ").append(w.forecast7.size()).append("-day");
        h.summary.setText(s.toString());
        h.time.setText(sdf.format(new java.util.Date(w.recvAt)) + "");

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onWeatherClick(w); });
        h.detail.setOnClickListener(v -> { if (listener != null) listener.onWeatherClick(w); });
    }

    private void appendF(StringBuilder s, String label, String val, String unit) {
        if (val == null) return;
        if (s.length() > 0) s.append("  ");
        s.append(label).append(" ").append(val).append(unit);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final View bar;
        final TextView title, summary, time;
        final View detail;
        VH(@NonNull View v) {
            super(v);
            bar = v.findViewById(R.id.wx_bar);
            title = v.findViewById(R.id.wx_title);
            summary = v.findViewById(R.id.wx_summary);
            time = v.findViewById(R.id.wx_time);
            detail = v.findViewById(R.id.wx_detail);
        }
    }
}
