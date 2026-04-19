package com.ah.acr.messagebox.tabs;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.database.SatTrackEntity;
import com.ah.acr.messagebox.database.SatTrackPointEntity;
import com.ah.acr.messagebox.database.SatTrackViewModel;
import com.ah.acr.messagebox.export.TrackExporter;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen dialog for viewing a saved Satellite TRACK session.
 * Shows map with polyline, start/end markers, and export button.
 */
public class SatTrackDetailFragment extends DialogFragment {

    private static final String TAG = "SatTrackDetail";
    private static final String ARG_TRACK_ID = "track_id";
    private static final String MBTILES_SUBDIR = "mbtiles";
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(37.5665, 126.9780);

    private int trackId = -1;
    private SatTrackViewModel viewModel;
    private SatTrackEntity currentTrack;
    private List<SatTrackPointEntity> currentPoints = new ArrayList<>();

    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvDistance;
    private TextView tvDuration;
    private TextView tvPoints;
    private TextView tvAvgSpeed;
    private ImageButton btnBack;
    private ImageButton btnExport;
    private MapView mapView;

    private Polyline polyline;
    private Marker startMarker;
    private Marker endMarker;


    public static SatTrackDetailFragment newInstance(int trackId) {
        SatTrackDetailFragment fragment = new SatTrackDetailFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TRACK_ID, trackId);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        if (getArguments() != null) {
            trackId = getArguments().getInt(ARG_TRACK_ID, -1);
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext().getApplicationContext();
        Configuration.getInstance().setUserAgentValue(ctx.getPackageName());
        File osmDir = ctx.getExternalFilesDir(null);
        if (osmDir != null) {
            Configuration.getInstance().setOsmdroidBasePath(osmDir);
            Configuration.getInstance().setOsmdroidTileCache(new File(osmDir, "cache"));
        }

        return inflater.inflate(R.layout.fragment_sat_track_detail, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupMap();
        setupButtons();
        loadTrackData();
    }


    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() != null ? getDialog().getWindow() : null;
        if (window != null) {
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }


    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) {
            mapView.onDetach();
            mapView = null;
        }
    }


    private void bindViews(View view) {
        tvTitle = view.findViewById(R.id.tvDetailTitle);
        tvSubtitle = view.findViewById(R.id.tvDetailSubtitle);
        tvDistance = view.findViewById(R.id.tvDetailDistance);
        tvDuration = view.findViewById(R.id.tvDetailDuration);
        tvPoints = view.findViewById(R.id.tvDetailPoints);
        tvAvgSpeed = view.findViewById(R.id.tvDetailAvgSpeed);
        btnBack = view.findViewById(R.id.btnBack);
        btnExport = view.findViewById(R.id.btnExport);
        mapView = view.findViewById(R.id.mapDetail);
    }


    private void setupButtons() {
        btnBack.setOnClickListener(v -> dismiss());
        btnExport.setOnClickListener(v -> showExportDialog());
    }


    // ═══════════════════════════════════════════════════════
    //   EXPORT
    // ═══════════════════════════════════════════════════════

    private void showExportDialog() {
        if (currentTrack == null || currentPoints.isEmpty()) {
            Toast.makeText(requireContext(),
                    "No data to export",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String[] formats = {
                "GPX (GPS Exchange Format)",
                "KML (Google Earth)",
                "CSV (Excel/Analysis)"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Export Satellite TRACK")
                .setItems(formats, (dialog, which) -> {
                    TrackExporter.Format format;
                    switch (which) {
                        case 0: format = TrackExporter.Format.GPX; break;
                        case 1: format = TrackExporter.Format.KML; break;
                        case 2: format = TrackExporter.Format.CSV; break;
                        default: return;
                    }
                    performExport(format);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void performExport(TrackExporter.Format format) {
        // Convert SatTrackEntity → MyTrackEntity for export compatibility
        // Create a temp adapter object with same structure
        com.ah.acr.messagebox.database.MyTrackEntity adapter =
                new com.ah.acr.messagebox.database.MyTrackEntity(
                        currentTrack.getId(),
                        currentTrack.getName(),
                        currentTrack.getStartTime(),
                        currentTrack.getEndTime(),
                        currentTrack.getTotalDistance(),
                        currentTrack.getPointCount(),
                        currentTrack.getAvgSpeed(),
                        currentTrack.getMaxSpeed(),
                        currentTrack.getMinAltitude(),
                        currentTrack.getMaxAltitude(),
                        currentTrack.getStatus(),
                        0,  // interval
                        0,  // min distance
                        currentTrack.getCreatedAt()
                );

        // Convert sat points → my points
        List<com.ah.acr.messagebox.database.MyTrackPointEntity> myPoints = new ArrayList<>();
        for (SatTrackPointEntity p : currentPoints) {
            myPoints.add(new com.ah.acr.messagebox.database.MyTrackPointEntity(
                    0,
                    currentTrack.getId(),
                    p.getLatitude(),
                    p.getLongitude(),
                    p.getAltitude(),
                    p.getSpeed(),
                    p.getBearing(),
                    0,
                    p.getTimestamp() != null ? p.getTimestamp() : p.getReceivedAt()
            ));
        }

        TrackExporter.ExportResult result = TrackExporter.exportTrack(
                requireContext(),
                adapter,
                myPoints,
                format
        );

        if (result.success) {
            showExportSuccessDialog(result.file, format);
        } else {
            Toast.makeText(requireContext(),
                    "❌ Export failed: " + result.errorMessage,
                    Toast.LENGTH_LONG).show();
        }
    }


    private void showExportSuccessDialog(File file, TrackExporter.Format format) {
        String displayPath = TrackExporter.getDisplayPath(file);

        new AlertDialog.Builder(requireContext())
                .setTitle("✅ Exported Successfully")
                .setMessage("File saved:\n\n📁 " + displayPath
                        + "\n\nWhat would you like to do?")
                .setPositiveButton("Share", (d, w) -> {
                    try {
                        Intent intent = TrackExporter.buildShareIntent(
                                requireContext(), file, format);
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Share failed: " + e.getMessage());
                        Toast.makeText(requireContext(),
                                "Share failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("OK", null)
                .show();
    }


    // ═══════════════════════════════════════════════════════
    //   MAP
    // ═══════════════════════════════════════════════════════

    private void setupMap() {
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
        mapView.setTilesScaledToDpi(true);
        mapView.getController().setZoom(14.0);
        mapView.getController().setCenter(DEFAULT_CENTER);

        loadMapSource();

        polyline = new Polyline();
        polyline.setColor(Color.parseColor("#378ADD"));  // Blue for satellite
        polyline.setWidth(8.0f);
        mapView.getOverlays().add(polyline);

        startMarker = new Marker(mapView);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        startMarker.setTitle("Start");

        endMarker = new Marker(mapView);
        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        endMarker.setTitle("End");
    }


    private void loadMapSource() {
        try {
            if (isNetworkAvailable()) {
                mapView.setTileSource(TileSourceFactory.MAPNIK);
                return;
            }

            File mbtilesDir = new File(
                    requireContext().getExternalFilesDir(null),
                    MBTILES_SUBDIR
            );
            if (!mbtilesDir.exists()) mbtilesDir.mkdirs();

            File[] mbtilesFiles = mbtilesDir.listFiles(
                    (dir, name) -> name.toLowerCase().endsWith(".mbtiles")
            );

            if (mbtilesFiles != null && mbtilesFiles.length > 0) {
                OfflineTileProvider tileProvider = new OfflineTileProvider(
                        new SimpleRegisterReceiver(requireContext()),
                        mbtilesFiles
                );
                mapView.setTileProvider(tileProvider);
                mapView.setTileSource(new XYTileSource(
                        "offline", 0, 18, 256, ".png", new String[]{}
                ));
                return;
            }

            mapView.setTileSource(TileSourceFactory.MAPNIK);
        } catch (Exception e) {
            Log.e(TAG, "Map source load failed: " + e.getMessage());
            mapView.setTileSource(TileSourceFactory.MAPNIK);
        }
    }


    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }


    // ═══════════════════════════════════════════════════════

    private void loadTrackData() {
        if (trackId <= 0) return;

        viewModel = new ViewModelProvider(this).get(SatTrackViewModel.class);

        viewModel.getTrackById(trackId).observe(getViewLifecycleOwner(), track -> {
            if (track == null) return;
            currentTrack = track;
            updateHeader(track);
        });

        viewModel.getPointsByTrack(trackId).observe(getViewLifecycleOwner(), points -> {
            if (points == null || points.isEmpty()) return;
            currentPoints = points;
            drawPointsOnMap(points);
        });
    }


    private void updateHeader(SatTrackEntity track) {
        tvTitle.setText(track.getName());

        SimpleDateFormat fmt = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.US);
        StringBuilder subtitle = new StringBuilder();
        if (track.getImei() != null && !track.getImei().isEmpty()) {
            subtitle.append("📡 ").append(track.getImei());
        }
        if (track.getStartTime() != null) {
            if (subtitle.length() > 0) subtitle.append(" · ");
            subtitle.append(fmt.format(track.getStartTime()));
        }
        tvSubtitle.setText(subtitle.toString());

        double km = track.getTotalDistance() / 1000.0;
        tvDistance.setText(String.format(Locale.US, "%.2f km", km));

        long durationMs = track.getDurationMillis();
        tvDuration.setText(formatDuration(durationMs));

        tvPoints.setText(String.valueOf(track.getPointCount()));
        tvAvgSpeed.setText(String.format(Locale.US, "%.0f km/h", track.getAvgSpeed()));
    }


    private void drawPointsOnMap(List<SatTrackPointEntity> points) {
        List<GeoPoint> geoPoints = new ArrayList<>();

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;

        for (SatTrackPointEntity p : points) {
            GeoPoint gp = new GeoPoint(p.getLatitude(), p.getLongitude());
            geoPoints.add(gp);

            if (p.getLatitude() < minLat) minLat = p.getLatitude();
            if (p.getLatitude() > maxLat) maxLat = p.getLatitude();
            if (p.getLongitude() < minLng) minLng = p.getLongitude();
            if (p.getLongitude() > maxLng) maxLng = p.getLongitude();
        }

        polyline.setPoints(geoPoints);

        if (!geoPoints.isEmpty()) {
            startMarker.setPosition(geoPoints.get(0));
            if (!mapView.getOverlays().contains(startMarker)) {
                mapView.getOverlays().add(startMarker);
            }

            endMarker.setPosition(geoPoints.get(geoPoints.size() - 1));
            if (!mapView.getOverlays().contains(endMarker)) {
                mapView.getOverlays().add(endMarker);
            }
        }

        if (geoPoints.size() >= 2) {
            double padLat = (maxLat - minLat) * 0.2;
            double padLng = (maxLng - minLng) * 0.2;
            if (padLat < 0.0005) padLat = 0.002;
            if (padLng < 0.0005) padLng = 0.002;

            BoundingBox box = new BoundingBox(
                    maxLat + padLat, maxLng + padLng,
                    minLat - padLat, minLng - padLng
            );

            double finalMinLat = minLat, finalMaxLat = maxLat;
            double finalMinLng = minLng, finalMaxLng = maxLng;
            mapView.post(() -> {
                try {
                    mapView.zoomToBoundingBox(box, true, 80);
                } catch (Exception e) {
                    GeoPoint center = new GeoPoint(
                            (finalMinLat + finalMaxLat) / 2,
                            (finalMinLng + finalMaxLng) / 2
                    );
                    mapView.getController().setCenter(center);
                    mapView.getController().setZoom(15.0);
                }
            });
        } else if (geoPoints.size() == 1) {
            mapView.getController().setCenter(geoPoints.get(0));
            mapView.getController().setZoom(16.0);
        }

        mapView.invalidate();
    }


    private String formatDuration(long ms) {
        if (ms <= 0) return "00:00:00";
        long seconds = ms / 1000;
        long hours = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs);
    }
}
