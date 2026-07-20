package com.ah.acr.messagebox.nav;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.SurvivalFavStore;
import com.ah.acr.messagebox.util.MapModeManager;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * [NAV] Route planning. Long-press map -> menu (set destination / set start / save favorite).
 * Computes route (OSRM) for the selected mode, draws distance/ETA + polyline.
 */
public class NavRouteFragment extends DialogFragment {

    private static final String ARG_MODE = "mode";   // 0=WALK,1=VEHICLE,2=VESSEL
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(37.5665, 126.9780);
    private static final double VESSEL_KNOTS = 10.0;
    private static final double WALK_KMH = 4.5;

    private int mMode = 0;
    private MapView mMap;
    private TextView mSummary, mModeLabel;
    private Marker mDestMarker, mStartMarker;
    private Polyline mRouteLine;
    private final List<Marker> mFavMarkers = new ArrayList<>();

    private double mStartLat, mStartLon;
    private double mDestLat = Double.NaN, mDestLon = Double.NaN;

    public static NavRouteFragment newInstance(int mode) {
        NavRouteFragment f = new NavRouteFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_MODE, mode);
        f.setArguments(b);
        return f;
    }

    @Override public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_nav_route, container, false);
        mMode = getArguments() != null ? getArguments().getInt(ARG_MODE, 0) : 0;

        mSummary = root.findViewById(R.id.nav_route_summary);
        mModeLabel = root.findViewById(R.id.nav_route_mode);
        mModeLabel.setText(modeName(mMode));

        root.findViewById(R.id.nav_route_close).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.nav_route_fav).setOnClickListener(v -> showFavList());
        root.findViewById(R.id.nav_route_calc).setOnClickListener(v -> calcRoute());
        root.findViewById(R.id.nav_route_clear).setOnClickListener(v -> clearRoute());

        mMap = root.findViewById(R.id.nav_route_map);
        mMap.setMultiTouchControls(true);
        mMap.setBuiltInZoomControls(false);
        MapModeManager.applyToMapView(requireContext(), mMap);
        mMap.getController().setZoom(13.0);

        root.findViewById(R.id.nav_route_zoom_in).setOnClickListener(v -> { if (mMap != null) mMap.getController().zoomIn(); });
        root.findViewById(R.id.nav_route_zoom_out).setOnClickListener(v -> { if (mMap != null) mMap.getController().zoomOut(); });

        // start = last known GPS, else map default center
        GeoPoint start = myLocationOrDefault();
        mStartLat = start.getLatitude(); mStartLon = start.getLongitude();
        mMap.getController().setCenter(start);

        // long-press -> menu
        MapEventsReceiver rx = new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) { return false; }
            @Override public boolean longPressHelper(GeoPoint p) { showPointMenu(p); return true; }
        };
        mMap.getOverlays().add(0, new MapEventsOverlay(rx));

        drawStart();
        drawFavMarkers();
        mSummary.setText("Long-press map: set destination / start / save favorite");
        return root;
    }

    private String modeName(int m) { return m == 0 ? "WALK" : (m == 1 ? "VEHICLE" : "VESSEL"); }

    private GeoPoint myLocationOrDefault() {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                    android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.location.LocationManager lm = (android.location.LocationManager) requireContext()
                        .getSystemService(android.content.Context.LOCATION_SERVICE);
                android.location.Location best = null;
                for (String prov : new String[]{ android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER }) {
                    try {
                        android.location.Location l = lm.getLastKnownLocation(prov);
                        if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                    } catch (Exception ignore) {}
                }
                if (best != null) return new GeoPoint(best.getLatitude(), best.getLongitude());
            }
        } catch (Exception ignore) {}
        return DEFAULT_CENTER;
    }

    private void showPointMenu(final GeoPoint p) {
        if (getContext() == null) return;
        String[] items = { "Set as destination", "Set as start", "Save to favorites" };
        new android.app.AlertDialog.Builder(getContext())
            .setTitle(String.format(Locale.US, "%.5f, %.5f", p.getLatitude(), p.getLongitude()))
            .setItems(items, (d, which) -> {
                if (which == 0) setDestination(p.getLatitude(), p.getLongitude());
                else if (which == 1) { mStartLat = p.getLatitude(); mStartLon = p.getLongitude(); drawStart(); }
                else saveFavorite(p.getLatitude(), p.getLongitude());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void saveFavorite(final double lat, final double lon) {
        if (getContext() == null) return;
        if (SurvivalFavStore.count(getContext()) >= SurvivalFavStore.MAX_SLOTS) {
            Toast.makeText(getContext(), "Favorites full (10/10). Delete one first.", Toast.LENGTH_SHORT).show();
            return;
        }
        final android.widget.EditText et = new android.widget.EditText(getContext());
        et.setHint("name");
        new android.app.AlertDialog.Builder(getContext())
            .setTitle("Save favorite")
            .setView(et)
            .setPositiveButton("Save", (d, w) -> {
                String name = et.getText().toString().trim();
                if (name.isEmpty()) name = String.format(Locale.US, "%.4f,%.4f", lat, lon);
                int slot = SurvivalFavStore.saveToFirstEmpty(getContext(), name, lat, lon);
                if (slot >= 0) {
                    Toast.makeText(getContext(), "Saved: Slot" + (slot + 1) + " " + name, Toast.LENGTH_SHORT).show();
                    drawFavMarkers();
                } else {
                    Toast.makeText(getContext(), "No empty slot", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void drawStart() {
        if (mMap == null) return;
        if (mStartMarker != null) mMap.getOverlays().remove(mStartMarker);
        mStartMarker = new Marker(mMap);
        mStartMarker.setPosition(new GeoPoint(mStartLat, mStartLon));
        mStartMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mStartMarker.setTitle("Start");
        mMap.getOverlays().add(mStartMarker);
        mMap.invalidate();
    }

    private void setDestination(double lat, double lon) {
        mDestLat = lat; mDestLon = lon;
        if (mDestMarker != null) mMap.getOverlays().remove(mDestMarker);
        mDestMarker = new Marker(mMap);
        mDestMarker.setPosition(new GeoPoint(lat, lon));
        mDestMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mDestMarker.setTitle("Destination");
        mMap.getOverlays().add(mDestMarker);
        mMap.invalidate();
        mSummary.setText(String.format(Locale.US, "Dest set: %.5f, %.5f  -> tap Get Route", lat, lon));
    }

    private void calcRoute() {
        if (Double.isNaN(mDestLat)) {
            Toast.makeText(getContext(), "Set destination first (long-press map)", Toast.LENGTH_SHORT).show();
            return;
        }
        mSummary.setText("Calculating...");
        NavRouteService.requestRoute(mMode, mStartLat, mStartLon, mDestLat, mDestLon, r -> {
            if (!isAdded()) return;
            if (!r.ok) { mSummary.setText("Route failed: " + r.error); return; }
            double durS = r.durationS;
            // ETA fallback when API returns 0 / vessel
            if (mMode == 2) {
                double mps = VESSEL_KNOTS * 1852.0 / 3600.0;
                durS = (mps > 0) ? r.distanceM / mps : 0;
            } else if (durS <= 0) {
                double kmh = (mMode == 0) ? WALK_KMH : 40.0;
                durS = r.distanceM / (kmh * 1000.0 / 3600.0);
            }
            drawRouteLine(r.geometry);
            mSummary.setText(String.format(Locale.US, "%.1f km  ·  %s  (%s)",
                    r.distanceM / 1000.0, fmtDur(durS), modeName(mMode)));
        });
    }

    private void drawRouteLine(List<GeoPoint> pts) {
        if (mMap == null || pts == null || pts.isEmpty()) return;
        if (mRouteLine != null) mMap.getOverlays().remove(mRouteLine);
        mRouteLine = new Polyline(mMap);
        mRouteLine.setPoints(pts);
        mRouteLine.getOutlinePaint().setColor(0xFFFF6D00); // bright orange
        mRouteLine.getOutlinePaint().setStrokeWidth(14f);
        // keep route above markers: add at end, then re-add markers on top
        mMap.getOverlays().add(mRouteLine);
        if (mStartMarker != null) { mMap.getOverlays().remove(mStartMarker); mMap.getOverlays().add(mStartMarker); }
        if (mDestMarker != null) { mMap.getOverlays().remove(mDestMarker); mMap.getOverlays().add(mDestMarker); }
        try {
            org.osmdroid.util.BoundingBox box = org.osmdroid.util.BoundingBox.fromGeoPoints(pts);
            mMap.zoomToBoundingBox(box, true, 100);
        } catch (Exception ignore) {}
        mMap.invalidate();
    }

    private void clearRoute() {
        if (mRouteLine != null) { mMap.getOverlays().remove(mRouteLine); mRouteLine = null; }
        if (mDestMarker != null) { mMap.getOverlays().remove(mDestMarker); mDestMarker = null; }
        mDestLat = Double.NaN; mDestLon = Double.NaN;
        mSummary.setText("Long-press map: set destination / start / save favorite");
        mMap.invalidate();
    }

    private String fmtDur(double sec) {
        long s = (long) Math.round(sec);
        long h = s / 3600, m = (s % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return "<1m";
    }

    private void drawFavMarkers() {
        if (mMap == null || getContext() == null) return;
        for (Marker m : mFavMarkers) mMap.getOverlays().remove(m);
        mFavMarkers.clear();
        android.graphics.drawable.Drawable star = androidx.core.content.res.ResourcesCompat.getDrawable(
                getResources(), android.R.drawable.btn_star_big_on, null);
        for (SurvivalFavStore.Fav f : SurvivalFavStore.getAll(getContext())) {
            if (f.isEmpty()) continue;
            Marker mk = new Marker(mMap);
            mk.setPosition(new GeoPoint(f.lat, f.lon));
            mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            if (star != null) mk.setIcon(star);
            mk.setTitle(f.name);
            mMap.getOverlays().add(mk);
            mFavMarkers.add(mk);
        }
        mMap.invalidate();
    }

    private void showFavList() {
        if (getContext() == null) return;
        final List<SurvivalFavStore.Fav> picks = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (SurvivalFavStore.Fav f : SurvivalFavStore.getAll(getContext())) {
            if (f.isEmpty()) continue;
            labels.add("Slot" + (f.slot + 1) + " : " + f.name);
            picks.add(f);
        }
        if (picks.isEmpty()) {
            Toast.makeText(getContext(), "No favorites. Long-press map to save.", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(getContext())
            .setTitle("Favorites (pick destination)")
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                SurvivalFavStore.Fav f = picks.get(which);
                setDestination(f.lat, f.lon);
                if (mMap != null) mMap.getController().animateTo(new GeoPoint(f.lat, f.lon));
            })
            .setNegativeButton("Close", null)
            .show();
    }

    @Override public void onResume() { super.onResume(); if (mMap != null) mMap.onResume(); }
    @Override public void onPause() { super.onPause(); if (mMap != null) mMap.onPause(); }
    @Override public void onDestroyView() {
        super.onDestroyView();
        if (mMap != null) { mMap.onDetach(); mMap = null; }
    }
}