package com.ah.acr.messagebox.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.util.TypedValue;

import org.osmdroid.views.overlay.Marker;

/**
 * ⭐ v4 UI-2026-04-23: 지도용 숫자 마커 공통 유틸
 *
 * 트랙 포인트에 숫자(1, 2, 3...) 와 투명도(alpha fade)를 적용하여
 * 최신 포인트는 뚜렷하게, 오래된 포인트는 희미하게 표시.
 *
 * 사용 예:
 * <pre>
 * int total = points.size();
 * for (int i = 0; i < total; i++) {
 *     int number = total - i;  // 최신=1, 오래됨=N
 *     float alpha = NumberedMarkerUtil.calculateAlpha(i, total);
 *     boolean isLatest = (i == total - 1);
 *
 *     Marker m = new Marker(mapView);
 *     NumberedMarkerUtil.applyToMarker(
 *         m, context, number,
 *         NumberedMarkerUtil.COLOR_TRACK, alpha, isLatest);
 *     m.setPosition(geoPoint);
 *     mapView.getOverlays().add(m);
 * }
 * </pre>
 */
public class NumberedMarkerUtil {

    // ═══════════════════════════════════════════════════════
    // 색상 상수 (RGB, alpha는 별도)
    // ═══════════════════════════════════════════════════════

    /** TRACK 모드 (청록/파랑) */
    public static final int COLOR_TRACK = 0xFF378ADD;

    /** SOS 모드 (빨강) */
    public static final int COLOR_SOS = 0xFFFF5252;

    /** 내 위치 (민트) */
    public static final int COLOR_MY = 0xFF00E5D1;

    /** 상대방 위치 (주황) */
    public static final int COLOR_OTHER = 0xFFFFA726;


    // ═══════════════════════════════════════════════════════
    // 크기 상수 (dp)
    // ═══════════════════════════════════════════════════════

    /** 일반 마커 크기 (dp) */
    private static final int NORMAL_SIZE_DP = 28;

    /** 최신 마커 크기 (dp, 더 큼) */
    private static final int LATEST_SIZE_DP = 36;

    /** 테두리 두께 (dp) */
    private static final int STROKE_WIDTH_DP = 2;


    // ═══════════════════════════════════════════════════════
    // 공개 메서드
    // ═══════════════════════════════════════════════════════

    /**
     * 숫자 마커 Bitmap 생성
     *
     * @param ctx Android Context
     * @param number 표시할 숫자 (최신=1 권장)
     * @param color 배경 색상 (COLOR_TRACK, COLOR_SOS 등)
     * @param alpha 투명도 (0.0 ~ 1.0, 오래될수록 낮게)
     * @param isLatest true면 크고 흰 테두리 강조
     * @return 렌더링된 Bitmap
     */
    public static Bitmap createNumberedMarker(
            Context ctx,
            int number,
            int color,
            float alpha,
            boolean isLatest) {

        // dp → px 변환
        int sizeDp = isLatest ? LATEST_SIZE_DP : NORMAL_SIZE_DP;
        int sizePx = dpToPx(ctx, sizeDp);
        int strokePx = dpToPx(ctx, STROKE_WIDTH_DP);

        // 빈 Bitmap + Canvas
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 중심점 & 반지름
        float cx = sizePx / 2f;
        float cy = sizePx / 2f;
        float radius = (sizePx - strokePx * 2) / 2f;

        // alpha 적용 색상
        int alphaInt = (int) (alpha * 255);
        alphaInt = Math.max(50, Math.min(255, alphaInt));  // 최소 50 (완전 투명 방지)

        int fillColor = applyAlpha(color, alphaInt);
        int strokeColor = isLatest ? Color.WHITE : applyAlpha(color, 255);

        // ━━━━━━━━ 배경 원 (색) ━━━━━━━━
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(fillColor);
        fillPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius, fillPaint);

        // ━━━━━━━━ 테두리 (흰색 또는 같은 색 진하게) ━━━━━━━━
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(strokeColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokePx);
        canvas.drawCircle(cx, cy, radius, strokePaint);

        // ━━━━━━━━ 중앙 숫자 ━━━━━━━━
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        // 숫자 자릿수에 따라 크기 조절
        String text = String.valueOf(number);
        int textSizeSp;
        if (text.length() == 1) textSizeSp = isLatest ? 16 : 13;
        else if (text.length() == 2) textSizeSp = isLatest ? 14 : 11;
        else textSizeSp = isLatest ? 12 : 9;

        textPaint.setTextSize(spToPx(ctx, textSizeSp));

        // 숫자에도 alpha 적용 (가독성 유지하면서)
        int textAlpha = Math.max(200, alphaInt);  // 숫자는 최소 200 (잘 보이게)
        textPaint.setAlpha(textAlpha);

        // 텍스트 Y 위치 보정 (baseline 조정)
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;

        canvas.drawText(text, cx, textY, textPaint);

        return bitmap;
    }

    /**
     * OSMDroid Marker에 숫자 아이콘 적용 (편의 메서드)
     */
    public static void applyToMarker(
            Marker marker,
            Context ctx,
            int number,
            int color,
            float alpha,
            boolean isLatest) {
        Bitmap bitmap = createNumberedMarker(ctx, number, color, alpha, isLatest);
        marker.setIcon(new BitmapDrawable(ctx.getResources(), bitmap));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
    }

    /**
     * 알파 값 자동 계산
     * 인덱스가 낮을수록 오래됨 → 더 희미
     * 인덱스가 높을수록 최신 → 더 뚜렷
     *
     * @param index 포인트 인덱스 (0부터, 시간 순)
     * @param total 전체 포인트 수
     * @return alpha (0.3 ~ 1.0 범위)
     */
    public static float calculateAlpha(int index, int total) {
        if (total <= 1) return 1.0f;

        // 0.3 (가장 오래됨) ~ 1.0 (최신)
        float ratio = (float) index / (total - 1);
        return 0.3f + (ratio * 0.7f);
    }


    // ═══════════════════════════════════════════════════════
    // 내부 헬퍼
    // ═══════════════════════════════════════════════════════

    /**
     * 기존 색상에 alpha 적용
     */
    private static int applyAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * dp → px 변환
     */
    private static int dpToPx(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                ctx.getResources().getDisplayMetrics());
    }

    /**
     * sp → px 변환 (텍스트용)
     */
    private static float spToPx(Context ctx, int sp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                ctx.getResources().getDisplayMetrics());
    }
}
