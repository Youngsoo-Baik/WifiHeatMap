# Wi-Fi Heatmap MVP

첨부 구현 명세를 기준으로 구성한 Android 프로젝트입니다. **Phase 1부터 Phase 10까지의 MVP 흐름**을 구현합니다.

## 현재 구현

- Kotlin + Jetpack Compose
- MVVM + StateFlow
- Compose Navigation 기본 구조
- Android Wi-Fi 런타임 권한 처리
- 현재 연결 AP 표시
  - SSID, BSSID, RSSI, Frequency
  - Link/Rx/Tx Speed
- 주변 AP ScanResult 목록
  - SSID, BSSID, RSSI, Frequency, Channel
  - Scan timestamp 기반 freshness
- 필요할 때만 Active Scan 요청
- Active Scan이 제한되면 캐시된 ScanResult 표시
- Wi-Fi API를 `wifi` 패키지로 분리
- 주파수 대역 변환 Unit Test
- 기본 평면도 표시 및 PNG/JPEG 선택
- 평면도 원본 비율 fit-center 표시
- 한 손가락 Pan과 두 손가락 1~5배 Zoom
- Zoom/Pan 상태를 반영한 탭 좌표 선택
- 해상도에 독립적인 0~1 normalized 좌표 계산
- 좌표 정규화 Unit Test
- 두 점 거리 보정과 pixel-to-meter 축척
- 3초 RSSI 다중 샘플 및 중앙값 측정
- 측정점 추가·재측정·삭제
- IDW 기본 히트맵과 미측정 영역 표시
- 공유기/Mesh/증폭기 위치 및 복수 BSSID 매핑
- OpenCV Canny/HoughLinesP 벽 후보 검출
- 수동 벽 추가·삭제·개구부 전환
- 벽 감쇠 전파 모델 + 잔차 IDW 하이브리드 히트맵
- 연결 AP와 최적 AP 비교 및 12 dB 로밍 후보 표시
- 측정 프로젝트 JSON 저장·불러오기

기존 Java Canvas 평면도·히트맵 프로토타입은 후속 Compose Survey 화면 마이그레이션을 위해 소스에 보존되어 있으며 현재 Launcher 화면에서는 사용하지 않습니다.

## 실행

1. Android Studio에서 프로젝트 루트를 엽니다.
2. Gradle JDK를 Android Studio 내장 JDK 17로 설정합니다.
3. 실제 Android 기기를 연결합니다.
4. `app` 실행 구성을 선택하고 Run을 누릅니다.
5. 위치 및 근처 기기 권한을 허용하고 기기의 위치 서비스를 켭니다.
6. `Phase 2 · 평면도 열기`부터 화면 하단의 다음 버튼을 따라 Phase 10까지 진행합니다.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
releases/wifi-heatmap-v1.0.0-debug.apk
```

## 권한

- `ACCESS_WIFI_STATE`
- `CHANGE_WIFI_STATE`
- `ACCESS_FINE_LOCATION`
- Android 13 이상: `NEARBY_WIFI_DEVICES`

## 현재 제한

- Android 에뮬레이터는 실제 Wi-Fi radio/BSSID 측정을 재현하지 못하므로 실제 기기 검증이 필요합니다.
- Active Scan은 Android throttling 정책에 따라 실패할 수 있으며 이 경우 캐시 결과가 표시됩니다.
- 에뮬레이터에서는 UI와 저장 흐름을 검증할 수 있지만 실제 RSSI/로밍 분석은 Android 실제 기기가 필요합니다.

전체 차이 분석과 단계 계획은 [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md)를 참고하세요.
