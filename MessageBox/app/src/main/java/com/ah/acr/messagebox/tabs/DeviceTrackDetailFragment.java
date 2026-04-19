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
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.R;
import com.ah.acr.messagebox.adapter.TrackPointAdapter;
import com.ah.acr.messagebox.database.LocationEntity;
import com.ah.acr.messagebox.database.LocationViewModel;
import com.ah.acr.messagebox.database.LocationWithAddress;
import com.ah.acr.messagebox.databinding.FragmentDeviceTrackDetailBinding;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * 장비별 전체 트랙 상세 팝업 (전체화면 DialogFragment)
 * - 해당 장비의 모든 위치 기록
 * - 폴리라인으로 경로 표시
 * - 시간순 하이라이트 자동재생
 */
public class DeviceTrackDetailFragment extends DialogFragment {
    private static final String TAG = DeviceTrackDetailFragment.class.getSimpleName();

    private static final String ARG_CODE_NUM = "codeNum";
    private static final String ARG_NAME = "name";

    private static final String MBTILES_SUBDIR = "mbtiles";
    private static final double DEFAULT_ZOOM = 10.0;

    // 속도 옵션 (포인트당 밀리초)
    private static final long[] SPEED_INTERVALS = {1000, 500, 250, 100};
    private static final String[] SPEED_LABELS = {"1x", "2x", "4x", "10x"};

    private FragmentDeviceTrackDetailBinding binding;
    private LocationViewModel locationViewModel;
    private TrackPointAdapter trackAdapter;
    private MapView mMapView;

    private String codeNum;
    private String deviceName;

    // 트랙 데이터
    private List<LocationWithAddress> mTrackPoints = new ArrayList<>();
    private final List<Marker> mMarkers = new ArrayList<>();
    private Polyline mPolyline;
    private LiveData<List<LocationWithAddress>> mCurrentTrackLive;

    // 재생 상태
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
                // 재생 완료
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


    /** 팩토리 메서드 */
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
        // 전체화면 테마
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
        observeInitial();

        // 기본 7일
        binding.chip7d.setSelected(true);

        return binding.getRoot();
    }


    private void setupHeader() {
        // TRACK - 이름 (IMEI)
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


    // ═══════════════════════════════════════════════════════
    // 지도 초기화
    // ═══════════════════════════════════════════════════════

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

        loadMapSource();

        mMapView.getController().setZoom(DEFAULT_ZOOM);
        mMapView.getController().setCenter(new GeoPoint(37.5665, 126.9780));

        mMapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                updateZoomLabel();
                return false;
            }
        });

        updateZoomLabel();
    }

    private void loadMapSource() {
        try {
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
                mMapView.setTileProvider(tileProvider);
                mMapView.setTileSource(new XYTileSource(
                        "offline", 0, 18, 256, ".png", new String[]{}
                ));
                binding.tvMapMode.setText("OFFLINE");
                binding.tvMapMode.setTextColor(0xFF00E5D1);
            } else {
                mMapView.setTileSource(TileSourceFactory.MAPNIK);
                binding.tvMapMode.setText("ONLINE");
                binding.tvMapMode.setTextColor(0xFFFFB300);
            }
        } catch (Exception e) {
            Log.e(TAG, "지도 소스 로드 실패: " + e.getMessage());
            mMapView.setTileSource(TileSourceFactory.MAPNIK);
        }
    }

    private void updateZoomLabel() {
        if (mMapView != null && binding != null) {
            int zoom = (int) mMapView.getZoomLevelDouble();
            binding.tvMapZoom.setText("ZOOM " + zoom);
        }
    }


    // ═══════════════════════════════════════════════════════
    // 트랙 목록
    // ═══════════════════════════════════════════════════════

    private void setupTrackList() {
        trackAdapter = new TrackPointAdapter((position, item) -> {
            // 목록 클릭 → 해당 포인트 하이라이트 + 지도 이동
            selectPoint(position);
        });

        binding.listTracks.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.listTracks.setAdapter(trackAdapter);
    }


    // ═══════════════════════════════════════════════════════
    // 초기 데이터 로드
    // ═══════════════════════════════════════════════════════

    /** 초기: 7일 범위로 조회 */
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
        // UI 업데이트
        binding.tvStartDate.setText(dateFmt.format(start));
        binding.tvEndDate.setText(dateFmt.format(end));

        // 이전 observer 제거
        if (mCurrentTrackLive != null) {
            mCurrentTrackLive.removeObservers(getViewLifecycleOwner());
        }

        // 특정 장비의 해당 범위 트랙 조회
        if (codeNum == null) return;

        mCurrentTrackLive = locationViewModel.getRepository() != null
                ? locationViewModel.getTrackByDeviceDirect(codeNum, start, end)
                : null;

        if (mCurrentTrackLive == null) {
            // fallback: 뷰모델의 getTrackByDevice 사용 (start/end 내부 필드 사용)
            // 우리의 setStartDate/setEndDate가 전체 쿼리에 영향 주지 않도록 직접 조회
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
        // 시간 DESC로 뒤집기 (최신이 위로)
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


    // ═══════════════════════════════════════════════════════
    // 지도에 트랙 그리기
    // ═══════════════════════════════════════════════════════

    private void drawTrackOnMap() {
        if (mMapView == null) return;

        // 기존 오버레이 제거
        clearMapOverlays();

        if (mTrackPoints.isEmpty()) {
            mMapView.invalidate();
            return;
        }

        int total = mTrackPoints.size();
        List<GeoPoint> polylinePoints = new ArrayList<>();

        // 마커 추가 (오래된 → 최신 순)
        for (int i = 0; i < total; i++) {
            LocationWithAddress item = mTrackPoints.get(i);
            LocationEntity loc = item.getLocation();

            if (loc.getLatitude() == null || loc.getLongitude() == null) continue;

            GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
            polylinePoints.add(point);

            Marker marker = new Marker(mMapView);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            // 최신일수록 뚜렷, 오래될수록 희미
            // alpha 범위: 0.35 ~ 1.0
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

        // 폴리라인 (경로)
        if (polylinePoints.size() > 1) {
            mPolyline = new Polyline();
            mPolyline.setPoints(polylinePoints);
            mPolyline.getOutlinePaint().setColor(Color.parseColor("#00E5D1"));
            mPolyline.getOutlinePaint().setStrokeWidth(6f);
            mPolyline.getOutlinePaint().setAlpha(180);
            mMapView.getOverlays().add(0, mPolyline);  // 마커 아래
        }

        mMapView.invalidate();

        // 자동 영역 조정
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


    private Drawable getMarkerIcon(int trackMode) {
        int iconRes;
        if (trackMode == 0x10 || trackMode == 0x11 || trackMode == 4 || trackMode == 5) {
            iconRes = R.drawable.ic_marker_sos;
        } else if (trackMode == 2) {
            iconRes = R.drawable.ic_marker_track;
        } else {
            iconRes = R.drawable.ic_marker_device;
        }

        // 매번 새로운 drawable 인스턴스 (alpha 설정 별도로)
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


    // ═══════════════════════════════════════════════════════
    // 포인트 선택 / 정보 박스
    // ═══════════════════════════════════════════════════════

    /** position 은 시간 오름차순(mTrackPoints) 기준 */
    private void selectPoint(int reversedListPosition) {
        // reversedListPosition → mTrackPoints 인덱스 변환 (최신이 0번 → 끝)
        int pointIndex = mTrackPoints.size() - 1 - reversedListPosition;
        if (pointIndex < 0 || pointIndex >= mTrackPoints.size()) return;

        // 어댑터 선택 업데이트
        trackAdapter.setSelectedPosition(reversedListPosition);

        LocationWithAddress item = mTrackPoints.get(pointIndex);
        LocationEntity loc = item.getLocation();
        if (loc.getLatitude() == null || loc.getLongitude() == null) return;

        // 지도 이동
        GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        mMapView.getController().animateTo(point);

        // 정보 박스 업데이트
        updateInfoBox(loc);
    }


    private void updateInfoBox(LocationEntity loc) {
        binding.infoBox.setVisibility(View.VISIBLE);

        // SPEED
        binding.tvInfoSpeed.setText(
                loc.getSpeed() != null ? String.valueOf(loc.getSpeed()) : "-"
        );

        // HEADING
        binding.tvInfoHeading.setText(
                loc.getDirection() != null ? String.valueOf(loc.getDirection()) : "-"
        );

        // TYPE
        int trackMode = loc.getTrackMode();
        if (trackMode == 0x10 || trackMode == 0x11 || trackMode == 4 || trackMode == 5) {
            binding.tvInfoType.setText("SOS");
            binding.tvInfoType.setTextColor(0xFFFF5252);
        } else if (trackMode == 2) {
            binding.tvInfoType.setText("TRACK");
            binding.tvInfoType.setTextColor(0xFF00E5D1);
        } else {
            binding.tvInfoType.setText("DATA");
            binding.tvInfoType.setTextColor(0xFF95B0D4);
        }

        // TIME
        if (loc.getCreateAt() != null) {
            binding.tvInfoTime.setText(datetimeFmt.format(loc.getCreateAt()));
        } else {
            binding.tvInfoTime.setText("-");
        }
    }


    // ═══════════════════════════════════════════════════════
    // 필터 칩 / 날짜
    // ═══════════════════════════════════════════════════════

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
            // Apply 버튼: 현재 날짜 텍스트로 재조회
            try {
                Date start = dateFmt.parse(binding.tvStartDate.getText().toString());
                Date end = dateFmt.parse(binding.tvEndDate.getText().toString());
                if (start != null && end != null) {
                    // 종료일은 23:59:59로
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


    // ═══════════════════════════════════════════════════════
    // 재생 컨트롤
    // ═══════════════════════════════════════════════════════

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
            mCurrentPlayIndex = -1;  // 처음부터
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
        // point index → reversed list position
        int reversedPos = mTrackPoints.size() - 1 - mCurrentPlayIndex;
        selectPoint(reversedPos);
        updateProgressLabel();

        // 리스트 스크롤
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


    // ═══════════════════════════════════════════════════════
    // 지도 컨트롤
    // ═══════════════════════════════════════════════════════

    private void setupMapControls() {
        binding.btnZoomIn.setOnClickListener(v -> {
            if (mMapView != null) mMapView.getController().zoomIn();
        });
        binding.btnZoomOut.setOnClickListener(v -> {
            if (mMapView != null) mMapView.getController().zoomOut();
        });
        binding.btnFitAll.setOnClickListener(v -> fitAllMarkers());
    }


    // ═══════════════════════════════════════════════════════
    // 생명주기
    // ═══════════════════════════════════════════════════════

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
