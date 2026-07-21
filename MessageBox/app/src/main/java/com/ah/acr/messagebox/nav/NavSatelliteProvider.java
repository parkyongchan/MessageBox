package com.ah.acr.messagebox.nav;

import com.ah.acr.messagebox.nav.db.NavPlan;

/**
 * NAV 4단계 - 위성(3순위) 폴백 공급자.
 *
 * 온라인·캐시가 모두 불가할 때(예: 현장에서 새 목적지 즉석 생성),
 * 앱의 기존 위성 송수신 패턴(요청 패킷 송신 → 서버 처리 → MT 수신 → 파싱)을
 * 재사용하여 경로+요약 날씨를 확보한다. 3종 이동수단(WALK/VEHICLE/VESSEL) 공통.
 *
 * 이 인터페이스만 앱의 위성 송수신 코드에 맞게 구현하면 NavPlanner가 자동으로 호출한다.
 * (위성은 대역폭·지연 특성상 최소 요약 데이터만 수신 → NavPlan에 핵심 구간·대표 날씨만 채워도 됨)
 *
 * 구현 메서드는 블로킹(동기)이며 NavPlanner가 백그라운드 스레드에서 호출한다.
 * 수신 대기(타임아웃) 처리는 구현체 책임.
 */
public interface NavSatelliteProvider {

    /**
     * 위성으로 경로+날씨를 확보하여 저장용 NavPlan 으로 반환한다.
     *
     * @param in 사용자가 지정한 이동수단/출발·목적지/순항속력 등(경로 좌표열 points 는 비어있을 수 있음)
     * @return 저장할 NavPlan (route + segments[+weather]). 확보 실패 시 예외를 던진다.
     * @throws Exception 위성 타임아웃/미수신/파싱 실패 등
     */
    NavPlan requestPlanViaSatellite(NavPlanner.RouteInput in) throws Exception;
}
