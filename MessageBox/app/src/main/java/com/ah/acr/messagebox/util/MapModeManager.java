package com.ah.acr.messagebox.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.views.MapView;

import java.io.File;

/**
 * 지도 모드(온라인/오프라인) 관리 유틸리티
 * SharedPreferences 에 사용자 선택을 저장하여 앱 재실행 후에도 유지
 */
public class MapModeManager {

    private static final String TAG = "MapModeManager";
    private static final String PREFS_NAME = "map_prefs";
    private static final String KEY_MODE = "map_mode";
    private static final String MBTILES_SUBDIR = "mbtiles";

    public enum Mode {
        ONLINE,   // 🌐 온라인 지도 (Mapnik)
        OFFLINE   // 📁 오프라인 지도 (MBTiles)
    }

    /** 현재 저장된 지도 모드 가져오기 (기본: ONLINE) */
    public static Mode getMode(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_MODE, Mode.ONLINE.name());
        try {
            return Mode.valueOf(saved);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Invalid saved mode: " + saved + " - defaulting to ONLINE");
            return Mode.ONLINE;
        }
    }

    /** 지도 모드 저장 */
    public static void setMode(Context ctx, Mode mode) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, mode.name())
                .apply();
        Log.v(TAG, "Map mode saved: " + mode);
    }

    public static boolean isOnline(Context ctx) {
        return getMode(ctx) == Mode.ONLINE;
    }

    public static boolean isOffline(Context ctx) {
        return getMode(ctx) == Mode.OFFLINE;
    }

    /**
     * ⭐ 현재 모드에 맞는 지도 소스를 MapView 에 적용
     * 
     * 중요: 오프라인 → 온라인 전환 시 TileProvider 자체를 교체해야 함
     * (setTileSource 만으로는 OfflineTileProvider 가 유지되어 타일이 안 보임)
     * 
     * @param ctx Context
     * @param mapView 대상 MapView
     */
    public static void applyToMapView(Context ctx, MapView mapView) {
        if (mapView == null) return;

        try {
            Mode mode = getMode(ctx);

            if (mode == Mode.ONLINE) {
                // 🌐 온라인 모드 - 기본 TileProvider 로 리셋
                applyOnlineMode(ctx, mapView);
            } else {
                // 📁 오프라인 모드 - MBTiles 로드 시도
                applyOfflineMode(ctx, mapView);
            }

            mapView.invalidate();
        } catch (Exception e) {
            Log.e(TAG, "지도 소스 적용 실패: " + e.getMessage(), e);
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.invalidate();
        }
    }


    /** 🌐 온라인 모드 적용 */
    private static void applyOnlineMode(Context ctx, MapView mapView) {
        // ⭐ 핵심: 기본 TileProvider 를 새로 만들어서 설정
        // (기존 OfflineTileProvider 덮어쓰기)
        MapTileProviderBase defaultProvider = new MapTileProviderBasic(
                ctx.getApplicationContext(),
                TileSourceFactory.MAPNIK
        );
        mapView.setTileProvider(defaultProvider);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        Log.v(TAG, "🌐 온라인 모드 적용");
    }


    /** 📁 오프라인 모드 적용 */
    private static void applyOfflineMode(Context ctx, MapView mapView) {
        File mbtilesDir = new File(
                ctx.getExternalFilesDir(null),
                MBTILES_SUBDIR
        );
        if (!mbtilesDir.exists()) mbtilesDir.mkdirs();

        File[] mbtilesFiles = mbtilesDir.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".mbtiles")
        );

        if (mbtilesFiles != null && mbtilesFiles.length > 0) {
            OfflineTileProvider tileProvider = new OfflineTileProvider(
                    new SimpleRegisterReceiver(ctx),
                    mbtilesFiles
            );
            mapView.setTileProvider(tileProvider);
            mapView.setTileSource(new XYTileSource(
                    "offline", 0, 18, 256, ".png", new String[]{}
            ));
            Log.v(TAG, "📁 오프라인 모드 적용 - MBTiles " + mbtilesFiles.length + "개");
        } else {
            // MBTiles 파일 없음 → 온라인으로 자동 폴백
            Toast.makeText(ctx,
                    "오프라인 지도 파일이 없습니다.\n온라인 모드로 전환합니다.",
                    Toast.LENGTH_LONG).show();
            setMode(ctx, Mode.ONLINE);
            applyOnlineMode(ctx, mapView);
            Log.w(TAG, "MBTiles 없음 - 온라인으로 폴백");
        }
    }
}
