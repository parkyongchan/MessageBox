package com.ah.acr.messagebox;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ah.acr.messagebox.util.MapModeManager;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MapEventsOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tactical Map - full screen tactical overlay map.
 * C: map(online/offline) + close/zoom/toggle.
 * D: markers (7 types, place/clear).
 * E: save/share/send (TODO).
 */
public class TacticalMapActivity extends AppCompatActivity {

    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(37.5665, 126.9780);
    private static final double DEFAULT_ZOOM = 15.0;

    private static final String[] MK_NAMES = {
            "HOSTILE (적)", "FRIENDLY (아군)", "UNKNOWN (미상)",
            "NEUTRAL (중립)", "POI (관심지점)", "ENGAGED (교전)", "THREAT (위협)"
    };
    private static final int[] MK_ICONS = {
            R.drawable.ic_tac_hostile, R.drawable.ic_tac_friendly,
            R.drawable.ic_tac_unknown, R.drawable.ic_tac_neutral,
            R.drawable.ic_tac_poi, R.drawable.ic_tac_engaged,
            R.drawable.ic_tac_threat
    };

    private MapView mMapView;
    private TextView mZoomLabel;
    private TextView mBtnOnline;
    private TextView mBtnOffline;
    private TextView mToolMarker;

    private int mMarkerType = -1;
    private final List<Marker> mTacMarkers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());
        File osmDir = getExternalFilesDir(null);
        if (osmDir != null) {
            Configuration.getInstance().setOsmdroidBasePath(osmDir);
            Configuration.getInstance().setOsmdroidTileCache(new File(osmDir, "cache"));
        }

        setContentView(R.layout.activity_tactical_map);

        setupMap();
        setupTopBar();
        setupZoomControls();
        setupModeToggle();
        setupMarkerTools();
        setupActionStubs();
    }

    private void setupMap() {
        mMapView = findViewById(R.id.tac_map);
        mZoomLabel = findViewById(R.id.tac_zoom);

        mMapView.setMultiTouchControls(true);
        mMapView.setBuiltInZoomControls(false);

        MapModeManager.applyToMapView(this, mMapView);

        mMapView.getController().setZoom(DEFAULT_ZOOM);
        mMapView.getController().setCenter(DEFAULT_CENTER);

        mMapView.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent event) { return false; }
            @Override public boolean onZoom(ZoomEvent event) {
                updateZoomLabel();
                return false;
            }
        });

        MapEventsReceiver receiver = new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (mMarkerType >= 0) {
                    placeMarker(p, mMarkerType);
                    return true;
                }
                return false;
            }
            @Override public boolean longPressHelper(GeoPoint p) { return false; }
        };
        mMapView.getOverlays().add(0, new MapEventsOverlay(receiver));

        updateZoomLabel();
    }

    private void updateZoomLabel() {
        if (mMapView != null && mZoomLabel != null) {
            int zoom = (int) mMapView.getZoomLevelDouble();
            mZoomLabel.setText("ZOOM " + zoom);
        }
    }

    private void setupTopBar() {
        ImageButton close = findViewById(R.id.btn_tac_close);
        close.setOnClickListener(v -> finish());
    }

    private void setupZoomControls() {
        ImageButton zin = findViewById(R.id.tac_zoom_in);
        ImageButton zout = findViewById(R.id.tac_zoom_out);
        zin.setOnClickListener(v -> { if (mMapView != null) mMapView.getController().zoomIn(); });
        zout.setOnClickListener(v -> { if (mMapView != null) mMapView.getController().zoomOut(); });
    }

    private void setupModeToggle() {
        mBtnOnline = findViewById(R.id.btn_tac_online);
        mBtnOffline = findViewById(R.id.btn_tac_offline);

        refreshModeUi();

        mBtnOnline.setOnClickListener(v -> {
            MapModeManager.setMode(this, MapModeManager.Mode.ONLINE);
            MapModeManager.applyToMapView(this, mMapView);
            refreshModeUi();
        });
        mBtnOffline.setOnClickListener(v -> {
            MapModeManager.setMode(this, MapModeManager.Mode.OFFLINE);
            MapModeManager.Mode applied = MapModeManager.applyToMapView(this, mMapView);
            if (applied == MapModeManager.Mode.ONLINE) {
                Toast.makeText(this, "오프라인 지도(MBTiles) 없음 - 온라인 사용", Toast.LENGTH_SHORT).show();
            }
            refreshModeUi();
        });
    }

    private void refreshModeUi() {
        boolean online = MapModeManager.isOnline(this);
        if (online) {
            mBtnOnline.setBackgroundColor(0xFF00E5D1);
            mBtnOnline.setTextColor(0xFF0A1628);
            mBtnOffline.setBackgroundColor(0x00000000);
            mBtnOffline.setTextColor(0xFF95B0D4);
        } else {
            mBtnOffline.setBackgroundColor(0xFF00E5D1);
            mBtnOffline.setTextColor(0xFF0A1628);
            mBtnOnline.setBackgroundColor(0x00000000);
            mBtnOnline.setTextColor(0xFF95B0D4);
        }
    }

    private void setupMarkerTools() {
        mToolMarker = findViewById(R.id.tac_tool_marker);
        mToolMarker.setOnClickListener(v -> showMarkerTypeDialog());

        TextView clear = findViewById(R.id.tac_tool_clear);
        clear.setOnClickListener(v -> clearMarkers());

        TextView line = findViewById(R.id.tac_tool_line);
        if (line != null) line.setOnClickListener(v ->
                Toast.makeText(this, "준비중 (LINE)", Toast.LENGTH_SHORT).show());
        TextView measure = findViewById(R.id.tac_tool_measure);
        if (measure != null) measure.setOnClickListener(v ->
                Toast.makeText(this, "준비중 (MEASURE)", Toast.LENGTH_SHORT).show());
    }

    private void showMarkerTypeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("마커 종류 선택")
                .setItems(MK_NAMES, (dialog, which) -> {
                    mMarkerType = which;
                    mToolMarker.setTextColor(0xFFFFEB3B);
                    Toast.makeText(this,
                            MK_NAMES[which] + " - 지도를 탭하세요", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", (d, w) -> {
                    mMarkerType = -1;
                    mToolMarker.setTextColor(0xFF00E5D1);
                })
                .show();
    }

    private void placeMarker(GeoPoint p, int type) {
        Marker marker = new Marker(mMapView);
        marker.setPosition(p);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle(MK_NAMES[type]);
        Drawable icon = ContextCompat.getDrawable(this, MK_ICONS[type]);
        if (icon != null) marker.setIcon(icon);
        marker.setOnMarkerClickListener((m, mv) -> {
            new AlertDialog.Builder(this)
                    .setTitle(m.getTitle())
                    .setMessage(String.format("위도 %.5f\n경도 %.5f",
                            m.getPosition().getLatitude(), m.getPosition().getLongitude()))
                    .setPositiveButton("확인", null)
                    .setNegativeButton("삭제", (d, w) -> {
                        mMapView.getOverlays().remove(m);
                        mTacMarkers.remove(m);
                        mMapView.invalidate();
                    })
                    .show();
            return true;
        });
        mTacMarkers.add(marker);
        mMapView.getOverlays().add(marker);
        mMapView.invalidate();
    }

    private void clearMarkers() {
        if (mTacMarkers.isEmpty()) {
            Toast.makeText(this, "마커 없음", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("마커 전체 삭제")
                .setMessage("마커 " + mTacMarkers.size() + "개를 모두 지울까요?")
                .setPositiveButton("삭제", (d, w) -> {
                    for (Marker m : mTacMarkers) mMapView.getOverlays().remove(m);
                    mTacMarkers.clear();
                    mMarkerType = -1;
                    mToolMarker.setTextColor(0xFF00E5D1);
                    mMapView.invalidate();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void setupActionStubs() {
        int[] actIds = { R.id.tac_act_save, R.id.tac_act_share, R.id.tac_act_send };
        for (int id : actIds) {
            TextView tv = findViewById(id);
            if (tv != null) tv.setOnClickListener(v ->
                    Toast.makeText(this, "준비중 (E단계)", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMapView != null) mMapView.onPause();
    }
}