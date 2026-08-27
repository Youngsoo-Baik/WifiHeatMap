# Wi-Fi Heatmap MVP

첨부 구현 명세를 기준으로 단계적으로 재구성 중인 Android 프로젝트입니다. 현재 실행 화면은 **Phase 1 Wi-Fi Debug Screen**이며, 실제 Android 기기에서 Wi-Fi 데이터 수집의 신뢰성을 먼저 검증합니다.

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

기존 Java Canvas 평면도·히트맵 프로토타입은 후속 Compose Survey 화면 마이그레이션을 위해 소스에 보존되어 있으며 현재 Launcher 화면에서는 사용하지 않습니다.

## 실행

1. Android Studio에서 프로젝트 루트를 엽니다.
2. Gradle JDK를 Android Studio 내장 JDK 17로 설정합니다.
3. 실제 Android 기기를 연결합니다.
4. `app` 실행 구성을 선택하고 Run을 누릅니다.
5. 위치 및 근처 기기 권한을 허용하고 기기의 위치 서비스를 켭니다.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 권한

- `ACCESS_WIFI_STATE`
- `CHANGE_WIFI_STATE`
- `ACCESS_FINE_LOCATION`
- Android 13 이상: `NEARBY_WIFI_DEVICES`

## 현재 제한

- Android 에뮬레이터는 실제 Wi-Fi radio/BSSID 측정을 재현하지 못하므로 실제 기기 검증이 필요합니다.
- Active Scan은 Android throttling 정책에 따라 실패할 수 있으며 이 경우 캐시 결과가 표시됩니다.
- 평면도, Calibration, Survey, Compose Heatmap은 다음 단계에서 구현합니다.

전체 차이 분석과 단계 계획은 [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md)를 참고하세요.
