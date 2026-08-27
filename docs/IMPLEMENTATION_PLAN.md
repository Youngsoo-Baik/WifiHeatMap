# 구현 명세 분석 및 단계 계획

## 현재 결정

첨부 명세의 `Codex 첫 작업 지시`를 우선 적용한다. 기존 Java Canvas 기반 프로토타입 코드는 삭제하지 않고 보존하되, 앱의 실행 진입점은 Kotlin + Jetpack Compose + MVVM 기반 Wi-Fi Debug Screen으로 전환한다.

## 명세 대비 기존 코드 차이

| 영역 | 기존 코드 | 명세 방향 | 처리 |
|---|---|---|---|
| 언어/UI | Java Custom View | Kotlin + Compose | Phase 1부터 전환 |
| 상태 관리 | View 내부 상태 | MVVM + Flow | ViewModel/StateFlow 도입 |
| Wi-Fi API | Activity에 직접 구현 | `wifi` 패키지 분리 | Scanner/Repository 분리 |
| 연결 AP | RSSI 중심 | SSID/BSSID/RSSI/Frequency/Link Speed | Debug Screen에서 검증 |
| 주변 AP | 측정 시 ScanResult | freshness 포함 목록 | timestamp age 표시 |
| 평면도/히트맵 | 기존 Java 프로토타입 존재 | Phase 2 이후 Compose로 재구성 | 기존 코드는 참고용 보존 |
| Calibration/벽 | 없음 | Phase 3/8 | 후속 단계 |
| 저장 | 단일 SharedPreferences 세션 | 프로젝트 JSON + 이미지 | 후속 저장 계층으로 교체 |

## 단계별 구현

1. **Phase 1 / Wi-Fi Debug — 완료**
   - Kotlin, Compose, MVVM, Navigation
   - 권한 처리
   - 연결 AP와 주변 AP 실제 데이터/freshness 표시
2. **Phase 2 / 평면도 — 완료**
   - 이미지 선택, Zoom/Pan, normalized tap coordinate
3. **Phase 3 / Calibration**
   - 두 점 선택, 실제 거리 입력, pixel-to-meter 변환
4. **Phase 5~6 / Survey와 기본 Heatmap**
   - 2~5초 RSSI 샘플, median, IDW, confidence/unmeasured 처리
5. **Phase 7 / Wi-Fi 장비**
   - Router/Mesh/Extender, 복수 BSSID 매핑, 장비별 Heatmap
6. **Phase 8~9 / 벽과 Hybrid Heatmap**
   - OpenCV 후보 검출, 수동 보정, propagation + residual IDW
7. **Phase 10 / Mesh 분석**
   - Connected AP와 Best AP 비교, roaming candidate 표시

## Phase 1 완료 결과

- 실제 Android 기기에서 권한 요청이 동작한다.
- 연결 AP의 SSID, BSSID, RSSI, Frequency, Link Speed가 표시된다.
- 주변 ScanResult의 SSID, BSSID, RSSI, Frequency, Channel, freshness가 표시된다.
- Active Scan 실패/제한 시 OS가 보유한 캐시 결과를 표시한다.
- Wi-Fi API 호출은 `wifi` 패키지 밖에서 수행하지 않는다.
- Gradle build와 최소 Unit Test가 통과한다.

## Phase 2 완료 결과

- 기본 평면도와 PNG/JPEG 사용자 이미지를 표시한다.
- 이미지 원본 비율을 유지한 fit-center 좌표계를 사용한다.
- 한 손가락 Pan, 두 손가락 1~5배 Zoom을 지원한다.
- 현재 변환 상태에서 탭한 지점을 normalized coordinate로 계산한다.
- Zoom/Pan 초기화를 지원한다.
- 좌표 정규화 Unit Test와 Debug APK 빌드가 통과한다.

다음 단계는 **Phase 3 Calibration**이다. 평면도에서 두 점을 선택하고 실제 거리를 입력해 pixel-to-meter scale을 계산한다.
