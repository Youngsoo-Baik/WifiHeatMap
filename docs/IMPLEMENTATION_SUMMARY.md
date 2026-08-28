# Wi-Fi Heatmap 구현 현황

작성일: 2026-08-28

## 1. 제품 목표

아파트 평면도 위에서 Wi-Fi 신호 세기를 측정하고 Coverage, Heatmap, 음영지역과 Mesh 로밍 상태를 확인하는 Android 앱이다. 현재 구현은 사용자가 설정 화면을 순서대로 통과하는 방식 대신 Home 결과 화면을 기준으로 동작하며, 필요한 설정과 보정 화면만 선택적으로 열도록 구성한다.

## 2. 사용자 흐름

```text
앱 실행
  → Home 결과 화면과 기본 평면도 표시
  → 저장 프로젝트가 있으면 자동 복원
  → 자동 측정 또는 수동 측정 선택

자동 측정
  → 평면도에서 시작 위치 선택
  → 초기 이동 방향 선택
  → PDR 이동 경로와 Wi-Fi 관측 자동 수집
  → 측정 종료
  → 측정점, BSSID 그룹, 추정 AP 위치 생성 및 저장
  → Home 결과 화면 복귀

결과 확인
  → Coverage / Heatmap / Weak Zone / Mesh 탭 전환
  → 확인이 필요한 추정 AP만 선택적으로 위치 수정
  → 설정 완료 또는 AP 확인 완료 후 Home 복귀
```

## 3. 화면 및 Navigation

| 화면 | 역할 |
|---|---|
| Home | 기본 평면도와 Coverage/Heatmap/Weak Zone/Mesh 결과 표시 |
| 자동 측정 | 시작 위치·방향 입력, PDR 경로 표시, Wi-Fi 자동 관측 |
| 수동 측정 | 평면도 측정 위치 선택과 RSSI 다중 샘플 측정 |
| 설정 허브 | 평면도, 축척, 벽, 장비, 프로젝트 관리 진입점 |
| 평면도 | PNG/JPEG 변경과 원본 비율 fit-center 표시 |
| 거리 보정 | 두 점과 실제 거리로 meter-per-pixel 계산 |
| 벽 편집 | OpenCV 자동 검출, 수동 추가·삭제·개구부 지정 |
| 장비 편집 | 공유기·Mesh·증폭기 위치와 복수 BSSID 매핑 |
| 추정 AP 검토 | 자동 추정 AP 위치·신뢰도 표시, 위치 수정과 확정 |
| Wi-Fi 진단 | 연결 AP, 주변 AP, 권한과 ScanResult 상태 확인 |

`home`이 시작 Route이며 설정 적용, 측정 완료, AP 검토 완료 시 `popUpTo(home)`과 `launchSingleTop`으로 Home에 복귀한다. 결과 화면은 별도 Back Stack을 만들지 않고 Home 내부 탭으로 통합했다.

## 4. 자동 측정

### 4.1 이동 추적

- Android `TYPE_STEP_DETECTOR`와 `TYPE_ROTATION_VECTOR` 센서를 사용한다.
- 시작 위치와 방향으로 평면도 좌표계와 PDR 좌표계를 정렬한다.
- 걸음마다 상대 이동량을 계산해 normalized 평면도 좌표로 변환한다.
- 평면도 위에 시작점, 방향점, 현재 위치와 이동 경로를 표시한다.
- 센서 미지원, 축척 미설정, 권한 거부 시 오류와 수동 측정 대안을 제공한다.

### 4.2 Wi-Fi 관측

- 측정 중 2초 주기로 연결 Wi-Fi와 주변 `ScanResult`를 수집한다.
- Active Scan 요청은 20초 간격으로 제한하고 나머지는 OS 캐시를 활용한다.
- 관측 시점의 현재 PDR 위치를 normalized 좌표와 결합한다.
- 별도 `ObservationJoiner`가 tracking timestamp 근접 결합과 stale scan 가중치 계산을 제공한다.
- 측정 종료 시 관측 snapshot을 기존 `SurveyMeasurement` 형식으로 변환한다.

## 5. AP 자동 분석

### 5.1 집 Wi-Fi 필터

- 자동 측정의 연결 SSID/BSSID를 집 Wi-Fi seed로 사용한다.
- 다른 SSID의 주변 AP 원자료는 측정에 보존하지만 자동 장비 후보에서는 제외한다.

### 5.2 BSSID 그룹화

- 위치별 RSSI 배열로 spatial fingerprint를 생성한다.
- SSID, OUI, BSSID hardware prefix, RSSI 상관관계와 peak 위치 거리를 점수화한다.
- threshold 이상의 BSSID를 하나의 물리 AP 후보로 병합한다.
- 서로 다른 위치에서 peak가 나타나는 동일 SSID Mesh 노드는 별도 후보로 유지한다.

### 5.3 AP 위치 추정

- 강한 RSSI 상위 관측을 우선 사용한다.
- dBm을 선형 power weight로 변환해 normalized weighted centroid를 계산한다.
- 관측 수, 공간 분산, peak 집중도와 cluster confidence를 조합해 위치 confidence를 계산한다.
- 추정 AP는 Home에서 주황색과 신뢰도 백분율로 표시한다.
- 사용자가 확정한 AP 위치는 다음 자동 분석에서도 유지한다.

## 6. 결과 계산

- `Coverage`: 축척, AP 위치와 관측값으로 Strong/Good/Usable 예상 반경을 계산한다.
- `Heatmap`: 기본 IDW 보간과 벽 감쇠를 포함한 Hybrid 모델을 선택할 수 있다.
- `Weak Zone`: 사용자 임계값보다 낮은 영역만 표시하고 음영 비율을 계산한다.
- `Mesh`: 측정 위치별 실제 연결 BSSID와 가장 강한 매핑 BSSID를 비교해 로밍 확인 지점을 계산한다.
- Band 필터는 All, 2.4 GHz, 5 GHz, 6 GHz를 지원한다.
- 신호 소스는 장비별, 전체 Mesh, 실제 연결 AP를 지원한다.

## 7. 데이터 저장

- 앱 내부 `wifi_heatmap_project` 디렉터리에 `project.json`과 `floorplan.png`를 저장한다.
- Calibration, 측정점, 장비·Radio, 벽, 결과 필터와 Hybrid 설정을 저장한다.
- 자동 추정 AP의 위치 confidence, cluster confidence, 자동 추정 여부와 사용자 확정 상태를 저장한다.
- 앱 시작 시 저장 프로젝트를 자동 복원한다.
- 기존 추정 필드가 없는 프로젝트 JSON도 nullable 기본값으로 읽는다.

## 8. 주요 코드 구조

| 패키지 | 책임 |
|---|---|
| `ui/home` | 통합 결과 Home |
| `ui/settings` | 설정 허브 |
| `ui/survey` | 수동·자동 측정 UI |
| `tracking` | PDR provider, 좌표 변환, observation join |
| `analysis` | 집 Wi-Fi 필터, BSSID cluster, AP 위치 추정 |
| `coverage` | 신호 집계와 Coverage 계산 |
| `heatmap` | IDW 및 벽 반영 Hybrid heatmap |
| `mesh` | 최적 AP와 실제 연결 AP 비교 |
| `persistence` | 프로젝트 JSON과 평면도 저장·복원 |
| `wifi` | 권한, 연결 정보, ScanResult 수집 |

## 9. 검증

다음 명령으로 단위 테스트와 Debug APK 빌드를 검증했다.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

주요 단위 테스트 범위:

- 평면도 normalized 좌표 변환
- 거리 보정
- PDR step/heading 적분
- tracking과 Wi-Fi timestamp 결합 및 stale scan 가중치
- BSSID spatial cluster와 외부 SSID 제외
- RSSI weighted centroid AP 위치 추정
- IDW/Hybrid heatmap
- Coverage와 Mesh 분석
- 프로젝트 저장 호환성

## 10. 현재 제한 및 다음 단계

- Android Emulator에서는 실제 Wi-Fi radio, BSSID와 RSSI 이동 변화를 재현하기 어렵다.
- PDR은 장시간 측정 시 drift가 누적될 수 있으므로 실제 기기 반복 측정과 step length 튜닝이 필요하다.
- AP 위치는 weighted centroid 1차 추정이며 벽 교차 손실을 사용하는 grid optimizer는 미구현이다.
- AP 검토는 위치 수정과 확정을 지원하며 BSSID 병합·분리와 집 Wi-Fi 포함·제외는 후속 작업이다.
- 측정 세션 중간 저장과 앱 중단 후 재개가 필요하다.
- ARCore 지원 기기용 Visual-Inertial tracking provider는 후속 단계다.

세부 단계와 완료 기준은 `AUTOMATIC_SURVEY_UX_IMPLEMENTATION_PLAN.md`에서 관리한다.
