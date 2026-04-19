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
 *   - Filename convention: {IMEI}.jpg
 *   - Storage: /files/avatars/
 *   - Return file path for DB storage
 *
 * Usage:
 *   // After user picks image from gallery
 *   String savedPath = AvatarPickerHelper.saveAvatarFromUri(
 *       context, uri, imei);
 *
 *   // Delete existing avatar
 *   AvatarPickerHelper.deleteAvatar(context, imei);
 *
 *   // Get avatar file path (for display)
 *   String path = AvatarPickerHelper.getAvatarPath(context, imei);
 */
public class AvatarPickerHelper {

    private static final String TAG = "AvatarPickerHelper";
    private static final String AVATAR_DIR = "avatars";
    private static final int AVATAR_SIZE_DP = 200;  // Stored size (larger = better quality)
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
     * Get avatar file for given IMEI (may not exist).
     */
    public static File getAvatarFile(Context context, String imei) {
        if (imei == null || imei.isEmpty()) return null;
        // Sanitize filename - only digits/letters
        String safeName = imei.replaceAll("[^a-zA-Z0-9]", "");
        return new File(getAvatarDir(context), safeName + ".jpg");
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
     * Save image from URI (gallery selection) as circular cropped JPG.
     *
     * Process:
     *   1. Load bitmap from URI
     *   2. Center-crop to square
     *   3. Resize to AVATAR_SIZE_DP
     *   4. Apply circular mask (via AvatarHelper.makeCircular)
     *   5. Save as JPG
     *
     * @param context context
     * @param uri image URI from gallery
     * @param imei identifier for filename
     * @return absolute path of saved file, or null on failure
     */
    public static String saveAvatarFromUri(Context context, Uri uri, String imei) {
        if (context == null || uri == null || imei == null || imei.isEmpty()) {
            Log.e(TAG, "Invalid parameters");
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

            // Apply circular crop (uses AvatarHelper)
            Bitmap circular = AvatarHelper.makeCircular(context, original, AVATAR_SIZE_DP);

            // Save to file
            File outFile = getAvatarFile(context, imei);
            if (outFile == null) {
                Log.e(TAG, "Cannot create output file for IMEI: " + imei);
                return null;
            }

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
     * Delete avatar file for given IMEI.
     * @return true if deleted, false if not found or error
     */
    public static boolean deleteAvatar(Context context, String imei) {
        File file = getAvatarFile(context, imei);
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            Log.v(TAG, "Avatar delete for " + imei + ": " + deleted);
            return deleted;
        }
        return false;
    }


    /**
     * Check if avatar exists for given IMEI.
     */
    public static boolean hasAvatar(Context context, String imei) {
        File file = getAvatarFile(context, imei);
        return file != null && file.exists();
    }
}
