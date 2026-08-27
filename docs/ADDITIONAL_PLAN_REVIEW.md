# 추가 구현 계획 검토 결과

## 기존 구현에 추가한 항목

- AP/Band별 실측 거리 기반 Strong(-50), Good(-60), Usable(-67) Coverage 반경 보간
- 측정 수에 따른 Coverage confidence와 Heatmap cell confidence
- 물리 장비의 복수 `WifiRadio` 및 BSSID/Band/Frequency 관리
- 동일 장비/Band의 복수 BSSID 중 strongest RSSI 집계
- 장비별, 전체 Mesh strongest, 실제 Connected AP 측정 결과 전환
- All/2.4/5/6 GHz Band 필터
- Coverage/Heatmap/Weak Zone 결과 View와 사용자 음영 임계값
- AP/측정점/벽 Overlay 표시 전환
- 장비 등록 화면의 주변 BSSID 후보 검색과 사용자 확정
- 벽 선분 끝점 위치 수정
- 프로젝트 이름, 결과 설정, 평면도 PNG를 JSON과 함께 저장·복원
- 기존 단일 JSON 프로젝트의 호환 로드

## 유지한 제한

- Coverage 원은 실측 샘플을 요약한 예상 범위이며 실제 RF 경계가 아니다.
- Android 에뮬레이터는 실제 Wi-Fi scan/RSSI/roaming 동작을 재현하지 못한다.
- 외부 AP 원자료는 측정에 보존하지만 등록 장비 기반 분석에서는 제외한다.
