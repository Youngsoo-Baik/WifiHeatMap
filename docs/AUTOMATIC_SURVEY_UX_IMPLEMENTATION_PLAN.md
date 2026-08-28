# 자동 측정 및 홈 중심 UX 통합 구현계획

## 1. 계획 목적

현재 구현된 평면도, Calibration, 벽 검출, 장비/BSSID, 수동 Survey, Coverage, Heatmap, Mesh 분석을 유지하면서 다음 두 방향을 통합한다.

1. 앱의 기준 화면을 결과 중심 Home으로 변경하고 설정 화면은 필요한 경우에만 진입한다.
2. 사용자가 측정 지점과 AP 위치를 반복 입력하지 않도록 이동 경로, Wi-Fi 관측, AP 위치와 Coverage를 자동 추정한다.

첨부 문서의 기술 제안은 요구사항 자료로 활용하며, 실제 구현은 Android 제약과 기존 코드 호환성을 고려해 단계적으로 수행한다.

## 2. 핵심 제품 결정

- 시작 화면은 Wi-Fi Debug가 아니라 `HomeResultsScreen`이다.
- 저장 프로젝트가 있으면 앱 시작 시 자동 복원한다.
- 프로젝트가 없어도 기본 평면도와 기본 Coverage 화면을 먼저 보여준다.
- 설정은 선형 Wizard가 아니라 Home에서 진입하는 독립 편집 화면으로 구성한다.
- 설정 화면의 완료 동작은 다음 설정 화면 이동이 아니라 `적용 후 Home 복귀`다.
- 자동 측정에는 평면도 축척과 시작 위치가 필요하다.
- 평면도와 실제 이동 방향 정렬을 위해 시작 위치와 초기 진행 방향을 한 번의 드래그 동작으로 입력받는다.
- 이동 추적은 공통 인터페이스 아래 `PDR 기본 지원 + ARCore 지원 기기에서 향상 모드`로 구성한다.
- AP/BSSID/위치는 앱이 먼저 추정하고 사용자는 신뢰도가 낮거나 잘못된 항목만 수정한다.
- 기존 수동 Survey는 자동 추적이 불가능한 기기의 fallback과 정밀 보정 수단으로 유지한다.
- 모든 데이터는 기기 내부에 저장하며 Cloud와 계정 기능은 도입하지 않는다.

## 3. 기존 구현 재사용 범위

| 기존 기능 | 처리 방향 |
|---|---|
| 평면도 이미지, Zoom/Pan, normalized 좌표 | 그대로 재사용 |
| 2점 Calibration | 자동 측정 시작 전 1회 설정으로 재사용 |
| OpenCV 벽 검출 및 수동 편집 | 평면도 변경 직후 백그라운드 후보 생성 |
| Wi-Fi 연결/ScanResult 수집 | 위치 timestamp와 결합하는 Observation Collector로 확장 |
| 수동 RSSI Survey | fallback 및 보정 측정으로 유지 |
| WifiDevice/WifiRadio | 자동 추정 위치·신뢰도·확정 상태 필드 추가 |
| IDW 및 Hybrid Heatmap | 자동 관측 결과를 입력으로 재사용 |
| CoverageRadiusEstimator | 자동 추정 AP와 Band별 관측 데이터에 재사용 |
| MeshAnalyzer | 통합 결과 화면의 분석 탭으로 이동 |
| JSON + floorplan.png 저장 | schemaVersion 기반 프로젝트 v2로 확장 |

## 4. 목표 사용자 흐름

```text
앱 실행
  ↓
Home / 기본 Coverage 화면
  ├─ 저장 프로젝트 자동 복원
  ├─ 설정 상태 및 신뢰도 표시
  ├─ [자동 측정 시작]
  └─ [설정]

자동 측정 시작
  ↓
필수 항목만 사전 확인
  ├─ 평면도
  ├─ 축척
  ├─ 위치/센서 권한
  └─ 시작 위치 + 초기 방향
  ↓
집 안을 천천히 이동
  ├─ 이동 경로 자동 추적
  ├─ Wi-Fi 관측 자동 수집
  └─ 측정 품질 실시간 표시
  ↓
[측정 종료]
  ↓
자동 분석
  ├─ 집 Wi-Fi 후보 필터
  ├─ BSSID 클러스터링
  ├─ 물리 AP 위치 추정
  ├─ Coverage/Heatmap 계산
  └─ 신뢰도 계산
  ↓
Home / 결과 화면 복귀
  ↓
확인 필요 항목만 수정
```

## 5. 목표 화면 및 Navigation 구조

### 5.1 Route

```text
home
auto_survey
manual_survey
settings/project
settings/floorplan
settings/calibration
settings/walls
settings/devices
review/access_points
diagnostics/wifi
```

Coverage, Heatmap, Weak Zone, Hybrid, Mesh는 별도 Route가 아니라 Home 결과 화면의 탭으로 통합한다.

### 5.2 Back Stack 정책

- `home`을 startDestination으로 지정한다.
- 설정 화면의 `적용`, Survey의 `측정 완료`, AP 검토의 `확인 완료`는 모두 `home`으로 복귀한다.
- Home 복귀는 `popUpTo(home)`과 `launchSingleTop`을 사용한다.
- 편집 화면의 시스템 Back은 변경사항이 있으면 저장/폐기 확인 후 Home으로 복귀한다.
- Home에서 시스템 Back을 누를 때만 앱을 종료한다.

### 5.3 Home 기본 구성

- 프로젝트 이름과 자동 저장 상태
- 평면도 위 기본 Coverage
- `Coverage / Heatmap / Weak Zone / Mesh` 탭
- 측정 품질, 감지 AP 수, 확인 필요 수, Weak Area 요약
- Primary CTA: `자동 측정 시작` 또는 `측정 계속`
- Secondary CTA: `수동 위치 측정`
- 설정 메뉴: 평면도, 축척, 벽, 장비, 프로젝트
- Wi-Fi Debug는 고급 진단 메뉴에 배치

## 6. 자동 측정 기술 설계

### 6.1 IndoorTrackingProvider

```kotlin
interface IndoorTrackingProvider {
    val trackingPoints: Flow<TrackingPoint>
    suspend fun start(startPose: FloorPlanPose)
    suspend fun stop()
}
```

구현체:

- `PdrTrackingProvider`: Step Detector, Rotation Vector, Accelerometer 기반 기본 지원
- `ArCoreTrackingProvider`: ARCore 지원 기기의 Visual-Inertial Odometry
- `TrackingProviderSelector`: 지원 여부와 권한에 따라 provider 선택

ARCore는 일반 Android API 21 기기 전체에서 동작하지 않으므로 필수 의존으로 만들지 않는다. ARCore 사용 불가, 카메라 권한 거부, Tracking 품질 저하 시 PDR로 전환한다.

### 6.2 좌표 정렬

시작 위치 한 점만으로는 AR/PDR 좌표축과 평면도 방향을 정렬할 수 없다. 자동 측정 시작 시 다음 한 번의 제스처를 받는다.

```text
평면도에서 현재 위치를 누르고 실제 첫 이동 방향으로 드래그
```

이를 통해 다음을 저장한다.

- normalized 시작 좌표
- 평면도상의 초기 heading
- meterPerPixel
- tracking 좌표 → 평면도 좌표 변환 행렬

### 6.3 측정 세션

- 화면이 켜진 상태에서 Foreground 측정 세션으로 실행한다.
- 연결 AP RSSI는 짧은 주기로 기록한다.
- 주변 ScanResult는 OS cache와 freshness를 우선 활용한다.
- Active Scan은 제한적으로 요청한다.
- TrackingPoint와 WifiObservation은 timestamp 기준 최근접 결합한다.
- 오래된 ScanResult는 age에 따라 weight를 낮추거나 제외한다.
- 경로와 관측은 중간 저장해 앱 중단 시 복구한다.

## 7. 자동 AP 분석 설계

### 7.1 집 Wi-Fi 후보 필터

초기 seed는 현재 연결 SSID/BSSID로 설정한다. 다음 feature를 점수화한다.

- 동일 SSID 또는 사용자가 추가한 집 SSID
- OUI/vendor 유사성
- BSSID bit/prefix 패턴
- 관측 위치별 RSSI 동시 변화
- Peak RSSI 위치 근접성
- Band 조합

외부 AP 원자료는 보존하되 기본 Coverage 계산에서는 제외한다.

### 7.2 BSSID 클러스터링

초기 구현은 Pairwise similarity score와 threshold 기반 계층적 병합을 사용한다.

```text
score =
SSID score
+ vendor score
+ BSSID pattern score
+ spatial correlation score
+ peak proximity score
```

결과는 `PhysicalAccessPointCandidate`로 저장하고 cluster confidence를 제공한다. 자동 병합 결과는 확정 데이터와 분리해 사용자가 병합/분리할 수 있게 한다.

### 7.3 AP 위치 추정

1차 구현:

- RSSI를 선형 power로 변환해 weighted centroid 계산
- 매우 오래된 scan과 낮은 tracking confidence 관측은 낮은 weight 적용
- 상위 RSSI percentile 중심으로 outlier 완화

2차 구현:

- 평면도 grid를 candidate AP 위치로 탐색
- 거리, Band, 벽 교차 수로 예상 RSSI 계산
- 실제 RSSI와의 robust loss가 최소인 위치 선택
- weighted centroid 결과를 optimizer 초기값으로 사용

### 7.4 위치 Confidence

다음 값을 0~1 점수로 정규화한다.

- 유효 관측 포인트 수
- 이동 경로의 공간 분산
- RSSI peak 집중도
- BSSID cluster confidence
- propagation residual
- tracking confidence
- 미측정 영역 비율

Confidence가 낮으면 단일 점 대신 후보 영역을 표시한다.

## 8. 프로젝트 데이터 v2

```kotlin
data class SurveySession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val trackingProvider: TrackingProviderType,
    val startPose: FloorPlanPose,
    val trackingPoints: List<TrackingPoint>,
    val observations: List<WifiObservation>
)

data class PhysicalAccessPoint(
    val id: String,
    val name: String,
    val estimatedPoint: NormalizedPoint,
    val positionConfidence: Double,
    val radios: List<WifiRadio>,
    val suggestedType: WifiDeviceType?,
    val positionSource: PositionSource,
    val userConfirmed: Boolean
)
```

프로젝트 JSON에 `schemaVersion = 2`를 추가하고 기존 v1의 수동 측정, WifiDevice, 벽, Calibration을 손실 없이 migration한다.

## 9. 단계별 구현

### Phase 0 — Home 중심 UX 개편

- HomeResultsScreen을 startDestination으로 변경
- 앱 시작 시 프로젝트 자동 복원
- 기존 결과 3개 화면을 Home 탭으로 통합
- 설정/Survey 완료 시 Home 복귀
- 저장을 자동 저장으로 변경
- Wi-Fi Debug를 진단 메뉴로 이동

완료 기준:

- 사용자가 설정 완료 후 Back을 연속으로 누르지 않는다.
- 앱 재실행 후 마지막 결과가 바로 표시된다.

### Phase 1 — Tracking 기반 구조

- TrackingPoint, FloorPlanPose, SurveySession 모델
- IndoorTrackingProvider 인터페이스
- Sensor capability/permission 진단
- PDR provider와 synthetic path provider
- 시작 위치+방향 입력 UI
- 평면도 경로 Overlay

완료 기준:

- 사용자가 이동하면 평면도에 상대 이동 경로가 표시된다.
- 지원하지 않는 기기에서는 수동 Survey fallback이 제공된다.

### Phase 2 — 자동 Wi-Fi Observation

- WifiObservationCollector
- TrackingPoint와 관측 timestamp 결합
- ScanResult freshness weight
- 측정 진행률과 감지 AP 수 UI
- 중단 가능한 Foreground 측정 세션

완료 기준:

- 사용자의 위치 터치 없이 `(x, y, BSSID, RSSI, frequency)` 데이터가 생성된다.

### Phase 3 — BSSID 자동 그룹화

- 집 Wi-Fi seed와 외부 AP 필터
- spatial fingerprint 생성
- pairwise similarity와 cluster 생성
- AP 개수 optional constraint
- cluster confidence

완료 기준:

- 여러 Band BSSID가 물리 AP 후보로 자동 그룹화된다.
- 외부 AP가 기본 분석에서 제외된다.

### Phase 4 — AP 위치 자동 추정

- weighted centroid estimator
- wall-aware grid optimizer
- 위치 confidence와 후보 영역
- AP 위치 Overlay

완료 기준:

- 수동 AP 위치 입력 없이 예상 위치가 표시된다.
- 신뢰도가 낮은 AP가 명확히 구분된다.

### Phase 5 — Coverage와 Heatmap 통합

- 자동 AP/Band별 Coverage radius
- 기존 IDW/Hybrid engine 연결
- 전체 Mesh strongest와 Connected 결과
- Weak/Unmeasured 영역
- 측정 경로 품질 분석과 추가 측정 추천 영역

완료 기준:

- 자동 측정 종료 후 Home에서 Coverage와 Heatmap이 바로 표시된다.

### Phase 6 — 사용자 예외 수정

- AP 위치 수정
- AP 병합/분리
- 집 Wi-Fi 포함/제외
- 이름과 장비 유형 수정
- 수정 후 Coverage/Heatmap 재계산

완료 기준:

- 사용자는 자동 결과 중 잘못된 항목만 수정할 수 있다.

### Phase 7 — ARCore 향상 및 현장 검증

- ARCore availability spike
- ArCoreTrackingProvider
- PDR/ARCore 전환과 tracking 품질 표시
- 실제 아파트 반복 측정 비교
- 배터리, scan throttling, drift 튜닝

완료 기준:

- ARCore 지원 기기에서는 이동 경로 오차가 PDR보다 개선된다.
- 미지원 기기에서도 PDR/수동 Survey로 전체 흐름을 완료한다.

## 10. 테스트 계획

Pure Kotlin Unit Test:

- tracking 좌표 → normalized floorplan 좌표 변환
- step/heading 기반 PDR 적분
- timestamp observation join
- stale scan weight
- spatial fingerprint correlation
- BSSID cluster merge/split
- weighted centroid AP 위치
- wall-aware position objective
- position confidence
- path coverage 및 미측정 영역
- project v1 → v2 migration

Android Test:

- 센서 lifecycle과 권한
- Foreground session 중단/복원
- ARCore availability fallback
- Home → Survey → Home Back Stack
- 설정 적용 → Home 복귀

## 11. 주요 위험과 대응

| 위험 | 대응 |
|---|---|
| PDR 누적 drift | 짧은 세션, 벽/경로 constraint, ARCore 옵션, 사용자 경로 보정 |
| 시작 방향 정렬 오류 | 시작점+방향 1회 드래그와 재정렬 기능 |
| ARCore 미지원 | PDR와 수동 Survey fallback |
| Android scan throttling | cache/freshness 활용, active scan 제한 |
| 외부 AP 오분류 | connected network seed, confidence, 사용자 포함/제외 |
| Mesh BSSID 오병합 | 자동 결과와 확정 상태 분리, 병합/분리 UX |
| 데이터 부족 | 결과 생성은 허용하되 confidence 하향 및 추가 측정 영역 안내 |
| 배터리 소모 | 화면 유지 측정, sampling rate 제한, 세션 중단/재개 |

## 12. 완료 기준

- 앱 실행 시 기본 Home/결과 화면이 먼저 나타난다.
- 기존 프로젝트는 자동 복원된다.
- 사용자는 평면도, 축척, 시작 위치와 방향만으로 자동 측정을 시작할 수 있다.
- 측정 지점마다 평면도를 터치하지 않는다.
- AP 위치와 BSSID 그룹이 자동 제안된다.
- 자동 분석 후 Home에 Coverage, Heatmap, Weak Zone이 표시된다.
- 데이터 부족과 추정 불확실성을 confidence로 표현한다.
- 잘못된 AP 위치, 병합, 분리만 사용자가 수정한다.
- 설정 및 수정 완료 후 항상 Home으로 복귀한다.

## 13. 구현 착수 순서

우선 Phase 0을 완료해 현재의 선형 화면 흐름과 저장 프로젝트 접근 문제를 해결한다. 이후 Phase 1의 Tracking 추상화와 PDR 기반 경로 표시를 구현하고, 실제 기기에서 경로 품질을 확인한 뒤 Wi-Fi Observation과 자동 AP 분석으로 확장한다.

## 14. 구현 진행 현황

| 단계 | 상태 | 현재 구현 |
|---|---|---|
| Phase 0 | 완료 | Home 시작 화면, 결과 탭 통합, 자동 복원·저장, 설정 완료 후 Home 복귀 |
| Phase 1 | 완료 | Tracking 모델/인터페이스, PDR provider, 시작 위치·방향, 경로 overlay |
| Phase 2 | 완료 | Wi-Fi 권한 공통화, 2초 관측, Active Scan 제한, timestamp join/freshness 모델, 자동 측정 변환 |
| Phase 3 | 1차 완료 | 연결 SSID seed, 외부 SSID 제외, spatial correlation, BSSID cluster와 confidence |
| Phase 4 | 1차 완료 | RSSI weighted centroid, 위치 confidence, 추정 AP overlay |
| Phase 5 | 1차 완료 | 기존 Coverage/IDW/Hybrid/Mesh에 자동 측정과 추정 AP 연결 |
| Phase 6 | 진행 중 | 확인 필요 AP 표시, 위치 수정, 개별·전체 확정; 병합/분리와 포함/제외는 남음 |
| Phase 7 | 미착수 | ARCore provider와 실제 아파트 반복 검증 필요 |

다음 우선순위는 실제 기기 PDR/RSSI 데이터 검증, wall-aware AP grid optimizer, AP 병합·분리, 세션 중간 저장, ARCore 선택 provider 순서다.
