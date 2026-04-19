package com.ah.acr.messagebox.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;

import java.io.File;

/**
 * AvatarHelper - Generates initial avatars for contacts.
 *
 * Initial rules (priority):
 *   1. If nickname exists → first character of nickname
 *      "박용찬"  → "박"
 *      "John"   → "J"
 *      "Kim"    → "K"
 *
 *   2. Else → last 4 digits of IMEI
 *      "300434061000001" → "0001"
 *      "1111111111111111" → "1111"
 *
 *   3. Else → "?"
 *
 * Color rules:
 *   - Consistent hash based on IMEI (same IMEI → same color always)
 *   - 12 pastel colors
 *
 * Usage:
 *   String initial = AvatarHelper.getInitial(imei, nickname);
 *   int color = AvatarHelper.getColor(imei);
 *   Bitmap bmp = AvatarHelper.createBitmap(context, imei, nickname, 48);
 *   Bitmap bmp = AvatarHelper.loadOrCreate(context, imei, nickname, avatarPath, 48);
 */
public class AvatarHelper {

    // 12 pastel colors
    private static final int[] PALETTE = {
            0xFF378ADD,  // blue (brand)
            0xFF00E5D1,  // cyan (brand accent)
            0xFFFFB300,  // amber
            0xFFFF7043,  // deep orange
            0xFFEC407A,  // pink
            0xFFAB47BC,  // purple
            0xFF7E57C2,  // deep purple
            0xFF5C6BC0,  // indigo
            0xFF26A69A,  // teal
            0xFF66BB6A,  // green
            0xFFFFA726,  // orange
            0xFF8D6E63   // brown
    };


    /**
     * Get the initial character(s) to display.
     *
     * Priority:
     *   1. Nickname first char (if nickname not empty)
     *   2. IMEI last 4 digits
     *   3. "?"
     */
    public static String getInitial(String imei, String nickname) {
        // Priority 1: Nickname first character
        if (!TextUtils.isEmpty(nickname)) {
            String trimmed = nickname.trim();
            if (trimmed.length() > 0) {
                // For Korean: "박용찬" → "박"
                // For English: "John" → "J"
                // For 2+ word names: "홍 길동" → "홍"
                return trimmed.substring(0, 1).toUpperCase();
            }
        }

        // Priority 2: IMEI last 4 digits
        if (!TextUtils.isEmpty(imei)) {
            String trimmed = imei.trim();
            if (trimmed.length() <= 4) return trimmed;
            return trimmed.substring(trimmed.length() - 4);
        }

        // Priority 3: fallback
        return "?";
    }


    /** Overload: initial from IMEI only */
    public static String getInitial(String imei) {
        return getInitial(imei, null);
    }


    /**
     * Get consistent color for this IMEI (same IMEI → same color).
     */
    public static int getColor(String imei) {
        if (TextUtils.isEmpty(imei)) return PALETTE[0];
        int hash = 0;
        for (int i = 0; i < imei.length(); i++) {
            hash = 31 * hash + imei.charAt(i);
        }
        int index = Math.abs(hash) % PALETTE.length;
        return PALETTE[index];
    }


    /**
     * Create a circular bitmap avatar with the initial.
     */
    public static Bitmap createBitmap(Context context, String imei, String nickname, int sizeDp) {
        int sizePx = dpToPx(context, sizeDp);
        String initial = getInitial(imei, nickname);
        int bgColor = getColor(imei);

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Circular background
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(bgColor);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint);

        // Initial text
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Size text to fit
        float textSize;
        int len = initial.length();
        if (len >= 4) {
            textSize = sizePx * 0.32f;
        } else if (len == 3) {
            textSize = sizePx * 0.38f;
        } else if (len == 2) {
            textSize = sizePx * 0.44f;
        } else {
            textSize = sizePx * 0.50f;
        }
        textPaint.setTextSize(textSize);

        // Vertical centering
        Rect bounds = new Rect();
        textPaint.getTextBounds(initial, 0, initial.length(), bounds);
        float textY = sizePx / 2f + bounds.height() / 2f - bounds.bottom;

        canvas.drawText(initial, sizePx / 2f, textY, textPaint);

        return bitmap;
    }


    /** Overload: create from IMEI only */
    public static Bitmap createBitmap(Context context, String imei, int sizeDp) {
        return createBitmap(context, imei, null, sizeDp);
    }


    /**
     * Load custom avatar image if exists, otherwise create initial avatar.
     *
     * @param context context
     * @param imei IMEI (for color + fallback)
     * @param nickname display nickname (null/empty → use IMEI)
     * @param imagePath path to custom image (null → use initial)
     * @param sizeDp size in dp
     */
    public static Bitmap loadOrCreate(
            Context context,
            String imei,
            String nickname,
            String imagePath,
            int sizeDp
    ) {
        if (!TextUtils.isEmpty(imagePath)) {
            File file = new File(imagePath);
            if (file.exists()) {
                try {
                    Bitmap loaded = BitmapFactory.decodeFile(imagePath);
                    if (loaded != null) {
                        return makeCircular(context, loaded, sizeDp);
                    }
                } catch (Exception e) {
                    // Fall through to initial avatar
                }
            }
        }
        return createBitmap(context, imei, nickname, sizeDp);
    }


    /** Overload: load or create with IMEI only */
    public static Bitmap loadOrCreate(Context context, String imei, String imagePath, int sizeDp) {
        return loadOrCreate(context, imei, null, imagePath, sizeDp);
    }


    /**
     * Crop bitmap to circle with target size.
     */
    public static Bitmap makeCircular(Context context, Bitmap source, int sizeDp) {
        int sizePx = dpToPx(context, sizeDp);

        // Square crop from center
        int minDim = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - minDim) / 2;
        int y = (source.getHeight() - minDim) / 2;
        Bitmap square = Bitmap.createBitmap(source, x, y, minDim, minDim);

        // Scale to target size
        Bitmap scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true);

        // Clip to circle
        Bitmap output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(scaled, 0, 0, paint);

        return output;
    }


    private static int dpToPx(Context context, int dp) {
        if (context == null) return dp;
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }
}
