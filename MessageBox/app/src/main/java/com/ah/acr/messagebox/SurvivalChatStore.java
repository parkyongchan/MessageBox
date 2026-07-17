package com.ah.acr.messagebox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [S5] \uc0dd\uc874 \uc548\ub0b4 \uba54\uc2dc\uc9c0 \uc800\uc7a5\uc18c (IMEI \uae30\uc900).
 * static \u2014 Activity \uc774\ub3d9 \uac04 \uc720\uc9c0, \uc571 \uc7ac\uc2dc\uc791 \uc2dc \ub9ac\uc14b.
 * server(GUIDE:) \uc218\uc2e0 \uba54\uc2dc\uc9c0\uc640 victim \uc1a1\uc2e0 \uba54\uc2dc\uc9c0\ub97c \ubaa8\ub450 \ub2f4\ub294\ub2e4.
 */
public final class SurvivalChatStore {

    private SurvivalChatStore() {}

    /** \uba54\uc2dc\uc9c0 \ud55c \uac74. server=\uc11c\ubc84 \uc548\ub0b4(\uc67c\ucabd), victim=\uc870\ub09c\uc790 \uc9c8\uc758(\uc624\ub978\ucabd). */
    public static final class Msg {
        public final String sender;   // "server" | "victim"
        public final String content;
        public final long ts;
        public Msg(String sender, String content, long ts) {
            this.sender = sender; this.content = content; this.ts = ts;
        }
    }

    public interface Listener { void onSurvivalMsg(String imei, Msg msg); }

    private static final Map<String, List<Msg>> sByImei = new LinkedHashMap<>();
    private static final List<Listener> sListeners = new ArrayList<>();

    private static String norm(String imei) { return imei == null ? "" : imei.trim(); }

    /** \uba54\uc2dc\uc9c0 \ucd94\uac00 + \ub9ac\uc2a4\ub108 \ube0c\ub85c\ub4dc\uce90\uc2a4\ud2b8. */
    public static synchronized void add(String imei, String sender, String content) {
        if (content == null || content.isEmpty()) return;
        String key = norm(imei);
        Msg m = new Msg(sender, content, System.currentTimeMillis());
        sByImei.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        for (Listener l : new ArrayList<>(sListeners)) {
            try { l.onSurvivalMsg(key, m); } catch (Exception ignore) {}
        }
    }

    /** \ud574\ub2f9 IMEI\uc758 \uba54\uc2dc\uc9c0 \uc804\uccb4 (\uc2dc\uac04\uc21c). */
    public static synchronized List<Msg> get(String imei) {
        List<Msg> l = sByImei.get(norm(imei));
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    public static synchronized void addListener(Listener l) {
        if (l != null && !sListeners.contains(l)) sListeners.add(l);
    }

    public static synchronized void removeListener(Listener l) {
        sListeners.remove(l);
    }
}