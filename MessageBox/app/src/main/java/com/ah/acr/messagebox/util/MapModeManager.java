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
 * Map Mode (Online/Offline) Manager
 *
 * BUGFIX (2026-04-25):
 *   - Offline -> Online not switching back
 *   - Cause: Old TileProvider not properly detached
 *   - Solution: Explicit detach() before setting new provider
 *
 *   - When MBTiles missing, auto-falls back to ONLINE but UI stays out of sync
 *   - Solution: applyToMapView() returns the actual applied mode
 *               (so caller can sync UI)
 */
public class MapModeManager {

    private static final String TAG = "MapModeManager";
    private static final String PREFS_NAME = "map_prefs";
    private static final String KEY_MODE = "map_mode";
    private static final String MBTILES_SUBDIR = "mbtiles";

    public enum Mode {
        ONLINE,   // 🌐 Online (Mapnik)
        OFFLINE   // 📁 Offline (MBTiles)
    }


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
     * Check if MBTiles files are available.
     */
    public static boolean hasMbtiles(Context ctx) {
        File mbtilesDir = new File(
                ctx.getExternalFilesDir(null),
                MBTILES_SUBDIR
        );
        if (!mbtilesDir.exists()) return false;

        File[] files = mbtilesDir.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".mbtiles")
        );
        return files != null && files.length > 0;
    }


    /**
     * Apply current mode to MapView.
     *
     * @return The mode actually applied. May differ from getMode() if fallback happened.
     */
    public static Mode applyToMapView(Context ctx, MapView mapView) {
        if (mapView == null) return getMode(ctx);

        Mode appliedMode = getMode(ctx);

        try {
            // Detach old provider FIRST
            detachOldProvider(mapView);

            if (appliedMode == Mode.ONLINE) {
                applyOnlineMode(ctx, mapView);
            } else {
                // applyOfflineMode may fallback to ONLINE
                appliedMode = applyOfflineMode(ctx, mapView);
            }

            // Clear stale tile cache
            clearTileCache(mapView);

            mapView.invalidate();
        } catch (Exception e) {
            Log.e(TAG, "Map source apply failed: " + e.getMessage(), e);
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.invalidate();
            appliedMode = Mode.ONLINE;
        }

        return appliedMode;
    }


    /**
     * Detach the current TileProvider properly.
     * Releases MBTiles file handles and network connections.
     */
    private static void detachOldProvider(MapView mapView) {
        try {
            MapTileProviderBase oldProvider = mapView.getTileProvider();
            if (oldProvider != null) {
                Log.v(TAG, "Detaching old provider: " + oldProvider.getClass().getSimpleName());
                oldProvider.detach();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Old provider detach failed (continuing): " + t.getMessage());
        }
    }


    /**
     * Clear MapView's tile cache.
     */
    private static void clearTileCache(MapView mapView) {
        try {
            MapTileProviderBase provider = mapView.getTileProvider();
            if (provider != null) {
                provider.clearTileCache();
            }
        } catch (Throwable t) {
            Log.v(TAG, "Tile cache clear skipped: " + t.getMessage());
        }
    }


    /** 🌐 Apply Online mode */
    private static void applyOnlineMode(Context ctx, MapView mapView) {
        MapTileProviderBase defaultProvider = new MapTileProviderBasic(
                ctx.getApplicationContext(),
                TileSourceFactory.MAPNIK
        );
        mapView.setTileProvider(defaultProvider);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setUseDataConnection(true);
        Log.v(TAG, "🌐 Online mode applied");
    }


    /**
     * 📁 Apply Offline mode.
     * @return Mode actually applied (may be ONLINE if MBTiles not found)
     */
    private static Mode applyOfflineMode(Context ctx, MapView mapView) {
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
            mapView.setUseDataConnection(false);
            Log.v(TAG, "📁 Offline mode applied - MBTiles count: " + mbtilesFiles.length);
            return Mode.OFFLINE;
        } else {
            // MBTiles not found -> auto fallback to ONLINE
            Toast.makeText(ctx,
                    "오프라인 지도 파일이 없습니다.\n온라인 모드로 전환합니다.",
                    Toast.LENGTH_LONG).show();
            setMode(ctx, Mode.ONLINE);  // Save fallback mode
            applyOnlineMode(ctx, mapView);
            Log.w(TAG, "MBTiles not found - fallback to online");
            return Mode.ONLINE;  // ⭐ Tell caller actual applied mode
        }
    }
}
