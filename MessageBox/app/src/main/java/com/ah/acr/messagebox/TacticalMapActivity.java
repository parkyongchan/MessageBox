package com.ah.acr.messagebox;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.content.ContentValues;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import androidx.lifecycle.ViewModelProvider;
import java.io.ByteArrayOutputStream;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import androidx.core.app.ActivityCompat;
import java.io.FileOutputStream;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.MapEventsOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TacticalMapActivity extends AppCompatActivity {

    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(37.5665, 126.9780);
    private static final double DEFAULT_ZOOM = 15.0;

    private static final String[] MK_NAMES = {
            "HOSTILE (적)", "FRIENDLY (아군)", "UNKNOWN (미상)",
            "NEUTRAL (중립)", "POI (관심지점)", "ENGAGED (교전)", "THREAT (위협)"
    };
    private static final String[] MK_SHORT = {
            "HOSTILE", "FRIENDLY", "UNKNOWN", "NEUTRAL", "POI", "ENGAGED", "THREAT"
    };
    private static final int[] MK_ICONS = {
            R.drawable.ic_tac_hostile, R.drawable.ic_tac_friendly,
            R.drawable.ic_tac_unknown, R.drawable.ic_tac_neutral,
            R.drawable.ic_tac_poi, R.drawable.ic_tac_engaged,
            R.drawable.ic_tac_threat
    };
    private static final int[] MK_COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFFFDD835,
            0xFF43A047, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00
    };

    private static final String[] UNIT_NAMES = {
            "Infantry (보병)", "Armor (기갑)", "Artillery (포병)",
            "UAV/Drone (드론)", "Recon (정찰)",
            "Air Defense (방공)", "HQ (본부)", "Medical (의무)"
    };
    private static final String[] UNIT_ABBR = { "INF", "ARM", "ART", "UAV", "REC", "AD", "HQ", "MED" };
    private static final String[] PLACE_NAMES = {
            "Building (건물)", "Bridge (교량)", "Helipad (헬기장)", "Checkpoint (검문소)"
    };
    private static final String[] PLACE_ABBR = { "BLD", "BRG", "HEL", "CKP" };

    private static class TacMarker {
        Marker marker;
        int type;
        int id;  // 고유 ID (트랙용, 삭제해도 유지)
        int unitType = -1;
        int placeType = -1;  // POI 전용: -1=none, 0~3
        GeoPoint point;
        TacMarker(Marker m, int t, GeoPoint p) { marker = m; type = t; point = p; }
    }

    // 앱 종료 전까지 마커 유지 (static, 재시작시 리셋)
    static class MarkerData {
        int id, type, unitType, placeType;
        double lat, lon;
        MarkerData(int id, int type, int unitType, int placeType, double lat, double lon) {
            this.id = id; this.type = type; this.unitType = unitType;
            this.placeType = placeType; this.lat = lat; this.lon = lon;
        }
    }
    private static final java.util.List<MarkerData> sMarkerData = new java.util.ArrayList<>();
    private static int sNextMarkerId = 1;

    private static class MeasureSet {
        Marker startDot;
        Polyline line;
        Marker label;
    }

    private static class LineSet {
        Polyline line;
        Marker label;
        java.util.List<Marker> dots = new java.util.ArrayList<>();
    }

    private MapView mMapView;
    private TextView mZoomLabel;
    private TextView mBtnOnline;
    private TextView mBtnOffline;
    private TextView mToolMarker;
    private TextView mCoordToggle;
    private LinearLayout mLegend;

    private int mMarkerType = -1;
    private boolean mShowCoords = false;
    private final List<TacMarker> mTacMarkers = new ArrayList<>();
    private int mNextMarkerId = 1;  // 고유 ID 카운터
    private boolean mRestoring = false;  // 복원 중이면 static에 재추가 안 함

    private android.location.LocationManager mLocMgr;
    private Marker mMyLocMarker;
    private boolean mMyLocOn = false;
    private boolean mMeasureMode = false;
    private GeoPoint mMeasureFirst = null;
    private final java.util.List<MeasureSet> mMeasureSets = new java.util.ArrayList<>();
    private Marker mPendingDot = null;
    private TextView mToolMeasure;
    private boolean mLineMode = false;
    private TextView mToolLine;
    private final java.util.List<GeoPoint> mLinePoints = new java.util.ArrayList<>();
    private final java.util.List<Marker> mLineDots = new java.util.ArrayList<>();
    private Polyline mLineCurrent = null;
    private Marker mLineLabel = null;
    private final java.util.List<LineSet> mLineSets = new java.util.ArrayList<>();
    private android.location.LocationListener mLocListener;
    private static final int REQ_LOC = 9001;
    private AddressViewModel mAddressVM;
    private java.util.List<AddressEntity> mAddressList = new java.util.ArrayList<>();

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
        setupCoordToggle();
        setupMarkerTools();
        setupActionStubs();
        setupMyLocation();

        restoreMarkers();  // 앱 종료 전 마커 복원
        mAddressVM = new ViewModelProvider(this).get(AddressViewModel.class);
        mAddressVM.getAllAddress().observe(this, list -> {
            if (list != null) mAddressList = list;
        });
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
                if (mLineMode) {
                    addLinePoint(p);
                    return true;
                }
                if (mMeasureMode) {
                    addMeasurePoint(p);
                    return true;
                }
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
                Toast.makeText(this, "No offline map (MBTiles) - using online", Toast.LENGTH_SHORT).show();
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

    private void setupCoordToggle() {
        mCoordToggle = findViewById(R.id.tac_coord_toggle);
        mLegend = findViewById(R.id.tac_legend);
        refreshCoordToggleUi();
        mCoordToggle.setOnClickListener(v -> {
            mShowCoords = !mShowCoords;
            refreshCoordToggleUi();
            rebuildAllMarkerIcons();
            updateLegend();
            Toast.makeText(this,
                    mShowCoords ? "Coords ON" : "Coords OFF",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshCoordToggleUi() {
        if (mCoordToggle == null) return;
        mCoordToggle.setTextColor(mShowCoords ? 0xFFFFEB3B : 0xFF95B0D4);
    }

    private void rebuildAllMarkerIcons() {
        for (int i = 0; i < mTacMarkers.size(); i++) {
            TacMarker tm = mTacMarkers.get(i);
            tm.marker.setIcon(makeMarkerIcon(tm.type, i + 1, tm.unitType, tm.placeType));
        }
        if (mMapView != null) mMapView.invalidate();
    }

    private void updateLegend() {
        if (mLegend == null) return;
        mLegend.removeAllViews();
        boolean hasMyLoc = (mMyLocOn && mMyLocMarker != null);
        if (!mShowCoords || (mTacMarkers.isEmpty() && !hasMyLoc)) {
            mLegend.setVisibility(View.GONE);
            return;
        }
        mLegend.setVisibility(View.VISIBLE);
        // My location row (top)
        if (hasMyLoc) {
            TextView my = new TextView(this);
            my.setText(String.format(Locale.US, "MY LOC  %.5f, %.5f",
                    mMyLocMarker.getPosition().getLatitude(),
                    mMyLocMarker.getPosition().getLongitude()));
            my.setTextColor(0xFF2196F3);
            my.setTextSize(9f);
            my.setTypeface(my.getTypeface(), android.graphics.Typeface.BOLD);
            mLegend.addView(my);
        }
        // Marker rows
        for (int i = 0; i < mTacMarkers.size(); i++) {
            TacMarker tm = mTacMarkers.get(i);
            TextView row = new TextView(this);
            String unitStr;
            if (tm.type == 4 && tm.placeType >= 0) {
                unitStr = " [" + PLACE_ABBR[tm.placeType] + "]";
            } else if (tm.unitType >= 0) {
                unitStr = " [" + UNIT_ABBR[tm.unitType] + "]";
            } else {
                unitStr = "";
            }
            String txt = String.format(Locale.US, "%d. %s%s  %.5f, %.5f",
                    i + 1, MK_SHORT[tm.type], unitStr,
                    tm.point.getLatitude(), tm.point.getLongitude());
            row.setText(txt);
            row.setTextColor(MK_COLORS[tm.type]);
            row.setTextSize(9f);
            row.setTypeface(row.getTypeface(), android.graphics.Typeface.BOLD);
            mLegend.addView(row);
        }
    }
    private void setupMarkerTools() {
        mToolMarker = findViewById(R.id.tac_tool_marker);
        mToolMarker.setOnClickListener(v -> showMarkerTypeDialog());

        TextView clear = findViewById(R.id.tac_tool_clear);
        clear.setOnClickListener(v -> clearMarkers());

        mToolLine = findViewById(R.id.tac_tool_line);
        if (mToolLine != null) mToolLine.setOnClickListener(v -> toggleLineMode());
        mToolMeasure = findViewById(R.id.tac_tool_measure);
        if (mToolMeasure != null) mToolMeasure.setOnClickListener(v -> toggleMeasureMode());
    }

    private void showMarkerTypeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Select Marker Type")
                .setItems(MK_NAMES, (dialog, which) -> {
                    mMarkerType = which;
                    mToolMarker.setTextColor(0xFFFFEB3B);
                    Toast.makeText(this,
                            MK_NAMES[which] + " - tap the map", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (d, w) -> {
                    mMarkerType = -1;
                    mToolMarker.setTextColor(0xFF00E5D1);
                })
                .show();
    }

    private Drawable makeMarkerIcon(int type, int number, int unitType, int placeType) {
        Drawable base = ContextCompat.getDrawable(this, MK_ICONS[type]);
        if (base == null) return null;

        int iconSize = dp(22);
        int badge = dp(12);
        int totalW = iconSize;
        int totalH = iconSize + badge;

        Bitmap bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        base.setBounds(0, badge, iconSize, badge + iconSize);
        base.draw(c);

        if (type == 4 && placeType >= 0) {
            drawPlaceSymbol(c, placeType, iconSize, badge);
        } else if (unitType >= 0) {
            drawUnitSymbol(c, unitType, iconSize, badge);
        }

        String num = String.valueOf(number);
        Paint numBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        numBg.setColor(0xFF0A1628);
        Paint numBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        numBorder.setColor(0xFFFFEB3B);
        numBorder.setStyle(Paint.Style.STROKE);
        numBorder.setStrokeWidth(dp(1.5f));

        float cx = totalW / 2f;
        float cy = badge / 2f + dp(1);
        float r = badge / 2f;
        c.drawCircle(cx, cy, r, numBg);
        c.drawCircle(cx, cy, r, numBorder);

        Paint numText = new Paint(Paint.ANTI_ALIAS_FLAG);
        numText.setColor(Color.WHITE);
        numText.setTextSize(dp(8));
        numText.setFakeBoldText(true);
        numText.setTextAlign(Paint.Align.CENTER);
        float ty = cy - (numText.descent() + numText.ascent()) / 2f;
        c.drawText(num, cx, ty, numText);

        return new BitmapDrawable(getResources(), bmp);
    }

    private void drawPlaceSymbol(Canvas c, int placeType, int iconSize, int badge) {
        Paint sym = new Paint(Paint.ANTI_ALIAS_FLAG);
        sym.setColor(0xFF0A1628);
        sym.setStyle(Paint.Style.STROKE);
        sym.setStrokeWidth(dp(2));
        float left = iconSize * 0.30f;
        float right = iconSize * 0.70f;
        float top = badge + iconSize * 0.32f;
        float bot = badge + iconSize * 0.60f;
        float cx = iconSize / 2f;
        float cy = badge + iconSize * 0.46f;
        switch (placeType) {
            case 0:
                c.drawRect(left, top, right, bot, sym);
                break;
            case 1:
                c.drawLine(left, top + (bot-top)*0.3f, right, top + (bot-top)*0.3f, sym);
                c.drawLine(left, top + (bot-top)*0.7f, right, top + (bot-top)*0.7f, sym);
                break;
            case 2: {
                Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
                tp.setColor(0xFF0A1628);
                tp.setTextSize(iconSize * 0.34f);
                tp.setFakeBoldText(true);
                tp.setTextAlign(Paint.Align.CENTER);
                float ty = cy - (tp.descent() + tp.ascent()) / 2f;
                c.drawText("H", cx, ty, tp);
                break;
            }
            case 3:
                c.drawLine(left, top, right, top, sym);
                c.drawLine(left, top, cx, bot, sym);
                c.drawLine(right, top, cx, bot, sym);
                break;
        }
    }

    private void drawUnitSymbol(Canvas c, int unitType, int iconSize, int badge) {
        Paint sym = new Paint(Paint.ANTI_ALIAS_FLAG);
        sym.setColor(Color.WHITE);
        sym.setStyle(Paint.Style.STROKE);
        sym.setStrokeWidth(dp(2));
        float left = iconSize * 0.28f;
        float right = iconSize * 0.72f;
        float top = badge + iconSize * 0.30f;
        float bot = badge + iconSize * 0.62f;
        float cx = iconSize / 2f;
        float cy = badge + iconSize * 0.46f;
        switch (unitType) {
            case 0:
                c.drawLine(left, top, right, bot, sym);
                c.drawLine(right, top, left, bot, sym);
                break;
            case 1:
                c.drawOval(left, top, right, bot, sym);
                break;
            case 2: {
                Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setColor(Color.WHITE);
                c.drawCircle(cx, cy, iconSize * 0.13f, fill);
                break;
            }
            case 3: {
                c.drawLine(left, cy, right, cy, sym);
                Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
                wp.setColor(Color.WHITE);
                c.drawCircle(left, cy, dp(2), wp);
                c.drawCircle(right, cy, dp(2), wp);
                break;
            }
            case 4:
                c.drawLine(left, bot, right, top, sym);
                break;
            case 5:
                c.drawLine(left, bot, cx, top, sym);
                c.drawLine(cx, top, right, bot, sym);
                break;
            case 6: {
                float poleX = left + dp(1);
                c.drawLine(poleX, top, poleX, bot, sym);
                Paint flag = new Paint(Paint.ANTI_ALIAS_FLAG);
                flag.setColor(Color.WHITE);
                c.drawRect(poleX, top, poleX + (right - left) * 0.5f, top + (bot - top) * 0.4f, flag);
                break;
            }
            case 7:
                c.drawLine(cx, top, cx, bot, sym);
                c.drawLine(left, cy, right, cy, sym);
                break;
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void placeMarker(GeoPoint p, int type) {
        Marker marker = new Marker(mMapView);
        marker.setPosition(p);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle(MK_NAMES[type]);
        marker.setDraggable(true);

        final TacMarker tm = new TacMarker(marker, type, p);
        mTacMarkers.add(tm);
        int number = mTacMarkers.size();
        if (!mRestoring) {
            tm.id = sNextMarkerId++;
            sMarkerData.add(new MarkerData(tm.id, type, tm.unitType, tm.placeType,
                    p.getLatitude(), p.getLongitude()));
        }

        Drawable icon = makeMarkerIcon(type, number, tm.unitType, tm.placeType);
        if (icon != null) marker.setIcon(icon);

        marker.setOnMarkerClickListener((m, mv) -> {
            int idx = mTacMarkers.indexOf(tm) + 1;
            new AlertDialog.Builder(this)
                    .setTitle("#" + idx + " " + m.getTitle())
                    .setMessage(String.format(Locale.US, "Lat %.5f\nLon %.5f",
                            m.getPosition().getLatitude(), m.getPosition().getLongitude()))
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Unit Type", (d, w) -> showUnitTypeDialog(tm))
                    .setNegativeButton("Delete", (d, w) -> {
                        mMapView.getOverlays().remove(m);
                        mTacMarkers.remove(tm);
                        for (int k = sMarkerData.size() - 1; k >= 0; k--) {
                            if (sMarkerData.get(k).id == tm.id) { sMarkerData.remove(k); break; }
                        }
                        rebuildAllMarkerIcons();
                        updateLegend();
                        mMapView.invalidate();
                    })
                    .show();
            return true;
        });

        marker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override public void onMarkerDrag(Marker m) { }
            @Override public void onMarkerDragEnd(Marker m) {
                tm.point = m.getPosition();
                for (MarkerData md : sMarkerData) {
                    if (md.id == tm.id) {
                        md.lat = m.getPosition().getLatitude();
                        md.lon = m.getPosition().getLongitude();
                        break;
                    }
                }
                updateLegend();
                mMapView.invalidate();
                Toast.makeText(TacticalMapActivity.this,
                        String.format(Locale.US, "#%d moved: %.5f, %.5f",
                                tm.id, m.getPosition().getLatitude(), m.getPosition().getLongitude()),
                        Toast.LENGTH_SHORT).show();
            }
            @Override public void onMarkerDragStart(Marker m) { }
        });
        mMapView.getOverlays().add(marker);
        updateLegend();
        mMapView.invalidate();
    }

    private void restoreMarkers() {
        if (sMarkerData.isEmpty()) return;
        mRestoring = true;
        for (MarkerData md : sMarkerData) {
            GeoPoint gp = new GeoPoint(md.lat, md.lon);
            placeMarker(gp, md.type);
            TacMarker tm = mTacMarkers.get(mTacMarkers.size() - 1);
            tm.id = md.id;
            tm.unitType = md.unitType;
            tm.placeType = md.placeType;
        }
        mRestoring = false;
        rebuildAllMarkerIcons();
        updateLegend();
        mMapView.invalidate();
    }

    private void showPlaceTypeDialog(TacMarker tm) {
        String[] opts = new String[PLACE_NAMES.length + 1];
        opts[0] = "None (없음)";
        for (int k = 0; k < PLACE_NAMES.length; k++) opts[k + 1] = PLACE_NAMES[k];
        new AlertDialog.Builder(this)
                .setTitle("Place Type")
                .setItems(opts, (d, which) -> {
                    tm.placeType = which - 1;
                    rebuildAllMarkerIcons();
                    mMapView.invalidate();
                    String nm = (which == 0) ? "None" : PLACE_ABBR[which - 1];
                    Toast.makeText(this, "Place: " + nm, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showUnitTypeDialog(TacMarker tm) {
        if (tm.type == 4) {  // POI는 병종 없음
            showPlaceTypeDialog(tm);
            return;
        }
        String[] opts = new String[UNIT_NAMES.length + 1];
        opts[0] = "None (없음)";
        for (int k = 0; k < UNIT_NAMES.length; k++) opts[k + 1] = UNIT_NAMES[k];
        new AlertDialog.Builder(this)
                .setTitle("Unit Type")
                .setItems(opts, (d, which) -> {
                    tm.unitType = which - 1;  // 0=None→-1
                    rebuildAllMarkerIcons();
                    mMapView.invalidate();
                    String nm = (which == 0) ? "None" : UNIT_ABBR[which - 1];
                    Toast.makeText(this, "Unit: " + nm, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearMarkers() {
        boolean hasMarkers = !mTacMarkers.isEmpty();
        boolean hasMeasures = !mMeasureSets.isEmpty() || mPendingDot != null;
        boolean hasLines = !mLineSets.isEmpty() || !mLinePoints.isEmpty();
        if (!hasMarkers && !hasMeasures && !hasLines) {
            Toast.makeText(this, "Nothing to clear", Toast.LENGTH_SHORT).show();
            return;
        }
        String msg = "Delete " + mTacMarkers.size() + " marker(s) and "
                + mMeasureSets.size() + " measurement(s), "
                + mLineSets.size() + " line(s)?";
        new AlertDialog.Builder(this)
                .setTitle("Clear All")
                .setMessage(msg)
                .setPositiveButton("Delete", (d, w) -> {
                    for (TacMarker tm : mTacMarkers) mMapView.getOverlays().remove(tm.marker);
                    mTacMarkers.clear();
                    mMarkerType = -1;
                    if (mToolMarker != null) mToolMarker.setTextColor(0xFF00E5D1);
                    clearAllMeasures();
                    clearAllLines();
                    updateLegend();
                    mMapView.invalidate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupMyLocation() {
        android.widget.ImageButton btn = findViewById(R.id.tac_my_location);
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            if (mMyLocOn) {
                stopMyLocation();
            } else {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
                    return;
                }
                startMyLocation();
            }
        });
    }

    private void startMyLocation() {
        try {
            if (mLocMgr == null)
                mLocMgr = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            if (mLocListener == null) {
                mLocListener = new android.location.LocationListener() {
                    @Override public void onLocationChanged(android.location.Location loc) {
                        showMyLocation(loc);
                    }
                    @Override public void onProviderEnabled(String p) {}
                    @Override public void onProviderDisabled(String p) {}
                    @Override public void onStatusChanged(String p, int s, android.os.Bundle b) {}
                };
            }
            android.location.Location last =
                    mLocMgr.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            if (last != null) showMyLocation(last);
            mLocMgr.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER, 2000, 5f, mLocListener);
            mMyLocOn = true;
            android.widget.ImageButton btn = findViewById(R.id.tac_my_location);
            if (btn != null) btn.setColorFilter(0xFFFFEB3B);
            Toast.makeText(this, "My Location ON", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopMyLocation() {
        if (mLocMgr != null && mLocListener != null) {
            try { mLocMgr.removeUpdates(mLocListener); } catch (Exception ignored) {}
        }
        if (mMyLocMarker != null) {
            mMapView.getOverlays().remove(mMyLocMarker);
            mMyLocMarker = null;
            mMapView.invalidate();
        }
        mMyLocOn = false;
        updateLegend();
        android.widget.ImageButton btn = findViewById(R.id.tac_my_location);
        if (btn != null) btn.setColorFilter(0xFFFFFFFF);
    }

    private void showMyLocation(android.location.Location loc) {
        GeoPoint p = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        if (mMyLocMarker == null) {
            mMyLocMarker = new Marker(mMapView);
            mMyLocMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            mMyLocMarker.setTitle("MY LOCATION");
            Drawable dot = ContextCompat.getDrawable(this, R.drawable.ic_my_location_dot);
            if (dot != null) mMyLocMarker.setIcon(dot);
            mMapView.getOverlays().add(mMyLocMarker);
        }
        mMyLocMarker.setPosition(p);
        mMapView.getController().animateTo(p);
        mMapView.invalidate();
        updateLegend();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_LOC && results.length > 0
                && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startMyLocation();
        }
    }

    private void sendTactical() {
        String[] modes = { "Send as Photo (map image)", "Send as Tactical Data (markers)" };
        new AlertDialog.Builder(this)
                .setTitle("Send Mode")
                .setItems(modes, (dialog, which) -> {
                    if (which == 0) {
                        sendAsPhoto();
                    } else {
                        sendAsTacticalData();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // [전술데이터] 마커/라인/메저 → 직렬화 텍스트 (프로토콜 v1.3)
    private void sendAsTacticalData() {
        if (mTacMarkers.isEmpty() && mLineSets.isEmpty() && mMeasureSets.isEmpty()) {
            Toast.makeText(this, "No tactical data to send", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mAddressList == null || mAddressList.isEmpty()) {
            Toast.makeText(this, "No recipients (address book empty)", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = new String[mAddressList.size()];
        for (int i = 0; i < mAddressList.size(); i++) {
            AddressEntity a = mAddressList.get(i);
            String nic = a.getNumbersNic();
            names[i] = (nic != null && !nic.isEmpty()) ? nic : a.getNumbers();
        }
        new AlertDialog.Builder(this)
                .setTitle("Select Recipient")
                .setItems(names, (dialog, which) -> {
                    AddressEntity sel = mAddressList.get(which);
                    // 전술 메모 입력 다이얼로그 (지시사항, 선택)
                    final android.widget.EditText noteInput = new android.widget.EditText(this);
                    noteInput.setHint("지시사항 메모 (선택)");
                    noteInput.setMaxLines(3);
                    new AlertDialog.Builder(this)
                            .setTitle("전술 메모")
                            .setView(noteInput)
                            .setPositiveButton("전송", (d2, w2) -> {
                                String note = noteInput.getText().toString();
                                String payload = serializeTactical(note);
                                TacticalShare.pendingTactical = payload;
                                TacticalShare.pendingMarkerCount = mTacMarkers.size();
                                TacticalShare.pendingLineCount = mLineSets.size();
                                TacticalShare.pendingCodeNum = sel.getNumbers();
                                TacticalShare.pendingName = names[which];
                                Toast.makeText(this, "Opening chat: " + names[which], Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .setNegativeButton("취소", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String serializeTactical(String note) {
        StringBuilder sb = new StringBuilder();
        String myImei = com.ah.acr.messagebox.util.ImeiStorage.getLast(this);
        if (myImei == null) myImei = "";
        sb.append("FROM:").append(myImei);
        sb.append(";TS:").append(System.currentTimeMillis() / 1000L);
        // 마커: M:id,type,unit,place,lat,lon
        for (TacMarker tm : mTacMarkers) {
            sb.append(";M:").append(tm.id)
              .append(",").append(tm.type)
              .append(",").append(tm.unitType)
              .append(",").append(tm.placeType)
              .append(",").append(String.format(Locale.US, "%.5f", tm.point.getLatitude()))
              .append(",").append(String.format(Locale.US, "%.5f", tm.point.getLongitude()));
        }
        // 라인: L:id,n,lat1,lon1|lat2,lon2|...
        int lineNo = 1;
        for (LineSet ls : mLineSets) {
            if (ls.line == null) continue;
            java.util.List<GeoPoint> pts = ls.line.getActualPoints();
            sb.append(";L:").append(lineNo++).append(",").append(pts.size());
            boolean firstPt = true;
            for (GeoPoint gp : pts) {
                sb.append(firstPt ? "," : "|"); firstPt = false;
                sb.append(String.format(Locale.US, "%.5f", gp.getLatitude()))
                  .append(",").append(String.format(Locale.US, "%.5f", gp.getLongitude()));
            }
        }
        // 메저: R:id,lat1,lon1|lat2,lon2
        int rNo = 1;
        for (MeasureSet ms : mMeasureSets) {
            if (ms.line == null) continue;
            java.util.List<GeoPoint> pts = ms.line.getActualPoints();
            if (pts.size() < 2) continue;
            sb.append(";R:").append(rNo++)
              .append(",").append(String.format(Locale.US, "%.5f", pts.get(0).getLatitude()))
              .append(",").append(String.format(Locale.US, "%.5f", pts.get(0).getLongitude()))
              .append("|").append(String.format(Locale.US, "%.5f", pts.get(1).getLatitude()))
              .append(",").append(String.format(Locale.US, "%.5f", pts.get(1).getLongitude()));
        }
        // 메모(N:) — 페이로드 맨 마지막 (프로토콜 v1.4)
        if (note != null && !note.trim().isEmpty()) {
            sb.append(";N:").append(note.trim());
        }
        return sb.toString();
    }

    private void sendAsPhoto() {
        Bitmap bmp = captureMapArea();
        if (bmp == null) {
            Toast.makeText(this, "Capture failed (map loading)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mAddressList == null || mAddressList.isEmpty()) {
            Toast.makeText(this, "No recipients (address book empty)", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = new String[mAddressList.size()];
        for (int i = 0; i < mAddressList.size(); i++) {
            AddressEntity a = mAddressList.get(i);
            String nic = a.getNumbersNic();
            names[i] = (nic != null && !nic.isEmpty()) ? nic : a.getNumbers();
        }
        new AlertDialog.Builder(this)
                .setTitle("Select Recipient")
                .setItems(names, (dialog, which) -> {
                    AddressEntity sel = mAddressList.get(which);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, bos);
                    TacticalShare.pendingImage = bos.toByteArray();
                    TacticalShare.pendingCodeNum = sel.getNumbers();
                    TacticalShare.pendingName = names[which];
                    Toast.makeText(this, "Opening chat: " + names[which], Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleLineMode() {
        mLineMode = !mLineMode;
        if (mLineMode) {
            mMarkerType = -1;
            mMeasureMode = false;
            if (mToolMarker != null) mToolMarker.setTextColor(0xFF00E5D1);
            if (mToolMeasure != null) mToolMeasure.setTextColor(0xFF00E5D1);
            if (mToolLine != null) mToolLine.setTextColor(0xFFFFEB3B);
            Toast.makeText(this, "Line mode - tap points, tap LINE to finish", Toast.LENGTH_SHORT).show();
        } else {
            finishLine();
            if (mToolLine != null) mToolLine.setTextColor(0xFF00E5D1);
        }
    }

    private void addLinePoint(GeoPoint p) {
        mLinePoints.add(p);
        Marker dot = new Marker(mMapView);
        dot.setPosition(p);
        dot.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        dot.setIcon(makeLineDotIcon());
        mMapView.getOverlays().add(dot);
        mLineDots.add(dot);
        if (mLineCurrent == null) {
            mLineCurrent = new Polyline();
            mLineCurrent.getOutlinePaint().setColor(0xFF00E5FF);
            mLineCurrent.getOutlinePaint().setStrokeWidth(6f);
            mMapView.getOverlays().add(mLineCurrent);
        }
        mLineCurrent.setPoints(new java.util.ArrayList<>(mLinePoints));
        double total = 0;
        for (int k = 1; k < mLinePoints.size(); k++) {
            total += mLinePoints.get(k - 1).distanceToAsDouble(mLinePoints.get(k));
        }
        String tStr = (total < 1000)
                ? String.format(Locale.US, "Total: %.0f m", total)
                : String.format(Locale.US, "Total: %.2f km", total / 1000.0);
        if (mLineLabel != null) mMapView.getOverlays().remove(mLineLabel);
        if (mLinePoints.size() >= 2) {
            mLineLabel = new Marker(mMapView);
            mLineLabel.setPosition(p);
            mLineLabel.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mLineLabel.setIcon(makeTextLabel(tStr));
            mLineLabel.setTitle(tStr);
            mMapView.getOverlays().add(mLineLabel);
        }
        mMapView.invalidate();
    }

    private void finishLine() {
        if (mLinePoints.size() < 2) {
            for (Marker d : mLineDots) mMapView.getOverlays().remove(d);
            if (mLineCurrent != null) mMapView.getOverlays().remove(mLineCurrent);
            if (mLineLabel != null) mMapView.getOverlays().remove(mLineLabel);
            mLineDots.clear(); mLinePoints.clear();
            mLineCurrent = null; mLineLabel = null;
            mMapView.invalidate();
            return;
        }
        final LineSet set = new LineSet();
        set.line = mLineCurrent;
        set.label = mLineLabel;
        set.dots = new java.util.ArrayList<>(mLineDots);
        if (mLineLabel != null) {
            mLineLabel.setOnMarkerClickListener((mk, mv) -> {
                new AlertDialog.Builder(this)
                        .setTitle("Line")
                        .setMessage(mk.getTitle())
                        .setPositiveButton("OK", null)
                        .setNegativeButton("Delete", (d, w) -> removeLineSet(set))
                        .show();
                return true;
            });
        }
        mLineSets.add(set);
        mLineCurrent = null; mLineLabel = null;
        mLineDots.clear(); mLinePoints.clear();
        mMapView.invalidate();
    }

    private void removeLineSet(LineSet set) {
        if (set.line != null) mMapView.getOverlays().remove(set.line);
        if (set.label != null) mMapView.getOverlays().remove(set.label);
        for (Marker d : set.dots) mMapView.getOverlays().remove(d);
        mLineSets.remove(set);
        mMapView.invalidate();
    }

    private void clearAllLines() {
        for (LineSet s : mLineSets) {
            if (s.line != null) mMapView.getOverlays().remove(s.line);
            if (s.label != null) mMapView.getOverlays().remove(s.label);
            for (Marker d : s.dots) mMapView.getOverlays().remove(d);
        }
        mLineSets.clear();
        for (Marker d : mLineDots) mMapView.getOverlays().remove(d);
        if (mLineCurrent != null) mMapView.getOverlays().remove(mLineCurrent);
        if (mLineLabel != null) mMapView.getOverlays().remove(mLineLabel);
        mLineDots.clear(); mLinePoints.clear();
        mLineCurrent = null; mLineLabel = null;
    }

    private Drawable makeLineDotIcon() {
        int sz = dp(12);
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFF00E5FF);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(0xFF0A1628);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2));
        float cx = sz / 2f, cy = sz / 2f, r = sz / 2f - dp(2);
        c.drawCircle(cx, cy, r, fill);
        c.drawCircle(cx, cy, r, border);
        return new BitmapDrawable(getResources(), bmp);
    }

    private void toggleMeasureMode() {
        mMeasureMode = !mMeasureMode;
        if (mMeasureMode) {
            mMarkerType = -1;
            if (mToolMarker != null) mToolMarker.setTextColor(0xFF00E5D1);
            if (mToolMeasure != null) mToolMeasure.setTextColor(0xFFFFEB3B);
            Toast.makeText(this, "Measure mode - tap two points", Toast.LENGTH_SHORT).show();
        } else {
            if (mToolMeasure != null) mToolMeasure.setTextColor(0xFF00E5D1);
            mMeasureFirst = null;
        }
    }

    private void addMeasurePoint(GeoPoint p) {
        if (mMeasureFirst == null) {
            mMeasureFirst = p;
            mPendingDot = new Marker(mMapView);
            mPendingDot.setPosition(p);
            mPendingDot.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            mPendingDot.setIcon(makeDotIcon());
            mPendingDot.setTitle("Measure start");
            mMapView.getOverlays().add(mPendingDot);
            mMapView.invalidate();
            Toast.makeText(this, "Start set - tap end point", Toast.LENGTH_SHORT).show();
            return;
        }
        double meters = mMeasureFirst.distanceToAsDouble(p);
        double bearing = mMeasureFirst.bearingTo(p);
        if (bearing < 0) bearing += 360;
        String distStr = (meters < 1000)
                ? String.format(Locale.US, "%.0f m", meters)
                : String.format(Locale.US, "%.2f km", meters / 1000.0);
        String label = distStr + " / " + String.format(Locale.US, "%03.0f", bearing) + "\u00B0";
        final MeasureSet set = new MeasureSet();
        set.startDot = mPendingDot;
        mPendingDot = null;
        Polyline line = new Polyline();
        java.util.List<GeoPoint> pts = new java.util.ArrayList<>();
        pts.add(mMeasureFirst);
        pts.add(p);
        line.setPoints(pts);
        line.getOutlinePaint().setColor(0xFFFF6D00);
        line.getOutlinePaint().setStrokeWidth(7f);
        line.setTitle(label);
        mMapView.getOverlays().add(line);
        set.line = line;
        GeoPoint mid = new GeoPoint(
                (mMeasureFirst.getLatitude() + p.getLatitude()) / 2,
                (mMeasureFirst.getLongitude() + p.getLongitude()) / 2);
        Marker lbl = new Marker(mMapView);
        lbl.setPosition(mid);
        lbl.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        lbl.setIcon(makeTextLabel(label));
        lbl.setTitle(label);
        lbl.setOnMarkerClickListener((m, mv) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Measurement")
                    .setMessage(label)
                    .setPositiveButton("OK", null)
                    .setNegativeButton("Delete", (d, w) -> removeMeasureSet(set))
                    .show();
            return true;
        });
        mMapView.getOverlays().add(lbl);
        set.label = lbl;
        mMeasureSets.add(set);
        mMapView.invalidate();
        mMeasureFirst = null;
        Toast.makeText(this, label, Toast.LENGTH_LONG).show();
    }

    private void removeMeasureSet(MeasureSet set) {
        if (set.startDot != null) mMapView.getOverlays().remove(set.startDot);
        if (set.line != null) mMapView.getOverlays().remove(set.line);
        if (set.label != null) mMapView.getOverlays().remove(set.label);
        mMeasureSets.remove(set);
        mMapView.invalidate();
    }

    private void clearAllMeasures() {
        for (MeasureSet s : mMeasureSets) {
            if (s.startDot != null) mMapView.getOverlays().remove(s.startDot);
            if (s.line != null) mMapView.getOverlays().remove(s.line);
            if (s.label != null) mMapView.getOverlays().remove(s.label);
        }
        mMeasureSets.clear();
        if (mPendingDot != null) { mMapView.getOverlays().remove(mPendingDot); mPendingDot = null; }
        mMeasureFirst = null;
    }

    private Drawable makeDotIcon() {
        int sz = dp(14);
        Bitmap bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFFF6D00);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(0xFF0A1628);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2));
        float cx = sz / 2f, cy = sz / 2f, r = sz / 2f - dp(2);
        c.drawCircle(cx, cy, r, fill);
        c.drawCircle(cx, cy, r, border);
        return new BitmapDrawable(getResources(), bmp);
    }

    private Drawable makeTextLabel(String text) {
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTextSize(dp(11));
        tp.setFakeBoldText(true);
        float tw = tp.measureText(text);
        int padH = dp(6), padV = dp(3);
        int w = (int) tw + padH * 2;
        int h = dp(18);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xCC0A1628);
        c.drawRoundRect(0, 0, w, h, dp(3), dp(3), bg);
        c.drawText(text, padH, h - padV - dp(2), tp);
        return new BitmapDrawable(getResources(), bmp);
    }

    private void setupActionStubs() {
        TextView save = findViewById(R.id.tac_act_save);
        if (save != null) save.setOnClickListener(v -> captureAndSave());

        TextView share = findViewById(R.id.tac_act_share);
        if (share != null) share.setOnClickListener(v -> shareCapture());

        TextView send = findViewById(R.id.tac_act_send);
        if (send != null) send.setOnClickListener(v -> sendTactical());
    }

    private Bitmap captureMapArea() {
        View area = findViewById(R.id.tac_map_area);
        if (area == null || area.getWidth() == 0) return null;
        Bitmap bmp = Bitmap.createBitmap(
                area.getWidth(), area.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        area.draw(c);
        return bmp;
    }

    private String timestampName() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        return "TYTO_TAC_" + fmt.format(new Date());
    }

    private void shareCapture() {
        Bitmap bmp = captureMapArea();
        if (bmp == null) {
            Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(getCacheDir(), "shared");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, timestampName() + ".jpg");
            FileOutputStream fos = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.close();
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", f);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/jpeg");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Tactical Map"));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void captureAndSave() {
        Bitmap bmp = captureMapArea();
        if (bmp == null) {
            Toast.makeText(this, "Capture failed (map loading)", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = timestampName() + ".jpg";
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/TYTO");
            Uri uri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new Exception("insert null");
            OutputStream os = getContentResolver().openOutputStream(uri);
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
            os.close();
            Toast.makeText(this, "Saved: " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        if (mMyLocOn) stopMyLocation();
        if (mMapView != null) mMapView.onPause();
    }
}
