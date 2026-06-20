package com.ah.acr.messagebox;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ah.acr.messagebox.util.MapModeManager;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.io.File;

/**
 * Tactical Map - full screen tactical overlay map.
 * C단계: 화면 뼈대 + 지도(온/오프라인) + 닫기/줌/토글.
 * D단계(마커), E단계(저장/공유/전송)는 추후.
 */
public class TacticalMapActivity extends AppCompatActivity {

    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(37.5665, 126.9780);
    private static final double DEFAULT_ZOOM = 15.0;

    private MapView mMapView;
    private TextView mZoomLabel;
    private TextView mBtnOnline;
    private TextView mBtnOffline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMDroid config (must be before setContentView with MapView)
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
        setupToolAndActionStubs();
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

    private void setupToolAndActionStubs() {
        // D단계(마커/선/측정/지우기) - 추후 구현, 지금은 안내
        int[] toolIds = {
                R.id.tac_tool_marker, R.id.tac_tool_line,
                R.id.tac_tool_measure, R.id.tac_tool_clear
        };
        for (int id : toolIds) {
            TextView tv = findViewById(id);
            if (tv != null) tv.setOnClickListener(v ->
                    Toast.makeText(this, "준비중 (D단계)", Toast.LENGTH_SHORT).show());
        }

        // E단계(저장/공유/전송) - 추후 구현, 지금은 안내
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