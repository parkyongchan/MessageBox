package com.ah.acr.messagebox;

/**
 * Tactical Map → ChatRoom 이미지 전달용 임시 보관소.
 * 전술지도(Activity)에서 캡처+수신처를 담고,
 * 복귀한 화면이 채팅방을 열어 첨부한다.
 */
public class TacticalShare {
    public static byte[] pendingImage;
    public static String pendingCodeNum;
    public static String pendingName;

    public static boolean hasPending() {
        return pendingImage != null && pendingCodeNum != null;
    }

    public static void clear() {
        pendingImage = null;
        pendingCodeNum = null;
        pendingName = null;
    }
}