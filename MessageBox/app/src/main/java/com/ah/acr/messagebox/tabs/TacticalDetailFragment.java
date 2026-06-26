package com.ah.acr.messagebox.tabs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.TacticalParser;
import com.ah.acr.messagebox.TacticalMarkerIcon;
import com.ah.acr.messagebox.adapter.TacticalElementAdapter;
import com.ah.acr.messagebox.util.MapModeManager;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/** TAC 상세 — 그 장비 전술 그룹(마커/라인/메저)을 지도+표로 표시 (보기 전용). */
public class TacticalDetailFragment extends DialogFragment {

    private static final String ARG_PAYLOAD = "payload";
    private static final String ARG_FROM = "fromImei";
    private static final String[] AFFIL = {"HOSTILE","FRIENDLY","UNKNOWN","NEUTRAL","POI","ENGAGED","THREAT"};

    private MapView mMapView;
    private TacticalElementAdapter mAdapter;
    private TacticalParser.TacticalData mData;

    public static TacticalDetailFragment newInstance(String payload, String fromImei) {
        TacticalDetailFragment f = new TacticalDetailFragment();
        Bundle b = new Bundle();
        b.putString(ARG_PAYLOAD, payload);
        b.putString(ARG_FROM, fromImei);
        f.setArguments(b);
        return f;
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
        View root = inflater.inflate(R.layout.fragment_tactical_detail, container, false);

        String payload = getArguments() != null ? getArguments().getString(ARG_PAYLOAD) : null;
        String fromImei = getArguments() != null ? getArguments().getString(ARG_FROM) : "";

        android.widget.TextView title = root.findViewById(R.id.tac_detail_title);
        title.setText("TACTICAL — " + ((fromImei == null || fromImei.isEmpty()) ? "Control" : fromImei));

        root.findViewById(R.id.tac_detail_close).setOnClickListener(v -> dismiss());

        // 지도
        mMapView = root.findViewById(R.id.tac_detail_map);
        mMapView.setMultiTouchControls(true);
        mMapView.setBuiltInZoomControls(false);
        MapModeManager.applyToMapView(requireContext(), mMapView);
        mMapView.getController().setZoom(13.0);
        mMapView.getController().setCenter(new GeoPoint(37.5665, 126.9780));

        // 목록
        RecyclerView list = root.findViewById(R.id.tac_detail_list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new TacticalElementAdapter(row -> {
            // 행 클릭 → 지도 그 위치로
            mMapView.getController().animateTo(new GeoPoint(row.lat, row.lon));
            mMapView.getController().setZoom(16.0);
        });
        list.setAdapter(mAdapter);

        // 파싱 + 표시
        if (payload != null) {
            try {
                mData = TacticalParser.parse(payload);
                renderOnMap();
                buildRows();
            } catch (Exception e) {
                android.util.Log.e("TAC-DETAIL", "parse fail", e);
            }
        }
        return root;
    }

    private void renderOnMap() {
        if (mData == null || mMapView == null) return;
        List<GeoPoint> all = new ArrayList<>();

        // 마커
        for (TacticalParser.TMarker tm : mData.markers) {
            Marker mk = new Marker(mMapView);
            GeoPoint gp = new GeoPoint(tm.lat, tm.lon);
            mk.setPosition(gp);
            mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            android.graphics.drawable.Drawable ic =
                    TacticalMarkerIcon.make(getContext(), tm.type, tm.id, tm.unit, tm.place);
            if (ic != null) mk.setIcon(ic);
            String ident = TacticalParser.makeIdentifier(tm.type, tm.unit, tm.place, tm.id);
            String affil = (tm.type >= 0 && tm.type < AFFIL.length) ? AFFIL[tm.type] : "-";
            mk.setTitle(ident);
            mk.setSnippet(affil + "\n" + String.format(java.util.Locale.US, "%.5f, %.5f", tm.lat, tm.lon));
            mk.setOnMarkerClickListener((m, mv) -> {
                if (m.isInfoWindowShown()) m.closeInfoWindow();
                else {
                    org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(mv);
                    m.showInfoWindow();
                }
                return true;
            });
            mMapView.getOverlays().add(mk);
            all.add(gp);
        }

        // 라인 (청록)
        for (TacticalParser.TLine ln : mData.lines) {
            Polyline pl = new Polyline();
            List<GeoPoint> pts = new ArrayList<>();
            for (double[] p : ln.points) { GeoPoint g = new GeoPoint(p[0], p[1]); pts.add(g); all.add(g); }
            pl.setPoints(pts);
            pl.getOutlinePaint().setColor(0xFF00E5FF);
            pl.getOutlinePaint().setStrokeWidth(6f);
            mMapView.getOverlays().add(pl);
        }
        // 메저 (주황)
        for (TacticalParser.TMeasure ms : mData.measures) {
            Polyline pl = new Polyline();
            List<GeoPoint> pts = new ArrayList<>();
            for (double[] p : ms.points) { GeoPoint g = new GeoPoint(p[0], p[1]); pts.add(g); all.add(g); }
            pl.setPoints(pts);
            pl.getOutlinePaint().setColor(0xFFFF6D00);
            pl.getOutlinePaint().setStrokeWidth(5f);
            mMapView.getOverlays().add(pl);
        }

        mMapView.invalidate();
        if (!all.isEmpty()) {
            mMapView.post(() -> {
                try {
                    BoundingBox box = BoundingBox.fromGeoPoints(all);
                    mMapView.zoomToBoundingBox(box, true, 100);
                } catch (Exception ignore) {}
            });
        }
    }

    private void buildRows() {
        if (mData == null) return;
        List<TacticalElementAdapter.Row> rows = new ArrayList<>();
        for (TacticalParser.TMarker tm : mData.markers) {
            String ident = TacticalParser.makeIdentifier(tm.type, tm.unit, tm.place, tm.id);
            String affil = (tm.type >= 0 && tm.type < AFFIL.length) ? AFFIL[tm.type] : "-";
            rows.add(new TacticalElementAdapter.Row("MARKER", ident, affil, tm.lat, tm.lon));
        }
        for (TacticalParser.TLine ln : mData.lines) {
            if (!ln.points.isEmpty())
                rows.add(new TacticalElementAdapter.Row("LINE", "-", "-", ln.points.get(0)[0], ln.points.get(0)[1]));
        }
        for (TacticalParser.TMeasure ms : mData.measures) {
            if (!ms.points.isEmpty())
                rows.add(new TacticalElementAdapter.Row("MEAS", "-", "-", ms.points.get(0)[0], ms.points.get(0)[1]));
        }
        mAdapter.submit(rows);
    }

    @Override
    public void onResume() { super.onResume(); if (mMapView != null) mMapView.onResume(); }

    @Override
    public void onPause() { super.onPause(); if (mMapView != null) mMapView.onPause(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mMapView != null) { mMapView.onDetach(); mMapView = null; }
    }
}