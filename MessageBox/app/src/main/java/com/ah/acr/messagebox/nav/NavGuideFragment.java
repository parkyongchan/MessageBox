package com.ah.acr.messagebox.nav;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.MsgRoomDatabase;
import com.ah.acr.messagebox.nav.db.NavDao;
import com.ah.acr.messagebox.nav.db.NavRoute;
import com.ah.acr.messagebox.nav.db.NavSegment;
import com.ah.acr.messagebox.nav.db.NavWeather;
import com.ah.acr.messagebox.nav.db.NavWeatherDay;
import com.ah.acr.messagebox.util.MapModeManager;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * [NAV step 5] Route guidance screen (English UI).
 * Map (per-segment coloured line, numbered waypoints, current-location marker + travelled trail
 * + custom compass), top banner (remaining / ETA / progress), bottom segment weather cards.
 * Map source can be switched Online / Offline(MBTiles) and a .mbtiles file can be imported.
 */
public class NavGuideFragment extends DialogFragment implements SensorEventListener {

    private static final String ARG_ROUTE_ID = "route_id";

    private static final int[] SEG_COLORS = {
            0xFFFF6D00, 0xFF2E7D32, 0xFF1565C0, 0xFF6A1B9A,
            0xFFC62828, 0xFF00838F, 0xFFF9A825, 0xFF4E342E
    };
    private static final int TRAIL_COLOR = 0xFF29B6F6;

    private MapView mMap;
    private TextView mBanner, mStatus, mTitle;
    private ProgressBar mProgress;
    private LinearLayout mCards;
    private HorizontalScrollView mCardsScroll;
    private Button mFollowBtn, mMapSrcBtn;
    private ImageView mCompass;

    private Marker mMeMarker;
    private Polyline mTrail;
    private final List<GeoPoint> mTrailPts = new ArrayList<>();
    private final List<View> mCardViews = new ArrayList<>();
    private boolean mFollow = true;
    private int mCurrentSeg = -1;

    private long mRouteId;
    private NavRoute mRoute;
    private List<SegModel> mModels = new ArrayList<>();
    private final List<GeoPoint> mLine = new ArrayList<>();
    private double[] mCum;
    private double mTotalM;

    private SensorManager mSensorMgr;
    private Sensor mRotSensor;
    private final float[] mRotMatrix = new float[9];
    private final float[] mOrient = new float[3];
    private float mLastAzimuth = 0f;

    private android.location.LocationManager mLm;
    private android.location.LocationListener mLocListener;
    private final Handler mMain = new Handler(Looper.getMainLooper());

    // .mbtiles file picker
    private final ActivityResultLauncher<String[]> mPickMbtiles =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> { if (uri != null) importMbtiles(uri); });

    public static NavGuideFragment newInstance(long routeId) {
        NavGuideFragment f = new NavGuideFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_ROUTE_ID, routeId);
        f.setArguments(b);
        return f;
    }

    @Override public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_nav_guide, container, false);
        mRouteId = getArguments() != null ? getArguments().getLong(ARG_ROUTE_ID, -1) : -1;

        mTitle = root.findViewById(R.id.nav_guide_title);
        mBanner = root.findViewById(R.id.nav_guide_banner);
        mStatus = root.findViewById(R.id.nav_guide_status);
        mProgress = root.findViewById(R.id.nav_guide_progress);
        mCards = root.findViewById(R.id.nav_guide_cards);
        mCardsScroll = root.findViewById(R.id.nav_guide_cards_scroll);
        mFollowBtn = root.findViewById(R.id.nav_guide_follow);
        mMapSrcBtn = root.findViewById(R.id.nav_guide_mapsrc);
        mCompass = root.findViewById(R.id.nav_guide_compass);
        root.findViewById(R.id.nav_guide_close).setOnClickListener(v -> dismiss());
        mFollowBtn.setOnClickListener(v -> {
            mFollow = !mFollow;
            mFollowBtn.setText(mFollow ? "Follow ●" : "Follow ○");
            if (mFollow && mMeMarker != null) mMap.getController().animateTo(mMeMarker.getPosition());
        });
        mMapSrcBtn.setOnClickListener(v -> showMapSourceDialog());

        mMap = root.findViewById(R.id.nav_guide_map);
        mMap.setMultiTouchControls(true);
        mMap.setBuiltInZoomControls(false);
        MapModeManager.Mode appliedSrc = MapModeManager.applyToMapView(requireContext(), mMap);
        syncMapSrcLabel(appliedSrc);
        mMap.getController().setZoom(12.0);

        mSensorMgr = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (mSensorMgr != null) mRotSensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        loadRouteAsync();
        return root;
    }

    // ---------------- map source ----------------

    private void showMapSourceDialog() {
        if (getContext() == null) return;
        String[] items = { "Online", "Offline (MBTiles)", "Import .mbtiles…" };
        new AlertDialog.Builder(getContext())
            .setTitle("Map source")
            .setItems(items, (d, which) -> {
                if (which == 0) {
                    MapModeManager.setMode(requireContext(), MapModeManager.Mode.ONLINE);
                    syncMapSrcLabel(MapModeManager.applyToMapView(requireContext(), mMap));
                } else if (which == 1) {
                    if (!MapModeManager.hasMbtiles(requireContext())) {
                        Toast.makeText(getContext(), "No MBTiles found. Import one first.", Toast.LENGTH_LONG).show();
                    } else {
                        MapModeManager.setMode(requireContext(), MapModeManager.Mode.OFFLINE);
                        syncMapSrcLabel(MapModeManager.applyToMapView(requireContext(), mMap));
                    }
                } else {
                    mPickMbtiles.launch(new String[]{ "*/*" });
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void importMbtiles(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String name = queryName(uri);
                if (name == null || !name.toLowerCase().endsWith(".mbtiles"))
                    name = "map_" + System.currentTimeMillis() + ".mbtiles";
                File dir = MapModeManager.getMbtilesDir(requireContext());
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, name);
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     OutputStream os = new FileOutputStream(out)) {
                    if (in == null) throw new Exception("cannot open input");
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
                final String fname = out.getName();
                post(() -> {
                    if (!isAdded()) return;
                    MapModeManager.setMode(requireContext(), MapModeManager.Mode.OFFLINE);
                    syncMapSrcLabel(MapModeManager.applyToMapView(requireContext(), mMap));
                    Toast.makeText(getContext(), "Imported: " + fname + " → Offline", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                post(() -> { if (isAdded()) Toast.makeText(getContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private String queryName(Uri uri) {
        try (Cursor c = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignore) {}
        return null;
    }

    private void syncMapSrcLabel(MapModeManager.Mode m) {
        if (mMapSrcBtn != null)
            mMapSrcBtn.setText(m == MapModeManager.Mode.OFFLINE ? "Map: Offline" : "Map: Online");
    }

    // ---------------- compass ----------------

    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR || mCompass == null) return;
        SensorManager.getRotationMatrixFromVector(mRotMatrix, e.values);
        SensorManager.getOrientation(mRotMatrix, mOrient);
        float az = (float) Math.toDegrees(mOrient[0]);
        if (az < 0) az += 360f;
        float smoothed = mLastAzimuth + 0.15f * angleDelta(mLastAzimuth, az);
        mLastAzimuth = (smoothed + 360f) % 360f;
        mCompass.setRotation(-mLastAzimuth);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private static float angleDelta(float from, float to) {
        return (to - from + 540f) % 360f - 180f;
    }

    // ---------------- load ----------------

    private void loadRouteAsync() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                NavDao dao = MsgRoomDatabase.Companion
                        .getDatabase(requireContext().getApplicationContext()).navDao();
                final NavRoute route = dao.getRoute(mRouteId);
                if (route == null) { post(() -> mBanner.setText("Route not found (id=" + mRouteId + ")")); return; }

                final List<NavSegment> segs = dao.getSegments(mRouteId);
                final List<SegModel> models = new ArrayList<>();
                for (NavSegment s : segs) {
                    SegModel m = new SegModel();
                    m.seg = s;
                    NavWeather w = dao.getWeatherForSegment(s.id);
                    m.marine = (w != null && w.marine == 1);
                    m.days = (w != null) ? dao.getWeatherDays(w.id) : new ArrayList<>();
                    models.add(m);
                }
                post(() -> onLoaded(route, models));
            } catch (Exception e) {
                post(() -> mBanner.setText("Load failed: " + e.getMessage()));
            }
        });
    }

    private void onLoaded(NavRoute route, List<SegModel> models) {
        if (!isAdded()) return;
        mRoute = route;
        mModels = models;
        mTotalM = route.totalDistanceM;
        mTitle.setText("Route Guide · " + modeEn(route.mode));

        mLine.clear();
        if (route.geometry != null && !route.geometry.isEmpty()) {
            mLine.addAll(NavSegmenter.decodePolyline(route.geometry));
        }
        if (mLine.size() < 2) {
            mLine.clear();
            for (SegModel m : models) mLine.add(new GeoPoint(m.seg.repLat, m.seg.repLon));
        }
        buildCum();

        drawRoute(models);
        buildWeatherCards(models);
        updateStatusLine(null);

        mBanner.setText(String.format(Locale.US, "Total %.1f km · Est. %s",
                mTotalM / 1000.0, fmtDur(route.totalDurationS)));

        startLocationUpdates();
    }

    // ---------------- map drawing ----------------

    private void drawRoute(List<SegModel> models) {
        if (mMap == null || mLine.size() < 2) return;

        for (int i = 0; i + 1 < models.size(); i++) {
            double d0 = models.get(i).seg.distFromStartM;
            double d1 = models.get(i + 1).seg.distFromStartM;
            List<GeoPoint> sub = subLine(d0, d1);
            if (sub.size() >= 2) {
                Polyline p = new Polyline(mMap);
                p.setPoints(sub);
                p.getOutlinePaint().setColor(SEG_COLORS[i % SEG_COLORS.length]);
                p.getOutlinePaint().setStrokeWidth(12f);
                mMap.getOverlays().add(p);
            }
        }
        if (models.size() < 2) {
            Polyline p = new Polyline(mMap);
            p.setPoints(mLine);
            p.getOutlinePaint().setColor(SEG_COLORS[0]);
            p.getOutlinePaint().setStrokeWidth(12f);
            mMap.getOverlays().add(p);
        }

        mTrail = new Polyline(mMap);
        mTrail.getOutlinePaint().setColor(TRAIL_COLOR);
        mTrail.getOutlinePaint().setStrokeWidth(9f);
        mMap.getOverlays().add(mTrail);

        for (int i = 0; i < models.size(); i++) {
            SegModel m = models.get(i);
            Marker mk = new Marker(mMap);
            mk.setPosition(new GeoPoint(m.seg.repLat, m.seg.repLon));
            mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            mk.setIcon(numberPin(i, SEG_COLORS[i % SEG_COLORS.length]));
            mk.setTitle((m.seg.label != null ? m.seg.label : ("Segment " + m.seg.seq)) + todaySuffix(m));
            mMap.getOverlays().add(mk);
        }

        try { mMap.zoomToBoundingBox(BoundingBox.fromGeoPoints(mLine), true, 80); } catch (Exception ignore) {}
        mMap.invalidate();
    }

    private List<GeoPoint> subLine(double d0, double d1) {
        List<GeoPoint> out = new ArrayList<>();
        if (mLine.size() < 2 || mCum == null) return out;
        out.add(pointAt(d0));
        for (int i = 0; i < mLine.size(); i++) if (mCum[i] > d0 && mCum[i] < d1) out.add(mLine.get(i));
        out.add(pointAt(d1));
        return out;
    }

    private GeoPoint pointAt(double targetM) {
        int n = mLine.size();
        if (n == 0) return new GeoPoint(0, 0);
        if (targetM <= 0) return mLine.get(0);
        if (targetM >= mCum[n - 1]) return mLine.get(n - 1);
        int i = 1;
        while (i < n && mCum[i] < targetM) i++;
        double f = (mCum[i] - mCum[i - 1]) <= 0 ? 0 : (targetM - mCum[i - 1]) / (mCum[i] - mCum[i - 1]);
        GeoPoint a = mLine.get(i - 1), b = mLine.get(i);
        return new GeoPoint(a.getLatitude() + (b.getLatitude() - a.getLatitude()) * f,
                a.getLongitude() + (b.getLongitude() - a.getLongitude()) * f);
    }

    private Drawable numberPin(int n, int color) {
        int dp = (int) getResources().getDisplayMetrics().density;
        int size = 22 * dp;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); fill.setColor(color);
        c.drawCircle(size / 2f, size / 2f, size / 2f - dp, fill);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(1.5f * dp); ring.setColor(Color.WHITE);
        c.drawCircle(size / 2f, size / 2f, size / 2f - dp, ring);
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(Color.WHITE); txt.setTextSize(12 * dp);
        txt.setTextAlign(Paint.Align.CENTER); txt.setFakeBoldText(true);
        float ty = size / 2f - (txt.descent() + txt.ascent()) / 2f;
        c.drawText(String.valueOf(n), size / 2f, ty, txt);
        return new BitmapDrawable(getResources(), bmp);
    }

    private String todaySuffix(SegModel m) {
        if (m.days == null || m.days.isEmpty()) return "";
        return "  |  " + dayLine(m.days.get(0), m.marine);
    }

    // ---------------- weather cards ----------------

    private void buildWeatherCards(List<SegModel> models) {
        if (mCards == null || getContext() == null) return;
        mCards.removeAllViews();
        mCardViews.clear();
        int dp = (int) getResources().getDisplayMetrics().density;

        for (int idx = 0; idx < models.size(); idx++) {
            SegModel m = models.get(idx);
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(158 * dp, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(6 * dp, 2 * dp, 6 * dp, 2 * dp);
            card.setLayoutParams(lp);
            card.setBackgroundColor(0xFF1B2530);
            card.setPadding(10 * dp, 8 * dp, 10 * dp, 8 * dp);

            TextView head = new TextView(getContext());
            head.setText((m.seg.label != null ? m.seg.label : ("Segment " + m.seg.seq)));
            head.setTextColor(SEG_COLORS[idx % SEG_COLORS.length] | 0xFF000000);
            head.setTextSize(13f);
            head.setTypeface(head.getTypeface(), android.graphics.Typeface.BOLD);
            card.addView(head);

            if (m.days == null || m.days.isEmpty()) {
                TextView none = new TextView(getContext());
                none.setText("No weather"); none.setTextColor(0xFF9E9E9E); none.setTextSize(12f);
                card.addView(none);
            } else {
                for (NavWeatherDay d : m.days) {
                    TextView row = new TextView(getContext());
                    row.setText(dayLine(d, m.marine));
                    row.setTextColor(0xFFECEFF1); row.setTextSize(12f);
                    row.setPadding(0, 3 * dp, 0, 3 * dp);
                    card.addView(row);
                }
            }
            mCards.addView(card);
            mCardViews.add(card);
        }
    }

    private String dayLine(NavWeatherDay d, boolean marine) {
        String date = (d.date != null && d.date.length() >= 10) ? d.date.substring(5)
                : (d.date == null ? "--" : d.date);
        String icon = wmo(d.weatherCode);
        String temp = (d.tMin != null && d.tMax != null)
                ? String.format(Locale.US, "%.0f/%.0f°", d.tMax, d.tMin) : "--";
        StringBuilder sb = new StringBuilder();
        sb.append(date).append(" ").append(icon).append(" ").append(temp);
        if (d.precipMm != null && d.precipMm > 0) sb.append(" R").append(String.format(Locale.US, "%.0f", d.precipMm));
        if (marine && d.waveMax != null) sb.append(" wave ").append(String.format(Locale.US, "%.1fm", d.waveMax));
        else if (d.windMax != null) sb.append(" W").append(String.format(Locale.US, "%.0f", d.windMax));
        return sb.toString();
    }

    private void highlightSeg(int idx) {
        if (idx == mCurrentSeg || idx < 0 || idx >= mCardViews.size()) return;
        mCurrentSeg = idx;
        for (int i = 0; i < mCardViews.size(); i++)
            mCardViews.get(i).setBackgroundColor(i == idx ? 0xFF2C4A63 : 0xFF1B2530);
        final View target = mCardViews.get(idx);
        if (mCardsScroll != null) mCardsScroll.post(() -> mCardsScroll.smoothScrollTo(target.getLeft(), 0));
    }

    // ---------------- GPS ----------------

    private void startLocationUpdates() {
        if (getContext() == null) return;
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            mStatus.setText("No location permission (progress hidden)");
            return;
        }
        mLm = (android.location.LocationManager) requireContext()
                .getSystemService(Context.LOCATION_SERVICE);
        mLocListener = new android.location.LocationListener() {
            @Override public void onLocationChanged(@NonNull android.location.Location loc) {
                onMyLocation(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
            }
            @Override public void onProviderEnabled(@NonNull String p) {}
            @Override public void onProviderDisabled(@NonNull String p) {}
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
        };
        try {
            android.location.Location last = mLm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            if (last == null) last = mLm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            if (last != null) onMyLocation(new GeoPoint(last.getLatitude(), last.getLongitude()));
            mLm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 3000, 5, mLocListener);
        } catch (Exception ignore) {}
    }

    private void onMyLocation(GeoPoint me) {
        if (!isAdded() || mMap == null) return;
        if (mMeMarker == null) {
            mMeMarker = new Marker(mMap);
            mMeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            mMeMarker.setIcon(dotMarker(0xFF1976D2));
            mMeMarker.setTitle("You");
            mMap.getOverlays().add(mMeMarker);
        }
        mMeMarker.setPosition(me);

        mTrailPts.add(me);
        if (mTrail != null) mTrail.setPoints(new ArrayList<>(mTrailPts));
        if (mFollow) mMap.getController().animateTo(me);
        mMap.invalidate();

        if (mLine.size() >= 2 && mCum != null && mTotalM > 0) {
            double traveled = projectTraveled(me);
            double remaining = Math.max(0, mTotalM - traveled);
            int pct = (int) Math.max(0, Math.min(100, Math.round(100.0 * traveled / mTotalM)));
            mProgress.setProgress(pct);
            long remSec = Math.round(mRoute.totalDurationS * (remaining / mTotalM));
            mBanner.setText(String.format(Locale.US, "ETA %s · %d%% · %.1f km left",
                    arrivalClock(remSec), pct, remaining / 1000.0));
            int segIdx = nearestSegByDistance(traveled);
            highlightSeg(segIdx);
            updateStatusLine(segIdx);
        }
    }

    private int nearestSegByDistance(double traveled) {
        int best = 0; double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < mModels.size(); i++) {
            double diff = Math.abs(mModels.get(i).seg.distFromStartM - traveled);
            if (diff < bestDiff) { bestDiff = diff; best = i; }
        }
        return best;
    }

    private double projectTraveled(GeoPoint me) {
        double best = Double.MAX_VALUE, bestAlong = 0;
        double cosLat = Math.cos(Math.toRadians(me.getLatitude()));
        for (int i = 1; i < mLine.size(); i++) {
            GeoPoint a = mLine.get(i - 1), b = mLine.get(i);
            double bx = (b.getLongitude() - a.getLongitude()) * cosLat * 111320.0;
            double by = (b.getLatitude() - a.getLatitude()) * 110540.0;
            double px = (me.getLongitude() - a.getLongitude()) * cosLat * 111320.0;
            double py = (me.getLatitude() - a.getLatitude()) * 110540.0;
            double segLen2 = bx * bx + by * by;
            double t = segLen2 <= 0 ? 0 : (px * bx + py * by) / segLen2;
            if (t < 0) t = 0; else if (t > 1) t = 1;
            double cx = t * bx, cy = t * by;
            double d2 = (px - cx) * (px - cx) + (py - cy) * (py - cy);
            if (d2 < best) { best = d2; bestAlong = mCum[i - 1] + t * (mCum[i] - mCum[i - 1]); }
        }
        return bestAlong;
    }

    private void buildCum() {
        int n = mLine.size();
        mCum = new double[Math.max(n, 1)];
        mCum[0] = 0;
        for (int i = 1; i < n; i++)
            mCum[i] = mCum[i - 1] + NavSegmenter.haversineM(mLine.get(i - 1), mLine.get(i));
        if (n >= 2 && mTotalM <= 0) mTotalM = mCum[n - 1];
    }

    private Drawable dotMarker(int color) {
        int dp = (int) getResources().getDisplayMetrics().density;
        int size = 16 * dp;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG); ring.setColor(Color.WHITE);
        c.drawCircle(size / 2f, size / 2f, size / 2f, ring);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); fill.setColor(color);
        c.drawCircle(size / 2f, size / 2f, size / 2f - 2 * dp, fill);
        return new BitmapDrawable(getResources(), bmp);
    }

    // ---------------- status / utils ----------------

    private void updateStatusLine(@Nullable Integer segIdx) {
        boolean online = NavConnectivity.isOnline(getContext());
        String when = android.text.format.DateFormat.format("MM-dd HH:mm", mRoute.fetchedAt).toString();
        StringBuilder sb = new StringBuilder();
        if (segIdx != null && segIdx >= 0 && segIdx < mModels.size()) {
            SegModel m = mModels.get(segIdx);
            String label = m.seg.label != null ? m.seg.label : ("Seg " + m.seg.seq);
            sb.append("Near ").append(label);
            if (m.days != null && !m.days.isEmpty()) sb.append(": ").append(dayLine(m.days.get(0), m.marine));
            sb.append(" · ");
        }
        sb.append(online ? "Online" : "Offline").append(" · fetched ").append(when);
        mStatus.setText(sb.toString());
    }

    private String arrivalClock(long remSec) {
        long arriveMs = System.currentTimeMillis() + remSec * 1000L;
        return android.text.format.DateFormat.format("HH:mm", arriveMs).toString();
    }

    private static String fmtDur(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return "<1m";
    }

    private static String modeEn(String mode) {
        if ("WALK".equals(mode)) return "Walk";
        if ("VEHICLE".equals(mode)) return "Vehicle";
        if ("VESSEL".equals(mode)) return "Vessel";
        return mode;
    }

    private static String wmo(Integer c) {
        if (c == null) return "-";
        int v = c;
        if (v == 0) return "Clear";
        if (v <= 2) return "PtCldy";
        if (v == 3) return "Cloudy";
        if (v >= 45 && v <= 48) return "Fog";
        if (v >= 51 && v <= 67) return "Rain";
        if (v >= 71 && v <= 77) return "Snow";
        if (v >= 80 && v <= 82) return "Show";
        if (v >= 95) return "Storm";
        return "-";
    }

    private void post(Runnable r) { mMain.post(r); }

    // ---------------- lifecycle ----------------

    @Override public void onResume() {
        super.onResume();
        if (mMap != null) mMap.onResume();
        if (mSensorMgr != null && mRotSensor != null)
            mSensorMgr.registerListener(this, mRotSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override public void onPause() {
        super.onPause();
        if (mMap != null) mMap.onPause();
        if (mSensorMgr != null) mSensorMgr.unregisterListener(this);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        try { if (mLm != null && mLocListener != null) mLm.removeUpdates(mLocListener); } catch (Exception ignore) {}
        if (mSensorMgr != null) mSensorMgr.unregisterListener(this);
        if (mMap != null) { mMap.onDetach(); mMap = null; }
    }

    private static class SegModel {
        NavSegment seg;
        boolean marine;
        List<NavWeatherDay> days;
    }
}
