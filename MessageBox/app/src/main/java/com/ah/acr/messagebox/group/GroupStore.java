package com.ah.acr.messagebox.group;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 그룹 주소록 로컬 저장소 (SharedPreferences + JSON).
 *
 * 그룹은 장비 로컬 번호(1,2,3...)로 관리. 구성원은 연락처(AddressEntity)의 IMEI 목록.
 * 등록 전송 후 서버 ~A: ACK를 받으면 confirmed=true 로 확정.
 */
public class GroupStore {

    private static final String PREF = "group_address_store";
    private static final String KEY_GROUPS = "groups";

    /** 그룹 1개 */
    public static class Group {
        public String groupNo;          // 10자리 (0000000001)
        public String name;             // 그룹명 (로컬 전용)
        public List<String> members;    // 구성원 IMEI(15자리) 목록
        public boolean confirmed;       // ACK 확정 여부
        public int pendingMsgId;        // 등록 전송 시 msgId (ACK 매칭용)

        public Group() {
            this.members = new ArrayList<>();
            this.confirmed = false;
            this.pendingMsgId = -1;
        }

        /** 표시용: "1번 (소대명)" 또는 "1번" */
        public String getDisplayLabel() {
            String no = shortNo();
            if (name != null && !name.trim().isEmpty()) {
                return "No." + no + " (" + name.trim() + ")";
            }
            return "No." + no;
        }

        /** 앞자리 0 제거한 짧은 번호 (0000000001 -> 1) */
        public String shortNo() {
            try { return String.valueOf(Long.parseLong(groupNo)); }
            catch (Exception e) { return groupNo; }
        }
    }

    private final SharedPreferences prefs;

    public GroupStore(Context ctx) {
        this.prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 전체 그룹 로드 (groupNo 오름차순) */
    public List<Group> loadAll() {
        List<Group> out = new ArrayList<>();
        String json = prefs.getString(KEY_GROUPS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Group g = new Group();
                g.groupNo = o.getString("groupNo");
                g.name = o.optString("name", "");
                g.confirmed = o.optBoolean("confirmed", false);
                g.pendingMsgId = o.optInt("pendingMsgId", -1);
                JSONArray m = o.optJSONArray("members");
                if (m != null) {
                    for (int j = 0; j < m.length(); j++) g.members.add(m.getString(j));
                }
                out.add(g);
            }
        } catch (Exception ignore) {}
        // groupNo 정렬
        out.sort((a, b) -> {
            try { return Long.compare(Long.parseLong(a.groupNo), Long.parseLong(b.groupNo)); }
            catch (Exception e) { return a.groupNo.compareTo(b.groupNo); }
        });
        return out;
    }

    /** 전체 저장 */
    private void saveAll(List<Group> groups) {
        JSONArray arr = new JSONArray();
        try {
            for (Group g : groups) {
                JSONObject o = new JSONObject();
                o.put("groupNo", g.groupNo);
                o.put("name", g.name == null ? "" : g.name);
                o.put("confirmed", g.confirmed);
                o.put("pendingMsgId", g.pendingMsgId);
                JSONArray m = new JSONArray();
                for (String s : g.members) m.put(s);
                o.put("members", m);
                arr.put(o);
            }
        } catch (Exception ignore) {}
        prefs.edit().putString(KEY_GROUPS, arr.toString()).apply();
    }

    /** 그룹 추가/수정 (같은 groupNo면 덮어쓰기) */
    public void upsert(Group group) {
        List<Group> all = loadAll();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).groupNo.equals(group.groupNo)) {
                all.set(i, group);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(group);
        saveAll(all);
    }

    /** 특정 그룹 조회 */
    public Group find(String groupNo) {
        for (Group g : loadAll()) {
            if (g.groupNo.equals(groupNo)) return g;
        }
        return null;
    }

    /** 그룹 삭제 */
    public void delete(String groupNo) {
        List<Group> all = loadAll();
        all.removeIf(g -> g.groupNo.equals(groupNo));
        saveAll(all);
    }

    /** msgId로 그룹 찾아 확정 처리 (ACK 수신 시 호출) */
    public Group confirmByMsgId(int msgId) {
        List<Group> all = loadAll();
        for (Group g : all) {
            if (g.pendingMsgId == msgId && !g.confirmed) {
                g.confirmed = true;
                saveAll(all);
                return g;
            }
        }
        return null;
    }

    /** 확정된 그룹만 (채팅 선택용) */
    public List<Group> loadConfirmed() {
        List<Group> out = new ArrayList<>();
        for (Group g : loadAll()) if (g.confirmed) out.add(g);
        return out;
    }

    /** 다음 사용 가능한 그룹번호 (1부터, 빈 번호 재사용) */
    public String nextGroupNo() {
        List<Group> all = loadAll();
        for (long n = 1; n <= 9999999999L; n++) {
            String candidate = String.format("%010d", n);
            boolean used = false;
            for (Group g : all) {
                if (g.groupNo.equals(candidate)) { used = true; break; }
            }
            if (!used) return candidate;
        }
        return "0000000001";
    }
}
