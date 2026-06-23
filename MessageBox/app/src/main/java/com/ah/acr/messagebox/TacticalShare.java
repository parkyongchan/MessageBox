package com.ah.acr.messagebox;

/**
 * Tactical Map → ChatRoom 전달용 임시 보관소.
 * 전술지도(Activity)에서 캡처/전술데이터 + 수신처를 담고,
 * 복귀한 화면이 채팅방을 열어 첨부/전송한다.
 */
public class TacticalShare {
    public static byte[] pendingImage;          // 1안: 사진
    public static String pendingTactical;       // 2안: 전술 데이터 직렬화 텍스트
    public static int pendingMarkerCount;       // 요약용: 마커 수
    public static int pendingLineCount;         // 요약용: 라인 수
    public static String pendingCodeNum;
    public static String pendingName;

    public static boolean hasPending() {
        return (pendingImage != null || pendingTactical != null) && pendingCodeNum != null;
    }

    public static boolean isTactical() {
        return pendingTactical != null;
    }

    public static void clear() {
        pendingImage = null;
        pendingTactical = null;
        pendingMarkerCount = 0;
        pendingLineCount = 0;
        pendingCodeNum = null;
        pendingName = null;
    }
}