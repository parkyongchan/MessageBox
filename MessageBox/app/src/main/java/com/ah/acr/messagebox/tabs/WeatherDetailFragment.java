package com.ah.acr.messagebox.tabs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.WeatherStore;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** [CLIMATE] 날씨 상세 페이지. 지도(내 위치) + 수신 이력(날짜별) + 선택 항목의 현재/7일 예보. */
public class WeatherDetailFragment extends DialogFragment {

    private MapView mMap;
    private android.widget.LinearLayout mForecast;
    private android.widget.TextView mCurrent;
    private android.widget.LinearLayout mHistoryHolder;   // 이력 항목 담는 곳 (동적)
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    // 내 위치
    private double mMyLat = Double.NaN, mMyLon = Double.NaN;
    private Marker mMyMarker;
    private android.location.LocationManager mLocMgr;
    private android.location.LocationListener mLocListener;

    private List<WeatherStore.Weather> mHistory;
    private WeatherStore.Weather mSelected;

    public static WeatherDetailFragment newInstance() {
        return new WeatherDetailFragment();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_weather_detail, container, false);

        mMap = root.findViewById(R.id.wd_map);
        mForecast = root.findViewById(R.id.wd_forecast);
        mCurrent = root.findViewById(R.id.wd_current);
        mHistoryHolder = root.findViewById(R.id.wd_history) instanceof android.widget.LinearLayout
                ? (android.widget.LinearLayout) root.findViewById(R.id.wd_history) : null;

        // 지도 초기화 (Configuration은 앱에서 이미 로드됨)
        mMap.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        mMap.setMultiTouchControls(true);
        mMap.setBuiltInZoomControls(false);
        mMap.getController().setZoom(13.0);

        root.findViewById(R.id.wd_close).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.wd_clear).setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(getContext())
                .setMessage("Clear all weather records?")
                .setPositiveButton("Clear All", (d, wch) -> {
                    WeatherStore.clearAndPersist(getContext().getApplicationContext());
                    mHistory = WeatherStore.getAll();
                    mSelected = null;
                    renderDetail();
                    renderWeatherOnMap();
                    androidx.recyclerview.widget.RecyclerView rv = getView() != null ? getView().findViewById(R.id.wd_history) : null;
                    if (rv != null && rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        // 이력 로드 (전부, 최신순)
        mHistory = WeatherStore.getAll();
        buildHistoryList(root);

        // 최신 것 기본 선택
        if (!mHistory.isEmpty()) select(mHistory.get(0));

        startMyLocation();
        return root;
    }

    /** 이력 항목을 History RecyclerView 자리(LinearLayout으로 대체 불가 시 별도)에 표시.
     *  여기서는 wd_history가 RecyclerView라 어댑터 대신 간단히 처리하기 위해
     *  상세 영역 위에 항목 텍스트를 동적 생성하는 대신, RecyclerView에 간이 어댑터 연결. */
    private void buildHistoryList(View root) {
        androidx.recyclerview.widget.RecyclerView rv = root.findViewById(R.id.wd_history);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        rv.setAdapter(new HistoryAdapter());
    }

    private void select(WeatherStore.Weather w) {
        mSelected = w;
        renderDetail();
        renderWeatherOnMap();
    }

    private void renderDetail() {
        WeatherStore.Weather w = mSelected;
        if (w == null) { mCurrent.setText(""); mForecast.removeAllViews(); return; }
        StringBuilder c = new StringBuilder();
        String _wc = com.ah.acr.messagebox.WeatherStore.currentText(w);
        c.append(w.marine ? "Marine Weather" : "Land Weather");
        if (!_wc.isEmpty()) c.append("  \u00b7  ").append(_wc);
        c.append("\n");
        c.append(String.format(Locale.US, "Location: %.4f, %.4f\n", w.lat(), w.lon()));
        c.append("Received: ").append(sdf.format(new java.util.Date(w.recvAt))).append("\n\n");
        if (w.marine) {
            f(c, "Wave", w, "WH", "m"); f(c, "Wave Dir", w, "WVD", "\u00b0");
            f(c, "Wave Period", w, "WP", "s"); f(c, "SST", w, "SST", "\u00b0C");
            f(c, "Temp", w, "T", "\u00b0C"); f(c, "Wind", w, "WS", ""); f(c, "Wind Dir", w, "WD", "\u00b0");
        } else {
            f(c, "Temp", w, "T", "\u00b0C"); f(c, "Feels", w, "FL", "\u00b0C");
            f(c, "Precip", w, "P", "mm"); f(c, "Wind", w, "WS", ""); f(c, "Wind Dir", w, "WD", "\u00b0");
        }
        mCurrent.setText(c.toString().trim());

        mForecast.removeAllViews();
        if (w.forecast7.isEmpty()) {
            android.widget.TextView t = new android.widget.TextView(getContext());
            t.setText("No forecast"); t.setTextColor(0xFF95B0D4); mForecast.addView(t);
        } else {
            for (WeatherStore.Day d : w.forecast7) {
                android.widget.TextView t = new android.widget.TextView(getContext());
                String _dwc = com.ah.acr.messagebox.WeatherStore.wcodeText(d.wcode);
                t.setText(String.format(Locale.US, "%s   Max %.0f\u00b0  Min %.0f\u00b0  Rain %.0fmm  Wind %.0f%s",
                        d.date, d.tmax, d.tmin, d.rain, d.wmax, _dwc.isEmpty() ? "" : "   " + _dwc));
                t.setTextColor(0xFFC5D6EC); t.setTextSize(13f); t.setPadding(0, 8, 0, 8);
                mForecast.addView(t);
            }
        }
    }

    private void f(StringBuilder sb, String label, WeatherStore.Weather w, String key, String unit) {
        String v = w.fields.get(key);
        if (v == null) return;
        sb.append(label).append(" ").append(v).append(unit).append("\n");
    }

    private final java.util.List<Polygon> mWxCircles = new java.util.ArrayList<>();
    private void renderWeatherOnMap() {
        if (mMap == null || mSelected == null) return;
        for (Polygon c : mWxCircles) mMap.getOverlays().remove(c);
        mWxCircles.clear();
        double lat = mSelected.lat(), lon = mSelected.lon();
        if (lat != 0 || lon != 0) {
            Polygon circle = new Polygon(mMap);
            circle.setPoints(Polygon.pointsAsCircle(new GeoPoint(lat, lon), 3000.0));
            int fill = mSelected.marine ? 0x330077CC : 0x3300C9FF;
            int stroke = mSelected.marine ? 0xFF0077CC : 0xFF00C9FF;
            circle.getFillPaint().setColor(fill);
            circle.getOutlinePaint().setColor(stroke);
            circle.getOutlinePaint().setStrokeWidth(3f);
            // 원 클릭 시 요약 정보
            StringBuilder ti = new StringBuilder(mSelected.marine ? "Marine" : "Land");
            String _t = mSelected.fields.get("T");
            String _ws = mSelected.fields.get("WS");
            if (_t != null) ti.append("  Temp ").append(_t).append("\u00b0");
            if (_ws != null) ti.append("  Wind ").append(_ws);
            circle.setTitle(ti.toString());
            circle.setOnClickListener((polygon, mapView, eventPos) -> {
                polygon.setInfoWindow(new org.osmdroid.views.overlay.infowindow.BasicInfoWindow(
                        org.osmdroid.library.R.layout.bonuspack_bubble, mapView));
                polygon.showInfoWindow();
                return true;
            });
            mWxCircles.add(circle);
            mMap.getOverlays().add(circle);
            mMap.getController().setCenter(new GeoPoint(lat, lon));
        }
        mMap.invalidate();
    }

    // 내 위치 (폰 GPS)
    private void startMyLocation() {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                    android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            mLocMgr = (android.location.LocationManager) requireContext()
                    .getSystemService(android.content.Context.LOCATION_SERVICE);
            android.location.Location last = mLocMgr.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            if (last == null) last = mLocMgr.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            if (last != null) { mMyLat = last.getLatitude(); mMyLon = last.getLongitude(); drawMyLocation(); }
            mLocListener = new android.location.LocationListener() {
                @Override public void onLocationChanged(@NonNull android.location.Location l) {
                    mMyLat = l.getLatitude(); mMyLon = l.getLongitude(); drawMyLocation();
                }
                @Override public void onProviderEnabled(@NonNull String p) {}
                @Override public void onProviderDisabled(@NonNull String p) {}
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
            };
            mLocMgr.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 5000, 10, mLocListener);
        } catch (Exception ignore) {}
    }

    private void drawMyLocation() {
        if (mMap == null || Double.isNaN(mMyLat)) return;
        if (mMyMarker == null) {
            mMyMarker = new Marker(mMap);
            mMyMarker.setTitle("My Location");
            mMap.getOverlays().add(mMyMarker);
        }
        mMyMarker.setPosition(new GeoPoint(mMyLat, mMyLon));
        mMap.invalidate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mLocMgr != null && mLocListener != null) {
            try { mLocMgr.removeUpdates(mLocListener); } catch (Exception ignore) {}
        }
    }

    // 이력 어댑터 (날짜순, 클릭 시 select)
    private class HistoryAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<HistoryAdapter.HVH> {
        @NonNull @Override
        public HVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.widget.TextView tv = new android.widget.TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setPadding(24, 20, 24, 20);
            tv.setTextColor(0xFFC5D6EC);
            tv.setTextSize(13f);
            return new HVH(tv);
        }
        @Override
        public void onBindViewHolder(@NonNull HVH h, int position) {
            WeatherStore.Weather w = mHistory.get(position);
            String t = w.fields.get("T");
            h.tv.setText(sdf.format(new java.util.Date(w.recvAt)) + "   "
                    + (w.marine ? "Marine" : "Land") + (t != null ? "  " + t + "\u00b0" : ""));
            h.tv.setBackgroundColor(w == mSelected ? 0xFF123859 : 0x00000000);
            h.tv.setOnClickListener(v -> { select(w); notifyDataSetChanged(); });
            h.tv.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(getContext())
                    .setMessage("Delete this record?")
                    .setPositiveButton("Delete", (d, wch) -> {
                        WeatherStore.remove(getContext().getApplicationContext(), w.recvAt);
                        mHistory = WeatherStore.getAll();
                        if (mSelected == w) { mSelected = mHistory.isEmpty() ? null : mHistory.get(0); renderDetail(); renderWeatherOnMap(); }
                        notifyDataSetChanged();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            });
        }
        @Override public int getItemCount() { return mHistory != null ? mHistory.size() : 0; }
        class HVH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            final android.widget.TextView tv;
            HVH(android.widget.TextView v) { super(v); tv = v; }
        }
    }
}
