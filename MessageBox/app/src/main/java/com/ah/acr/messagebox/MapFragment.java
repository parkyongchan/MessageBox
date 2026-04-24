package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MapFragment extends Fragment {
    private static final String TAG = MapFragment.class.getSimpleName();

    private MapView mMapView;

    // 오프라인 지도 파일 경로 (나중에 설정)
    // /sdcard/osmdroid/ 폴더에 .mbtiles 파일 넣으면 자동 인식
    private static final String OFFLINE_MAP_PATH = "/sdcard/osmdroid/";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // OSMDroid 초기화
        Configuration.getInstance().setUserAgentValue(
                requireActivity().getPackageName()
        );

        // 오프라인 지도 캐시 경로 설정
        File osmDir = new File(OFFLINE_MAP_PATH);
        if (!osmDir.exists()) osmDir.mkdirs();
        Configuration.getInstance().setOsmdroidBasePath(osmDir);
        Configuration.getInstance().setOsmdroidTileCache(
                new File(osmDir, "cache")
        );

        // MapView 찾기
        mMapView = view.findViewById(R.id.map);
        mMapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        mMapView.setMultiTouchControls(true);
        mMapView.getController().setZoom(5.0);

        // 오프라인 MBTiles 파일 자동 탐색
        setupOfflineMap();

        // 나침반 오버레이
        CompassOverlay compassOverlay = new CompassOverlay(
                requireContext(), mMapView
        );
        compassOverlay.enableCompass();
        mMapView.getOverlays().add(compassOverlay);

        // 내 위치 오버레이
        MyLocationNewOverlay myLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(requireContext()), mMapView
        );
        myLocationOverlay.enableMyLocation();
        mMapView.getOverlays().add(myLocationOverlay);

        // 전달받은 좌표로 마커 표시
        Bundle args = getArguments();
        if (args != null
                && args.containsKey("title")
                && args.containsKey("lat")
                && args.containsKey("lng")) {

            final String title = args.getString("title");
            final double lat = args.getDouble("lat");
            final double lng = args.getDouble("lng");

            Log.v(TAG, title + " " + lat + ", " + lng);

            GeoPoint point = new GeoPoint(lat, lng);

            // ⭐ UI-2026-04-24: 말풍선 정보 분리 (시간 + 좌표)
            SimpleDateFormat fmt = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.US);
            String currentTime = fmt.format(new Date());

            // 마커 추가
            Marker marker = new Marker(mMapView);
            marker.setPosition(point);
            // 타이틀: IMEI/이름 (간단하게)
            marker.setTitle(title);
            // 스니펫: 좌표 + 시간 (2줄)
            marker.setSnippet(String.format(Locale.US,
                    "📍 %.6f, %.6f\n🕐 %s\n(탭하여 상세보기)",
                    lat, lng, currentTime));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            // ⭐ UI-2026-04-24: 마커 탭 → 상세 다이얼로그
            marker.setOnMarkerClickListener((m, mv) -> {
                showLocationDetailDialog(title, lat, lng);
                return true;
            });

            mMapView.getOverlays().add(marker);

            // 해당 위치로 이동
            mMapView.getController().setZoom(16.0);
            mMapView.getController().setCenter(point);
        }
    }


    /**
     * ⭐ UI-2026-04-24: 위치 상세 다이얼로그
     * 마커 탭하면 나타나는 상세 정보창 (트랙 정보 + 좌표 복사 등)
     */
    private void showLocationDetailDialog(String title, double lat, double lng) {
        SimpleDateFormat fmt = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.US);
        String currentTime = fmt.format(new Date());

        StringBuilder sb = new StringBuilder();
        sb.append("📡 IMEI/이름: ").append(title != null ? title : "-").append("\n\n");
        sb.append("📍 좌표: ").append(String.format(Locale.US,
                "%.6f, %.6f", lat, lng)).append("\n\n");
        sb.append("🕐 조회 시각: ").append(currentTime);

        new AlertDialog.Builder(requireContext())
                .setTitle("📍 위치 상세 정보")
                .setMessage(sb.toString())
                .setPositiveButton("좌표 복사", (d, w) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) requireContext()
                                    .getSystemService(Context.CLIPBOARD_SERVICE);
                    String coords = String.format(Locale.US, "%f,%f", lat, lng);
                    ClipData clip = ClipData.newPlainText("좌표", coords);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(requireContext(),
                            "좌표가 복사되었습니다: " + coords,
                            Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("지도 앱에서 열기", (d, w) -> {
                    try {
                        String uri = String.format(Locale.US,
                                "geo:%f,%f?q=%f,%f(%s)",
                                lat, lng, lat, lng,
                                title != null ? title : "Location");
                        android.content.Intent mapIntent = new android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(uri));
                        startActivity(mapIntent);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(),
                                "지도 앱을 찾을 수 없습니다",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }


    // 오프라인 MBTiles 파일 탐색 및 적용
    private void setupOfflineMap() {
        try {
            File mapDir = new File(OFFLINE_MAP_PATH);
            File[] files = mapDir.listFiles(
                    (dir, name) -> name.endsWith(".mbtiles")
            );

            if (files != null && files.length > 0) {
                // MBTiles 파일 발견 → 오프라인 모드
                Log.v(TAG, "오프라인 지도 발견: " + files[0].getName());
                OfflineTileProvider tileProvider = new OfflineTileProvider(
                        new SimpleRegisterReceiver(requireContext()),
                        files
                );
                mMapView.setTileProvider(tileProvider);
                mMapView.setTileSource(new XYTileSource(
                        "offline", 0, 18, 256, ".png", new String[]{}
                ));
            } else {
                // MBTiles 없음 → 온라인 OSM (인터넷 있을 때만)
                Log.v(TAG, "오프라인 지도 없음 → 온라인 모드");
                mMapView.setTileSource(
                        org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "오프라인 지도 로드 실패: " + e.getMessage());
        }
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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mMapView != null) mMapView.onDetach();
    }
}
