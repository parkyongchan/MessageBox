package com.ah.acr.messagebox.tabs;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.adapter.MyTrackAdapter;
import com.ah.acr.messagebox.adapter.SatTrackAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.database.MyTrackEntity;
import com.ah.acr.messagebox.database.MyTrackViewModel;
import com.ah.acr.messagebox.database.SatTrackEntity;
import com.ah.acr.messagebox.database.SatTrackStateHolder;
import com.ah.acr.messagebox.database.SatTrackViewModel;
import com.ah.acr.messagebox.service.LocationPermissionHelper;
import com.ah.acr.messagebox.service.LocationTrackingService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DevicesTabFragment extends Fragment {

    private static final String TAG = "DevicesTabFragment";
    private static final String MBTILES_SUBDIR = "mbtiles";
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(37.5665, 126.9780);

    private static final int TAB_MY_LOCATION = 0;
    private static final int TAB_SATELLITE = 1;
    private int currentTab = TAB_MY_LOCATION;

    // Segment tabs
    private TextView segMyLocation;
    private TextView segSatellite;
    private View containerMyLocation;
    private View containerSatellite;

    // ─── Tab 1: My Location ───
    private View stateNotTracking;
    private Spinner spinnerInterval;
    private Spinner spinnerDistance;
    private Button btnStartTracking;
    private RecyclerView rvTracks;
    private TextView tvTrackCount;
    private TextView tvEmptyTracks;

    private View stateTracking;
    private TextView tvElapsed;
    private TextView tvDistance;
    private TextView tvSpeed;
    private TextView tvPoints;
    private TextView tvWaitingGps;
    private MapView mapViewTracking;
    private Button btnStopTracking;

    // ─── Tab 2: Satellite TRACK ───
    private View satStateNotTracking;
    private View linkSettings;  // ⭐ NEW: Settings link
    private TextView tvSatConnectedImei;
    private Button btnSatStart;
    private RecyclerView rvSatTracks;
    private TextView tvSatTrackCount;
    private TextView tvEmptySatTracks;

    private View satStateTracking;
    private TextView tvSatElapsed;
    private TextView tvSatDistance;
    private TextView tvSatPoints;
    private TextView tvSatWaitingGps;
    private MapView mapViewSatTracking;
    private Button btnSatStop;


    // ViewModels & state
    private MyTrackViewModel myTrackViewModel;
    private MyTrackAdapter trackAdapter;
    private int currentTrackId = -1;
    private long trackingStartTime = 0;

    private SatTrackViewModel satTrackViewModel;
    private SatTrackAdapter satTrackAdapter;
    private int currentSatTrackId = -1;
    private long satTrackingStartTime = 0;
    private String connectedImei = null;

    // Map overlays (my location)
    private Polyline pathPolyline;
    private Marker currentLocationMarker;
    private List<GeoPoint> pathPoints = new ArrayList<>();

    // Map overlays (satellite)
    private Polyline satPolyline;
    private Marker satCurrentMarker;
    private List<GeoPoint> satPathPoints = new ArrayList<>();

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Runnable satTimerRunnable;

    private final int[] INTERVAL_SECONDS = {10, 30, 60, 120, 300, 600};
    private final int[] MIN_DISTANCES = {5, 10, 20, 50, 100};


    // ═══════════════════════════════════════════════════════════════
    //   BROADCAST RECEIVER (My Location)
    // ═══════════════════════════════════════════════════════════════

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (LocationTrackingService.BROADCAST_LOCATION_UPDATE.equals(action)) {
                handleLocationUpdate(intent);
            } else if (LocationTrackingService.BROADCAST_SERVICE_STATE.equals(action)) {
                handleServiceStateChange(intent);
            }
        }
    };


    // ═══════════════════════════════════════════════════════════════
    //   LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
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

        View root = inflater.inflate(R.layout.fragment_devices_tab, container, false);

        bindViews(root);
        setupSegmentTabs();
        setupSpinners();
        setupRecyclerViews();
        setupButtons();
        setupViewModels();
        setupMap();
        setupSatMap();
        observeBleStatus();

        updateSegmentVisuals(TAB_MY_LOCATION);

        // My Location state restore
        if (LocationTrackingService.isServiceRunning) {
            currentTrackId = LocationTrackingService.currentTrackId;
            switchToTrackingState();
        } else {
            switchToNotTrackingState();
        }

        // Satellite state restore
        if (SatTrackStateHolder.isSessionActive()) {
            currentSatTrackId = SatTrackStateHolder.activeTrackId;
            switchToSatTrackingState();
        } else {
            switchToSatNotTrackingState();
        }

        return root;
    }


    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationTrackingService.BROADCAST_LOCATION_UPDATE);
        filter.addAction(LocationTrackingService.BROADCAST_SERVICE_STATE);
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(locationReceiver, filter);

        if (mapViewTracking != null) mapViewTracking.onResume();
        if (mapViewSatTracking != null) mapViewSatTracking.onResume();

        if (LocationTrackingService.isServiceRunning) {
            currentTrackId = LocationTrackingService.currentTrackId;
            switchToTrackingState();
            reloadPathFromDb();
        }

        if (SatTrackStateHolder.isSessionActive()) {
            currentSatTrackId = SatTrackStateHolder.activeTrackId;
            switchToSatTrackingState();
            reloadSatPathFromDb();
        }
    }


    @Override
    public void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(locationReceiver);

        if (mapViewTracking != null) mapViewTracking.onPause();
        if (mapViewSatTracking != null) mapViewSatTracking.onPause();

        stopElapsedTimer();
        stopSatElapsedTimer();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopElapsedTimer();
        stopSatElapsedTimer();
        if (mapViewTracking != null) {
            mapViewTracking.onDetach();
            mapViewTracking = null;
        }
        if (mapViewSatTracking != null) {
            mapViewSatTracking.onDetach();
            mapViewSatTracking = null;
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //   SETUP
    // ═══════════════════════════════════════════════════════════════

    private void bindViews(View root) {
        segMyLocation = root.findViewById(R.id.segMyLocation);
        segSatellite = root.findViewById(R.id.segSatellite);
        containerMyLocation = root.findViewById(R.id.containerMyLocation);
        containerSatellite = root.findViewById(R.id.containerSatellite);

        // My Location
        stateNotTracking = root.findViewById(R.id.stateNotTracking);
        spinnerInterval = root.findViewById(R.id.spinnerInterval);
        spinnerDistance = root.findViewById(R.id.spinnerDistance);
        btnStartTracking = root.findViewById(R.id.btnStartTracking);
        rvTracks = root.findViewById(R.id.rvTracks);
        tvTrackCount = root.findViewById(R.id.tvTrackCount);
        tvEmptyTracks = root.findViewById(R.id.tvEmptyTracks);

        stateTracking = root.findViewById(R.id.stateTracking);
        tvElapsed = root.findViewById(R.id.tvElapsed);
        tvDistance = root.findViewById(R.id.tvDistance);
        tvSpeed = root.findViewById(R.id.tvSpeed);
        tvPoints = root.findViewById(R.id.tvPoints);
        tvWaitingGps = root.findViewById(R.id.tvWaitingGps);
        mapViewTracking = root.findViewById(R.id.mapViewTracking);
        btnStopTracking = root.findViewById(R.id.btnStopTracking);

        // Satellite
        satStateNotTracking = root.findViewById(R.id.satStateNotTracking);
        linkSettings = root.findViewById(R.id.linkSettings);  // ⭐ NEW
        tvSatConnectedImei = root.findViewById(R.id.tvSatConnectedImei);
        btnSatStart = root.findViewById(R.id.btnSatStart);
        rvSatTracks = root.findViewById(R.id.rvSatTracks);
        tvSatTrackCount = root.findViewById(R.id.tvSatTrackCount);
        tvEmptySatTracks = root.findViewById(R.id.tvEmptySatTracks);

        satStateTracking = root.findViewById(R.id.satStateTracking);
        tvSatElapsed = root.findViewById(R.id.tvSatElapsed);
        tvSatDistance = root.findViewById(R.id.tvSatDistance);
        tvSatPoints = root.findViewById(R.id.tvSatPoints);
        tvSatWaitingGps = root.findViewById(R.id.tvSatWaitingGps);
        mapViewSatTracking = root.findViewById(R.id.mapViewSatTracking);
        btnSatStop = root.findViewById(R.id.btnSatStop);
    }


    private void setupSegmentTabs() {
        segMyLocation.setOnClickListener(v -> switchTab(TAB_MY_LOCATION));
        segSatellite.setOnClickListener(v -> switchTab(TAB_SATELLITE));
    }


    private void switchTab(int tabIndex) {
        if (currentTab == tabIndex) return;
        currentTab = tabIndex;
        updateSegmentVisuals(tabIndex);
    }


    private void updateSegmentVisuals(int tabIndex) {
        if (tabIndex == TAB_MY_LOCATION) {
            segMyLocation.setSelected(true);
            segSatellite.setSelected(false);
            segMyLocation.setTextColor(0xFF0A1628);
            segSatellite.setTextColor(0xFF95B0D4);
            containerMyLocation.setVisibility(View.VISIBLE);
            containerSatellite.setVisibility(View.GONE);
        } else {
            segMyLocation.setSelected(false);
            segSatellite.setSelected(true);
            segMyLocation.setTextColor(0xFF95B0D4);
            segSatellite.setTextColor(0xFF0A1628);
            containerMyLocation.setVisibility(View.GONE);
            containerSatellite.setVisibility(View.VISIBLE);
        }
    }


    private void setupSpinners() {
        String[] intervals = {"10s", "30s", "1m", "2m", "5m", "10m"};
        ArrayAdapter<String> intervalAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, intervals);
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInterval.setAdapter(intervalAdapter);
        spinnerInterval.setSelection(1);

        String[] distances = {"5m", "10m", "20m", "50m", "100m"};
        ArrayAdapter<String> distanceAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, distances);
        distanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDistance.setAdapter(distanceAdapter);
        spinnerDistance.setSelection(2);
    }


    private void setupRecyclerViews() {
        // My Location adapter
        trackAdapter = new MyTrackAdapter(new MyTrackAdapter.OnTrackActionListener() {
            @Override public void onTrackClick(MyTrackEntity track) { openTrackDetail(track); }
            @Override public void onTrackDelete(MyTrackEntity track) { confirmDeleteTrack(track); }
            @Override public void onTrackExport(MyTrackEntity track) {
                Toast.makeText(requireContext(), "Tap 📤 in the header to export",
                        Toast.LENGTH_SHORT).show();
                openTrackDetail(track);
            }
        });
        rvTracks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTracks.setAdapter(trackAdapter);
        rvTracks.setNestedScrollingEnabled(false);

        // Satellite adapter
        satTrackAdapter = new SatTrackAdapter(new SatTrackAdapter.OnTrackActionListener() {
            @Override public void onTrackClick(SatTrackEntity track) { openSatTrackDetail(track); }
            @Override public void onTrackDelete(SatTrackEntity track) { confirmDeleteSatTrack(track); }
            @Override public void onTrackExport(SatTrackEntity track) {
                Toast.makeText(requireContext(), "Tap 📤 in the header to export",
                        Toast.LENGTH_SHORT).show();
                openSatTrackDetail(track);
            }
        });
        rvSatTracks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSatTracks.setAdapter(satTrackAdapter);
        rvSatTracks.setNestedScrollingEnabled(false);
    }


    private void setupButtons() {
        btnStartTracking.setOnClickListener(v -> onStartClicked());
        btnStopTracking.setOnClickListener(v -> onStopClicked());

        btnSatStart.setOnClickListener(v -> onSatStartClicked());
        btnSatStop.setOnClickListener(v -> onSatStopClicked());

        // ⭐ NEW: Settings link → navigate to Settings tab
        if (linkSettings != null) {
            linkSettings.setOnClickListener(v -> navigateToSettings());
        }
    }


    /**
     * ⭐ NEW: Navigate to Settings tab by programmatically selecting
     * it in the MainActivity's BottomNavigationView.
     */
    private void navigateToSettings() {
        try {
            View bottomNav = requireActivity().findViewById(R.id.bottom_nav);
            if (bottomNav instanceof BottomNavigationView) {
                ((BottomNavigationView) bottomNav)
                        .setSelectedItemId(R.id.tab_settings);
            } else {
                Toast.makeText(requireContext(),
                        "Please open Settings tab manually",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Navigate to settings failed: " + e.getMessage());
            Toast.makeText(requireContext(),
                    "Please open Settings tab manually",
                    Toast.LENGTH_SHORT).show();
        }
    }


    private void setupViewModels() {
        myTrackViewModel = new ViewModelProvider(this).get(MyTrackViewModel.class);

        myTrackViewModel.getCompletedTracks().observe(getViewLifecycleOwner(), tracks -> {
            int count = tracks != null ? tracks.size() : 0;
            tvTrackCount.setText(String.valueOf(count));

            if (count == 0) {
                tvEmptyTracks.setVisibility(View.VISIBLE);
                rvTracks.setVisibility(View.GONE);
            } else {
                tvEmptyTracks.setVisibility(View.GONE);
                rvTracks.setVisibility(View.VISIBLE);
                trackAdapter.submitList(tracks);
            }
        });

        satTrackViewModel = new ViewModelProvider(this).get(SatTrackViewModel.class);

        satTrackViewModel.getCompletedTracks().observe(getViewLifecycleOwner(), tracks -> {
            int count = tracks != null ? tracks.size() : 0;
            tvSatTrackCount.setText(String.valueOf(count));

            if (count == 0) {
                tvEmptySatTracks.setVisibility(View.VISIBLE);
                rvSatTracks.setVisibility(View.GONE);
            } else {
                tvEmptySatTracks.setVisibility(View.GONE);
                rvSatTracks.setVisibility(View.VISIBLE);
                satTrackAdapter.submitList(tracks);
            }
        });
    }


    private void observeBleStatus() {
        BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), info -> {
            if (info != null && info.getImei() != null && !info.getImei().isEmpty()) {
                connectedImei = info.getImei();
                tvSatConnectedImei.setText(connectedImei);
                tvSatConnectedImei.setTextColor(0xFF00E5D1);
            } else {
                connectedImei = null;
                tvSatConnectedImei.setText("Not connected");
                tvSatConnectedImei.setTextColor(0xFFFF5252);
            }
        });

        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device == null) {
                connectedImei = null;
                tvSatConnectedImei.setText("Not connected");
                tvSatConnectedImei.setTextColor(0xFFFF5252);
            }
        });
    }


    // ═══════════════════════════════════════════════════════════════
    //   TRACK ACTIONS (My Location)
    // ═══════════════════════════════════════════════════════════════

    private void openTrackDetail(MyTrackEntity track) {
        MyTrackDetailFragment dialog = MyTrackDetailFragment.newInstance(track.getId());
        dialog.show(getParentFragmentManager(), "MyTrackDetail");
    }


    private void confirmDeleteTrack(MyTrackEntity track) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Track")
                .setMessage("Delete \"" + track.getName() + "\"?\nThis cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    myTrackViewModel.deleteTrack(track);
                    Toast.makeText(requireContext(), "✅ Track deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    // ═══════════════════════════════════════════════════════════════
    //   TRACK ACTIONS (Satellite)
    // ═══════════════════════════════════════════════════════════════

    private void openSatTrackDetail(SatTrackEntity track) {
        SatTrackDetailFragment dialog = SatTrackDetailFragment.newInstance(track.getId());
        dialog.show(getParentFragmentManager(), "SatTrackDetail");
    }


    private void confirmDeleteSatTrack(SatTrackEntity track) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Satellite Track")
                .setMessage("Delete \"" + track.getName() + "\"?\nThis cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    satTrackViewModel.deleteTrack(track);
                    Toast.makeText(requireContext(), "✅ Track deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    // ═══════════════════════════════════════════════════════════════
    //   MAP SETUP (My Location)
    // ═══════════════════════════════════════════════════════════════

    private void setupMap() {
        mapViewTracking.setMultiTouchControls(true);
        mapViewTracking.setBuiltInZoomControls(false);
        mapViewTracking.setTilesScaledToDpi(true);

        loadMapSource(mapViewTracking);

        mapViewTracking.getController().setZoom(16.0);
        mapViewTracking.getController().setCenter(DEFAULT_CENTER);

        pathPolyline = new Polyline();
        pathPolyline.setColor(Color.RED);
        pathPolyline.setWidth(8.0f);
        mapViewTracking.getOverlays().add(pathPolyline);

        currentLocationMarker = new Marker(mapViewTracking);
        currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        currentLocationMarker.setTitle("Current Position");
        mapViewTracking.getOverlays().add(currentLocationMarker);
    }


    private void setupSatMap() {
        mapViewSatTracking.setMultiTouchControls(true);
        mapViewSatTracking.setBuiltInZoomControls(false);
        mapViewSatTracking.setTilesScaledToDpi(true);

        loadMapSource(mapViewSatTracking);

        mapViewSatTracking.getController().setZoom(14.0);
        mapViewSatTracking.getController().setCenter(DEFAULT_CENTER);

        satPolyline = new Polyline();
        satPolyline.setColor(Color.parseColor("#378ADD"));
        satPolyline.setWidth(8.0f);
        mapViewSatTracking.getOverlays().add(satPolyline);

        satCurrentMarker = new Marker(mapViewSatTracking);
        satCurrentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        satCurrentMarker.setTitle("Last Position");
        mapViewSatTracking.getOverlays().add(satCurrentMarker);
    }


    private void loadMapSource(MapView mapView) {
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


    // ═══════════════════════════════════════════════════════════════
    //   MY LOCATION - START/STOP
    // ═══════════════════════════════════════════════════════════════

    private void onStartClicked() {
        if (!LocationPermissionHelper.hasLocationPermission(requireContext())) {
            requestLocationPermission();
            return;
        }
        if (!LocationPermissionHelper.isGpsEnabled(requireContext())) {
            showGpsDisabledDialog();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !LocationPermissionHelper.hasNotificationPermission(requireContext())) {
            requestNotificationPermission();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && !LocationPermissionHelper.hasBackgroundLocationPermission(requireContext())) {
            requestBackgroundPermission();
        }
        startTracking();
    }


    private void startTracking() {
        int intervalSec = INTERVAL_SECONDS[spinnerInterval.getSelectedItemPosition()];
        int minDist = MIN_DISTANCES[spinnerDistance.getSelectedItemPosition()];

        String trackName = "Track " + android.text.format.DateFormat.format(
                "MM/dd HH:mm", new Date()).toString();

        myTrackViewModel.startNewTrack(trackName, intervalSec, minDist, trackId -> {
            currentTrackId = trackId.intValue();
            trackingStartTime = System.currentTimeMillis();

            LocationTrackingService.start(
                    requireContext(), currentTrackId, intervalSec, minDist);

            requireActivity().runOnUiThread(() -> {
                switchToTrackingState();
                Toast.makeText(requireContext(), "🔴 Tracking started",
                        Toast.LENGTH_SHORT).show();
            });
            return null;
        });
    }


    private void onStopClicked() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Stop Tracking")
                .setMessage("Stop and save this track?")
                .setPositiveButton("Stop & Save", (d, w) -> stopTracking())
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void stopTracking() {
        LocationTrackingService.stop(requireContext());
        if (currentTrackId > 0) {
            myTrackViewModel.stopTrack(currentTrackId);
        }
        Toast.makeText(requireContext(), "✅ Track saved", Toast.LENGTH_SHORT).show();
        currentTrackId = -1;
        trackingStartTime = 0;
        switchToNotTrackingState();
    }


    // ═══════════════════════════════════════════════════════════════
    //   SATELLITE - START/STOP
    // ═══════════════════════════════════════════════════════════════

    private void onSatStartClicked() {
        if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
            Toast.makeText(requireContext(),
                    "❌ Please connect BLE device first",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (connectedImei == null || connectedImei.isEmpty()) {
            Toast.makeText(requireContext(),
                    "⚠️ Waiting for device info...",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Start Satellite TRACK")
                .setMessage("Send TRACK START command to device?\n\nIMEI: " + connectedImei)
                .setPositiveButton("Start", (d, w) -> startSatTracking())
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void startSatTracking() {
        String trackName = "SatTrack " + android.text.format.DateFormat.format(
                "MM/dd HH:mm", new Date()).toString();

        satTrackViewModel.startNewTrack(trackName, connectedImei, trackId -> {
            currentSatTrackId = trackId.intValue();
            satTrackingStartTime = System.currentTimeMillis();

            SatTrackStateHolder.startSession(currentSatTrackId, connectedImei);

            BLE.INSTANCE.getWriteQueue().offer("LOCATION=2");

            requireActivity().runOnUiThread(() -> {
                switchToSatTrackingState();
                Toast.makeText(requireContext(),
                        "🛰 Satellite TRACK started",
                        Toast.LENGTH_SHORT).show();
            });
            return null;
        });
    }


    private void onSatStopClicked() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Stop Satellite TRACK")
                .setMessage("Stop TRACK mode and save this session?")
                .setPositiveButton("Stop & Save", (d, w) -> stopSatTracking())
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void stopSatTracking() {
        BLE.INSTANCE.getWriteQueue().offer("LOCATION=3");

        if (currentSatTrackId > 0) {
            satTrackViewModel.stopTrack(currentSatTrackId);
        }

        SatTrackStateHolder.stopSession();

        Toast.makeText(requireContext(),
                "✅ Satellite TRACK saved",
                Toast.LENGTH_SHORT).show();

        currentSatTrackId = -1;
        satTrackingStartTime = 0;
        switchToSatNotTrackingState();
    }


    // ═══════════════════════════════════════════════════════════════
    //   UI STATE (My Location)
    // ═══════════════════════════════════════════════════════════════

    private void switchToTrackingState() {
        stateNotTracking.setVisibility(View.GONE);
        stateTracking.setVisibility(View.VISIBLE);

        tvDistance.setText("0.00 km");
        tvSpeed.setText("0 km/h");
        tvPoints.setText("0 pt");
        tvElapsed.setText("00:00:00");
        tvWaitingGps.setVisibility(View.VISIBLE);

        pathPoints.clear();
        if (pathPolyline != null) pathPolyline.setPoints(pathPoints);
        if (mapViewTracking != null) mapViewTracking.invalidate();

        startElapsedTimer();
    }


    private void switchToNotTrackingState() {
        stateTracking.setVisibility(View.GONE);
        stateNotTracking.setVisibility(View.VISIBLE);
        stopElapsedTimer();
    }


    // ═══════════════════════════════════════════════════════════════
    //   UI STATE (Satellite)
    // ═══════════════════════════════════════════════════════════════

    private void switchToSatTrackingState() {
        satStateNotTracking.setVisibility(View.GONE);
        satStateTracking.setVisibility(View.VISIBLE);

        tvSatDistance.setText("0.00 km");
        tvSatPoints.setText("0 pt");
        tvSatElapsed.setText("00:00:00");
        tvSatWaitingGps.setVisibility(View.VISIBLE);

        satPathPoints.clear();
        if (satPolyline != null) satPolyline.setPoints(satPathPoints);
        if (mapViewSatTracking != null) mapViewSatTracking.invalidate();

        startSatElapsedTimer();
    }


    private void switchToSatNotTrackingState() {
        satStateTracking.setVisibility(View.GONE);
        satStateNotTracking.setVisibility(View.VISIBLE);
        stopSatElapsedTimer();
    }


    // ═══════════════════════════════════════════════════════════════
    //   TIMERS
    // ═══════════════════════════════════════════════════════════════

    private void startElapsedTimer() {
        stopElapsedTimer();

        if (trackingStartTime == 0 && currentTrackId > 0) {
            myTrackViewModel.getActiveTrack().observe(getViewLifecycleOwner(), track -> {
                if (track != null && trackingStartTime == 0) {
                    trackingStartTime = track.getStartTime().getTime();
                }
            });
        }

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (trackingStartTime > 0) {
                    long elapsed = System.currentTimeMillis() - trackingStartTime;
                    tvElapsed.setText(formatElapsed(elapsed));
                }
                uiHandler.postDelayed(this, 1000);
            }
        };
        uiHandler.post(timerRunnable);
    }


    private void stopElapsedTimer() {
        if (timerRunnable != null) {
            uiHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }


    private void startSatElapsedTimer() {
        stopSatElapsedTimer();

        if (satTrackingStartTime == 0 && currentSatTrackId > 0) {
            satTrackViewModel.getActiveTrack().observe(getViewLifecycleOwner(), track -> {
                if (track != null && satTrackingStartTime == 0) {
                    satTrackingStartTime = track.getStartTime().getTime();
                }
            });
        }

        satTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (satTrackingStartTime > 0) {
                    long elapsed = System.currentTimeMillis() - satTrackingStartTime;
                    tvSatElapsed.setText(formatElapsed(elapsed));
                }
                uiHandler.postDelayed(this, 1000);
            }
        };
        uiHandler.post(satTimerRunnable);
    }


    private void stopSatElapsedTimer() {
        if (satTimerRunnable != null) {
            uiHandler.removeCallbacks(satTimerRunnable);
            satTimerRunnable = null;
        }
    }


    private String formatElapsed(long ms) {
        long seconds = ms / 1000;
        long hours = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs);
    }


    // ═══════════════════════════════════════════════════════════════
    //   LOCATION UPDATE (My Location)
    // ═══════════════════════════════════════════════════════════════

    private void handleLocationUpdate(Intent intent) {
        double lat = intent.getDoubleExtra(LocationTrackingService.EXTRA_LATITUDE, 0);
        double lng = intent.getDoubleExtra(LocationTrackingService.EXTRA_LONGITUDE, 0);
        double speed = intent.getDoubleExtra(LocationTrackingService.EXTRA_SPEED, 0);

        tvWaitingGps.setVisibility(View.GONE);

        GeoPoint newPoint = new GeoPoint(lat, lng);
        pathPoints.add(newPoint);

        pathPolyline.setPoints(pathPoints);
        currentLocationMarker.setPosition(newPoint);

        if (pathPoints.size() <= 3) {
            mapViewTracking.getController().animateTo(newPoint);
            mapViewTracking.getController().setZoom(17.0);
        } else {
            mapViewTracking.getController().animateTo(newPoint);
        }

        mapViewTracking.invalidate();
        tvSpeed.setText(String.format(Locale.US, "%.0f km/h", speed));
        updateStatsFromDb();
    }


    private void handleServiceStateChange(Intent intent) {
        boolean isRunning = intent.getBooleanExtra(
                LocationTrackingService.EXTRA_IS_RUNNING, false);
        if (!isRunning && currentTrackId != -1) {
            switchToNotTrackingState();
            currentTrackId = -1;
        }
    }


    private void updateStatsFromDb() {
        if (currentTrackId <= 0) return;
        myTrackViewModel.getActiveTrack().observe(getViewLifecycleOwner(), track -> {
            if (track == null) return;
            double distanceKm = track.getTotalDistance() / 1000.0;
            tvDistance.setText(String.format(Locale.US, "%.2f km", distanceKm));
            tvPoints.setText(String.format(Locale.US, "%d pt", track.getPointCount()));
            if (trackingStartTime == 0) {
                trackingStartTime = track.getStartTime().getTime();
            }
        });
    }


    private void reloadPathFromDb() {
        if (currentTrackId <= 0) return;
        myTrackViewModel.getPointsByTrack(currentTrackId)
                .observe(getViewLifecycleOwner(), points -> {
                    if (points == null || points.isEmpty()) return;
                    pathPoints.clear();
                    for (com.ah.acr.messagebox.database.MyTrackPointEntity p : points) {
                        pathPoints.add(new GeoPoint(p.getLatitude(), p.getLongitude()));
                    }
                    pathPolyline.setPoints(pathPoints);
                    if (!pathPoints.isEmpty()) {
                        GeoPoint last = pathPoints.get(pathPoints.size() - 1);
                        currentLocationMarker.setPosition(last);
                        mapViewTracking.getController().animateTo(last);
                        tvWaitingGps.setVisibility(View.GONE);
                    }
                    mapViewTracking.invalidate();
                });
    }


    // ═══════════════════════════════════════════════════════════════
    //   SATELLITE POINT UPDATE
    // ═══════════════════════════════════════════════════════════════

    private void reloadSatPathFromDb() {
        if (currentSatTrackId <= 0) return;

        satTrackViewModel.getPointsByTrack(currentSatTrackId)
                .observe(getViewLifecycleOwner(), points -> {
                    if (points == null || points.isEmpty()) return;

                    satPathPoints.clear();
                    for (com.ah.acr.messagebox.database.SatTrackPointEntity p : points) {
                        satPathPoints.add(new GeoPoint(p.getLatitude(), p.getLongitude()));
                    }
                    satPolyline.setPoints(satPathPoints);

                    if (!satPathPoints.isEmpty()) {
                        GeoPoint last = satPathPoints.get(satPathPoints.size() - 1);
                        satCurrentMarker.setPosition(last);
                        mapViewSatTracking.getController().animateTo(last);
                        tvSatWaitingGps.setVisibility(View.GONE);
                    }

                    mapViewSatTracking.invalidate();
                });

        satTrackViewModel.getActiveTrack().observe(getViewLifecycleOwner(), track -> {
            if (track == null) return;
            double distanceKm = track.getTotalDistance() / 1000.0;
            tvSatDistance.setText(String.format(Locale.US, "%.2f km", distanceKm));
            tvSatPoints.setText(String.format(Locale.US, "%d pt", track.getPointCount()));
            if (satTrackingStartTime == 0) {
                satTrackingStartTime = track.getStartTime().getTime();
            }
        });
    }


    // ═══════════════════════════════════════════════════════════════
    //   PERMISSIONS
    // ═══════════════════════════════════════════════════════════════

    private void requestLocationPermission() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Location Permission Required")
                .setMessage("GPS tracking requires location permission.")
                .setPositiveButton("Grant", (d, w) -> {
                    String[] perms = {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    };
                    requestPermissions(perms, LocationPermissionHelper.REQUEST_CODE_LOCATION);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void requestBackgroundPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Background Location")
                .setMessage("For reliable tracking when the screen is off, please allow location access \"All the time\".")
                .setPositiveButton("Grant", (d, w) -> {
                    String[] perms = {Manifest.permission.ACCESS_BACKGROUND_LOCATION};
                    requestPermissions(perms, LocationPermissionHelper.REQUEST_CODE_BACKGROUND_LOCATION);
                })
                .setNegativeButton("Skip", null)
                .show();
    }


    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Notification Permission")
                .setMessage("A notification will keep tracking alive in the background.")
                .setPositiveButton("Grant", (d, w) -> {
                    String[] perms = {Manifest.permission.POST_NOTIFICATIONS};
                    requestPermissions(perms, LocationPermissionHelper.REQUEST_CODE_NOTIFICATIONS);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        switch (requestCode) {
            case LocationPermissionHelper.REQUEST_CODE_LOCATION:
                if (granted) onStartClicked();
                else Toast.makeText(requireContext(),
                        "Location permission denied", Toast.LENGTH_SHORT).show();
                break;
            case LocationPermissionHelper.REQUEST_CODE_NOTIFICATIONS:
                onStartClicked();
                break;
            case LocationPermissionHelper.REQUEST_CODE_BACKGROUND_LOCATION:
                startTracking();
                break;
        }
    }


    private void showGpsDisabledDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("GPS is disabled")
                .setMessage("Please enable GPS/Location in your device settings.")
                .setPositiveButton("Settings", (d, w) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
