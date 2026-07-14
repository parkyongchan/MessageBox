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
import com.ah.acr.messagebox.TacticalStore;
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
    private static final int[] SET_COLORS = {0xFF00E5FF, 0xFFFF6D00, 0xFFFFEB3B, 0xFF76FF03, 0xFFE040FB, 0xFFFF4081, 0xFF40C4FF, 0xFFB388FF};

    private MapView mMapView;
    private java.util.List<TacticalStore.Entry> mSets = new java.util.ArrayList<>();
    private java.util.List<TacticalStore.Entry> mAllSets = new java.util.ArrayList<>(); // 전체 백업(재생용)
    private int mPlayIndex = -1;
    private boolean mPlaying = false;
    private final android.os.Handler mPlayH = new android.os.Handler(android.os.Looper.getMainLooper()); // 그 장비 전술 이력(시간순)
    private TacticalElementAdapter mAdapter;
    // [S5-nav] 내 위치(폰 GPS) + 생존 안내
    private double mMyLat = Double.NaN, mMyLon = Double.NaN;
    private org.osmdroid.views.overlay.Marker mMyMarker;
    private org.osmdroid.views.overlay.Polyline mNavLine;
    private android.location.LocationManager mLocMgr;
    private android.location.LocationListener mLocListener;
    private boolean mTrackingOn = false;   // [S5-nav] 실시간 트래킹 on/off
    private android.widget.ImageButton mTrackBtn;
    // [S5-nav] 나침반: 자기센서 heading + 선택 표적
    private android.hardware.SensorManager mSensorMgr;
    private android.hardware.SensorEventListener mSensorListener;
    private float mHeading = Float.NaN;   // 폰이 향한 방위(0~360)
    private double mSelLat = Double.NaN, mSelLon = Double.NaN;   // 선택된 표적(나침반 대상)
    private final float[] mRotMat = new float[9];
    private final float[] mOrient = new float[3];
    private float[] mGravity, mGeomag;

    public static TacticalDetailFragment newInstance(String fromImei) {
        TacticalDetailFragment f = new TacticalDetailFragment();
        Bundle b = new Bundle();
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

                String fromImei = getArguments() != null ? getArguments().getString(ARG_FROM) : "";
        String fKey = (fromImei == null) ? "" : fromImei;
        for (TacticalStore.Entry e : TacticalStore.getAll()) {
            if (e.data == null) continue;
            String k = (e.data.fromImei == null) ? "" : e.data.fromImei;
            if (k.equals(fKey)) mSets.add(e);
        }
        java.util.Collections.sort(mSets, (x, y) -> Long.compare(x.recvAt, y.recvAt));
        mAllSets = new java.util.ArrayList<>(mSets);

        android.widget.TextView title = root.findViewById(R.id.tac_detail_title);
        title.setText("TACTICAL — " + ((fromImei == null || fromImei.isEmpty()) ? "Control" : fromImei));

        root.findViewById(R.id.tac_detail_close).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.tac_detail_export).setOnClickListener(v -> showExportDialog());
        root.findViewById(R.id.tac_detail_play).setOnClickListener(v -> togglePlay());
        root.findViewById(R.id.tac_detail_prev).setOnClickListener(v -> { stopPlay(); stepPlay(-1); });
        root.findViewById(R.id.tac_detail_next).setOnClickListener(v -> { stopPlay(); stepPlay(1); });
        // coord: 하단 목록 접기/펴기
        final View listContainer = root.findViewById(R.id.tac_detail_list_container);
        final android.widget.ImageButton coordBtn = root.findViewById(R.id.tac_detail_coord);
        final boolean[] showList = {true};
        coordBtn.setOnClickListener(v -> {
            showList[0] = !showList[0];
            listContainer.setVisibility(showList[0] ? View.VISIBLE : View.GONE);
            coordBtn.setColorFilter(showList[0] ? 0xFFFFEB3B : 0xFF95B0D4);
        });
        coordBtn.setColorFilter(0xFFFFEB3B); // 초기 ON
        setupDateBar(root);

        // 줌 인/아웃/fit
        root.findViewById(R.id.tac_detail_zoom_in).setOnClickListener(v -> {
            if (mMapView != null) mMapView.getController().zoomIn();
        });
        root.findViewById(R.id.tac_detail_zoom_out).setOnClickListener(v -> {
            if (mMapView != null) mMapView.getController().zoomOut();
        });
        root.findViewById(R.id.tac_detail_fit).setOnClickListener(v -> fitAll());
        // [S5-nav] 실시간 트래킹 토글
        mTrackBtn = root.findViewById(R.id.tac_detail_track);
        mTrackBtn.setColorFilter(0xFF95B0D4);
        mTrackBtn.setOnClickListener(v -> toggleTracking());

        // 온라인/오프라인 토글
        com.ah.acr.messagebox.util.MapModeToggleHelper.setup(
                root, requireContext(),
                newMode -> {
                    if (mMapView != null)
                        com.ah.acr.messagebox.util.MapModeManager.applyToMapView(requireContext(), mMapView);
                });

        // 지도
        mMapView = root.findViewById(R.id.tac_detail_map);
        mMapView.setMultiTouchControls(true);
        mMapView.setBuiltInZoomControls(false);
        MapModeManager.applyToMapView(requireContext(), mMapView);
        mMapView.getController().setZoom(13.0);
        mMapView.getController().setCenter(new GeoPoint(37.5665, 126.9780));

        startMyLocation();   // [S5-nav] 내 위치 수신 시작

        // 목록
        RecyclerView list = root.findViewById(R.id.tac_detail_list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new TacticalElementAdapter(row -> {
            // 행 클릭 → 지도 그 위치로
            mMapView.getController().animateTo(new GeoPoint(row.lat, row.lon));
            mMapView.getController().setZoom(16.0);
        });
        list.setAdapter(mAdapter);

                // 이력 표시 (그룹 트랙)
        try {
            renderHistory();
            buildRows();
        } catch (Exception e) {
            android.util.Log.e("TAC-DETAIL", "render fail", e);
        }
        return root;
    }

    // [S5-nav] 내 위치(폰 GPS) 수신 시작. 권한 있으면 마지막 위치 즉시 + 실시간 갱신.
    private void startMyLocation() {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                    android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("S5-NAV", "위치 권한 없음 - 내 위치 표시 불가");
                return;
            }
            mLocMgr = (android.location.LocationManager) requireContext()
                    .getSystemService(android.content.Context.LOCATION_SERVICE);
            // 1) 마지막 위치 즉시 (위급 상황 - 있으면 바로 사용)
            android.location.Location last = null;
            try { last = mLocMgr.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER); } catch (Exception ignore) {}
            if (last == null) { try { last = mLocMgr.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER); } catch (Exception ignore) {} }
            if (last != null) { mMyLat = last.getLatitude(); mMyLon = last.getLongitude(); drawMyMarker(); }
            // 2) 실시간 갱신
            mLocListener = new android.location.LocationListener() {
                @Override public void onLocationChanged(android.location.Location loc) {
                    mMyLat = loc.getLatitude(); mMyLon = loc.getLongitude(); drawMyMarker();
                    if (mTrackingOn && mMapView != null) mMapView.getController().animateTo(new GeoPoint(mMyLat, mMyLon));
                }
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
                @Override public void onStatusChanged(String p, int s, android.os.Bundle b) {}
            };
            try { mLocMgr.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 3000, 5, mLocListener); } catch (Exception ignore) {}
            try { mLocMgr.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 5000, 10, mLocListener); } catch (Exception ignore) {}
        } catch (Exception e) {
            android.util.Log.e("S5-NAV", "위치 수신 실패: " + e.getMessage());
        }
    }

    // [S5-nav] 나침반 센서 시작 (가속도+자기 → heading).
    private void startCompass() {
        try {
            if (mSensorMgr == null)
                mSensorMgr = (android.hardware.SensorManager) requireContext().getSystemService(android.content.Context.SENSOR_SERVICE);
            android.hardware.Sensor acc = mSensorMgr.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
            android.hardware.Sensor mag = mSensorMgr.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD);
            if (acc == null || mag == null) { android.util.Log.w("S5-NAV", "나침반 센서 없음"); return; }
            if (mSensorListener == null) {
                mSensorListener = new android.hardware.SensorEventListener() {
                    @Override public void onSensorChanged(android.hardware.SensorEvent e) {
                        if (e.sensor.getType() == android.hardware.Sensor.TYPE_ACCELEROMETER) mGravity = e.values.clone();
                        else if (e.sensor.getType() == android.hardware.Sensor.TYPE_MAGNETIC_FIELD) mGeomag = e.values.clone();
                        if (mGravity != null && mGeomag != null) {
                            if (android.hardware.SensorManager.getRotationMatrix(mRotMat, null, mGravity, mGeomag)) {
                                android.hardware.SensorManager.getOrientation(mRotMat, mOrient);
                                float deg = (float) Math.toDegrees(mOrient[0]);
                                mHeading = (deg + 360) % 360;
                                updateCompass();
                            }
                        }
                    }
                    @Override public void onAccuracyChanged(android.hardware.Sensor s, int a) {}
                };
            }
            mSensorMgr.registerListener(mSensorListener, acc, android.hardware.SensorManager.SENSOR_DELAY_UI);
            mSensorMgr.registerListener(mSensorListener, mag, android.hardware.SensorManager.SENSOR_DELAY_UI);
        } catch (Exception ex) { android.util.Log.e("S5-NAV", "나침반 시작 실패: " + ex.getMessage()); }
    }

    private void stopCompass() {
        if (mSensorMgr != null && mSensorListener != null) mSensorMgr.unregisterListener(mSensorListener);
    }

    // [S5-nav] 나침반 갱신: 선택 표적 대비 방향을 상단 안내 바에 표시.
    private void updateCompass() {
        if (Float.isNaN(mHeading) || Double.isNaN(mSelLat) || Double.isNaN(mMyLat)) return;
        if (getView() == null) return;
        double tb = com.ah.acr.messagebox.util.SurvivalNav.bearingDegrees(mMyLat, mMyLon, mSelLat, mSelLon);
        double rel = com.ah.acr.messagebox.util.SurvivalNav.relativeBearing(tb, mHeading);
        double dist = com.ah.acr.messagebox.util.SurvivalNav.distanceMeters(mMyLat, mMyLon, mSelLat, mSelLon);
        android.view.View bar = getView().findViewById(R.id.tac_nav_bar);
        android.widget.TextView arrow = getView().findViewById(R.id.tac_nav_arrow);
        android.widget.TextView hint = getView().findViewById(R.id.tac_nav_hint);
        android.widget.TextView distTv = getView().findViewById(R.id.tac_nav_dist);
        if (bar == null) return;
        bar.setVisibility(android.view.View.VISIBLE);
        // 화살표: 상대방위에 따라
        double a = Math.abs(rel);
        String arr;
        if (a <= 15) arr = "\u2191";           // ↑ 정면
        else if (a <= 75) arr = rel > 0 ? "\u2197" : "\u2196";  // ↗ ↖
        else if (a <= 135) arr = rel > 0 ? "\u2192" : "\u2190"; // → ←
        else arr = "\u2193";                   // ↓ 뒤
        int color = com.ah.acr.messagebox.util.SurvivalNav.onCourse(rel) ? 0xFF2ECC71 : (a > 135 ? 0xFFE74C3C : 0xFFFF9500);
        arrow.setText(arr);
        arrow.setTextColor(color);
        hint.setText(com.ah.acr.messagebox.util.SurvivalNav.steerHint(rel));
        hint.setTextColor(color);
        distTv.setText(com.ah.acr.messagebox.util.SurvivalNav.compass8(tb) + " · "
                + com.ah.acr.messagebox.util.SurvivalNav.formatDistance(dist) + " · 도보 "
                + com.ah.acr.messagebox.util.SurvivalNav.formatWalk(dist));
    }

    // [S5-nav] 실시간 트래킹 토글. ON=내 위치로 지도 이동+추적, 버튼 초록.
    private void toggleTracking() {
        mTrackingOn = !mTrackingOn;
        if (mTrackBtn != null) mTrackBtn.setColorFilter(mTrackingOn ? 0xFF2ECC71 : 0xFF95B0D4);
        if (mTrackingOn) {
            if (!Double.isNaN(mMyLat)) {
                mMapView.getController().animateTo(new GeoPoint(mMyLat, mMyLon));
                mMapView.getController().setZoom(17.0);
            } else {
                android.widget.Toast.makeText(getContext(), "내 위치 확인 중… (My location acquiring)", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    // [S5-nav] 내 위치 마커(파란 점) 그리기/갱신.
    private void drawMyMarker() {
        if (mMapView == null || Double.isNaN(mMyLat)) return;
        GeoPoint me = new GeoPoint(mMyLat, mMyLon);
        if (mMyMarker == null) {
            mMyMarker = new org.osmdroid.views.overlay.Marker(mMapView);
            mMyMarker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER);
            mMyMarker.setIcon(com.ah.acr.messagebox.TacticalMarkerIcon.makeMyLocation(getContext()));
            mMyMarker.setTitle("My Location");
            mMapView.getOverlays().add(mMyMarker);
        }
        mMyMarker.setPosition(me);
        mMapView.invalidate();
    }

    // [S5-nav] 내 위치 → 선택 표적 직선 라인. 기존 라인 있으면 갱신.
    private void drawNavLine(double tlat, double tlon) {
        if (mMapView == null || Double.isNaN(mMyLat)) return;
        if (mNavLine != null) mMapView.getOverlays().remove(mNavLine);
        mNavLine = new org.osmdroid.views.overlay.Polyline();
        java.util.List<GeoPoint> pts = new java.util.ArrayList<>();
        pts.add(new GeoPoint(mMyLat, mMyLon));
        pts.add(new GeoPoint(tlat, tlon));
        mNavLine.setPoints(pts);
        mNavLine.getOutlinePaint().setColor(0xFF2196F3);
        mNavLine.getOutlinePaint().setStrokeWidth(6f);
        mNavLine.getOutlinePaint().setPathEffect(new android.graphics.DashPathEffect(new float[]{20f, 15f}, 0f));
        mMapView.getOverlays().add(mNavLine);
        mMapView.invalidate();
    }

    private void renderHistory() {
        if (mSets.isEmpty() || mMapView == null) return;
        mMapView.getOverlays().clear();
        // [S5-nav] clear로 지워진 내 위치 마커/점선 복원
        if (mMyMarker != null) mMapView.getOverlays().add(mMyMarker);
        if (mNavLine != null) mMapView.getOverlays().add(mNavLine);
        java.util.List<GeoPoint> all = new java.util.ArrayList<>();

        java.util.LinkedHashMap<String, java.util.List<Object[]>> groups = new java.util.LinkedHashMap<>();
        for (TacticalStore.Entry e : mSets) {
            if (e.data == null) continue;
            for (TacticalParser.TMarker m : e.data.markers) {
                String label = "S".equals(m.cat) ? TacticalMarkerIcon.survivalIdentifier(m.survType, m.survDisaster, m.id) : TacticalParser.makeIdentifier(m.type, m.unit, m.place, m.id);
                groups.computeIfAbsent(label, k -> new java.util.ArrayList<>()).add(new Object[]{m, e.recvAt});
            }
        }
        for (java.util.List<Object[]> pts : groups.values()) {
            if (pts.size() >= 2) {
                Polyline track = new Polyline();
                java.util.List<GeoPoint> coords = new java.util.ArrayList<>();
                for (Object[] o : pts) {
                    TacticalParser.TMarker m = (TacticalParser.TMarker) o[0];
                    coords.add(new GeoPoint(m.lat, m.lon));
                }
                track.setPoints(coords);
                track.getOutlinePaint().setColor(0xFF00E5FF);
                track.getOutlinePaint().setStrokeWidth(4f);
                mMapView.getOverlays().add(0, track);
            }
            for (int i = 0; i < pts.size(); i++) {
                TacticalParser.TMarker m = (TacticalParser.TMarker) pts.get(i)[0];
                boolean latest = (i == pts.size() - 1);
                Marker mk = new Marker(mMapView);
                GeoPoint gp = new GeoPoint(m.lat, m.lon);
                mk.setPosition(gp);
                mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                android.graphics.drawable.Drawable ic =
                        "S".equals(m.cat) ? TacticalMarkerIcon.makeSurvival(getContext(), m.survType, m.survDisaster, m.id) : TacticalMarkerIcon.make(getContext(), m.type, m.id, m.unit, m.place);
                if (ic != null) mk.setIcon(ic);
                mk.setAlpha(latest ? 1.0f : 0.4f);
                String ident = "S".equals(m.cat) ? TacticalMarkerIcon.survivalIdentifier(m.survType, m.survDisaster, m.id) : TacticalParser.makeIdentifier(m.type, m.unit, m.place, m.id);
                String affil = "S".equals(m.cat) ? TacticalMarkerIcon.survivalName(m.survType, m.survDisaster) : ((m.type >= 0 && m.type < AFFIL.length) ? AFFIL[m.type] : "-");
                mk.setTitle(ident);
                mk.setSnippet(affil + "\n" + String.format(java.util.Locale.US, "%.5f, %.5f", m.lat, m.lon));
                final double _tlat = m.lat, _tlon = m.lon;
                final boolean _isSurv = "S".equals(m.cat);
                final String _sBase = affil + "\n" + String.format(java.util.Locale.US, "%.5f, %.5f", m.lat, m.lon);
                mk.setOnMarkerClickListener((mm, mv) -> {
                    if (mm.isInfoWindowShown()) mm.closeInfoWindow();
                    else {
                        if (_isSurv && !Double.isNaN(mMyLat)) {
                            double _d = com.ah.acr.messagebox.util.SurvivalNav.distanceMeters(mMyLat, mMyLon, _tlat, _tlon);
                            double _b = com.ah.acr.messagebox.util.SurvivalNav.bearingDegrees(mMyLat, mMyLon, _tlat, _tlon);
                            String _nav = "🧭 " + com.ah.acr.messagebox.util.SurvivalNav.compass8(_b) + " "
                                    + com.ah.acr.messagebox.util.SurvivalNav.formatDistance(_d)
                                    + " · 도보 " + com.ah.acr.messagebox.util.SurvivalNav.formatWalk(_d);
                            mm.setSnippet(_sBase + "\n" + _nav);
                            drawNavLine(_tlat, _tlon);
                            mSelLat = _tlat; mSelLon = _tlon; startCompass();   // [S5-nav] 나침반 대상 지정 + 센서 시작
                        }
                        org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(mv);
                        mm.showInfoWindow();
                    }
                    return true;
                });
                mMapView.getOverlays().add(mk);
                all.add(gp);
            }
        }

        TacticalStore.Entry last = mSets.get(mSets.size() - 1);
        if (last.data != null) {
            for (TacticalParser.TLine ln : last.data.lines) {
                Polyline pl = new Polyline();
                java.util.List<GeoPoint> p2 = new java.util.ArrayList<>();
                for (double[] p : ln.points) { GeoPoint g = new GeoPoint(p[0], p[1]); p2.add(g); all.add(g); }
                pl.setPoints(p2);
                pl.getOutlinePaint().setColor(0xFF00E5FF);
                pl.getOutlinePaint().setStrokeWidth(6f);
                mMapView.getOverlays().add(pl);
            }
            for (TacticalParser.TMeasure ms : last.data.measures) {
                Polyline pl = new Polyline();
                java.util.List<GeoPoint> p2 = new java.util.ArrayList<>();
                for (double[] p : ms.points) { GeoPoint g = new GeoPoint(p[0], p[1]); p2.add(g); all.add(g); }
                pl.setPoints(p2);
                pl.getOutlinePaint().setColor(0xFFFF6D00);
                pl.getOutlinePaint().setStrokeWidth(5f);
                mMapView.getOverlays().add(pl);
            }
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

    private void showExportDialog() {
        if (mSets.isEmpty()) {
            android.widget.Toast.makeText(getContext(), "No tactical data", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String[] formats = {"GPX", "KML", "CSV"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Export Tactical")
                .setItems(formats, (d, which) -> {
                    com.ah.acr.messagebox.export.TrackExporter.Format f;
                    switch (which) {
                        case 0: f = com.ah.acr.messagebox.export.TrackExporter.Format.GPX; break;
                        case 1: f = com.ah.acr.messagebox.export.TrackExporter.Format.KML; break;
                        default: f = com.ah.acr.messagebox.export.TrackExporter.Format.CSV; break;
                    }
                    performExport(f);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performExport(com.ah.acr.messagebox.export.TrackExporter.Format format) {
        // 전술 마커(모든 세트)를 좌표점으로 변환
        java.util.List<com.ah.acr.messagebox.database.MyTrackPointEntity> pts = new java.util.ArrayList<>();
        java.util.Date start = new java.util.Date(mSets.get(0).recvAt);
        java.util.Date end = new java.util.Date(mSets.get(mSets.size() - 1).recvAt);
        for (TacticalStore.Entry e : mSets) {
            if (e.data == null) continue;
            java.util.Date t = new java.util.Date(e.recvAt);
            for (TacticalParser.TMarker m : e.data.markers) {
                pts.add(new com.ah.acr.messagebox.database.MyTrackPointEntity(
                        0, 0, m.lat, m.lon, 0.0, 0.0, 0f, 0, t));
            }
        }
        if (pts.isEmpty()) {
            android.widget.Toast.makeText(getContext(), "No markers", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String name = "Tactical " + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(start);
        com.ah.acr.messagebox.database.MyTrackEntity track =
                new com.ah.acr.messagebox.database.MyTrackEntity(
                        0, name, start, end, 0.0, pts.size(),
                        0.0, 0.0, 0.0, 0.0, "COMPLETED", 0, 0, new java.util.Date());
        com.ah.acr.messagebox.export.TrackExporter.ExportResult result =
                com.ah.acr.messagebox.export.TrackExporter.exportTrack(requireContext(), track, pts, format);
        if (result.success) {
            // 바로 공유 (카톡 등)
            try {
                android.content.Intent intent =
                        com.ah.acr.messagebox.export.TrackExporter.buildShareIntent(requireContext(), result.file, format);
                startActivity(android.content.Intent.createChooser(intent, "Share Tactical"));
            } catch (Exception e) {
                android.widget.Toast.makeText(getContext(), "Share failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            }
        } else {
            android.widget.Toast.makeText(getContext(), "Export failed: " + result.errorMessage, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private final Runnable mPlayRunnable = new Runnable() {
        @Override public void run() {
            if (!mPlaying) return;
            if (mPlayIndex < mAllSets.size() - 1) {
                mPlayIndex++;
                renderUpTo(mPlayIndex);
                if (mPlayIndex < mAllSets.size() - 1) {
                    mPlayH.postDelayed(this, 1200);
                } else {
                    mPlaying = false;
                    updatePlayIcon();
                }
            } else {
                mPlaying = false;
                updatePlayIcon();
            }
        }
    };

    private void togglePlay() {
        if (mAllSets.size() <= 1) return;
        if (mPlaying) stopPlay();
        else startPlay();
    }

    private void startPlay() {
        if (mPlayIndex >= mAllSets.size() - 1) mPlayIndex = -1;
        mPlaying = true;
        updatePlayIcon();
        mPlayH.post(mPlayRunnable);
    }

    private void stopPlay() {
        mPlaying = false;
        mPlayH.removeCallbacks(mPlayRunnable);
        updatePlayIcon();
    }

    private void stepPlay(int dir) {
        if (mAllSets.isEmpty()) return;
        int ni = mPlayIndex + dir;
        if (ni < 0) ni = 0;
        if (ni > mAllSets.size() - 1) ni = mAllSets.size() - 1;
        mPlayIndex = ni;
        renderUpTo(mPlayIndex);
    }

    private void renderUpTo(int idx) {
        if (mAllSets.isEmpty()) return;
        int max = (idx < 0 || idx >= mAllSets.size() - 1) ? mAllSets.size() - 1 : idx;
        mSets = new java.util.ArrayList<>(mAllSets.subList(0, max + 1));
        renderHistory();
        updateProgress();
    }

    private void updatePlayIcon() {
        View v = getView();
        if (v == null) return;
        android.widget.ImageButton btn = v.findViewById(R.id.tac_detail_play);
        if (btn != null) btn.setImageResource(mPlaying
                ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
    }

    private void updateProgress() {
        View v = getView();
        if (v == null) return;
        android.widget.TextView tv = v.findViewById(R.id.tac_detail_progress);
        if (tv != null) {
            int cur = (mPlayIndex < 0) ? mAllSets.size() : (mPlayIndex + 1);
            tv.setText(cur + "/" + mAllSets.size());
        }
    }

    private final java.text.SimpleDateFormat mDateFmt =
            new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
    private java.util.Calendar mStartCal, mEndCal;
    private android.widget.TextView mTvStart, mTvEnd;

    private void setupDateBar(View root) {
        android.widget.TextView tvStart = root.findViewById(R.id.tac_start_date);
        android.widget.TextView tvEnd = root.findViewById(R.id.tac_end_date);
        mTvStart = tvStart; mTvEnd = tvEnd;
        android.widget.TextView btnApply = root.findViewById(R.id.tac_date_apply);
        android.widget.TextView btnAll = root.findViewById(R.id.tac_date_all);

        // 초기값: 이력 첫/끝 날짜
        if (!mAllSets.isEmpty()) {
            mStartCal = java.util.Calendar.getInstance();
            mStartCal.setTimeInMillis(mAllSets.get(0).recvAt);
            mEndCal = java.util.Calendar.getInstance();
            mEndCal.setTimeInMillis(mAllSets.get(mAllSets.size() - 1).recvAt);
            tvStart.setText(mDateFmt.format(mStartCal.getTime()));
            tvEnd.setText(mDateFmt.format(mEndCal.getTime()));
        }

        tvStart.setOnClickListener(v -> pickDate(true, tvStart));
        tvEnd.setOnClickListener(v -> pickDate(false, tvEnd));

        btnApply.setOnClickListener(v -> {
            if (mStartCal == null || mEndCal == null) return;
            long s = startOfDay(mStartCal);
            long e = endOfDay(mEndCal);
            applyDateFilter(s, e);
        });

        btnAll.setOnClickListener(v -> {
            mSets = new java.util.ArrayList<>(mAllSets);
            mPlayIndex = -1;
            stopPlay();
            renderHistory();
            buildRows();
            updateProgress();
        });

        // 빠른 칩 (현재 시각 기준)
        root.findViewById(R.id.tac_chip_24h).setOnClickListener(v -> filterRecentHours(24));
        root.findViewById(R.id.tac_chip_48h).setOnClickListener(v -> filterRecentHours(48));
        root.findViewById(R.id.tac_chip_3d).setOnClickListener(v -> filterRecentHours(24 * 3));
        root.findViewById(R.id.tac_chip_7d).setOnClickListener(v -> filterRecentHours(24 * 7));
        root.findViewById(R.id.tac_chip_30d).setOnClickListener(v -> filterRecentHours(24 * 30));
    }

    private void filterRecentHours(int hours) {
        long now = System.currentTimeMillis();
        long from = now - (long) hours * 3600_000L;
        // 날짜 표시 + Calendar 갱신
        mStartCal = java.util.Calendar.getInstance();
        mStartCal.setTimeInMillis(from);
        mEndCal = java.util.Calendar.getInstance();
        mEndCal.setTimeInMillis(now);
        if (mTvStart != null) mTvStart.setText(mDateFmt.format(mStartCal.getTime()));
        if (mTvEnd != null) mTvEnd.setText(mDateFmt.format(mEndCal.getTime()));
        applyDateFilter(from, now);
    }

    private void pickDate(boolean isStart, android.widget.TextView tv) {
        java.util.Calendar base = isStart ? mStartCal : mEndCal;
        if (base == null) base = java.util.Calendar.getInstance();
        new android.app.DatePickerDialog(requireContext(),
                (view, y, m, d) -> {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    c.set(y, m, d);
                    if (isStart) mStartCal = c; else mEndCal = c;
                    tv.setText(mDateFmt.format(c.getTime()));
                },
                base.get(java.util.Calendar.YEAR),
                base.get(java.util.Calendar.MONTH),
                base.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private long startOfDay(java.util.Calendar cal) {
        java.util.Calendar c = (java.util.Calendar) cal.clone();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long endOfDay(java.util.Calendar cal) {
        java.util.Calendar c = (java.util.Calendar) cal.clone();
        c.set(java.util.Calendar.HOUR_OF_DAY, 23);
        c.set(java.util.Calendar.MINUTE, 59);
        c.set(java.util.Calendar.SECOND, 59);
        c.set(java.util.Calendar.MILLISECOND, 999);
        return c.getTimeInMillis();
    }

    private void applyDateFilter(long startMs, long endMs) {
        java.util.List<TacticalStore.Entry> filtered = new java.util.ArrayList<>();
        for (TacticalStore.Entry e : mAllSets) {
            if (e.recvAt >= startMs && e.recvAt <= endMs) filtered.add(e);
        }
        if (filtered.isEmpty()) {
            android.widget.Toast.makeText(getContext(), "해당 기간 데이터 없음", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        mSets = filtered;
        mPlayIndex = -1;
        stopPlay();
        renderHistory();
        buildRows();
        updateProgress();
    }

    private void fitAll() {
        if (mSets.isEmpty() || mMapView == null) return;
        java.util.List<GeoPoint> all = new java.util.ArrayList<>();
        for (TacticalStore.Entry e : mSets) {
            if (e.data == null) continue;
            for (TacticalParser.TMarker tm : e.data.markers) all.add(new GeoPoint(tm.lat, tm.lon));
            for (TacticalParser.TLine ln : e.data.lines)
                for (double[] p : ln.points) all.add(new GeoPoint(p[0], p[1]));
            for (TacticalParser.TMeasure ms : e.data.measures)
                for (double[] p : ms.points) all.add(new GeoPoint(p[0], p[1]));
        }
        if (all.isEmpty()) return;
        try {
            org.osmdroid.util.BoundingBox box = org.osmdroid.util.BoundingBox.fromGeoPoints(all);
            mMapView.zoomToBoundingBox(box, true, 100);
        } catch (Exception ignore) {}
    }

    private void buildRows() {
        if (mSets.isEmpty()) return;
        List<TacticalElementAdapter.Row> rows = new ArrayList<>();
        int[] setIdxRef = {0};
        for (TacticalStore.Entry e : mSets) {
            if (e.data == null) { setIdxRef[0]++; continue; }
            final int __color = SET_COLORS[setIdxRef[0] % SET_COLORS.length];
            for (TacticalParser.TMarker tm : e.data.markers) {
                String ident = "S".equals(tm.cat) ? TacticalMarkerIcon.survivalIdentifier(tm.survType, tm.survDisaster, tm.id) : TacticalParser.makeIdentifier(tm.type, tm.unit, tm.place, tm.id);
                String affil = "S".equals(tm.cat) ? TacticalMarkerIcon.survivalName(tm.survType, tm.survDisaster) : ((tm.type >= 0 && tm.type < AFFIL.length) ? AFFIL[tm.type] : "-");
                { TacticalElementAdapter.Row __r = new TacticalElementAdapter.Row("MARKER", ident, affil, tm.lat, tm.lon); __r.setColor = __color; rows.add(__r); }
            }
            for (TacticalParser.TLine ln : e.data.lines) {
                if (!ln.points.isEmpty())
                    { TacticalElementAdapter.Row __r = new TacticalElementAdapter.Row("LINE", "-", "-", ln.points.get(0)[0], ln.points.get(0)[1]); __r.setColor = __color; rows.add(__r); }
            }
            for (TacticalParser.TMeasure ms : e.data.measures) {
                if (!ms.points.isEmpty())
                    { TacticalElementAdapter.Row __r = new TacticalElementAdapter.Row("MEAS", "-", "-", ms.points.get(0)[0], ms.points.get(0)[1]); __r.setColor = __color; rows.add(__r); }
            }
            setIdxRef[0]++;
        }
        mAdapter.submit(rows);
    }

    @Override
    public void onResume() { super.onResume(); if (mMapView != null) mMapView.onResume(); }

    @Override
    public void onPause() { super.onPause(); if (mMapView != null) mMapView.onPause(); stopCompass(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mMapView != null) { mMapView.onDetach(); mMapView = null; }
    }
}