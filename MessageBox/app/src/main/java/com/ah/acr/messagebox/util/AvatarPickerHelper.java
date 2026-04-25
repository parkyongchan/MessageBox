package com.ah.acr.messagebox.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * AvatarPickerHelper - utilities for avatar image handling.
 *
 * Responsibilities:
 *   - Save selected image as circular cropped JPG
 *   - Filename convention: {IMEI}_{timestamp}.jpg
 *     ⭐ BUGFIX (2026-04-25): Added timestamp suffix
 *     - Old: {IMEI}.jpg → DiffUtil sees same path → no UI refresh on 2nd upload
 *     - New: {IMEI}_{timestamp}.jpg → unique path each time → forces UI refresh
 *   - Storage: /files/avatars/
 *   - Return file path for DB storage
 *
 * Usage:
 *   String savedPath = AvatarPickerHelper.saveAvatarFromUri(
 *       context, uri, imei);
 *
 *   AvatarPickerHelper.deleteAvatar(context, imei);
 *
 *   String path = AvatarPickerHelper.getAvatarPath(context, imei);
 */
public class AvatarPickerHelper {

    private static final String TAG = "AvatarPickerHelper";
    private static final String AVATAR_DIR = "avatars";
    private static final int AVATAR_SIZE_DP = 200;
    private static final int JPG_QUALITY = 90;


    /**
     * Get avatars directory (creates if not exists).
     */
    public static File getAvatarDir(Context context) {
        File dir = new File(context.getFilesDir(), AVATAR_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }


    /**
     * Sanitize IMEI for use as filename prefix.
     */
    private static String getSafeName(String imei) {
        if (imei == null || imei.isEmpty()) return null;
        return imei.replaceAll("[^a-zA-Z0-9]", "");
    }


    /**
     * Get avatar file for given IMEI - returns FIRST matching file (any timestamp).
     *
     * ⭐ BUGFIX: Files are now named {IMEI}_{timestamp}.jpg
     * This method finds the most recent one.
     */
    public static File getAvatarFile(Context context, String imei) {
        String safeName = getSafeName(imei);
        if (safeName == null) return null;

        File dir = getAvatarDir(context);

        // Find files matching {IMEI}_*.jpg or {IMEI}.jpg (legacy)
        File[] files = dir.listFiles((d, name) -> {
            if (!name.endsWith(".jpg")) return false;
            // Legacy: exactly "IMEI.jpg"
            if (name.equals(safeName + ".jpg")) return true;
            // New: "IMEI_timestamp.jpg"
            return name.startsWith(safeName + "_");
        });

        if (files == null || files.length == 0) return null;

        // Return the most recent file (largest timestamp)
        File latest = files[0];
        for (int i = 1; i < files.length; i++) {
            if (files[i].lastModified() > latest.lastModified()) {
                latest = files[i];
            }
        }
        return latest;
    }


    /**
     * Get avatar path if file exists.
     * @return absolute path or null if no avatar saved
     */
    public static String getAvatarPath(Context context, String imei) {
        File file = getAvatarFile(context, imei);
        if (file != null && file.exists()) {
            return file.getAbsolutePath();
        }
        return null;
    }


    /**
     * Save image from URI as circular cropped JPG.
     *
     * ⭐ BUGFIX (2026-04-25): Use timestamp in filename to force UI refresh
     *   - Old behavior: same path → DiffUtil thinks unchanged → no re-bind
     *   - New behavior: unique path each save → DiffUtil detects change → re-bind
     *   - Also deletes old file(s) for same IMEI to save disk space
     *
     * @return absolute path of saved file, or null on failure
     */
    public static String saveAvatarFromUri(Context context, Uri uri, String imei) {
        if (context == null || uri == null || imei == null || imei.isEmpty()) {
            Log.e(TAG, "Invalid parameters");
            return null;
        }

        String safeName = getSafeName(imei);
        if (safeName == null) {
            Log.e(TAG, "Cannot create safe name for IMEI: " + imei);
            return null;
        }

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            // Load bitmap from URI
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "Cannot open URI: " + uri);
                return null;
            }

            Bitmap original = BitmapFactory.decodeStream(inputStream);
            if (original == null) {
                Log.e(TAG, "Failed to decode bitmap");
                return null;
            }

            // Apply circular crop
            Bitmap circular = AvatarHelper.makeCircular(context, original, AVATAR_SIZE_DP);

            // ⭐ BUGFIX: Delete old files for this IMEI first (cleanup)
            deleteAllAvatarsFor(context, imei);

            // ⭐ BUGFIX: Save with timestamp suffix
            long timestamp = System.currentTimeMillis();
            String filename = safeName + "_" + timestamp + ".jpg";
            File outFile = new File(getAvatarDir(context), filename);

            outputStream = new FileOutputStream(outFile);
            circular.compress(Bitmap.CompressFormat.PNG, JPG_QUALITY, outputStream);
            outputStream.flush();

            // Clean up bitmaps
            if (original != circular) {
                original.recycle();
            }

            Log.v(TAG, "Avatar saved: " + outFile.getAbsolutePath());
            return outFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Save failed: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error: " + e.getMessage());
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }


    /**
     * ⭐ NEW: Delete ALL avatar files for given IMEI (any timestamp).
     * Used internally to clean up old files before saving new one.
     */
    private static int deleteAllAvatarsFor(Context context, String imei) {
        String safeName = getSafeName(imei);
        if (safeName == null) return 0;

        File dir = getAvatarDir(context);
        File[] files = dir.listFiles((d, name) -> {
            if (!name.endsWith(".jpg")) return false;
            if (name.equals(safeName + ".jpg")) return true;  // legacy
            return name.startsWith(safeName + "_");           // new format
        });

        if (files == null) return 0;

        int deleted = 0;
        for (File f : files) {
            if (f.delete()) deleted++;
        }
        if (deleted > 0) {
            Log.v(TAG, "Cleaned up " + deleted + " old avatar(s) for " + imei);
        }
        return deleted;
    }


    /**
     * Delete avatar file(s) for given IMEI.
     * @return true if any file deleted
     */
    public static boolean deleteAvatar(Context context, String imei) {
        int deleted = deleteAllAvatarsFor(context, imei);
        return deleted > 0;
    }


    /**
     * Check if avatar exists for given IMEI.
     */
    public static boolean hasAvatar(Context context, String imei) {
        File file = getAvatarFile(context, imei);
        return file != null && file.exists();
    }
}
