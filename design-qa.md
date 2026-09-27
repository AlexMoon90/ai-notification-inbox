# 알림 기준 관계도 QA

- Source visual truth: `/Users/alexmoon/Downloads/ChatGPT Image 2026년 9월 24일 오후 03_30_17.png` (1448 × 1086).
- Implementation: `evaluations/policy-relation-ui/native-preview.png` (393 × 852, mdpi Native Robolectric, 실제 PolicySettingsPanel), Galaxy 합성 정책 테스트 캡처도 비교.
- Comparison: `evaluations/policy-relation-ui/comparison.png`. 소스에서 앱 콘텐츠 (456,99)–(992,974)를 잘라 너비 393으로 정규화했다. 외부 설명 패널·iPhone 프레임·OS 상태바는 비교 대상에서 제외했다. Android에는 CSS viewport가 없다.
- State: 첫 공통 상황을 선택한 기준 화면. 소스의 여섯 앱/예시 지시사항과 구현의 합성 전체 앱 정책은 데이터가 다르다. 따라서 구조·강조·타이포그래피를 비교하며 내용이나 연결 수의 1:1 동일성을 주장하지 않는다.

## 비교 이력

1. 초기 렌더링에서 두꺼운 기본 슬라이더와 과도한 세로 간격으로 추가 설정이 아래로 밀렸다(P2). `before-polish.png`.
2. 얇은 트랙과 작은 손잡이, 노드·헤더 간격, 선택 강조를 조정했다. `comparison.png`에서 세 열·파란 강조·흐린 비선택 항목·분리된 추가 설정을 재확인했다.
3. Galaxy에서 실제 Compose 화면과 상세 진입을 확인했다. 첫 초기화 UI 자동 검사는 확인창 버튼을 찾지 못했다. 실패 로그를 보존하고 테스트용 화면의 시스템 영역 여백/스크롤 후 클릭을 보완했다. 안전한 클릭 영역을 검사하도록 테스트를 보완한 3차 실행에서 전체 UI 흐름이 통과했다(40.756초). 두 실패 로그도 보존한다.

## 필수 표면

- 폰트: 플랫폼 한글 글꼴, 제목 굵기와 보조 설명 계층. 작은 화면에서 긴 지시사항은 2줄 요약 후 상세 창에서 전체 제공.
- 간격: 세 열 비율과 좌우 조절, 둥근 카드, 별도 추가 설정. 고정 iPhone 비율 대신 Android 화면·글자 배율에 대응하며 긴 목록은 스크롤한다.
- 색: 흰 배경, 옅은 청회색 카드, 파란 선택 테두리·연결선. 비선택 항목을 흐리게 표시.
- 자산: 설치 앱의 실제 아이콘을 조회한다. 전체 앱 노드는 공통 알림 아이콘으로 표시한다. 예시 브랜드/AI Watch 규칙을 사용자 정책으로 만들지 않는다.
- 문구: 실제 정책의 이름·조건·행동과 적용/검토 상태. 초기화 범위 및 원본 알림 보존 안내를 포함한다.
- Focused evidence: 관계도와 추가 설정을 native-preview 및 Galaxy 캡처에서 직접 확인했다. 웹 콘솔 검사는 네이티브 앱에 해당하지 않는다.

## 잔여 확인

실기기 선택·상세·초기화 취소/확인·저장 복원이 통과했다. Galaxy 캡처는 1080×2340이며 galaxy-comparison.png에서 너비 393으로 정규화한다. 전체 앱 범위의 합성 정책이므로 다중 설치 앱 아이콘 표시와 폭 조절 제스처는 별도 실기기 자동 검증하지 않았다(P3 후속 확인). 새로운 광범위 AI 정확도 검증을 의미하지 않는다.

final result: passed


## 사용자 후속 수정: 텍스트만 표시 / 직접 드래그

이번 사용자 지시가 원래 시안의 카드 배경과 보조 제목보다 우선한다. 관계도 헤더/조건/앱 선택 배경·테두리 제거, 조건 한 줄 Clip, 언제·어디서·어떻게 제거를 적용했다. `evaluations/policy-relation-ui/text-only/native-preview.png`(393×852) 및 Galaxy `galaxy-overview.png`, `galaxy-dragged.png`(1080×2340)로 배경 없는 텍스트와 넓어진 왼쪽/잘린 오른쪽을 확인했다. 실기기 양방향 드래그 후 개별 지시사항의 X 좌표 변화 및 정책 불변 assertion이 통과했다. 기존 상세/초기화 흐름도 통과했다. 원래 시안과 다른 카드·문구 표현은 의도된 사용자 변경이며 회귀가 아니다.

final result: passed


## 사용자 후속 수정: 촘촘한 목록 / 터치한 연결만 전경 표시

사용자 최신 지시를 기준으로 기본 행 30dp·간격 0, 초기 선택 없음, 첫 터치 시 기존 화면을 배경으로 유지한 연결 레이어, 두 번째 터치 후 설정하기를 구현했다. `evaluations/policy-relation-ui/focus/native-preview.png`와 `galaxy-neutral.png`, `galaxy-focused.png`에서 기본/강조 상태를 확인했다. 첫 시각 확인에서 글자·아이콘과 선 사이 공백이 커 실제 경계를 측정하도록 수정했다. 최종 Galaxy 캡처에서 카카오톡 실제 아이콘까지 이어진 곡선을 확인했다. 실제 UI 자동 검사는 해당 아이콘과 두 단계 설정 진입·배경 복귀·기존 초기화까지 통과했다. 합성 알림의 앱 표시는 테스트용이며 실제 정책은 변경하지 않았다.

final result: passed


## 사용자 후속 수정: 앱별 전체 연결

`evaluations/policy-relation-ui/app-connections/galaxy-app-connections.png`를 직접 확인했다. 기존 화면을 어두운 배경으로 유지하고 카카오톡 실제 아이콘의 왼쪽에 공통 상황 8개, 오른쪽에 해당하는 개별 기준 4개를 한 줄 Clip 텍스트와 곡선으로 표시한다. 전체 앱 기준 포함 안내와 설정 진입 힌트가 보인다. 기본 화면 및 텍스트 강조 상태에서 앱 선택이 가능하며 설정 상세 진입을 Galaxy 자동 검사에서 통과했다. 합성 정책의 확인이며 실제 사용자 기준을 변경하지 않았다.

final result: passed


## 사용자 후속 수정: 같은 앱별 조건을 공통으로 묶기

`evaluations/policy-common-grouping/native-preview.png`와 `galaxy-kakao.png`를 직접 확인했다. 왼쪽 공통 광고 숨김 하나, 중앙 카카오톡/문자, 오른쪽 카카오 발신자 쇼핑 광고 예외 하나로 표시한다. 카카오톡 연결 레이어에서도 공통/개별이 양쪽으로 구분된다. 합성 정책이며 실제 사용자 데이터는 유지했다. 일반 overview 캡처는 FLAG_SECURE 전환 직후 검정 화면이어서 시각 근거로 사용하지 않았다. 정상 캡처된 연결 레이어와 UI assertions로 해당 화면을 확인했다.

final result: passed


## 사용자 후속 수정: 별도 검토 창·작은 앱 아이콘·앱 옆 정렬

`evaluations/policy-review-dialog/native-adjacent-rules.png`에서 24dp 앱 아이콘과 앱 구간 옆에 각각 붙은 두 개별 조건을 확인했다. 질문·답변·Diff는 본문에서 제거하고 전용 Dialog로 옮겼다. Galaxy USB 연결이 없어 실제 모달 화면과 기기 UI 동작 확인은 남아 있다. 이번 항목은 기기 검증 완료로 표시하지 않는다.

final result: pending device verification


## 기준 확인 창 Galaxy 검증 갱신

USB 재연결 뒤 `evaluations/policy-review-dialog/galaxy-adjacent.png`와 `galaxy-review.png`를 확인했다. 작은 앱 아이콘과 각 앱 옆의 개별 조건, 배경과 분리된 불투명 검토 창을 확인했다. 검토 본문은 내부 스크롤이고 최종 확인 버튼까지 스크롤하여 적용하는 자동 검사도 통과했다. 동일 기준이 공통으로 묶이는 테스트 데이터 오류를 수정한 뒤 모달 검사가 통과했다.

final result: passed


## 연결선 가독성 및 아이콘 옆 여백 축소

`evaluations/policy-relation-lines/`의 native-preview, galaxy-overview, galaxy-app, galaxy-focus 화면을 직접 확인했다. 기본 화면의 진한 파란색 곡선과 선택 화면의 밝은 선이 실제 카카오톡 아이콘 가장자리에 붙으며, 앱 열 여백이 줄었다. 한 줄 텍스트 clipping과 기존 선택·드래그 동작을 유지한다. Galaxy 격리 회귀 검사를 통과했으며 실제 사용자 정책은 변경하지 않았다.

final result: passed


## 왼쪽 문구 오른쪽 정렬·텍스트 연결점 간격 — 2026-09-24

왼쪽 공통 문구는 오른쪽 정렬하고 연결점 쪽에 6dp 안쪽 여백을 확보했다. 오른쪽 개별 문구도 연결점 쪽 6dp 여백으로 점과 글자의 겹침을 방지한다. 기본·앱 선택·텍스트 선택 화면 모두 적용했다. 아이콘 쪽 밀착은 유지한다. 대상 네이티브 렌더 1개·debug/test 빌드·lint, Galaxy 격리 관계도 회귀 1개 통과(56.851초). 세 기기 화면을 직접 확인했다. API 0회, 사용자 정책 보존, Phase 전체 상태 유지. 검증 자료: `evaluations/policy-relation-text-spacing/`.


## 사용자 결과 중심 확인 화면 — 2026-09-25

자연어 요청 후 내부 정리 메시지·검증 사유·미완성 JSON 설명·변경 없는 기준 목록을 화면에서 제거했다. 보충 질문은 질문과 선택지, 직접 답변/대화 선택 진입으로 구성한다. 확정 결과는 추가/변경/삭제와 대상·조건 → 처리만 보여주며 실제 예외·시간·꺼짐 상태는 의미가 달라지지 않도록 남긴다. 변경 시 이전 결과도 간단히 표시한다. 상세 원본 정책 화면 및 검증/확인 후 적용 절차는 유지한다.

단위 70개·빌드·lint 통과, 네이티브 결과 화면 렌더 및 시각 확인 통과. Galaxy 설치 완료. 기기 UI 자동 검사는 잠금 화면(com.android.systemui)에서 두 차례 막혔으며 사용자 잠금 해제를 요청했다. 질문→적용 실기기 검증은 아직 완료하지 않았다. 사용자 정책 유지, 전체 Phase 상태 유지. 결과: evaluations/simple-policy-results/result.json.
