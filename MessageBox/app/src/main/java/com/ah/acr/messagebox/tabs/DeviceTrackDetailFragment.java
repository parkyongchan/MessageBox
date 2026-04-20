package com.ah.acr.messagebox.tabs;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.adapter.TrackPointAdapter;
import com.ah.acr.messagebox.database.LocationEntity;
import com.ah.acr.messagebox.database.LocationViewModel;
import com.ah.acr.messagebox.database.LocationWithAddress;
import com.ah.acr.messagebox.databinding.FragmentDeviceTrackDetailBinding;
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * 장비별 전체 트랙 상세 팝업 (전체화면 DialogFragment)
 * - 해당 장비의 모든 위치 기록
 * - 폴리라인으로 경로 표시
 * - 시간순 하이라이트 자동재생
 * - ⭐ 지도 모드 수동 토글
 * - ⭐ 아이콘/타입 분류: 0x10=SOS, 0x11=CAR(Track)
 */
public class DeviceTrackDetailFragment extends DialogFragment {
    private static final String TAG = DeviceTrackDetailFragment.class.getSimpleName();

    private static final String ARG_CODE_NUM = "codeNum";
    private static final String ARG_NAME = "name";

    private static final double DEFAULT_ZOOM = 10.0;

    private static final long[] SPEED_INTERVALS = {1000, 500, 250, 100};
    private static final String[] SPEED_LABELS = {"1x", "2x", "4x", "10x"};

    private FragmentDeviceTrackDetailBinding binding;
    private LocationViewModel locationViewModel;
    private TrackPointAdapter trackAdapter;
    private MapView mMapView;

    private String codeNum;
    private String deviceName;

    private List<LocationWithAddress> mTrackPoints = new ArrayList<>();
    private final List<Marker> mMarkers = new ArrayList<>();
    private Polyline mPolyline;
    private LiveData<List<LocationWithAddress>> mCurrentTrackLive;

    private boolean mIsPlaying = false;
    private int mCurrentPlayIndex = -1;
    private int mSpeedIndex = 0;
    private final Handler mPlayHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPlayRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsPlaying) return;
            advancePlayback();
            if (mIsPlaying && mCurrentPlayIndex < mTrackPoints.size() - 1) {
                mPlayHandler.postDelayed(this, SPEED_INTERVALS[mSpeedIndex]);
            } else {
                mIsPlaying = false;
                updatePlayIcon();
            }
        }
    };

    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat timeFmt =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat datetimeFmt =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());


    public static DeviceTrackDetailFragment newInstance(String codeNum, String name) {
        DeviceTrackDetailFragment fragment = new DeviceTrackDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CODE_NUM, codeNum);
        args.putString(ARG_NAME, name);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        if (getArguments() != null) {
            codeNum = getArguments().getString(ARG_CODE_NUM);
            deviceName = getArguments().getString(ARG_NAME);
        }
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        return dialog;
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDeviceTrackDetailBinding.inflate(inflater, container, false);

        setupHeader();
        setupViewModel();
        setupMap();
        setupTrackList();
        setupFilterChips();
        setupDatePickers();
        setupPlaybackControls();
        setupMapControls();
        setupMapModeToggle();
        observeInitial();

        binding.chip7d.setSelected(true);

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


    private void setupHeader() {
        String title;
        if (deviceName != null && !deviceName.equals(codeNum)) {
            title = "TRACK - " + deviceName + " (" + codeNum + ")";
        } else {
            title = "TRACK - " + (codeNum != null ? codeNum : "Unknown");
        }
        binding.tvTrackTitle.setText(title);

        binding.btnClose.setOnClickListener(v -> dismiss());
    }


    private void setupViewModel() {
        locationViewModel = new ViewModelProvider(requireActivity())
                .get(LocationViewModel.class);
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
        mMapView.getController().setCenter(new GeoPoint(37.5665, 126.9780));

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


    private void setupTrackList() {
        trackAdapter = new TrackPointAdapter((position, item) -> {
            selectPoint(position);
        });

        binding.listTracks.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.listTracks.setAdapter(trackAdapter);
    }


    private void observeInitial() {
        loadTrackDays(7);
    }


    private void loadTrackHours(int hours) {
        Calendar cal = Calendar.getInstance();
        Date end = cal.getTime();
        cal.add(Calendar.HOUR, -hours);
        Date start = cal.getTime();
        loadTrackRange(start, end);
    }

    private void loadTrackDays(int days) {
        Calendar cal = Calendar.getInstance();
        Date end = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        Date start = cal.getTime();
        loadTrackRange(start, end);
    }

    private void loadTrackRange(Date start, Date end) {
        binding.tvStartDate.setText(dateFmt.format(start));
        binding.tvEndDate.setText(dateFmt.format(end));

        if (mCurrentTrackLive != null) {
            mCurrentTrackLive.removeObservers(getViewLifecycleOwner());
        }

        if (codeNum == null) return;

        mCurrentTrackLive = locationViewModel.getRepository() != null
                ? locationViewModel.getTrackByDeviceDirect(codeNum, start, end)
                : null;

        if (mCurrentTrackLive == null) {
            Toast.makeText(getContext(), "Loading tracks...", Toast.LENGTH_SHORT).show();
            return;
        }

        mCurrentTrackLive.observe(getViewLifecycleOwner(), locations -> {
            mTrackPoints.clear();
            if (locations != null) mTrackPoints.addAll(locations);

            updateTrackList();
            drawTrackOnMap();
            updateProgressLabel();
        });
    }


    private void updateTrackList() {
        List<LocationWithAddress> reversed = new ArrayList<>(mTrackPoints);
        java.util.Collections.reverse(reversed);

        trackAdapter.setItems(reversed);

        int count = mTrackPoints.size();
        binding.tvTotalCount.setText(String.valueOf(count));

        if (count == 0) {
            binding.listTracks.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.VISIBLE);
        } else {
            binding.listTracks.setVisibility(View.VISIBLE);
            binding.emptyState.setVisibility(View.GONE);
        }
    }


    private void drawTrackOnMap() {
        if (mMapView == null) return;

        clearMapOverlays();

        if (mTrackPoints.isEmpty()) {
            mMapView.invalidate();
            return;
        }

        int total = mTrackPoints.size();
        List<GeoPoint> polylinePoints = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            LocationWithAddress item = mTrackPoints.get(i);
            LocationEntity loc = item.getLocation();

            if (loc.getLatitude() == null || loc.getLongitude() == null) continue;

            GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
            polylinePoints.add(point);

            Marker marker = new Marker(mMapView);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            float alpha;
            if (total == 1) alpha = 1.0f;
            else alpha = 0.35f + (float) i / (total - 1) * 0.65f;

            Drawable icon = getMarkerIcon(loc.getTrackMode());
            if (icon != null) {
                icon.setAlpha((int) (alpha * 255));
                marker.setIcon(icon);
            }

            marker.setTitle(timeFmt.format(
                    loc.getCreateAt() != null ? loc.getCreateAt() : new Date()
            ));

            mMarkers.add(marker);
            mMapView.getOverlays().add(marker);
        }

        if (polylinePoints.size() > 1) {
            mPolyline = new Polyline();
            mPolyline.setPoints(polylinePoints);
            mPolyline.getOutlinePaint().setColor(Color.parseColor("#00E5D1"));
            mPolyline.getOutlinePaint().setStrokeWidth(6f);
            mPolyline.getOutlinePaint().setAlpha(180);
            mMapView.getOverlays().add(0, mPolyline);
        }

        mMapView.invalidate();

        mMapView.post(this::fitAllMarkers);
    }


    private void clearMapOverlays() {
        if (mMapView == null) return;
        for (Marker m : mMarkers) {
            mMapView.getOverlays().remove(m);
        }
        mMarkers.clear();
        if (mPolyline != null) {
            mMapView.getOverlays().remove(mPolyline);
            mPolyline = null;
        }
    }


    /**
     * ⭐ trackMode 에 따른 마커 아이콘
     *
     * TYTO 프로토콜 ver 코드:
     * - 0x10 : SOS (긴급)    → 🚨 빨간 마커
     * - 0x11 : CAR (Tracking)→ 🚗 Track 마커
     * - 0x12 : UAV (드론)    → ✈️ Track 마커
     * - 0x13 : UAT (차량확장)→ 🚙 Track 마커
     * - 4, 5 : Legacy SOS
     * - 2    : Legacy Track
     */
    private Drawable getMarkerIcon(int trackMode) {
        int iconRes;
        // 🚨 SOS: 0x10 만! (0x11 제외)
        if (trackMode == 0x10 || trackMode == 4 || trackMode == 5) {
            iconRes = R.drawable.ic_marker_sos;
        }
        // 🚗 TRACK: 0x11, 0x12, 0x13, 2
        else if (trackMode == 0x11 || trackMode == 0x12
                || trackMode == 0x13 || trackMode == 2) {
            iconRes = R.drawable.ic_marker_track;
        }
        // 📍 기본
        else {
            iconRes = R.drawable.ic_marker_device;
        }

        Drawable src = ContextCompat.getDrawable(requireContext(), iconRes);
        if (src == null) return null;
        return src.mutate();
    }


    private void fitAllMarkers() {
        if (mMarkers.isEmpty() || mMapView == null) return;

        if (mMarkers.size() == 1) {
            mMapView.getController().animateTo(mMarkers.get(0).getPosition());
            mMapView.getController().setZoom(14.0);
            return;
        }

        double north = -90, south = 90, east = -180, west = 180;
        for (Marker m : mMarkers) {
            GeoPoint p = m.getPosition();
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

        mMapView.zoomToBoundingBox(box, true, 80);
    }


    private void selectPoint(int reversedListPosition) {
        int pointIndex = mTrackPoints.size() - 1 - reversedListPosition;
        if (pointIndex < 0 || pointIndex >= mTrackPoints.size()) return;

        trackAdapter.setSelectedPosition(reversedListPosition);

        LocationWithAddress item = mTrackPoints.get(pointIndex);
        LocationEntity loc = item.getLocation();
        if (loc.getLatitude() == null || loc.getLongitude() == null) return;

        GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        mMapView.getController().animateTo(point);

        updateInfoBox(loc);
    }


    /**
     * ⭐ 정보 박스 업데이트 - TYPE 분류도 같이 수정
     */
    private void updateInfoBox(LocationEntity loc) {
        binding.infoBox.setVisibility(View.VISIBLE);

        binding.tvInfoSpeed.setText(
                loc.getSpeed() != null ? String.valueOf(loc.getSpeed()) : "-"
        );

        binding.tvInfoHeading.setText(
                loc.getDirection() != null ? String.valueOf(loc.getDirection()) : "-"
        );

        // ⭐ TYPE 분류 수정: 0x10 만 SOS, 나머지 Track 는 TRACK
        int trackMode = loc.getTrackMode();
        if (trackMode == 0x10 || trackMode == 4 || trackMode == 5) {
            binding.tvInfoType.setText("SOS");
            binding.tvInfoType.setTextColor(0xFFFF5252);
        } else if (trackMode == 0x11 || trackMode == 0x12
                || trackMode == 0x13 || trackMode == 2) {
            binding.tvInfoType.setText("TRACK");
            binding.tvInfoType.setTextColor(0xFF00E5D1);
        } else {
            binding.tvInfoType.setText("DATA");
            binding.tvInfoType.setTextColor(0xFF95B0D4);
        }

        if (loc.getCreateAt() != null) {
            binding.tvInfoTime.setText(datetimeFmt.format(loc.getCreateAt()));
        } else {
            binding.tvInfoTime.setText("-");
        }
    }


    private void setupFilterChips() {
        binding.chip24h.setOnClickListener(v -> selectQuickChip(v, 24, true));
        binding.chip48h.setOnClickListener(v -> selectQuickChip(v, 48, true));
        binding.chip3d.setOnClickListener(v -> selectQuickChip(v, 3, false));
        binding.chip7d.setOnClickListener(v -> selectQuickChip(v, 7, false));
        binding.chip30d.setOnClickListener(v -> selectQuickChip(v, 30, false));
    }

    private void selectQuickChip(View chip, int value, boolean isHours) {
        binding.chip24h.setSelected(false);
        binding.chip48h.setSelected(false);
        binding.chip3d.setSelected(false);
        binding.chip7d.setSelected(false);
        binding.chip30d.setSelected(false);
        chip.setSelected(true);

        stopPlayback();
        if (isHours) loadTrackHours(value);
        else loadTrackDays(value);
    }


    private void setupDatePickers() {
        binding.btnStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.btnEndDate.setOnClickListener(v -> showDatePicker(false));
        binding.btnApply.setOnClickListener(v -> {
            try {
                Date start = dateFmt.parse(binding.tvStartDate.getText().toString());
                Date end = dateFmt.parse(binding.tvEndDate.getText().toString());
                if (start != null && end != null) {
                    Calendar c = Calendar.getInstance();
                    c.setTime(end);
                    c.set(Calendar.HOUR_OF_DAY, 23);
                    c.set(Calendar.MINUTE, 59);
                    c.set(Calendar.SECOND, 59);
                    stopPlayback();
                    clearQuickChips();
                    loadTrackRange(start, c.getTime());
                }
            } catch (Exception e) {
                Toast.makeText(getContext(), "Invalid date format", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        try {
            Date current = dateFmt.parse(isStart
                    ? binding.tvStartDate.getText().toString()
                    : binding.tvEndDate.getText().toString());
            if (current != null) cal.setTime(current);
        } catch (Exception ignored) {}

        new DatePickerDialog(requireContext(),
                (view, year, month, day) -> {
                    Calendar c = Calendar.getInstance();
                    c.set(year, month, day);
                    if (isStart) {
                        binding.tvStartDate.setText(dateFmt.format(c.getTime()));
                    } else {
                        binding.tvEndDate.setText(dateFmt.format(c.getTime()));
                    }
                    clearQuickChips();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void clearQuickChips() {
        binding.chip24h.setSelected(false);
        binding.chip48h.setSelected(false);
        binding.chip3d.setSelected(false);
        binding.chip7d.setSelected(false);
        binding.chip30d.setSelected(false);
    }


    private void setupPlaybackControls() {
        binding.btnRewind.setOnClickListener(v -> rewindToStart());
        binding.btnPrev.setOnClickListener(v -> stepPrev());
        binding.btnNext.setOnClickListener(v -> stepNext());
        binding.btnPlay.setOnClickListener(v -> togglePlay());
        binding.btnSpeed.setOnClickListener(v -> cycleSpeed());
    }


    private void togglePlay() {
        if (mTrackPoints.isEmpty()) {
            Toast.makeText(getContext(), "No track points", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mIsPlaying) {
            stopPlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (mTrackPoints.isEmpty()) return;
        if (mCurrentPlayIndex >= mTrackPoints.size() - 1) {
            mCurrentPlayIndex = -1;
        }
        mIsPlaying = true;
        updatePlayIcon();
        mPlayHandler.post(mPlayRunnable);
    }

    private void stopPlayback() {
        mIsPlaying = false;
        mPlayHandler.removeCallbacks(mPlayRunnable);
        updatePlayIcon();
    }

    private void advancePlayback() {
        mCurrentPlayIndex++;
        if (mCurrentPlayIndex >= mTrackPoints.size()) {
            mCurrentPlayIndex = mTrackPoints.size() - 1;
            return;
        }
        int reversedPos = mTrackPoints.size() - 1 - mCurrentPlayIndex;
        selectPoint(reversedPos);
        updateProgressLabel();

        binding.listTracks.smoothScrollToPosition(reversedPos);
    }

    private void rewindToStart() {
        stopPlayback();
        mCurrentPlayIndex = -1;
        if (!mTrackPoints.isEmpty()) {
            mCurrentPlayIndex = 0;
            int reversedPos = mTrackPoints.size() - 1;
            selectPoint(reversedPos);
            updateProgressLabel();
            binding.listTracks.smoothScrollToPosition(reversedPos);
        }
    }

    private void stepNext() {
        stopPlayback();
        if (mCurrentPlayIndex < mTrackPoints.size() - 1) {
            mCurrentPlayIndex++;
            int reversedPos = mTrackPoints.size() - 1 - mCurrentPlayIndex;
            selectPoint(reversedPos);
            updateProgressLabel();
            binding.listTracks.smoothScrollToPosition(reversedPos);
        }
    }

    private void stepPrev() {
        stopPlayback();
        if (mCurrentPlayIndex > 0) {
            mCurrentPlayIndex--;
            int reversedPos = mTrackPoints.size() - 1 - mCurrentPlayIndex;
            selectPoint(reversedPos);
            updateProgressLabel();
            binding.listTracks.smoothScrollToPosition(reversedPos);
        }
    }

    private void cycleSpeed() {
        mSpeedIndex = (mSpeedIndex + 1) % SPEED_INTERVALS.length;
        binding.tvSpeedLabel.setText(SPEED_LABELS[mSpeedIndex]);
    }

    private void updatePlayIcon() {
        if (binding == null) return;
        binding.btnPlay.setImageResource(
                mIsPlaying ? R.drawable.ic_pause : R.drawable.ic_play
        );
    }

    private void updateProgressLabel() {
        if (binding == null) return;
        int current = mCurrentPlayIndex + 1;
        int total = mTrackPoints.size();
        if (current < 0) current = 0;
        binding.tvProgress.setText(current + " / " + total);

        if (mCurrentPlayIndex >= 0 && mCurrentPlayIndex < mTrackPoints.size()) {
            LocationEntity loc = mTrackPoints.get(mCurrentPlayIndex).getLocation();
            if (loc.getCreateAt() != null) {
                binding.tvCurrentTime.setText(datetimeFmt.format(loc.getCreateAt()));
            } else {
                binding.tvCurrentTime.setText("-");
            }
        } else {
            binding.tvCurrentTime.setText("-");
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


    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mMapView != null) mMapView.onPause();
        stopPlayback();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPlayback();
        if (mMapView != null) {
            mMapView.onDetach();
            mMapView = null;
        }
        clearMapOverlays();
        mTrackPoints.clear();
        binding = null;
    }
}
