package com.ah.acr.messagebox.nav;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

/**
 * NAV 4단계 - 온라인/오프라인 판정.
 * 데이터 확보 3단계 폴백의 1순위(온라인) 판정에 사용.
 *
 * 주의: 이 판정은 "네트워크 인터페이스 연결 여부"만 본다(실제 인터넷 도달성 아님).
 * Open-Meteo 호출 자체가 실패하면 NavPlanner에서 구간별로 캐시/위성으로 폴백한다.
 */
public final class NavConnectivity {

    private NavConnectivity() {}

    public static boolean isOnline(Context ctx) {
        if (ctx == null) return false;
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            @SuppressWarnings("deprecation")
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
    }
}
