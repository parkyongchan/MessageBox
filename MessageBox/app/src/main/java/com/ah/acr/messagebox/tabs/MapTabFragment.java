package com.ah.acr.messagebox.tabs;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * 지도 탭 (Map Tab)
 * - 받은 위치 데이터 목록 + 지도 시각화
 * - 검색 포커스 시 지도 숨겨 목록 확대
 * - 지도 모드 수동 토글 (온라인/오프라인)
 *
 * ⭐ UI-2026-04-24: 마커 탭 → 상세 다이얼로그 추가
 *    - 마커 클릭 시 AlertDialog 팝업
 *    - 상세 정보 (IMEI, 시간, 좌표, 모드 등)
 *    - "트랙 상세 보기" 버튼 (DeviceTrackDetail 호출)
 *    - "좌표 복사" 버튼
 *
 * ⭐ 마커 아이콘 구분 (프로토콜 ver 값 기준):
 *
 *   수신 (다른 장비):
 *     🚨 0x10 = SOS 수신 → 빨강 (ic_marker_sos)
 *     🚗 0x11, 0x12, 0x13 = TRACK 수신 → 파랑 (ic_marker_track)
 *
 *   송신 (내 장비):
 *     🆘 0x00 = 내 SOS 송신 → 주황 (ic_marker_my_sos)
 *     🎯 0x01, 0x02, 0x03 = 내 TRACK 송신 → 초록 (ic_marker_my_track)
 *
 *   Legacy (테스트 데이터):
 *     4, 5 = SOS (빨강)
 *     2    = TRACK (파랑)
 */
public class MapTabFragment extends Fragment {
    private static final String TAG = MapTabFragment.class.getSimpleName();

    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(37.5665, 126.9780);
    private static final double DEFAULT_ZOOM = 10.0;
    private static final double DEFAULT_ZOOM_SINGLE = 14.0;

    private FragmentMapTabBinding binding;
    private LocationAdapter mAdapter;
    private LocationViewModel locationViewModel;
    private AddressViewModel addressViewModel;
    private BleViewModel mBleViewModel;

    private MapView mMapView;
    private final List<Marker> mMarkers = new ArrayList<>();
    private boolean mInitialFitDone = false;

    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private static final int MODE_ALL = 0;
    private static final int MODE_TRACK = 2;
    private static final int MODE_SOS = 4;

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

        return binding.getRoot();
    }


    private void setupMapModeToggle() {
        MapModeToggleHelper.setup(
                binding.getRoot(),
                requireContext(),
                newMode -> {
                    Log.v(TAG, "지도 모드 변경: " + newMode);
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
    }


    private void fitAllMarkers() {
        if (mMarkers.isEmpty()) {
            Toast.makeText(getContext(), "No markers to fit", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mMarkers.size() == 1) {
            GeoPoint point = mMarkers.get(0).getPosition();
            mMapView.getController().animateTo(point);
            mMapView.getController().setZoom(DEFAULT_ZOOM_SINGLE);
            return;
        }

        double north = -90, south = 90, east = -180, west = 180;
        for (Marker marker : mMarkers) {
            GeoPoint p = marker.getPosition();
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
                displayName = loc.getCodeNum() != null ? loc.getCodeNum() : "Unknown";
            }
            marker.setTitle(displayName);

            String snippet = String.format(Locale.US, "%.6f, %.6f",
                    loc.getLatitude(), loc.getLongitude());
            if (loc.getCreateAt() != null) {
                snippet += "\n" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()).format(loc.getCreateAt());
            }
            // ⭐ UI-2026-04-24: "탭하여 상세보기" 안내 (다국어)
            snippet += "\n" + getString(R.string.marker_snippet_tap_for_details);
            marker.setSnippet(snippet);

            // ⭐ 마커 아이콘: trackMode 와 isIncomeLoc 으로 판별
            Drawable icon = getMarkerIcon(loc.getTrackMode(), loc.isIncomeLoc());
            if (icon != null) marker.setIcon(icon);

            // ⭐ UI-2026-04-24: 마커 탭 → 상세 다이얼로그
            marker.setOnMarkerClickListener((m, mv) -> {
                Log.v(TAG, "🔵 마커 탭: " + displayName);
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
    }


    /**
     * ⭐ UI-2026-04-24: 마커 탭 시 상세 다이얼로그
     * - 상세 정보 표시 (IMEI, 시간, 좌표, 고도, 모드)
     * - "트랙 상세 보기" 버튼 → DeviceTrackDetailFragment 호출
     * - "좌표 복사" 버튼 → 클립보드 복사
     */
    private void showMarkerDetailDialog(LocationEntity loc, String displayName) {
        SimpleDateFormat fmt = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.US);
        String time = loc.getCreateAt() != null
                ? fmt.format(loc.getCreateAt())
                : "-";

        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.dialog_label_imei)).append(
                loc.getCodeNum() != null ? loc.getCodeNum() : "-").append("\n\n");
        if (!displayName.equals(loc.getCodeNum())) {
            sb.append(getString(R.string.dialog_label_name)).append(displayName).append("\n\n");
        }
        sb.append(getString(R.string.dialog_label_time)).append(time).append("\n\n");
        sb.append(getString(R.string.dialog_label_coordinates)).append(String.format(Locale.US,
                "%.6f, %.6f",
                loc.getLatitude(), loc.getLongitude())).append("\n\n");

        if (loc.getAltitude() != 0.0) {
            sb.append(getString(R.string.dialog_label_altitude)).append(String.format(Locale.US,
                    getString(R.string.dialog_altitude_unit), loc.getAltitude())).append("\n\n");
        }

        String modeText = getTrackModeText(loc.getTrackMode(), loc.isIncomeLoc());
        sb.append(getString(R.string.dialog_label_mode)).append(modeText);

        final String devName = displayName;
        final String codeNum = loc.getCodeNum();

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_location_detail_title))
                .setMessage(sb.toString())
                .setPositiveButton(getString(R.string.btn_view_track_details), (d, w) -> {
                    if (codeNum != null) {
                        DeviceTrackDetailFragment dialog =
                                DeviceTrackDetailFragment.newInstance(codeNum, devName);
                        dialog.show(getParentFragmentManager(), "DeviceTrackDetail");
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


    /**
     * ⭐ UI-2026-04-24: trackMode 값을 읽기 쉬운 텍스트로 변환
     *
     * ⚠️ 수정: isIncomeLoc = true → "수신" (Income = 들어오는)
     *          isIncomeLoc = false → "송신"
     */
    private String getTrackModeText(int trackMode, boolean isIncomeLoc) {
        if (isIncomeLoc) {
            // 수신 (다른 장비로부터)
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
            // 송신 (내 장비)
            switch (trackMode) {
                case 0x00: return getString(R.string.mode_tx_sos);
                case 0x01: return getString(R.string.mode_tx_car_track);
                case 0x02: return getString(R.string.mode_tx_uav_track);
                case 0x03: return getString(R.string.mode_tx_uat_track);
                default: return getString(R.string.mode_tx_unknown, trackMode);
            }
        }
    }


    /**
     * ⭐ 마커 아이콘 선택 (프로토콜 ver 기반 + 송수신 구분)
     *
     * ⚠️ 수정: isIncomeLoc = true → 수신 / false → 송신
     */
    private Drawable getMarkerIcon(int trackMode, boolean isIncomeLoc) {
        int iconRes;

        // ═══ 수신 (다른 장비로부터) - isIncomeLoc = true ═══
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
        // ═══ 송신 (내 장비) - isIncomeLoc = false ═══
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
    }


    private void observeData() {
        locationViewModel.getFilteredLocations().observe(
                getViewLifecycleOwner(),
                new Observer<List<LocationWithAddress>>() {
                    @Override
                    public void onChanged(List<LocationWithAddress> locations) {
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

                        refreshMarkers(locations);
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
        chip.setSelected(true);

        mInitialFitDone = false;
        locationViewModel.setFilterMode(mode);
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

        Log.v(TAG, "검색 모드 진입: 지도 숨김");
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

        Log.v(TAG, "검색 모드 해제: 지도 복원");
    }


    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && binding != null) {
            imm.hideSoftInputFromWindow(binding.editSearch.getWindowToken(), 0);
        }
    }


    private void setupRefresh() {
        binding.buttonReflesh.setOnClickListener(view -> {
            BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            locationViewModel.refresh();
            Toast.makeText(getContext(), "Refreshing...", Toast.LENGTH_SHORT).show();
        });
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

    public void handleLocationDelClick(LocationEntity location) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Location")
                .setMessage("Are you sure you want to delete this location?")
                .setPositiveButton("Delete", (dialog, which) ->
                        locationViewModel.delete(location))
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void handleLocationCopyClick(LocationEntity location) {
        String loc = String.format(Locale.US, "%f,%f",
                location.getLatitude(), location.getLongitude());
        ClipboardManager clipboard = (ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("copy", loc);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Copied to clipboard.", Toast.LENGTH_SHORT).show();
    }

    public void handleLocationMapClick(LocationEntity location) {
        if (location.getCodeNum() == null) {
            Toast.makeText(getContext(), "Invalid device", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(getContext(), "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (location.getAddress() == null) {
                addressViewModel.insert(new AddressEntity(0, code, name, new Date(), null));
            } else {
                addressViewModel.updateNumbersNic(code, name);
            }

            dialog.dismiss();
            Toast.makeText(getContext(), "Saved.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }


    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
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
