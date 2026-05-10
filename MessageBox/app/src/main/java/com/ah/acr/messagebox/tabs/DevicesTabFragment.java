package com.ah.acr.messagebox.tabs;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import com.ah.acr.messagebox.database.MyTrackEntity;
import com.ah.acr.messagebox.database.MyTrackViewModel;
import com.ah.acr.messagebox.service.LocationPermissionHelper;
import com.ah.acr.messagebox.service.LocationTrackingService;
import com.ah.acr.messagebox.util.MapModeManager;
import com.ah.acr.messagebox.util.MapModeToggleHelper;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Self tab - My Location (phone GPS) tracking only.
 *
 * [Removed 2026-05] Satellite TRACK section completely removed per user request.
 * - Header TRACK/SOS buttons still transmit BLE commands but no longer save to DB.
 * - Existing sat_track DB data is preserved (read-only via SatTrackDetailFragment if used).
 * - Segmented tabs removed - My Location is now the only screen on Self tab.
 */
public class DevicesTabFragment extends Fragment {

    private static final String TAG = "DevicesTabFragment";
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(37.5665, 126.9780);

    // ===== My Location (phone GPS) views =====
    private View containerMyLocation;

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

    private View mapModeToggleMyLoc;

    // ===== ViewModels & state =====
    private MyTrackViewModel myTrackViewModel;
    private MyTrackAdapter trackAdapter;
    private int currentTrackId = -1;
    private long trackingStartTime = 0;

    private Polyline pathPolyline;
    private Marker currentLocationMarker;
    private List<GeoPoint> pathPoints = new ArrayList<>();

    private final List<Marker> myNumberedMarkers = new ArrayList<>();

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private final int[] INTERVAL_SECONDS = {10, 30, 60, 120, 300, 600};
    private final int[] MIN_DISTANCES = {5, 10, 20, 50, 100};


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
        setupSpinners();
        setupRecyclerViews();
        setupButtons();
        setupViewModels();
        setupMap();
        setupMapModeToggle();

        if (LocationTrackingService.isServiceRunning) {
            currentTrackId = LocationTrackingService.currentTrackId;
            switchToTrackingState();
        } else {
            switchToNotTrackingState();
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

        if (LocationTrackingService.isServiceRunning) {
            currentTrackId = LocationTrackingService.currentTrackId;
            switchToTrackingState();
            reloadPathFromDb();
        }
    }


    @Override
    public void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(locationReceiver);

        if (mapViewTracking != null) mapViewTracking.onPause();

        stopElapsedTimer();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopElapsedTimer();
        if (mapViewTracking != null) {
            mapViewTracking.onDetach();
            mapViewTracking = null;
        }
    }


    private void setupMapModeToggle() {
        if (mapModeToggleMyLoc != null) {
            MapModeToggleHelper.setup(
                    mapModeToggleMyLoc,
                    requireContext(),
                    newMode -> {
                        applyMapSourceToAllMaps();
                        MapModeToggleHelper.syncUI(mapModeToggleMyLoc, requireContext());
                    }
            );
        }
    }


    private void applyMapSourceToAllMaps() {
        if (mapViewTracking != null) {
            MapModeManager.applyToMapView(requireContext(), mapViewTracking);
        }
    }


    private void bindViews(View root) {
        containerMyLocation = root.findViewById(R.id.containerMyLocation);

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

        mapModeToggleMyLoc = root.findViewById(R.id.mapModeToggleMyLoc);
    }


    private void setupSpinners() {
        String[] intervals = {"10s", "30s", "1m", "2m", "5m", "10m"};
        ArrayAdapter<String> intervalAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.spinner_item_dark,
                intervals);
        intervalAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item_dark);
        spinnerInterval.setAdapter(intervalAdapter);
        spinnerInterval.setSelection(1);

        String[] distances = {"5m", "10m", "20m", "50m", "100m"};
        ArrayAdapter<String> distanceAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.spinner_item_dark,
                distances);
        distanceAdapter.setDropDownViewResource(
                R.layout.spinner_dropdown_item_dark);
        spinnerDistance.setAdapter(distanceAdapter);
        spinnerDistance.setSelection(2);
    }


    private void setupRecyclerViews() {
        trackAdapter = new MyTrackAdapter(new MyTrackAdapter.OnTrackActionListener() {
            @Override public void onTrackClick(MyTrackEntity track) { openTrackDetail(track); }
            @Override public void onTrackDelete(MyTrackEntity track) { confirmDeleteTrack(track); }
            @Override public void onTrackExport(MyTrackEntity track) {
                Toast.makeText(requireContext(),
                        getString(R.string.mygps_export_hint),
                        Toast.LENGTH_SHORT).show();
                openTrackDetail(track);
            }
        });
        rvTracks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTracks.setAdapter(trackAdapter);
        rvTracks.setNestedScrollingEnabled(false);
    }


    private void setupButtons() {
        btnStartTracking.setOnClickListener(v -> onStartClicked());
        btnStopTracking.setOnClickListener(v -> onStopClicked());
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
    }


    private void openTrackDetail(MyTrackEntity track) {
        MyTrackDetailFragment dialog = MyTrackDetailFragment.newInstance(track.getId());
        dialog.show(getParentFragmentManager(), "MyTrackDetail");
    }


    private void confirmDeleteTrack(MyTrackEntity track) {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.mygps_dialog_delete_track_title))
                .setMessage(getString(R.string.mygps_dialog_delete_msg, track.getName()))
                .setPositiveButton(getString(R.string.addr_btn_delete), (d, w) -> {
                    myTrackViewModel.deleteTrack(track);
                    Toast.makeText(requireContext(),
                            getString(R.string.mygps_toast_track_deleted),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    private void setupMap() {
        mapViewTracking.setMultiTouchControls(true);
        mapViewTracking.setBuiltInZoomControls(false);
        mapViewTracking.setTilesScaledToDpi(true);

        MapModeManager.applyToMapView(requireContext(), mapViewTracking);

        mapViewTracking.getController().setZoom(16.0);
        mapViewTracking.getController().setCenter(DEFAULT_CENTER);

        pathPolyline = new Polyline();
        pathPolyline.setColor(Color.RED);
        pathPolyline.setWidth(8.0f);
        mapViewTracking.getOverlays().add(pathPolyline);

        currentLocationMarker = new Marker(mapViewTracking);
        currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        currentLocationMarker.setTitle(getString(R.string.mygps_marker_current_pos));
        mapViewTracking.getOverlays().add(currentLocationMarker);
    }


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
                Toast.makeText(requireContext(),
                        getString(R.string.mygps_toast_tracking_started),
                        Toast.LENGTH_SHORT).show();
            });
            return null;
        });
    }


    private void onStopClicked() {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.mygps_dialog_stop_tracking_title))
                .setMessage(getString(R.string.mygps_dialog_stop_tracking_msg))
                .setPositiveButton(getString(R.string.mygps_btn_stop_save_text),
                        (d, w) -> stopTracking())
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    private void stopTracking() {
        LocationTrackingService.stop(requireContext());
        if (currentTrackId > 0) {
            myTrackViewModel.stopTrack(currentTrackId);
        }
        Toast.makeText(requireContext(),
                getString(R.string.mygps_toast_track_saved),
                Toast.LENGTH_SHORT).show();
        currentTrackId = -1;
        trackingStartTime = 0;
        switchToNotTrackingState();
    }


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

        if (mapViewTracking != null) {
            for (Marker m : myNumberedMarkers) {
                mapViewTracking.getOverlays().remove(m);
            }
            myNumberedMarkers.clear();
            mapViewTracking.invalidate();
        }

        startElapsedTimer();
    }


    private void switchToNotTrackingState() {
        stateTracking.setVisibility(View.GONE);
        stateNotTracking.setVisibility(View.VISIBLE);
        stopElapsedTimer();
    }


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


    private String formatElapsed(long ms) {
        long seconds = ms / 1000;
        long hours = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs);
    }


    private void handleLocationUpdate(Intent intent) {
        double lat = intent.getDoubleExtra(LocationTrackingService.EXTRA_LATITUDE, 0);
        double lng = intent.getDoubleExtra(LocationTrackingService.EXTRA_LONGITUDE, 0);
        double speed = intent.getDoubleExtra(LocationTrackingService.EXTRA_SPEED, 0);

        tvWaitingGps.setVisibility(View.GONE);

        GeoPoint newPoint = new GeoPoint(lat, lng);
        pathPoints.add(newPoint);

        pathPolyline.setPoints(pathPoints);
        currentLocationMarker.setPosition(newPoint);
        currentLocationMarker.setVisible(false);

        redrawMyNumberedMarkers();

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

                    redrawMyNumberedMarkers();

                    if (!pathPoints.isEmpty()) {
                        GeoPoint last = pathPoints.get(pathPoints.size() - 1);
                        currentLocationMarker.setPosition(last);
                        currentLocationMarker.setVisible(false);
                        mapViewTracking.getController().animateTo(last);
                        tvWaitingGps.setVisibility(View.GONE);
                    }
                    mapViewTracking.invalidate();
                });
    }


    private void redrawMyNumberedMarkers() {
        if (mapViewTracking == null) return;

        for (Marker m : myNumberedMarkers) {
            mapViewTracking.getOverlays().remove(m);
        }
        myNumberedMarkers.clear();

        int total = pathPoints.size();
        if (total == 0) return;

        Context ctx = requireContext();

        for (int i = 0; i < total; i++) {
            GeoPoint pt = pathPoints.get(i);

            int number = total - i;
            float alpha = com.ah.acr.messagebox.util.NumberedMarkerUtil
                    .calculateAlpha(i, total);
            boolean isLatest = (i == total - 1);
            int color = com.ah.acr.messagebox.util.NumberedMarkerUtil.COLOR_MY;

            Marker marker = new Marker(mapViewTracking);
            marker.setPosition(pt);
            com.ah.acr.messagebox.util.NumberedMarkerUtil.applyToMarker(
                    marker, ctx, number, color, alpha, isLatest);

            mapViewTracking.getOverlays().add(marker);
            myNumberedMarkers.add(marker);
        }

        mapViewTracking.invalidate();
    }


    private void requestLocationPermission() {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.mygps_dialog_loc_perm_title))
                .setMessage(getString(R.string.mygps_dialog_loc_perm_msg))
                .setPositiveButton(getString(R.string.mygps_btn_grant), (d, w) -> {
                    String[] perms = {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    };
                    requestPermissions(perms, LocationPermissionHelper.REQUEST_CODE_LOCATION);
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    private void requestBackgroundPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.mygps_dialog_bg_loc_title))
                .setMessage(getString(R.string.mygps_dialog_bg_loc_msg))
                .setPositiveButton(getString(R.string.mygps_btn_grant), (d, w) -> {
                    String[] perms = {Manifest.permission.ACCESS_BACKGROUND_LOCATION};
                    requestPermissions(perms, LocationPermissionHelper.REQUEST_CODE_BACKGROUND_LOCATION);
                })
                .setNegativeButton(getString(R.string.mygps_btn_skip), null)
                .show();
    }


    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.mygps_dialog_notif_perm_title))
                .setMessage(getString(R.string.mygps_dialog_notif_perm_msg))
                .setPositiveButton(getString(R.string.mygps_btn_grant), (d, w) -> {
                    String[] perms = {Manifest.permission.POST_NOTIFICATIONS};
                    requestPermissions(perms, LocationPermissionHelper.REQUEST_CODE_NOTIFICATIONS);
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
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
                        getString(R.string.mygps_toast_loc_perm_denied),
                        Toast.LENGTH_SHORT).show();
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
                .setTitle(getString(R.string.mygps_dialog_gps_disabled_title))
                .setMessage(getString(R.string.mygps_dialog_gps_disabled_msg))
                .setPositiveButton(getString(R.string.mygps_btn_settings), (d, w) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }
}
