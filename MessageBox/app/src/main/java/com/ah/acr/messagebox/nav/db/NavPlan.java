package com.ah.acr.messagebox.nav.db;

import java.util.ArrayList;
import java.util.List;

/**
 * 확보 파이프라인 결과를 한 번에 저장하기 위한 전송 객체.
 * NavDao.saveWholePlan(NavPlan) 에서 사용.
 */
public class NavPlan {

    public NavRoute route;
    public final List<SegmentBundle> segments = new ArrayList<>();

    /** 구간 1건 + 그 구간의 날씨(원본/일별) 묶음 */
    public static class SegmentBundle {
        public NavSegment segment;
        public NavWeather weather;               // null 가능(날씨 확보 실패 구간)
        public List<NavWeatherDay> weatherDays;  // null 가능
    }
}
