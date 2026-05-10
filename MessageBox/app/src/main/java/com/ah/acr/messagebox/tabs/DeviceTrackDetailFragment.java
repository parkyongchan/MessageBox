package com.ah.acr.messagebox.tabs;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
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
import androidx.appcompat.app.AlertDialog;
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
import com.ah.acr.messagebox.export.TrackExporter;
import com.ah.acr.messagebox.util.MapModeManager;
import com.ah.acr.messagebox.util.MapModeToggleHelper;
import com.ah.acr.messagebox.util.NumberedMarkerUtil;

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
 * Device-specific full track detail popup (full-screen DialogFragment)
 *
 * UI-2026-04-23 update:
 * - Numbered markers (NumberedMarkerUtil)
 * - SOS/TRACK color distinction
 * - Expanded info box (ALT, SEND_TIME, RECV_TIME)
 * - Export feature (GPX/KML/CSV)
 * - Localized for ko/en/ja
 *
 * ⭐ v6 patch (2026-05-03):
 * - Fix marker tap crash (IllegalFormatConversionException: f != Integer)
 * - Unified UAT spec
 *
 * ⭐ Phase 5-P 후속 패치 (2026-05-04):
 * - 트랙 번호 정순 변경: 1번=가장 오래된 점, N번=최신 점
 *   (이전: 1번=최신, N번=가장 오래된 점)
 * - mTrackPoints는 DB 순서(시간 ASC = 오래된 → 최신) 그대로 유지
 * - 모든 인덱스/번호 계산 로직을 자연스러운 순서로 통일
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

    /**
     * mTrackPoints 순서: DB ASC (시간 오래된 → 최신)
     *   [0] = 가장 오래된 점 (#1로 표시됨)
     *   [N-1] = 최신 점 (#N으로 표시됨)
     */
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

    // Locale-neutral ISO format
    private final SimpleDateFormat richTimeFmt =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());


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
        setupExportButton();
        observeInitial();

        binding.chip7d.setSelected(true);

        return binding.getRoot();
    }


    private void setupMapModeToggle() {
        MapModeToggleHelper.setup(
                binding.getRoot(),
                requireContext(),
                newMode -> {
                    Log.v(TAG, "Map mode changed: " + newMode);
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


    private void setupExportButton() {
        if (binding.btnExport != null) {
            binding.btnExport.setOnClickListener(v -> showExportDialog());
        }
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
        // ⭐ Phase 5-P 후속: position이 곧 mTrackPoints의 인덱스 (정순)
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
            Toast.makeText(getContext(),
                    getString(R.string.track_detail_loading),
                    Toast.LENGTH_SHORT).show();
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


    /**
     * ⭐ Phase 5-P 후속 (2026-05-04):
     * 트랙 리스트 표시 - 정순 (오래된 → 최신)
     *   #1 = 가장 오래된 점 (mTrackPoints[0])
     *   #N = 최신 점 (mTrackPoints[N-1])
     *
     * 이전: reversed (최신이 #1)
     * 변경: mTrackPoints를 DB 순서 그대로 어댑터에 전달
     */
    private void updateTrackList() {
        // ⭐ reverse 제거 - mTrackPoints 그대로 사용
        List<LocationWithAddress> displayList = new ArrayList<>(mTrackPoints);
        java.util.Collections.reverse(displayList);
        trackAdapter.setItems(displayList);
        trackAdapter.setDisplayReversed(true);

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


    /**
     * ⭐ Phase 5-P 후속: 마커 번호 정순 (오래된=#1, 최신=#N)
     * "isLatest" 강조 표시는 인덱스 N-1 (마지막)에 적용
     */
    private void drawTrackOnMap() {
        if (mMapView == null) return;

        clearMapOverlays();

        if (mTrackPoints.isEmpty()) {
            mMapView.invalidate();
            return;
        }

        List<GeoPoint> polylinePoints = new ArrayList<>();

        // Step 1: collect valid points (DB 순서대로 = 오래된 → 최신)
        List<LocationEntity> validLocations = new ArrayList<>();
        for (LocationWithAddress item : mTrackPoints) {
            LocationEntity loc = item.getLocation();
            if (loc.getLatitude() == null || loc.getLongitude() == null) continue;
            validLocations.add(loc);
            polylinePoints.add(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
        }

        int validTotal = validLocations.size();
        if (validTotal == 0) {
            mMapView.invalidate();
            return;
        }

        // Step 2: Polyline (오래된 → 최신 순으로 라인 연결)
        if (polylinePoints.size() > 1) {
            mPolyline = new Polyline();
            mPolyline.setPoints(polylinePoints);
            mPolyline.getOutlinePaint().setColor(Color.parseColor("#378ADD"));
            mPolyline.getOutlinePaint().setStrokeWidth(6f);
            mPolyline.getOutlinePaint().setAlpha(180);
            mMapView.getOverlays().add(0, mPolyline);
        }

        // Step 3: numbered markers (정순: #1=가장 오래됨, #N=최신)
        for (int i = 0; i < validTotal; i++) {
            final LocationEntity loc = validLocations.get(i);
            GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());

            // ⭐ Phase 5-P 후속: 번호는 i+1 (정순)
            //    i=0 → #1 (가장 오래됨)
            //    i=N-1 → #N (최신)
            final int number = i + 1;

            // ⭐ Alpha: 오래된 점은 흐리게, 최신 점은 진하게
            //    i=0 (오래됨) → alpha 낮음
            //    i=N-1 (최신) → alpha 높음
            // 기존 NumberedMarkerUtil.calculateAlpha(i, validTotal)이
            // i 작을수록 alpha 낮은지 높은지 모르므로, 인덱스를 반대로 전달하여
            // "가장 오래된 점이 흐리고, 최신 점이 강조"되도록 처리.
            // → 역순 인덱스 사용: validTotal - 1 - i
            float alpha = NumberedMarkerUtil.calculateAlpha(
                    validTotal - 1 - i, validTotal);

            // ⭐ isLatest는 마지막 점 (최신 = i == validTotal - 1)
            boolean isLatest = (i == validTotal - 1);
            int color = getColorForTrackMode(loc.getTrackMode());

            Marker marker = new Marker(mMapView);
            marker.setPosition(point);
            NumberedMarkerUtil.applyToMarker(
                    marker, requireContext(), number, color, alpha, isLatest);

            // Title + snippet (localized)
            SimpleDateFormat fullFmt = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.US);
            String title = "#" + number + "  " + fullFmt.format(
                    loc.getCreateAt() != null ? loc.getCreateAt() : new Date());
            String snippet = String.format(Locale.US,
                    "%.6f, %.6f  (%s)",
                    loc.getLatitude(), loc.getLongitude(),
                    getString(R.string.point_detail_marker_tap_for_details));
            marker.setTitle(title);
            marker.setSnippet(snippet);

            // Marker tap -> detail dialog
            marker.setOnMarkerClickListener((m, mv) -> {
                showPointDetailDialog(loc, number);
                return true;
            });

            mMarkers.add(marker);
            mMapView.getOverlays().add(marker);
        }

        mMapView.invalidate();
        mMapView.post(this::fitAllMarkers);
    }


    /**
     * Point detail dialog (localized for ko/en/ja)
     *
     * ⭐ v6 patch (2026-05-03):
     * - Fixed crash: Integer altitude was passed to %.1f format
     * - Use String.valueOf for integer fields
     * - Null safety on all nullable Integer fields
     * - UAT-spec: show altitude + speed + direction (only for UAV/UAT modes)
     */
    private void showPointDetailDialog(LocationEntity loc, int number) {
        SimpleDateFormat fullFmt = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.US);
        String time = loc.getCreateAt() != null
                ? fullFmt.format(loc.getCreateAt())
                : "-";

        StringBuilder sb = new StringBuilder();

        // IMEI
        sb.append(getString(R.string.point_detail_label_imei))
                .append(loc.getCodeNum() != null ? loc.getCodeNum() : "-")
                .append("\n\n");

        // Time
        sb.append(getString(R.string.point_detail_label_time))
                .append(time)
                .append("\n\n");

        // Coordinates
        sb.append(getString(R.string.point_detail_label_coordinates))
                .append(String.format(Locale.US, "%.6f, %.6f",
                        loc.getLatitude(), loc.getLongitude()))
                .append("\n\n");

        // ⭐ v6: Altitude / Speed / Direction (UAV/UAT modes only)
        int trackMode = loc.getTrackMode();
        boolean isUavOrUat = (trackMode == 0x02 || trackMode == 0x12
                || trackMode == 0x03 || trackMode == 0x13);

        if (isUavOrUat) {
            Integer altitude = loc.getAltitude();
            if (altitude != null && altitude != 0) {
                sb.append(getString(R.string.point_detail_label_altitude))
                        .append(altitude)
                        .append(" m")
                        .append("\n\n");
            }

            Integer speed = loc.getSpeed();
            if (speed != null && speed != 0) {
                sb.append(getString(R.string.dialog_label_speed))
                        .append(speed)
                        .append(" km/h")
                        .append("\n\n");
            }

            Integer direction = loc.getDirection();
            if (direction != null) {
                sb.append(getString(R.string.dialog_label_direction))
                        .append(direction)
                        .append("°")
                        .append("\n\n");
            }
        }

        // Mode (always last)
        String modeText = getTrackModeText(trackMode);
        sb.append(getString(R.string.point_detail_label_mode)).append(modeText);

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.point_detail_title, number))
                .setMessage(sb.toString())
                .setPositiveButton(getString(R.string.btn_copy_coordinates), (d, w) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) requireContext()
                                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    String coords = String.format(Locale.US, "%f,%f",
                            loc.getLatitude(), loc.getLongitude());
                    android.content.ClipData clip =
                            android.content.ClipData.newPlainText(
                                    getString(R.string.clipboard_label_coordinates), coords);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(requireContext(),
                            getString(R.string.point_detail_toast_copied),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.btn_close), null)
                .show();
    }


    /**
     * Convert trackMode to readable text (localized)
     */
    private String getTrackModeText(int trackMode) {
        switch (trackMode) {
            case 0: return getString(R.string.point_mode_my_sos);
            case 1: return getString(R.string.point_mode_my_car);
            case 2: return getString(R.string.point_mode_my_uav);
            case 3: return getString(R.string.point_mode_my_uat);
            case 4:
            case 5: return getString(R.string.point_mode_legacy_sos, trackMode);
            case 16: return getString(R.string.point_mode_rx_sos);
            case 17: return getString(R.string.point_mode_rx_car);
            case 18: return getString(R.string.point_mode_rx_uav);
            case 19: return getString(R.string.point_mode_rx_uat);
            default: return getString(R.string.point_mode_other,
                    String.format("%02X", trackMode));
        }
    }


    /**
     * Numbered marker color by trackMode
     */
    private int getColorForTrackMode(int trackMode) {
        if (trackMode == 0x10 || trackMode == 4 || trackMode == 5) {
            return NumberedMarkerUtil.COLOR_SOS;
        } else if (trackMode == 0x11 || trackMode == 0x12
                || trackMode == 0x13 || trackMode == 2) {
            return NumberedMarkerUtil.COLOR_TRACK;
        } else {
            return NumberedMarkerUtil.COLOR_OTHER;
        }
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


    /**
     * ⭐ Phase 5-P 후속: position이 곧 mTrackPoints의 인덱스 (정순)
     * 어댑터가 mTrackPoints 그대로 표시하므로, 어댑터의 position이 곧 인덱스.
     *
     * @param position 0=가장 오래된 점, N-1=최신 점
     */
    private void selectPoint(int position) {
        if (position < 0 || position >= mTrackPoints.size()) return;

        trackAdapter.setSelectedPosition(position);

        LocationWithAddress item = mTrackPoints.get(position);
        LocationEntity loc = item.getLocation();
        if (loc.getLatitude() == null || loc.getLongitude() == null) return;

        GeoPoint point = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        mMapView.getController().animateTo(point);

        updateInfoBox(loc);
    }


    private void updateInfoBox(LocationEntity loc) {
        binding.infoBox.setVisibility(View.VISIBLE);

        binding.tvInfoSpeed.setText(
                loc.getSpeed() != null ? String.valueOf(loc.getSpeed()) : "-"
        );

        binding.tvInfoHeading.setText(
                loc.getDirection() != null ? String.valueOf(loc.getDirection()) : "-"
        );

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

        if (binding.tvInfoAlt != null) {
            if (loc.getAltitude() != null) {
                binding.tvInfoAlt.setText(String.valueOf(loc.getAltitude()));
            } else {
                binding.tvInfoAlt.setText("-");
            }
        }

        if (binding.tvInfoSendTime != null) {
            if (loc.getGpsDate() != null) {
                binding.tvInfoSendTime.setText(richTimeFmt.format(loc.getGpsDate()));
            } else {
                binding.tvInfoSendTime.setText("-");
            }
        }

        if (binding.tvInfoRecvTime != null) {
            if (loc.getCreateAt() != null) {
                binding.tvInfoRecvTime.setText(richTimeFmt.format(loc.getCreateAt()));
            } else {
                binding.tvInfoRecvTime.setText("-");
            }
        }

        if (loc.getCreateAt() != null) {
            binding.tvInfoTime.setText(datetimeFmt.format(loc.getCreateAt()));
        } else {
            binding.tvInfoTime.setText("-");
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //   Export feature - localized
    // ═══════════════════════════════════════════════════════════════

    private void showExportDialog() {
        if (mTrackPoints.isEmpty()) {
            Toast.makeText(getContext(),
                    getString(R.string.export_toast_no_data),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String[] formats = {
                getString(R.string.export_format_gpx),
                getString(R.string.export_format_kml),
                getString(R.string.export_format_csv)
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.export_dialog_title))
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
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    private void performExport(TrackExporter.Format format) {
        Date start = mTrackPoints.get(0).getLocation().getCreateAt();
        Date end = mTrackPoints.get(mTrackPoints.size() - 1).getLocation().getCreateAt();
        if (start == null) start = new Date();
        if (end == null) end = new Date();

        String trackName = (deviceName != null && !deviceName.isEmpty())
                ? deviceName : (codeNum != null ? codeNum : "Device");
        trackName += " Track " + dateFmt.format(start);

        com.ah.acr.messagebox.database.MyTrackEntity adapter =
                new com.ah.acr.messagebox.database.MyTrackEntity(
                        0, trackName, start, end,
                        0.0,
                        mTrackPoints.size(),
                        0.0, 0.0, 0.0, 0.0,
                        "COMPLETED",
                        0, 0,
                        new Date()
                );

        List<com.ah.acr.messagebox.database.MyTrackPointEntity> pts = new ArrayList<>();
        for (LocationWithAddress item : mTrackPoints) {
            LocationEntity loc = item.getLocation();
            if (loc.getLatitude() == null || loc.getLongitude() == null) continue;

            pts.add(new com.ah.acr.messagebox.database.MyTrackPointEntity(
                    0,
                    0,
                    loc.getLatitude(),
                    loc.getLongitude(),
                    loc.getAltitude() != null ? loc.getAltitude().doubleValue() : 0.0,
                    loc.getSpeed() != null ? loc.getSpeed().doubleValue() : 0.0,
                    loc.getDirection() != null ? loc.getDirection().floatValue() : 0f,
                    0,
                    loc.getCreateAt() != null ? loc.getCreateAt() : new Date()
            ));
        }

        TrackExporter.ExportResult result = TrackExporter.exportTrack(
                requireContext(),
                adapter,
                pts,
                format
        );

        if (result.success) {
            showExportSuccessDialog(result.file, format);
        } else {
            Toast.makeText(getContext(),
                    getString(R.string.export_failed, result.errorMessage),
                    Toast.LENGTH_LONG).show();
        }
    }


    private void showExportSuccessDialog(File file, TrackExporter.Format format) {
        String displayPath = TrackExporter.getDisplayPath(file);

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.export_success_title))
                .setMessage(getString(R.string.export_success_msg, displayPath))
                .setPositiveButton(getString(R.string.export_btn_share), (d, w) -> {
                    try {
                        Intent intent = TrackExporter.buildShareIntent(
                                requireContext(), file, format);
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Share failed: " + e.getMessage());
                        Toast.makeText(getContext(),
                                getString(R.string.export_share_failed, e.getMessage()),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(getString(R.string.btn_ok), null)
                .show();
    }


    // ═══════════════════════════════════════════════════════════════

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
                Toast.makeText(getContext(),
                        getString(R.string.track_detail_invalid_date),
                        Toast.LENGTH_SHORT).show();
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


    // ═══════════════════════════════════════════════════════════════
    //   ⭐ Phase 5-P 후속 (2026-05-04): 재생 컨트롤 — 정순으로 통일
    //
    //   재생 순서: mTrackPoints[0] (#1, 가장 오래됨) → [N-1] (#N, 최신)
    //   - mCurrentPlayIndex는 mTrackPoints의 인덱스 (정순)
    //   - 어댑터의 position도 mTrackPoints의 인덱스 (정순)
    //   - reverse 변환 불필요
    // ═══════════════════════════════════════════════════════════════

    private void setupPlaybackControls() {
        binding.btnRewind.setOnClickListener(v -> rewindToStart());
        binding.btnPrev.setOnClickListener(v -> stepPrev());
        binding.btnNext.setOnClickListener(v -> stepNext());
        binding.btnPlay.setOnClickListener(v -> togglePlay());
        binding.btnSpeed.setOnClickListener(v -> cycleSpeed());
    }


    private void togglePlay() {
        if (mTrackPoints.isEmpty()) {
            Toast.makeText(getContext(),
                    getString(R.string.track_detail_no_points),
                    Toast.LENGTH_SHORT).show();
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

    /**
     * ⭐ Phase 5-P 후속: 재생 진행 (정순)
     * mCurrentPlayIndex 0 → N-1 순서로 진행 (오래된 → 최신)
     */
    private void advancePlayback() {
        mCurrentPlayIndex++;
        if (mCurrentPlayIndex >= mTrackPoints.size()) {
            mCurrentPlayIndex = mTrackPoints.size() - 1;
            return;
        }
        // ⭐ position이 곧 mCurrentPlayIndex (정순)
        selectPoint(mCurrentPlayIndex);
        updateProgressLabel();

        binding.listTracks.smoothScrollToPosition(mCurrentPlayIndex);
    }

    /**
     * ⭐ Phase 5-P 후속: 처음으로 (가장 오래된 점)
     */
    private void rewindToStart() {
        stopPlayback();
        mCurrentPlayIndex = -1;
        if (!mTrackPoints.isEmpty()) {
            mCurrentPlayIndex = 0;
            // ⭐ 정순: index 0 = 가장 오래된 점 = #1
            selectPoint(0);
            updateProgressLabel();
            binding.listTracks.smoothScrollToPosition(0);
        }
    }

    /**
     * ⭐ Phase 5-P 후속: 다음 점 (시간 순서로 다음 = 더 최신)
     */
    private void stepNext() {
        stopPlayback();
        if (mCurrentPlayIndex < mTrackPoints.size() - 1) {
            mCurrentPlayIndex++;
            selectPoint(mCurrentPlayIndex);
            updateProgressLabel();
            binding.listTracks.smoothScrollToPosition(mCurrentPlayIndex);
        }
    }

    /**
     * ⭐ Phase 5-P 후속: 이전 점 (시간 순서로 이전 = 더 오래됨)
     */
    private void stepPrev() {
        stopPlayback();
        if (mCurrentPlayIndex > 0) {
            mCurrentPlayIndex--;
            selectPoint(mCurrentPlayIndex);
            updateProgressLabel();
            binding.listTracks.smoothScrollToPosition(mCurrentPlayIndex);
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

    /**
     * ⭐ Phase 5-P 후속: 진행 상태 표시 (정순)
     * mCurrentPlayIndex가 곧 mTrackPoints의 인덱스이므로 그대로 사용.
     */
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
