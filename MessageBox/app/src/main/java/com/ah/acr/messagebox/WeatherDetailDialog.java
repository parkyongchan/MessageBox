package com.ah.acr.messagebox;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import java.util.Locale;

/** [CLIMATE] 날씨 상세 다이얼로그. 육상/해상별 현재 정보 + 7일 예보. */
public class WeatherDetailDialog extends DialogFragment {

    private static WeatherStore.Weather sTarget;   // 클릭한 날씨 (간단 전달)

    public static WeatherDetailDialog newInstance(WeatherStore.Weather w) {
        sTarget = w;
        return new WeatherDetailDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        if (d.getWindow() != null) d.getWindow().requestFeature(android.view.Window.FEATURE_NO_TITLE);
        return d;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle b) {
        View root = inflater.inflate(R.layout.dialog_weather_detail, container, false);
        WeatherStore.Weather w = sTarget;

        TextView title = root.findViewById(R.id.wxd_title);
        TextView loc = root.findViewById(R.id.wxd_loc);
        TextView current = root.findViewById(R.id.wxd_current);
        LinearLayout forecast = root.findViewById(R.id.wxd_forecast);
        TextView close = root.findViewById(R.id.wxd_close);

        if (w == null) {
            title.setText("No weather data");
            close.setOnClickListener(v -> dismiss());
            return root;
        }

        title.setText(w.marine ? "Marine Weather" : "Land Weather");
        loc.setText(String.format(Locale.US, "Location: %.4f, %.4f", w.lat(), w.lon()));

        // 현재 정보 (육상/해상 다르게)
        StringBuilder c = new StringBuilder();
        if (w.marine) {
            line(c, "Wave", w, "WH", "m");
            line(c, "Wave Dir", w, "WVD", "\u00b0");
            line(c, "Wave Period", w, "WP", "s");
            line(c, "SST", w, "SST", "\u00b0C");
            line(c, "Temp", w, "T", "\u00b0C");
            line(c, "Wind", w, "WS", "");
            line(c, "Wind Dir", w, "WD", "\u00b0");
        } else {
            line(c, "Temp", w, "T", "\u00b0C");
            line(c, "Feels", w, "FL", "\u00b0C");
            line(c, "Precip", w, "P", "mm");
            line(c, "Wind", w, "WS", "");
            line(c, "Wind Dir", w, "WD", "\u00b0");
        }
        current.setText(c.toString().trim());

        // 7일 예보
        if (w.forecast7.isEmpty()) {
            TextView none = new TextView(getContext());
            none.setText("No forecast");
            none.setTextColor(0xFF95B0D4);
            forecast.addView(none);
        } else {
            for (WeatherStore.Day day : w.forecast7) {
                TextView row = new TextView(getContext());
                row.setText(String.format(Locale.US,
                        "%s   Max %.0f\u00b0  Min %.0f\u00b0  Rain %.0fmm  Wind %.0f",
                        day.date, day.tmax, day.tmin, day.rain, day.wmax));
                row.setTextColor(0xFFC5D6EC);
                row.setTextSize(13f);
                row.setPadding(0, 8, 0, 8);
                forecast.addView(row);
            }
        }

        close.setOnClickListener(v -> dismiss());
        return root;
    }

    private void line(StringBuilder sb, String label, WeatherStore.Weather w, String key, String unit) {
        String v = w.fields.get(key);
        if (v == null) return;
        sb.append(label).append(" ").append(v).append(unit).append("\n");
    }
}
