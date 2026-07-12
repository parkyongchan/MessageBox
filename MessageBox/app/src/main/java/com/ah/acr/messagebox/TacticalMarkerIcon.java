package com.ah.acr.messagebox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

/**
 * 전술 마커 아이콘 생성 (소속 색 + 병종/지점 도형 + 번호 배지).
 * 작도 화면(TacticalMapActivity)과 메인/상세 지도가 공통으로 사용.
 * 코드표는 프로토콜 v1.4 기준 (소속 7, 병종 8, 지점 4).
 */
public class TacticalMarkerIcon {

    private static final int[] MK_ICONS = {
            R.drawable.ic_tac_hostile, R.drawable.ic_tac_friendly,
            R.drawable.ic_tac_unknown, R.drawable.ic_tac_neutral,
            R.drawable.ic_tac_poi, R.drawable.ic_tac_engaged,
            R.drawable.ic_tac_threat
    };

    public static final int[] MK_COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFFFDD835,
            0xFF43A047, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00
    };

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    /**
     * 마커 아이콘 생성.
     * @param type 소속(0~6), number 번호(식별자 숫자), unitType 병종(-1~7), placeType 지점(-1~3)
     */
    public static Drawable make(Context ctx, int type, int number, int unitType, int placeType) {
        if (type < 0 || type >= MK_ICONS.length) return null;
        Drawable base = ContextCompat.getDrawable(ctx, MK_ICONS[type]);
        if (base == null) return null;
        int iconSize = dp(ctx, 22);
        int badge = dp(ctx, 12);
        int totalW = iconSize;
        int totalH = iconSize + badge;
        Bitmap bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        base.setBounds(0, badge, iconSize, badge + iconSize);
        base.draw(c);
        if (type == 4 && placeType >= 0) {
            drawPlaceSymbol(ctx, c, placeType, iconSize, badge);
        } else if (unitType >= 0) {
            drawUnitSymbol(ctx, c, unitType, iconSize, badge);
        }
        String num = String.valueOf(number);
        Paint numBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        numBg.setColor(0xFF0A1628);
        Paint numBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        numBorder.setColor(0xFFFFEB3B);
        numBorder.setStyle(Paint.Style.STROKE);
        numBorder.setStrokeWidth(dp(ctx, 1.5f));
        float cx = totalW / 2f;
        float cy = badge / 2f + dp(ctx, 1);
        float r = badge / 2f;
        c.drawCircle(cx, cy, r, numBg);
        c.drawCircle(cx, cy, r, numBorder);
        Paint numText = new Paint(Paint.ANTI_ALIAS_FLAG);
        numText.setColor(Color.WHITE);
        numText.setTextSize(dp(ctx, 8));
        numText.setFakeBoldText(true);
        numText.setTextAlign(Paint.Align.CENTER);
        float ty = cy - (numText.descent() + numText.ascent()) / 2f;
        c.drawText(num, cx, ty, numText);
        return new BitmapDrawable(ctx.getResources(), bmp);
    }

    private static void drawPlaceSymbol(Context ctx, Canvas c, int placeType, int iconSize, int badge) {
        Paint sym = new Paint(Paint.ANTI_ALIAS_FLAG);
        sym.setColor(0xFF0A1628);
        sym.setStyle(Paint.Style.STROKE);
        sym.setStrokeWidth(dp(ctx, 2));
        float left = iconSize * 0.30f;
        float right = iconSize * 0.70f;
        float top = badge + iconSize * 0.32f;
        float bot = badge + iconSize * 0.60f;
        float cx = iconSize / 2f;
        float cy = badge + iconSize * 0.46f;
        switch (placeType) {
            case 0:
                c.drawRect(left, top, right, bot, sym);
                break;
            case 1:
                c.drawLine(left, top + (bot-top)*0.3f, right, top + (bot-top)*0.3f, sym);
                c.drawLine(left, top + (bot-top)*0.7f, right, top + (bot-top)*0.7f, sym);
                break;
            case 2: {
                Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
                tp.setColor(0xFF0A1628);
                tp.setTextSize(iconSize * 0.34f);
                tp.setFakeBoldText(true);
                tp.setTextAlign(Paint.Align.CENTER);
                float ty = cy - (tp.descent() + tp.ascent()) / 2f;
                c.drawText("H", cx, ty, tp);
                break;
            }
            case 3:
                c.drawLine(left, top, right, top, sym);
                c.drawLine(left, top, cx, bot, sym);
                c.drawLine(right, top, cx, bot, sym);
                break;
        }
    }

    private static void drawUnitSymbol(Context ctx, Canvas c, int unitType, int iconSize, int badge) {
        Paint sym = new Paint(Paint.ANTI_ALIAS_FLAG);
        sym.setColor(Color.WHITE);
        sym.setStyle(Paint.Style.STROKE);
        sym.setStrokeWidth(dp(ctx, 2));
        float left = iconSize * 0.28f;
        float right = iconSize * 0.72f;
        float top = badge + iconSize * 0.30f;
        float bot = badge + iconSize * 0.62f;
        float cx = iconSize / 2f;
        float cy = badge + iconSize * 0.46f;
        switch (unitType) {
            case 0:
                c.drawLine(left, top, right, bot, sym);
                c.drawLine(right, top, left, bot, sym);
                break;
            case 1:
                c.drawOval(left, top, right, bot, sym);
                break;
            case 2: {
                Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setColor(Color.WHITE);
                c.drawCircle(cx, cy, iconSize * 0.13f, fill);
                break;
            }
            case 3: {
                c.drawLine(left, cy, right, cy, sym);
                Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
                wp.setColor(Color.WHITE);
                c.drawCircle(left, cy, dp(ctx, 2), wp);
                c.drawCircle(right, cy, dp(ctx, 2), wp);
                break;
            }
            case 4:
                c.drawLine(left, bot, right, top, sym);
                break;
            case 5:
                c.drawLine(left, bot, cx, top, sym);
                c.drawLine(cx, top, right, bot, sym);
                break;
            case 6: {
                float poleX = left + dp(ctx, 1);
                c.drawLine(poleX, top, poleX, bot, sym);
                Paint flag = new Paint(Paint.ANTI_ALIAS_FLAG);
                flag.setColor(Color.WHITE);
                c.drawRect(poleX, top, poleX + (right - left) * 0.5f, top + (bot - top) * 0.4f, flag);
                break;
            }
            case 7:
                c.drawLine(cx, top, cx, bot, sym);
                c.drawLine(left, cy, right, cy, sym);
                break;
        }
    }
    // ─── 생존/재난 마커 (앱→웹 규격 통일) ───────────────────────
    public static final int[] SURV_BASE_GLYPH     = { 0, 1, 2, 3, 4, 5 };   // R/W/D/H/X/L 대응
    public static final String[] SURV_BASE_CHARS    = { "R","W","D","H","X","L" };
    public static final String[] SURV_DISASTER_CHARS = { "F","T","O","E","R","L" };

    /** 생존 식별자: 재난이면 D+글자+id, 아니면 S+글자+id. */
    public static String survivalIdentifier(int survType, int survDisaster, int id) {
        if (survDisaster >= 0) {
            String g = (survDisaster < SURV_DISASTER_CHARS.length) ? SURV_DISASTER_CHARS[survDisaster] : "?";
            return "D" + g + id;
        }
        String g = (survType >= 0 && survType < SURV_BASE_CHARS.length) ? SURV_BASE_CHARS[survType] : "?";
        return "S" + g + id;
    }

    /**
     * 생존/재난 마커 아이콘. survDisaster>=0 이면 재난(삼각/주황), 아니면 생존(원/청록).
     */
    public static Drawable makeSurvival(Context ctx, int survType, int survDisaster, int id) {
        boolean disaster = survDisaster >= 0;
        int fillColor = disaster ? 0xFFFF6B35 : 0xFF00C9B7;
        int iconSize = dp(ctx, 22);
        int badge = dp(ctx, 12);
        int totalW = iconSize;
        int totalH = iconSize + badge;
        Bitmap bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float cx = iconSize / 2f;
        float cy = badge + iconSize / 2f;
        float rad = iconSize * 0.40f;
        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setColor(fillColor);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setColor(0xFF0A1628);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(dp(ctx, 1.5f));
        if (disaster) {
            android.graphics.Path tri = new android.graphics.Path();
            tri.moveTo(cx, cy - rad);
            tri.lineTo(cx - rad, cy + rad * 0.85f);
            tri.lineTo(cx + rad, cy + rad * 0.85f);
            tri.close();
            c.drawPath(tri, body);
            c.drawPath(tri, edge);
        } else {
            c.drawCircle(cx, cy, rad, body);
            c.drawCircle(cx, cy, rad, edge);
        }
        // 하단 식별자 배지
        String ident = survivalIdentifier(survType, survDisaster, id);
        Paint badgeBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeBg.setColor(0xFF0A1628);
        Paint badgeBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeBorder.setColor(fillColor);
        badgeBorder.setStyle(Paint.Style.STROKE);
        badgeBorder.setStrokeWidth(dp(ctx, 1.5f));
        float bx = totalW / 2f;
        float by = badge / 2f + dp(ctx, 1);
        float br = badge / 2f;
        c.drawCircle(bx, by, br, badgeBg);
        c.drawCircle(bx, by, br, badgeBorder);
        Paint bt = new Paint(Paint.ANTI_ALIAS_FLAG);
        bt.setColor(Color.WHITE);
        bt.setTextSize(dp(ctx, 7));
        bt.setFakeBoldText(true);
        bt.setTextAlign(Paint.Align.CENTER);
        float ty = by - (bt.descent() + bt.ascent()) / 2f;
        c.drawText(ident, bx, ty, bt);
        return new BitmapDrawable(ctx.getResources(), bmp);
    }
}