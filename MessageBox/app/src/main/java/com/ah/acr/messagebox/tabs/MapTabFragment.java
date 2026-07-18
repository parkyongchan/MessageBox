package com.ah.acr.messagebox.tabs;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import com.ah.acr.messagebox.TacticalStore;
import com.ah.acr.messagebox.TacticalParser;
import com.ah.acr.messagebox.TacticalMarkerIcon;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.adapter.LocationAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.LocationEntity;
import com.ah.acr.messagebox.database.LocationViewModel;
import com.ah.acr.messagebox.database.LocationWithAddress;
import com.ah.acr.messagebox.databinding.FragmentMapTabBinding;
import com.ah.acr.messagebox.util.MapModeManager;
import com.ah.acr.messagebox.util.MapModeToggleHelper;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.Polygon;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * Devices Tab (a.k.a. Map Tab)
 * - Received location data list + map visualization
 * - Hide map when search is focused (expand list)
 * - Manual map mode toggle (online/offline)
 *
 * ⭐ v6 patch (2026-05-03):
 * - Marker tap crash fix (Integer altitude/speed/direction → no %f)
 * - UAT-spec unified popup
 * - Bulk delete: trash icon → 3-option dialog (current range / full / cancel)
 * - Refresh button long-press → delete ALL (with confirmation)
 */
public class MapTabFragment extends Fragment {
    private static final String TAG = MapTabFragment.class.getSimpleName();

    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(37.5665, 126.9780);
    private static final double DEFAULT_ZOOM = 10.0;
    private static final double DEFAULT_ZOOM_SINGLE = 14.0;

    private FragmentMapTabBinding binding;
    private LocationAdapter mAdapter;
    private com.ah.acr.messagebox.adapter.TacticalListAdapter mTacticalAdapter;
    private com.ah.acr.messagebox.adapter.WeatherListAdapter mWeatherAdapter;
    private LocationViewModel locationViewModel;
    private AddressViewModel addressViewModel;
    private BleViewModel mBleViewModel;

    private MapView mMapView;
    private final List<Marker> mMarkers = new ArrayList<>();
    // [tactical] 전술 오버레이 (위치 마커와 분리 관리, 필터로 토글)
    private final List<Marker> mTacticalMarkers = new ArrayList<>();
    private final List<Polyline> mTacticalLines = new ArrayList<>();
    private int mCurrentMode = MODE_ALL;
    private boolean mInitialFitDone = false;

    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private static final int MODE_ALL = 0;
    private static final int MODE_TRACK = 2;
    private static final int MODE_SOS = 4;
    private static final int MODE_TACTICAL = 6;
    private static final int MODE_SURVIVAL = 5;   // [SURV] 생존 전용 필터
    private static final int MODE_WEATHER = 7;   // [CLIMATE] 날씨 전용 필터

    private boolean mIsSearchMode = false;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMapTabBinding.inflate(inflater, container, false);

        setupRecyclerView();
        setupViewModel();
        setupFilterChips();
        setupDatePickers();
        setupSearch();
        setupRefresh();
        setupMap();
        setupMapControls();
        setupMapModeToggle();
        observeData();

        binding.chip7d.setSelected(true);
        binding.chipAll.setSelected(true);

        // 전술지도 버튼: 설정 ON일 때만 표시
        boolean tacticalOn = android.preference.PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean("pref_tactical_enabled", false);
        binding.btnTactical.setVisibility(tacticalOn ? View.VISIBLE : View.GONE);
        binding.btnTactical.setOnClickListener(v ->
                startActivity(new android.content.Intent(
                        requireContext(), com.ah.acr.messagebox.TacticalMapActivity.class)));

        return binding.getRoot();
    }


    private void setupMapModeToggle() {
        MapModeToggleHelper.setup(
                binding.getRoot(),
                requireContext(),
                newMode -> {
                    Log.v(TAG, "Map mode changed: " + newMode);
                    MapModeManager.applyToMapView(requireContext(), mMapView);
                }
        );
    }


    private void setupMap() {
        Configuration.getInstance().setUserAgentValue(
                requireActivity().getPackageName()
        );

        File osmDir = requireContext().getExternalFilesDir(null);
        if (osmDir != null) {
            Configuration.getInstance().setOsmdroidBasePath(osmDir);
            Configuration.getInstance().setOsmdroidTileCache(
                    new File(osmDir, "cache")
            );
        }

        mMapView = binding.map;
        mMapView.setMultiTouchControls(true);
        mMapView.setBuiltInZoomControls(false);

        MapModeManager.applyToMapView(requireContext(), mMapView);

        mMapView.getController().setZoom(DEFAULT_ZOOM);
        mMapView.getController().setCenter(DEFAULT_CENTER);

        mMapView.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent event) { return false; }
            @Override public boolean onZoom(ZoomEvent event) {
                updateZoomLabel();
                return false;
            }
        });

        updateZoomLabel();
    }


    private void updateZoomLabel() {
        if (mMapView != null && binding != null) {
            int zoom = (int) mMapView.getZoomLevelDouble();
            binding.tvMapZoom.setText("ZOOM " + zoom);
        }
    }


    private void setupMapControls() {
        binding.btnZoomIn.setOnClickListener(v -> {
            if (mMapView != null) mMapView.getController().zoomIn();
        });
        binding.btnZoomOut.setOnClickListener(v -> {
            if (mMapView != null) mMapView.getController().zoomOut();
        });
        binding.btnFitAll.setOnClickListener(v -> fitAllMarkers());
        if (binding.btnFullscreen != null) {
            binding.btnFullscreen.setOnClickListener(v -> toggleFullscreen());
        }
    }


    private void fitAllMarkers() {
        // [tactical] 통합 fit: 위치 마커 + 전술 마커 + 전술 라인 점 전부 포함
        java.util.List<GeoPoint> allPts = new ArrayList<>();
        for (Marker mk : mMarkers) allPts.add(mk.getPosition());
        for (Marker mk : mTacticalMarkers) allPts.add(mk.getPosition());
        for (Polyline pl : mTacticalLines) {
            if (pl.getActualPoints() != null) allPts.addAll(pl.getActualPoints());
        }
        if (allPts.isEmpty()) {
            Toast.makeText(getContext(),
                    getString(R.string.devices_toast_no_markers),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (allPts.size() == 1) {
            mMapView.getController().animateTo(allPts.get(0));
            mMapView.getController().setZoom(DEFAULT_ZOOM_SINGLE);
            return;
        }
        double north = -90, south = 90, east = -180, west = 180;
        for (GeoPoint p : allPts) {
            if (p.getLatitude() > north) north = p.getLatitude();
            if (p.getLatitude() < south) south = p.getLatitude();
            if (p.getLongitude() > east) east = p.getLongitude();
            if (p.getLongitude() < west) west = p.getLongitude();
        }
        double padLat = (north - south) * 0.2;
        double padLng = (east - west) * 0.2;
        if (padLat < 0.001) padLat = 0.01;
        if (padLng < 0.001) padLng = 0.01;
        BoundingBox box = new BoundingBox(
                north + padLat, east + padLng,
                south - padLat, west - padLng
        );
        mMapView.post(() -> mMapView.zoomToBoundingBox(box, true, 50));
    }


    private void refreshMarkers(List<LocationWithAddress> locations) {
        if (mMapView == null) return;

        for (Marker m : mMarkers) {
            mMapView.getOverlays().remove(m);
        }
        mMarkers.clear();

        if (locations == null || locations.isEmpty()) {
            mMapView.invalidate();
            return;
        }

        for (LocationWithAddress item : locations) {
            final LocationEntity loc = item.getLocation();
            if (loc.getLatitude() == null || loc.getLongitude() == null) continue;

            GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
            Marker marker = new Marker(mMapView);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            final String displayName;
            if (item.getAddress() != null
                    && item.getAddress().getNumbersNic() != null) {
                displayName = item.getAddress().getNumbersNic();
            } else {
                displayName = loc.getCodeNum() != null
                        ? loc.getCodeNum()
                        : getString(R.string.devices_marker_unknown);
            }
            marker.setTitle(displayName);

            String snippet = String.format(Locale.US, "%.6f, %.6f",
                    loc.getLatitude(), loc.getLongitude());
            if (loc.getCreateAt() != null) {
                snippet += "\n" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()).format(loc.getCreateAt());
            }
            snippet += "\n" + getString(R.string.marker_snippet_tap_for_details);
            marker.setSnippet(snippet);

            Drawable icon = getMarkerIcon(loc.getTrackMode(), loc.isIncomeLoc());
            if (icon != null) marker.setIcon(icon);

            marker.setOnMarkerClickListener((m, mv) -> {
                Log.v(TAG, "Marker tap: " + displayName);
                showMarkerDetailDialog(loc, displayName);
                return true;
            });

            mMarkers.add(marker);
            mMapView.getOverlays().add(marker);
        }

        mMapView.invalidate();

        if (!mInitialFitDone && !mMarkers.isEmpty()) {
            mInitialFitDone = true;
            mMapView.post(this::fitAllMarkers);
        }
        renderTacticalOverlays();
        renderWeatherOverlays(); // refreshMarkers 후 전술도 다시 그림
    }


    // [tactical] 전술 오버레이 렌더 (TacticalStore 읽어 마커/라인/메저 표시)
    // [상세진입] 생존/전술 마커 2탭 시 상세 지도(트래킹) 열기.
    private void openTacticalDetail(String fromImei) {
        android.util.Log.d("SURV-DETAIL", "openTacticalDetail 호출: fromImei=[" + fromImei + "]");
        try {
            com.ah.acr.messagebox.tabs.TacticalDetailFragment.newInstance(fromImei)
                .show(getParentFragmentManager(), "TacticalDetail");
        } catch (Exception ex) {
            android.util.Log.e(TAG, "openTacticalDetail 실패: " + ex.getMessage(), ex);
        }
    }

    private final java.util.List<org.osmdroid.views.overlay.Polygon> mWeatherCircles = new java.util.ArrayList<>();

    private void renderWeatherOverlays() {
        if (mMapView == null) return;
        for (org.osmdroid.views.overlay.Polygon c : mWeatherCircles) mMapView.getOverlays().remove(c);
        mWeatherCircles.clear();
        if (mCurrentMode != MODE_WEATHER) { mMapView.invalidate(); return; }
        for (com.ah.acr.messagebox.WeatherStore.Weather w : com.ah.acr.messagebox.WeatherStore.getAll()) {
            double lat = w.lat(), lon = w.lon();
            if (lat == 0 && lon == 0) continue;
            org.osmdroid.views.overlay.Polygon circle = new org.osmdroid.views.overlay.Polygon(mMapView);
            circle.setPoints(org.osmdroid.views.overlay.Polygon.pointsAsCircle(new GeoPoint(lat, lon), 3000.0));
            int fill = w.marine ? 0x330077CC : 0x3300C9FF;
            int stroke = w.marine ? 0xFF0077CC : 0xFF00C9FF;
            circle.getFillPaint().setColor(fill);
            circle.getOutlinePaint().setColor(stroke);
            circle.getOutlinePaint().setStrokeWidth(3f);
            // 요약 (탭 시 정보창)
            StringBuilder t = new StringBuilder(w.marine ? "\ud574\uc0c1 \ub0a0\uc528" : "\uc721\uc0c1 \ub0a0\uc528");
            if (w.marine) {
                t.append("\n\ud30c\uace0 ").append(v(w,"WH")).append("m  \uc218\uc628 ").append(v(w,"SST")).append("\u00b0");
            } else {
                t.append("\n\uae30\uc628 ").append(v(w,"T")).append("\u00b0  \uccb4\uac10 ").append(v(w,"FL")).append("\u00b0");
            }
            t.append("  \ud48d\uc18d ").append(v(w,"WS"));
            circle.setTitle(t.toString());
            mWeatherCircles.add(circle);
            mMapView.getOverlays().add(circle);
        }
        mMapView.invalidate();
    }

    private String v(com.ah.acr.messagebox.WeatherStore.Weather w, String k) {
        String s = w.fields.get(k);
        return s == null ? "-" : s;
    }

    private void renderTacticalOverlays() {
        if (mMapView == null) return;
        for (Marker m : mTacticalMarkers) mMapView.getOverlays().remove(m);
        for (Polyline p : mTacticalLines) mMapView.getOverlays().remove(p);
        mTacticalMarkers.clear();
        mTacticalLines.clear();

        if (mCurrentMode != MODE_ALL && mCurrentMode != MODE_TACTICAL && mCurrentMode != MODE_SURVIVAL) {
            mMapView.invalidate();
            return;
        }

        java.util.List<TacticalStore.Entry> allEntries = TacticalStore.getAll();
        // [장비별 최신 1건] 발신자(fromImei)별로 recvAt 최신 1건만 (웹과 동일, 과거 전송 제외)
        java.util.Map<String, TacticalStore.Entry> latestByImei = new java.util.HashMap<>();
        for (TacticalStore.Entry e : allEntries) {
            if (e.data == null) continue;
            String key = (e.data.fromImei == null) ? "" : e.data.fromImei;
            TacticalStore.Entry cur = latestByImei.get(key);
            if (cur == null || e.recvAt > cur.recvAt) latestByImei.put(key, e);
        }
        java.util.List<TacticalStore.Entry> entries = new java.util.ArrayList<>(latestByImei.values());
        long latestSid = -1;
        for (TacticalStore.Entry _e : entries) { if (_e.data != null && _e.data.sessionId > latestSid) latestSid = _e.data.sessionId; }
        for (TacticalStore.Entry e : entries) {
            if (e.data == null) continue;
            for (TacticalParser.TMarker tm : e.data.markers) {
                boolean _surv = "S".equals(tm.cat);
                if (mCurrentMode == MODE_SURVIVAL && !_surv) continue;   // [SURV] 생존만
                if (mCurrentMode == MODE_TACTICAL && _surv) continue;    // [TAC] 전술만
                Marker mk = new Marker(mMapView);
                mk.setPosition(new GeoPoint(tm.lat, tm.lon));
                mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                if ("S".equals(tm.cat) && e.data.sessionId >= 0 && e.data.sessionId != latestSid) mk.setAlpha(0.4f);
                Drawable ic = "S".equals(tm.cat)
                        ? TacticalMarkerIcon.makeSurvival(getContext(), tm.survType, tm.survDisaster, tm.id)
                        : TacticalMarkerIcon.make(getContext(), tm.type, tm.id, tm.unit, tm.place);
                if (ic != null) mk.setIcon(ic);
                String ident = "S".equals(tm.cat)
                        ? TacticalMarkerIcon.survivalIdentifier(tm.survType, tm.survDisaster, tm.id)
                        : TacticalParser.makeIdentifier(tm.type, tm.unit, tm.place, tm.id);
                mk.setTitle(ident);
                StringBuilder sn = new StringBuilder();
                sn.append(String.format(Locale.US, "%.5f, %.5f", tm.lat, tm.lon));
                if ("S".equals(tm.cat)) sn.append("\n").append(TacticalMarkerIcon.survivalName(tm.survType, tm.survDisaster));
                String from = (e.data.fromImei == null || e.data.fromImei.isEmpty()) ? "HQ/Control" : e.data.fromImei;
                sn.append("\nFrom: ").append(from);
                if (e.data.note != null && !e.data.note.isEmpty()) sn.append("\nNote: ").append(e.data.note);
                if ("S".equals(tm.cat)) sn.append("\n\u25B6 탭하여 상세 안내 보기 (Tap for details)");
                mk.setSnippet(sn.toString());
                final String _fromImei = (e.data.fromImei == null) ? "" : e.data.fromImei;
                final boolean _isSurv = "S".equals(tm.cat);
                // 전술 마커 클릭 토글: 열려있으면 닫고, 아니면 정보 표시
                mk.setOnMarkerClickListener((m2, mv2) -> {
                    if (_isSurv) {
                        // [생존] 1탭=문구 표시, 2탭(이미 열림)=상세 지도 이동
                        if (m2.isInfoWindowShown()) {
                            openTacticalDetail(_fromImei);
                        } else {
                            org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(mv2);
                            m2.showInfoWindow();
                        }
                    } else {
                        if (m2.isInfoWindowShown()) {
                            m2.closeInfoWindow();
                        } else {
                            org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(mv2);
                            m2.showInfoWindow();
                        }
                    }
                    return true;
                });
                mTacticalMarkers.add(mk);
                mMapView.getOverlays().add(mk);
            }
            for (TacticalParser.TLine ln : e.data.lines) {
                Polyline pl = new Polyline(mMapView);
                java.util.List<GeoPoint> pts = new ArrayList<>();
                for (double[] p : ln.points) pts.add(new GeoPoint(p[0], p[1]));
                pl.setPoints(pts);
                pl.getOutlinePaint().setColor(0xFF00E5FF);
                pl.getOutlinePaint().setStrokeWidth(6f);
                mTacticalLines.add(pl);
                mMapView.getOverlays().add(pl);
            }
            for (TacticalParser.TMeasure ms : e.data.measures) {
                Polyline pl = new Polyline(mMapView);
                java.util.List<GeoPoint> pts = new ArrayList<>();
                for (double[] p : ms.points) pts.add(new GeoPoint(p[0], p[1]));
                pl.setPoints(pts);
                pl.getOutlinePaint().setColor(0xFFFF6D00);
                pl.getOutlinePaint().setStrokeWidth(5f);
                mTacticalLines.add(pl);
                mMapView.getOverlays().add(pl);
            }
        }
        mMapView.invalidate();
        // renderTacticalOverlays 후 자동 fit (전술 표시될 때 전체 보이게)
        if (!mTacticalMarkers.isEmpty() || !mTacticalLines.isEmpty()) {
            mMapView.post(this::fitAllMarkers);
        }
    }

    private void showMarkerDetailDialog(LocationEntity loc, String displayName) {
        SimpleDateFormat fmt = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.US);
        String time = loc.getCreateAt() != null
                ? fmt.format(loc.getCreateAt())
                : "-";

        StringBuilder sb = new StringBuilder();

        sb.append(getString(R.string.dialog_label_imei))
                .append(loc.getCodeNum() != null ? loc.getCodeNum() : "-")
                .append("\n\n");

        if (displayName != null && !displayName.equals(loc.getCodeNum())) {
            sb.append(getString(R.string.dialog_label_name))
                    .append(displayName)
                    .append("\n\n");
        }

        sb.append(getString(R.string.dialog_label_time))
                .append(time)
                .append("\n\n");

        sb.append(getString(R.string.dialog_label_coordinates))
                .append(String.format(Locale.US, "%.6f, %.6f",
                        loc.getLatitude(), loc.getLongitude()))
                .append("\n\n");

        int trackMode = loc.getTrackMode();
        boolean isUavOrUat = (trackMode == 0x02 || trackMode == 0x12
                || trackMode == 0x03 || trackMode == 0x13);

        if (isUavOrUat) {
            Integer altitude = loc.getAltitude();
            if (altitude != null && altitude != 0) {
                sb.append(getString(R.string.dialog_label_altitude))
                        .append(altitude)
                        .append(" m")
                        .append("\n\n");
            }

            Integer speed = loc.getSpeed();
            if (speed != null && speed != 0) {
                sb.append(getString(R.string.dialog_label_speed))
                        .append(speed)
                        .append(" km/h")
                        .append("\n\n");
            }

            Integer direction = loc.getDirection();
            if (direction != null) {
                sb.append(getString(R.string.dialog_label_direction))
                        .append(direction)
                        .append("°")
                        .append("\n\n");
            }
        }

        String modeText = getTrackModeText(trackMode, loc.isIncomeLoc());
        sb.append(getString(R.string.dialog_label_mode)).append(modeText);

        final String devName = displayName;
        final String codeNum = loc.getCodeNum();

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_location_detail_title))
                .setMessage(sb.toString())
                .setPositiveButton(getString(R.string.btn_view_track_details), (d, w) -> {
                    if (codeNum != null) {
                        try {
                            DeviceTrackDetailFragment dialog =
                                    DeviceTrackDetailFragment.newInstance(codeNum, devName);
                            dialog.show(getParentFragmentManager(), "DeviceTrackDetail");
                        } catch (Exception e) {
                            Log.e(TAG, "DeviceTrackDetail show failed: " + e.getMessage(), e);
                            Toast.makeText(requireContext(),
                                    "Track detail open failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(requireContext(),
                                getString(R.string.toast_no_imei),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(getString(R.string.btn_copy_coordinates), (d, w) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) requireContext()
                                    .getSystemService(Context.CLIPBOARD_SERVICE);
                    String coords = String.format(Locale.US, "%f,%f",
                            loc.getLatitude(), loc.getLongitude());
                    ClipData clip = ClipData.newPlainText(
                            getString(R.string.clipboard_label_coordinates), coords);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(requireContext(),
                            getString(R.string.toast_coordinates_copied),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.btn_close), null)
                .show();
    }


    private String getTrackModeText(int trackMode, boolean isIncomeLoc) {
        if (isIncomeLoc) {
            switch (trackMode) {
                case 0x10: return getString(R.string.mode_rx_sos);
                case 0x11: return getString(R.string.mode_rx_car_track);
                case 0x12: return getString(R.string.mode_rx_uav_track);
                case 0x13: return getString(R.string.mode_rx_uat_track);
                case 2: return getString(R.string.mode_rx_track_legacy);
                case 4:
                case 5: return getString(R.string.mode_rx_sos_legacy);
                default: return getString(R.string.mode_rx_unknown, trackMode);
            }
        } else {
            switch (trackMode) {
                case 0x00: return getString(R.string.mode_tx_sos);
                case 0x01: return getString(R.string.mode_tx_car_track);
                case 0x02: return getString(R.string.mode_tx_uav_track);
                case 0x03: return getString(R.string.mode_tx_uat_track);
                default: return getString(R.string.mode_tx_unknown, trackMode);
            }
        }
    }


    private Drawable getMarkerIcon(int trackMode, boolean isIncomeLoc) {
        int iconRes;

        if (isIncomeLoc) {
            if (trackMode == 0x10 || trackMode == 4 || trackMode == 5) {
                iconRes = R.drawable.ic_marker_sos;
            }
            else if (trackMode == 0x11 || trackMode == 0x12
                    || trackMode == 0x13 || trackMode == 2) {
                iconRes = R.drawable.ic_marker_track;
            }
            else {
                iconRes = R.drawable.ic_marker_device;
            }
        }
        else {
            if (trackMode == 0x00) {
                iconRes = R.drawable.ic_marker_my_sos;
            }
            else if (trackMode == 0x01 || trackMode == 0x02 || trackMode == 0x03) {
                iconRes = R.drawable.ic_marker_my_track;
            }
            else {
                iconRes = R.drawable.ic_marker_device;
            }
        }

        return ContextCompat.getDrawable(requireContext(), iconRes);
    }


    private void setupViewModel() {
        mBleViewModel = new ViewModelProvider(requireActivity()).get(BleViewModel.class);
        locationViewModel = new ViewModelProvider(requireActivity()).get(LocationViewModel.class);
        addressViewModel = new ViewModelProvider(requireActivity()).get(AddressViewModel.class);
    }


    private void setupRecyclerView() {
        mAdapter = new LocationAdapter(new LocationAdapter.OnLocationClickListener() {
            @Override public void onLocationClick(LocationEntity location) {
                handleLocationClick(location);
            }
            @Override public void onLocationDeleteClick(LocationEntity location) {
                handleLocationDelClick(location);
            }
            @Override public void onLocationCopyClick(LocationEntity location) {
                handleLocationCopyClick(location);
            }
            @Override public void onLocationMapClick(LocationEntity location) {
                handleLocationMapClick(location);
            }
            @Override public void onAddressClick(LocationWithAddress location) {
                handleAddressClick(location);
            }
        });

        RecyclerView recyclerView = binding.listLocation;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(mAdapter);

        mTacticalAdapter = new com.ah.acr.messagebox.adapter.TacticalListAdapter(
            new com.ah.acr.messagebox.adapter.TacticalListAdapter.OnTacticalClickListener() {
                @Override public void onTacticalClick(TacticalStore.Entry e) {
                    // 지도에서 그 전술 첫 마커 위치로 이동
                    if (e.data != null && e.data.markers != null && !e.data.markers.isEmpty()) {
                        TacticalParser.TMarker m0 = e.data.markers.get(0);
                        mMapView.getController().animateTo(new GeoPoint(m0.lat, m0.lon));
                    }
                }
                @Override public void onTacticalDetail(TacticalStore.Entry e) {
                    // [TAC 상세] 그 전술 그룹을 상세 화면으로 (원본 payload 전달)
                    if (e.data == null || e.payload == null) return;
                    String fromImei = (e.data.fromImei == null) ? "" : e.data.fromImei;
                    try {
                        com.ah.acr.messagebox.tabs.TacticalDetailFragment dlg =
                            com.ah.acr.messagebox.tabs.TacticalDetailFragment.newInstance(fromImei);
                        dlg.show(getParentFragmentManager(), "TacticalDetail");
                    } catch (Exception ex) {
                        android.util.Log.e(TAG, "TacticalDetail open failed: " + ex.getMessage(), ex);
                    }
                }
            });
        // [CLIMATE] 날씨 목록 어댑터
        mWeatherAdapter = new com.ah.acr.messagebox.adapter.WeatherListAdapter(w -> {
            if (w != null) {
                // [2d] 날씨 상세 다이얼로그
                try {
                    com.ah.acr.messagebox.WeatherDetailDialog.newInstance(w)
                        .show(getParentFragmentManager(), "WeatherDetail");
                } catch (Exception ex) {
                    android.util.Log.e(TAG, "WeatherDetail open failed: " + ex.getMessage(), ex);
                }
            }
        });
    }


    private boolean mFullscreen = false;
    private void toggleFullscreen() {
        mFullscreen = !mFullscreen;
        if (getActivity() != null) {
            View header = getActivity().findViewById(R.id.header_area);
            View status = getActivity().findViewById(R.id.status_area);
            View bottomNav = getActivity().findViewById(R.id.bottom_nav);
            if (header != null) header.setVisibility(mFullscreen ? View.GONE : View.VISIBLE);
            if (status != null) status.setVisibility(mFullscreen ? View.GONE : View.VISIBLE);
            if (bottomNav != null) bottomNav.setVisibility(mFullscreen ? View.GONE : View.VISIBLE);
        }
        // 풀스크린 버튼 아이콘 토글
        if (binding.btnFullscreen != null) {
            binding.btnFullscreen.setImageResource(mFullscreen
                ? android.R.drawable.ic_menu_close_clear_cancel
                : android.R.drawable.ic_menu_crop);
        }
    }

    private void observeData() {
        locationViewModel.getFilteredLocations().observe(
                getViewLifecycleOwner(),
                new Observer<List<LocationWithAddress>>() {
                    @Override
                    public void onChanged(List<LocationWithAddress> locations) {
                        refreshMarkers(locations); // [지도항상] TAC 모드여도 위치 마커 정리(옛 SOS 제거)
                        // [목록가드] TAC/SURV/ALL은 전술목록 어댑터 사용 → 위치목록 갱신으로 덮어쓰지 않음
                        if (mCurrentMode == MODE_WEATHER) {
                            // [CLIMATE] 날씨 모드는 위치 갱신으로 목록을 덮지 않음
                            java.util.List<com.ah.acr.messagebox.WeatherStore.Weather> _wl = com.ah.acr.messagebox.WeatherStore.getAll();
                            binding.listLocation.setAdapter(mWeatherAdapter);
                            mWeatherAdapter.submit(_wl);
                            binding.listLocation.setVisibility(_wl.isEmpty() ? View.GONE : View.VISIBLE);
                            binding.emptyState.setVisibility(_wl.isEmpty() ? View.VISIBLE : View.GONE);
                            mMapView.invalidate();
                            return;
                        }
                        if (mCurrentMode == MODE_TACTICAL || mCurrentMode == MODE_SURVIVAL) {
                            java.util.List<TacticalStore.Entry> _lt = getLatestTacticalEntries(mCurrentMode);
                            binding.listLocation.setAdapter(mTacticalAdapter);
                            mTacticalAdapter.submit(_lt);
                            binding.listLocation.setVisibility(_lt.isEmpty() ? View.GONE : View.VISIBLE);
                            binding.emptyState.setVisibility(_lt.isEmpty() ? View.VISIBLE : View.GONE);
                            mMapView.invalidate();
                            return;
                        }
                        mAdapter.submitList(locations);

                        int count = locations != null ? locations.size() : 0;
                        binding.tvCount.setText(String.valueOf(count));
                        binding.tvPoints.setText(String.valueOf(count));

                        if (count == 0) {
                            binding.listLocation.setVisibility(View.GONE);
                            binding.emptyState.setVisibility(View.VISIBLE);
                        } else {
                            binding.listLocation.setVisibility(View.VISIBLE);
                            binding.emptyState.setVisibility(View.GONE);
                        }


                    }
                }
        );

        locationViewModel.getStartDate().observe(getViewLifecycleOwner(), date -> {
            if (date != null) binding.tvStartDate.setText(dateFmt.format(date));
        });

        locationViewModel.getEndDate().observe(getViewLifecycleOwner(), date -> {
            if (date != null) binding.tvEndDate.setText(dateFmt.format(date));
        });
    }


    private void setupFilterChips() {
        binding.chip1h.setOnClickListener(v -> selectQuickDate(v, 1, true));
        binding.chip24h.setOnClickListener(v -> selectQuickDate(v, 24, true));
        binding.chip3d.setOnClickListener(v -> selectQuickDate(v, 3, false));
        binding.chip7d.setOnClickListener(v -> selectQuickDate(v, 7, false));
        binding.chip30d.setOnClickListener(v -> selectQuickDate(v, 30, false));

        binding.chipAll.setOnClickListener(v -> selectModeChip(v, MODE_ALL));
        binding.chipTrack.setOnClickListener(v -> selectModeChip(v, MODE_TRACK));
        binding.chipSos.setOnClickListener(v -> selectModeChip(v, MODE_SOS));
        binding.chipTactical.setOnClickListener(v -> selectModeChip(v, MODE_TACTICAL));
        binding.chipSurvival.setOnClickListener(v -> selectModeChip(v, MODE_SURVIVAL));
        binding.chipWeather.setOnClickListener(v -> selectModeChip(v, MODE_WEATHER));
    }

    private void selectQuickDate(View chip, int value, boolean isHours) {
        binding.chip1h.setSelected(false);
        binding.chip24h.setSelected(false);
        binding.chip3d.setSelected(false);
        binding.chip7d.setSelected(false);
        binding.chip30d.setSelected(false);
        chip.setSelected(true);

        mInitialFitDone = false;

        if (isHours) locationViewModel.setQuickRange(value);
        else locationViewModel.setQuickDays(value);
    }

    private void selectModeChip(View chip, int mode) {
        binding.chipAll.setSelected(false);
        binding.chipTrack.setSelected(false);
        binding.chipSos.setSelected(false);
        binding.chipTactical.setSelected(false);
        binding.chipSurvival.setSelected(false);
        binding.chipWeather.setSelected(false);
        chip.setSelected(true);

        mInitialFitDone = false;
        mCurrentMode = mode;
        locationViewModel.setFilterMode(mode);
        renderTacticalOverlays();
        renderWeatherOverlays();

        // [TAC 목록] TAC 모드면 전술 목록 어댑터로 교체, 아니면 위치 목록
        if (mode == MODE_WEATHER) {
            // [CLIMATE] 날씨 목록 + 지도 반경 원
            if (mWeatherAdapter == null) {
                mWeatherAdapter = new com.ah.acr.messagebox.adapter.WeatherListAdapter(w -> {
                    if (w != null) {
                        try {
                            com.ah.acr.messagebox.WeatherDetailDialog.newInstance(w)
                                .show(getParentFragmentManager(), "WeatherDetail");
                        } catch (Exception ex) { android.util.Log.e(TAG, "WxDetail fail", ex); }
                    }
                });
            }
            if (binding.listLocation.getLayoutManager() == null) {
                binding.listLocation.setLayoutManager(new LinearLayoutManager(getContext()));
            }
            binding.listLocation.setAdapter(mWeatherAdapter);
            android.util.Log.d("WEATHER-LIST", "adapter=" + (mWeatherAdapter != null) + " lm=" + (binding.listLocation.getLayoutManager() != null));
            java.util.List<com.ah.acr.messagebox.WeatherStore.Weather> wl = com.ah.acr.messagebox.WeatherStore.getAll();
            mWeatherAdapter.submit(wl);
            android.util.Log.d("WEATHER-LIST", "날씨 모드 count=" + wl.size());
            binding.emptyState.setVisibility(wl.isEmpty() ? View.VISIBLE : View.GONE);
            binding.listLocation.setVisibility(wl.isEmpty() ? View.GONE : View.VISIBLE);
            renderWeatherOverlays();   // [2c] 지도 반경 원
            // TODO(2d): 상세지도
        } else if (mode == MODE_TACTICAL || mode == MODE_SURVIVAL) {
            binding.listLocation.setAdapter(mTacticalAdapter);
            java.util.List<TacticalStore.Entry> latest = getLatestTacticalEntries(mode);
            mTacticalAdapter.submit(latest);
            android.util.Log.d("SURV-LIST", "목록표시 mode=" + mode + " latest.size=" + latest.size() + " empty=" + latest.isEmpty());
            binding.listLocation.setVisibility(latest.isEmpty() ? View.GONE : View.VISIBLE);
            binding.emptyState.setVisibility(latest.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            binding.listLocation.setAdapter(mAdapter);
        }
    }

    /** [TAC 목록] 발신자(fromImei)별 최신 전술 1건씩. renderTacticalOverlays와 동일 기준. */
    private String cats(java.util.List<TacticalParser.TMarker> ms) { StringBuilder sb = new StringBuilder(); for (TacticalParser.TMarker m : ms) sb.append("[").append(m.cat).append("/st").append(m.survType).append("]"); return sb.toString(); }

    private java.util.List<TacticalStore.Entry> getLatestTacticalEntries(int mode) {
        java.util.Map<String, TacticalStore.Entry> latestByImei = new java.util.HashMap<>();
        for (TacticalStore.Entry e : TacticalStore.getAll()) {
            if (e.data == null) continue;
            boolean hasSurv = false, hasTac = false;
            for (TacticalParser.TMarker tm : e.data.markers) { if ("S".equals(tm.cat)) hasSurv = true; else hasTac = true; }
            if (!e.data.lines.isEmpty() || !e.data.measures.isEmpty()) hasTac = true;
            android.util.Log.d("SURV-LIST", "mode=" + mode + " hasSurv=" + hasSurv + " hasTac=" + hasTac + " markers=" + e.data.markers.size() + " cats=" + cats(e.data.markers));
            if (mode == MODE_SURVIVAL && !hasSurv) continue;
            if (mode == MODE_TACTICAL && !hasTac) continue;
            String key = ((e.data.fromImei == null) ? "" : e.data.fromImei) + "#" + (hasSurv ? "S" : "T");   // 종류 포함 → 생존/전술 서로 안 밀어냄
            TacticalStore.Entry cur = latestByImei.get(key);
            if (cur == null || e.recvAt > cur.recvAt) latestByImei.put(key, e);
        }
        java.util.List<TacticalStore.Entry> result = new java.util.ArrayList<>(latestByImei.values());
        return result;
    }


    private void setupDatePickers() {
        binding.btnStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.btnEndDate.setOnClickListener(v -> showDatePicker(false));
    }

    private void showDatePicker(boolean isStart) {
        Date current = isStart
                ? locationViewModel.getStartDate().getValue()
                : locationViewModel.getEndDate().getValue();

        Calendar cal = Calendar.getInstance();
        if (current != null) cal.setTime(current);

        new DatePickerDialog(requireContext(),
                (view, year, month, day) -> {
                    Calendar c = Calendar.getInstance();
                    c.set(year, month, day, isStart ? 0 : 23,
                            isStart ? 0 : 59, isStart ? 0 : 59);
                    if (isStart) locationViewModel.setStartDate(c.getTime());
                    else locationViewModel.setEndDate(c.getTime());
                    clearQuickChips();
                    mInitialFitDone = false;
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void clearQuickChips() {
        binding.chip1h.setSelected(false);
        binding.chip24h.setSelected(false);
        binding.chip3d.setSelected(false);
        binding.chip7d.setSelected(false);
        binding.chip30d.setSelected(false);
    }


    private void setupSearch() {
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                locationViewModel.setSearchText(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.editSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                enterSearchMode();
            } else {
                exitSearchMode();
            }
        });

        binding.editSearch.setOnEditorActionListener((v, actionId, event) -> {
            hideKeyboard();
            binding.editSearch.clearFocus();
            return true;
        });
    }


    private void enterSearchMode() {
        if (mIsSearchMode) return;
        mIsSearchMode = true;

        ViewGroup.LayoutParams mapParams = binding.mapContainer.getLayoutParams();
        if (mapParams instanceof android.widget.LinearLayout.LayoutParams) {
            android.widget.LinearLayout.LayoutParams lp =
                    (android.widget.LinearLayout.LayoutParams) mapParams;
            lp.weight = 0;
            lp.height = 0;
            binding.mapContainer.setLayoutParams(lp);
        }

        Log.v(TAG, "Search mode entered: map hidden");
    }


    private void exitSearchMode() {
        if (!mIsSearchMode) return;
        mIsSearchMode = false;

        ViewGroup.LayoutParams mapParams = binding.mapContainer.getLayoutParams();
        if (mapParams instanceof android.widget.LinearLayout.LayoutParams) {
            android.widget.LinearLayout.LayoutParams lp =
                    (android.widget.LinearLayout.LayoutParams) mapParams;
            lp.weight = 100;
            lp.height = 0;
            binding.mapContainer.setLayoutParams(lp);
        }

        Log.v(TAG, "Search mode exited: map restored");
    }


    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && binding != null) {
            imm.hideSoftInputFromWindow(binding.editSearch.getWindowToken(), 0);
        }
    }


    /**
     * ⭐ v6 patch (2026-05-03):
     * - Refresh button click: 기존 동작 (RECEIVED=? 송신 + UI 새로고침)
     * - Refresh button long-press: 전체 트랙 삭제 (모든 장비, 모든 기간)
     */
    private void setupRefresh() {
        binding.buttonReflesh.setOnClickListener(view -> {
            BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            locationViewModel.refresh();
            Toast.makeText(getContext(),
                    getString(R.string.devices_toast_refreshing),
                    Toast.LENGTH_SHORT).show();
        });

        // ⭐ v6: 길게 누르기 = 전체 삭제
        binding.buttonReflesh.setOnLongClickListener(view -> {
            showDeleteAllDialog();
            return true;  // 이벤트 소비 (단일 클릭 트리거 방지)
        });
    }


    // ═════════════════════════════════════════════════════════════
    //   ⭐ v6 일괄 삭제 (2026-05-03)
    // ═════════════════════════════════════════════════════════════

    /**
     * 휴지통 아이콘 클릭 시 다이얼로그 — 3 옵션
     * 1. 현재 기간만 삭제 (필터 적용된 startDate~endDate 범위)
     * 2. 전체 트랙 삭제 (이 장비의 모든 트랙)
     * 3. 취소
     */
    public void handleLocationDelClick(LocationEntity location) {
        if (location.getCodeNum() == null) {
            Toast.makeText(getContext(),
                    getString(R.string.devices_toast_invalid),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final String codeNum = location.getCodeNum();
        String displayName = codeNum;

        // 닉네임 찾기
        int count = mAdapter.getItemCount();
        for (int i = 0; i < count; i++) {
            LocationWithAddress item = mAdapter.getCurrentList().get(i);
            if (item.getLocation().getId() == location.getId()) {
                if (item.getAddress() != null
                        && item.getAddress().getNumbersNic() != null) {
                    displayName = item.getAddress().getNumbersNic();
                }
                break;
            }
        }

        final String finalDisplayName = displayName;
        String message = getString(R.string.devices_dialog_delete_choice_msg, finalDisplayName);

        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.devices_dialog_delete_title))
                .setMessage(message)
                .setPositiveButton(getString(R.string.devices_btn_delete_range), (dialog, which) -> {
                    // 현재 기간만 삭제
                    locationViewModel.deleteByCodeNumInCurrentRange(codeNum, deletedCount -> {
                        if (deletedCount >= 0) {
                            Toast.makeText(getContext(),
                                    getString(R.string.devices_toast_deleted_count, deletedCount),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(),
                                    getString(R.string.devices_toast_delete_failed),
                                    Toast.LENGTH_SHORT).show();
                        }
                        return null;
                    });
                })
                .setNeutralButton(getString(R.string.devices_btn_delete_all_device), (dialog, which) -> {
                    // 이 장비의 전체 트랙 삭제
                    locationViewModel.deleteAllByCodeNum(codeNum, deletedCount -> {
                        if (deletedCount >= 0) {
                            Toast.makeText(getContext(),
                                    getString(R.string.devices_toast_deleted_count, deletedCount),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(),
                                    getString(R.string.devices_toast_delete_failed),
                                    Toast.LENGTH_SHORT).show();
                        }
                        return null;
                    });
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    /**
     * 새로고침 버튼 길게 누름 → 전체 삭제 다이얼로그
     */
    private void showDeleteAllDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.devices_dialog_delete_all_title))
                .setMessage(getString(R.string.devices_dialog_delete_all_msg))
                .setPositiveButton(getString(R.string.devices_btn_delete_all_confirm), (dialog, which) -> {
                    locationViewModel.deleteAll(deletedCount -> {
                        if (deletedCount >= 0) {
                            Toast.makeText(getContext(),
                                    getString(R.string.devices_toast_deleted_count, deletedCount),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(),
                                    getString(R.string.devices_toast_delete_failed),
                                    Toast.LENGTH_SHORT).show();
                        }
                        return null;
                    });
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    public void handleLocationClick(LocationEntity location) {
        Log.v(TAG, "Click Item: " + location.getCodeNum());

        if (mIsSearchMode) {
            binding.editSearch.clearFocus();
            hideKeyboard();
        }

        if (location.getLatitude() != null && location.getLongitude() != null) {
            GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
            mMapView.getController().animateTo(point);
            mMapView.getController().setZoom(DEFAULT_ZOOM_SINGLE);

            for (Marker m : mMarkers) {
                if (m.getPosition().getLatitude() == location.getLatitude()
                        && m.getPosition().getLongitude() == location.getLongitude()) {
                    m.showInfoWindow();
                    break;
                }
            }
        }
    }

    public void handleLocationCopyClick(LocationEntity location) {
        String loc = String.format(Locale.US, "%f,%f",
                location.getLatitude(), location.getLongitude());
        ClipboardManager clipboard = (ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("copy", loc);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(),
                getString(R.string.devices_toast_copied),
                Toast.LENGTH_SHORT).show();
    }

    public void handleLocationMapClick(LocationEntity location) {
        if (location.getCodeNum() == null) {
            Toast.makeText(getContext(),
                    getString(R.string.devices_toast_invalid),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (mIsSearchMode) {
            binding.editSearch.clearFocus();
            hideKeyboard();
        }

        String name = location.getCodeNum();
        int count = mAdapter.getItemCount();
        for (int i = 0; i < count; i++) {
            LocationWithAddress item = mAdapter.getCurrentList().get(i);
            if (item.getLocation().getId() == location.getId()) {
                if (item.getAddress() != null
                        && item.getAddress().getNumbersNic() != null) {
                    name = item.getAddress().getNumbersNic();
                }
                break;
            }
        }

        DeviceTrackDetailFragment dialog = DeviceTrackDetailFragment.newInstance(
                location.getCodeNum(), name
        );
        dialog.show(getParentFragmentManager(), "DeviceTrackDetail");
    }

    public void handleAddressClick(LocationWithAddress location) {
        showAddressDialog(location);
    }


    private void showAddressDialog(LocationWithAddress location) {
        Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.dialog_address);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etName = dialog.findViewById(R.id.et_name);
        EditText etCode = dialog.findViewById(R.id.et_code);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_save);
        ImageView btnClose = dialog.findViewById(R.id.btn_close);

        if (location.getAddress() == null) {
            etName.setText(location.getLocation().getCodeNum());
        } else {
            etName.setText(location.getAddress().getNumbersNic());
        }
        etCode.setText(location.getLocation().getCodeNum());
        etCode.setEnabled(false);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String code = etCode.getText().toString().trim();

            if (name.isEmpty() || code.isEmpty()) {
                Toast.makeText(getContext(),
                        getString(R.string.devices_toast_fill_all),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (location.getAddress() == null) {
                addressViewModel.insert(new AddressEntity(0, code, name, new Date(), null));
            } else {
                addressViewModel.updateNumbersNic(code, name);
            }

            dialog.dismiss();
            Toast.makeText(getContext(),
                    getString(R.string.devices_toast_saved),
                    Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }


    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
        // 전술 데이터 DB 로드 후 지도에 렌더 (재시작/탭전환에도 유지)
        new Thread(() -> {
            TacticalStore.loadFromDb(getContext());
            if (getActivity() != null) getActivity().runOnUiThread(this::renderTacticalOverlays);
        }).start();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mMapView != null) mMapView.onPause();

        if (mIsSearchMode) {
            exitSearchMode();
            hideKeyboard();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mMapView != null) {
            mMapView.onDetach();
            mMapView = null;
        }
        mMarkers.clear();
        binding = null;
    }
}
