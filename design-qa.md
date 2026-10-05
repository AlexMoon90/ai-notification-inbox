# 스마트함 대시보드 디자인 검증

- source visual truth: `/Users/alexmoon/Downloads/스마트함 대시보드 UX 스펙보드.png` (1448 × 1086).
- implementation: Galaxy S23 / Android native Compose, 1080 × 2340 pixels. 합성 기록 사용.
- viewport: 기기 스크린, 시스템 상태/탐색 바를 제외한 대시보드 영역. Web/CSS viewport는 해당 없음.
- normalization: 디자인 보드에서 앱 내용 `(343,91)-(813,954)`을 잘라 폭 393으로 정규화. 기기 화면은 시스템 바 제외 `(0,98)-(1080,2196)`을 같은 폭으로 정규화. 소스는 선택 카테고리와 자체 하단 탭을 포함하고 구현 테스트는 대시보드 컴포넌트만 표시하므로 상단/돈/주요 카테고리의 계층과 밀도를 비교한다. 실제 앱은 기존 Now/메시지/스마트함 하단 탐색을 유지한다.

## 비교 1

- implementation screenshot: `/private/tmp/smart-life-qa/main-v2.png`
- full-view side-by-side: `/private/tmp/smart-life-qa/comparison-v1.png`
- [P1] 돈 카드가 첫 화면을 지나치게 차지하여 쇼핑·일정이 보이지 않음. 기존 전역 Typography의 27sp 줄높이가 작은 본문에도 상속됨. 스마트함 전용 typography, 압축된 거래 행, 메인의 날짜 중복 제거로 수정.
- [P2] 링크가 기존 앱의 붉은 강조색, 필터가 각진 회색 형태로 표시됨. 스마트함의 파란색/흰색/둥근 토큰을 별도 MaterialTheme로 지정.
- [P2] 실제 Hub 상단 제목과 대시보드 제목이 중복될 수 있음. Hub의 중복 제목을 제거하고 설정 버튼은 대시보드 헤더로 연결.

## 필수 표면

- Fonts/typography: 한국어 시스템 글꼴, 큰 제목/금액과 작은 출처의 위계. 전역 본문 줄높이 상속 문제 수정 후 재검증 필요.
- Spacing/layout: 돈 큰 카드, 4개 바로가기, 최근 3개, 두 열 카테고리. 수정 후 첫 화면 노출 확인 필요.
- Colors/tokens: 네이비 본문, 파란 주요색, 연한 파란 돈 카드, 초록 입금, 작은 새/변경 배지.
- Image/assets: 데이터에 없는 스타벅스 로고, 날씨, 금융 그래프는 생성하지 않음. 기존 Material 벡터 아이콘 사용. 참조 보드의 외부 도시/장식은 앱 범위 밖.
- Copy/content: 합성 기관/계좌/금액 사용. 실제 계좌 일부·기관은 원문 근거로 표시. 투자·건강 카드와 비교 퍼센트는 근거 없을 때 숨김.

## 비교 2

- implementation screenshot: `/private/tmp/smart-life-qa/main-v3.png`
- full-view side-by-side: `/private/tmp/smart-life-qa/comparison-v2.png`
- 색상/본문 줄높이 문제 해결. 돈 네 메뉴, 카드 필터, 원문 토글 실기기 테스트 통과.
- [P2] 쇼핑·일정 카드는 보이지만 최신 정보 부분이 화면 아래에 남음. 메인 거래를 지시서 허용 범위인 2개로 줄이고 지출 금액을 돈 제목과 묶어 개선. 최근 거래의 더보기 행 높이를 줄임. 최종 캡처에서 재확인 예정.

## 최종 비교

- implementation screenshot: `/private/tmp/smart-life-qa/main-final.png`
- full-view comparison: `/private/tmp/smart-life-qa/comparison-final.png`
- focused money comparison: `/private/tmp/smart-life-qa/money-focus-final.png`
- 상태: 합성 결제/입금/청구, 배송, 변경 일정. 소스의 3개 최근 거래 대신 지시서 1~3개 범위 안에서 2개를 표시해 계좌 정보를 유지하면서 아래 카테고리를 노출한다.
- 첫 비교의 P1/P2 해결: 글자 줄높이와 색상, 돈 영역 높이, 아래 주요 카테고리의 내용 노출. 마지막 비교에서는 쇼핑·일정 카드의 제목/상태/새·변경 수/최신 항목이 모두 보임. 실제 앱 하단 탐색용 56dp를 고려해도 카테고리 내용이 들어가는 배치.
- Fonts: 제목/금액/출처의 위계 및 한글 줄바꿈 확인. 본문은 시스템 글꼴을 사용하며 참고 보드의 정확한 폰트 복제는 범위 밖.
- Spacing: 연한 돈 카드 안의 4개 바로가기, 2개 거래, 아래 두 열 카드의 정렬 확인. 큰 글꼴 설정에서는 세로 스크롤로 접근 가능하며 별도 최대 글꼴 검증은 하지 않음.
- Colors: 파란 링크/강조, 둥근 필터, 네이비 본문, 초록 입금, 작은 새/변경 배지 확인. 기본 Material의 연보라 선택 필터는 보조색으로 허용.
- Assets: 고해상도 Material 라이브러리 벡터 사용. 소스에만 존재하는 특정 가맹점 로고/날씨/장식 그래프를 근거 없는 실제 데이터처럼 옮기지 않음. 일반 계좌 아이콘을 쓴 것은 기존 아이콘 라이브러리 범위의 차이로 허용; 추후 전용 금융 아이콘은 P3 개선 가능.
- Copy: 합성 값, 기관·부분 계좌, 원문 숨김, 처리 전/완료 구분 확인. 선택 카테고리를 데이터 없이 보여주지 않는 차이는 의도된 동작.

## 검증 결과

- 전체 단위 테스트 210개 통과. 마지막 변경은 메인 돈 카드 배치/표시 개수이며 APK 빌드와 실기기 화면 테스트를 다시 수행함.
- `SmartLifeDashboardDeviceTest` 최종 실기기 통과: 계좌/거래/납부/통계, 카드 필터, 원본 토글, 투자·건강 비노출.
- `SmartLifeBackfillDeviceTest` 통과: 기존 기록 로컬 보완, 외부 재분석 없음.
- 최초 실기기 UI 실행은 잠금 화면에 막혔고, 이후 합성 화면만 `setShowWhenLocked`로 표시하여 검증함. 실제 데이터 화면의 시각 검증과 신규 실시간 알림 도착부터 표시까지의 전체 과정은 이번 QA에 포함하지 않음.
- 네이티브 앱이므로 웹 콘솔 검사는 해당 없음. 기기 DB 마이그레이션 및 instrumentation은 성공.
- 접근성 최대 글꼴/태블릿/다른 Android 버전은 후속 검증 범위.

final result: passed

## 잠금 해제 후 실제 데이터 확인

사용자가 기기 잠금을 해제한 뒤 일반 앱에서 스마트함 → 계좌 입출금 → 배송 → 일정 메뉴를 직접 열어 확인했다. 실제 앱은 FLAG_SECURE를 사용하므로 화면 캡처는 검게 나오며, 보안 설정을 변경하지 않고 UiAutomator 화면 요소/좌표와 클릭으로 점검했다. 실제 화면 픽셀 비교를 했다는 의미는 아니다.

- 일반 앱의 제목은 한 번만 표시된다.
- 실제 데이터의 돈 카드 아래 배송·일정 카드 전체 영역이 하단 탐색 위에 위치한다.
- 계좌 입출금 화면에 새마을금고의 서로 다른 두 부분 계좌번호 라벨이 표시된다.
- 배송 상세에서 구조화 기록 6개와 도착 완료 상태를 확인했다.
- 일정 상세에서 기록과 `일정 관련 · 확인 필요` 상태, 오늘/예정/변경 필터를 확인했다.
- 확인 후 스마트함 메인으로 돌아왔다. 상태 변경이나 원문 재분석은 하지 않았다.
- 실제 원문/금액/이름/계좌번호가 포함될 수 있는 UI 덤프는 로컬 임시 경로에만 저장했고 저장소에는 추가하지 않았다.

## 사용자 후속 요청: 스와이프 탭·압축·자동 확인·상세 원문

이번 비교 기준은 이전 설치 화면과 사용자의 구체적인 후속 지시다. 원문 토글과 수동 읽음은 이전 스펙에서 변경된 요구사항이다.

- 변경 전: `/private/tmp/smart-life-qa/main-final.png` (1080 × 2340).
- 변경 후: `/private/tmp/smart-ux-qa/main.png`, `transactions.png`, `detail.png` (각 1080 × 2340, 합성 데이터).
- 비교: `/private/tmp/smart-ux-qa/comparison.png` (두 기기 화면을 같은 360 × 780으로 축소).
- 돈 카드 높이: 첫 시도 754px는 목표보다 커서 중복 최근 거래/더보기 행을 제거했다. 최종 실제 데이터 화면에서 1012px → 658px, 약 65%를 확인했다.
- Typography/layout: 상대방·금액·기관/부분 계좌를 유지하고 헤더를 가로 배치했다. 최근 거래 2개 유지. 4개 바로가기 터치 영역은 최소 48dp로 유지된다.
- Colors/navigation: 상위 메뉴는 흰 배경의 파란 밑줄 탭, 기간·종류 선택은 둥근 필터로 구분. HorizontalPager 스와이프와 탭 선택 동기화 확인.
- Assets: 기존 Material 벡터 아이콘 유지, 바로가기의 장식 아이콘은 공간 절약을 위해 제거.
- Content: 돈/배송 상세의 원문 자동 표시, 관련 증거 내용 표시, 원래 앱 열기 동작 확인. 수동 확인/원문 펼치기 버튼 제거.
- 실기기 회귀 테스트: 돈 카드 목표 높이, 계좌→거래 스와이프, 네 메뉴, 필터, 목록 자동 읽음, 상세 원문 자동 표시, 원래 앱 열기 콜백, 배송 목록 읽음, 새 변경 배지 복원, 읽은 업데이트 행 유지 통과.
- 실제 데이터 목록: 표시된 4개 계좌 기록에 대응해 읽음 키 4개가 저장되고, 화면의 미확인 배지가 0개가 됨을 확인했다. 금융 값이나 원문은 검증 문서/저장소에 포함하지 않았다.
- 실제 앱의 FLAG_SECURE는 유지했다. 실제 데이터 레이아웃/읽음 검증은 UI 계층과 로컬 상태로 했으며 픽셀 검증은 합성 화면에서 수행했다.

후속 UX 검증 final result: passed

## 2026-10-05 Now 묶음 알림 본문 회귀 수정

- 원인: 카카오페이 개별 알림의 제목/본문은 저장됐지만, 직후 도착한 빈 Android group summary가 같은 앱의 최신 Now 카드로 선택됨.
- 수정: Now 표시 대상에서 group summary를 제외하고 개별 알림의 기존 정책 판정을 유지. 숨김 처리된 개별 알림을 대신 노출하지 않음. 저장 원문/사진 캡션 처리 변경 없음.
- 검증: 빈 summary가 뒤늦게 도착하는 회귀 테스트 포함 단위 테스트 211개 통과, debug 빌드 성공.
- Galaxy S23에 데이터 유지 업데이트 설치. 실제 Now UI에서 최근 카카오페이 제목과 본문이 저장 값과 일치하고 본문 없음 표시가 사라졌음을 로컬 비교로 확인. 재수집/외부 AI 재분류 없이 복구.
- 개인 알림 원문과 기기 DB/UI 덤프는 저장소에 포함하지 않음.

## 2026-10-05 체크카드 연결 확인

- 같은 금액·2분 이내·양방향 유일 후보인 삼성 월렛 결제/출금에만 사용자 확인 질문 제공. 명시적 신용카드와 복수 후보 제외.
- 체크카드 확인 전에는 결제수단을 바꾸지 않음. 확인 후 결제수단별 집계에 포함하고 연결 출금 및 양쪽 원문 유지. 다른 거래 선택/상세에서 되돌리기 제공.
- 단위 테스트 214개 통과: 모호한 후보, 시간/금액 불일치, 신용카드 제외, 미확인/거절 무변경, 확인 후 단일 지출 집계, 금액 변경 시 확인 결과 재사용 방지.
- Galaxy S23 합성 UI 테스트 통과: 목록 질문 → 체크카드 선택 → 상세 결제수단 → 다시 확인 → 다른 거래 선택.
- 데이터 유지 설치 후 실제 돈 거래 내역에서 질문 및 두 선택 버튼 표시 확인. 실제 거래의 답변은 대신 선택하지 않았음.
- 실제 데이터/개인 금융 원문은 저장소에 포함하지 않음.

## 2026-10-05 체크카드 계좌 기준 기억

- 거래별 반복 질문을 월렛 패키지·기관·부분 계좌번호 조합의 최초 확인으로 변경. 동일 계좌의 기존/후속 거래에 동일 금액·2분 이내·양방향 유일 후보 조건을 매번 적용.
- 다른 계좌/기관, 식별정보 없음, 출금 없음, 복수 후보, 명시적 신용카드는 기억된 규칙으로 확정하지 않음. 거래별 거절은 규칙보다 우선.
- 기준 로컬 저장 및 상세에서 계좌 기준 해제 제공. 단위 테스트 216개 통과, 최종 debug 빌드 및 데이터 유지 설치 완료.
- 확장한 실기기 테스트는 미래시각 fixture를 실제 수신 범위로 보정. 재실행 중 문자 앱이 전면에 있어 첫 화면을 찾지 못해 완료하지 못함. 단위 테스트 결과와 구분하며 사용자 기기 사용을 방해하지 않도록 화면 테스트 대기.

## 2026-10-05 카드 승인 알림 안내

- 완료 출금만 있고 결제수단/동시 결제 정보가 없는 경우 돈 화면에 최초 1회 안내. 확인·닫기 이후 반복하지 않으며 안내 버튼으로 다시 열 수 있음. 긴 안내는 스크롤 가능.
- 카드 이용·결제 승인 문자/앱 알림의 신청·설정 및 기존 이용자의 권한 확인 안내. 상대방 이름/상호명으로 카드 결제를 추정하거나 거래를 변경하지 않음.
- 최근 30일 및 수신 후 2분 대기 조건, 연결 결제/명시적 체크카드/입금 제외 등을 포함해 단위 테스트 217개 통과. debug 빌드 성공, 데이터 유지 설치 성공.
- 사용자가 실기기 화면 테스트를 생략하도록 요청했으므로 이번 작업에서는 화면 조작/실기기 UI 테스트를 하지 않음. 이전 계좌 규칙 UI 테스트 재시도도 진행하지 않음.

## 2026-10-05 세 알림 결제 묶음

- 삼성 월렛·명시적인 체크카드 승인·출금을 동일 거래 근거로 묶는 읽기 투영 추가. 대표 결제 한 건과 원문 전체 연결, 계좌 흐름 별도 유지.
- 단위 테스트 225개 통과. 세 원문 유지/한 번 집계, 도착 순서, 두 알림 조합, 확인한 계좌 연결, 가맹점·금액·시간·카드 식별정보 불일치, 복수 계좌, 신용/체크 경쟁 승인, 사용자 거절 등을 검증.
- 최종 debug 빌드 및 기존 데이터 유지 설치 성공. 사용자의 요청에 따라 실기기 화면 테스트 생략. 실제 신규 체크카드 승인 알림 형식에 대한 기기 재현 검증은 수행하지 않음.
- 데이터는 삭제/재작성하지 않고 목록과 통계에서 대표 거래로 표시하므로 근거 부족한 후보는 분리 유지.

## 2026-10-06 메시지 AI 답변 도우미

- 대화방 하단 고정 답변/원본 열기, 최초 전송 안내, 일회성 팁, 하단 패널 톤/자연어 입력, 편집 가능한 초안, 복사/재작성/원본 열기, 설정 ON/OFF 구현.
- 메시지 수신/방 열기/톤 선택 시 호출 없음. 최대 10개·본문 6,000자·이미지 제외, 필요한 원문만 읽기. rewrite는 편집된 초안과 변경 요청만 사용.
- 단위 테스트 233개 통과: JSON 응답 검증, 요청 범위/연속 맥락/이미지 제외, 화면 미리보기의 원문 복원, 설정 지속, 명시적 실행, strict schema/store=false, 기존 분류 회귀 포함.
- 합성 대화 실제 OpenAI 호출 2건 통과: 지시 없는 일정 수락 금지 확인, 명시적 화요일 요청 반영. 실제 사용자 대화 전송 없음.
- Galaxy S23 네이티브 API 테스트 통과: 앱의 기존 연결/Structured Output으로 합성 답변 생성.
- Galaxy S23 합성 UI 테스트 최종 16.421초 통과: 최초 안내/무자동호출, 톤+자연어, 오류/동일 요청 재시도, 직접 수정/클립보드 일치, 수정된 초안만 재작성, 원본 열기 콜백, 동의 재표시 없음, 톤만 선택, 생성 중 방 전환 취소, OFF 시 버튼 숨김. 클립보드는 테스트 이전 값으로 복구.
- UI 테스트 보완: 별도 dialog/sheet의 테스트 표식 연결, 키보드 없는 입력에 불필요한 뒤로가기 제거, 재작성 패널 이동 후 클릭. 원본 앱 열기는 기존 콜백 연결을 검증했고 실제 카카오톡 대화 이동/발송은 수행하지 않았음.
- 기존 데이터 유지 설치, 테스트 종료 후 일반 앱 복귀. 임시저장/자동 입력/자동 발송 없음. API 원문/초안/키를 로그에 남기지 않음. 구현 범위는 docs/REPLY_ASSISTANT.md 참고.

## 2026-10-06 — Group meeting schedule recovery

Explicit group notices now bypass the contextual AI allowance, retain day-only/ambiguous-time evidence, and support existing meeting-related Now exceptions without overriding room rules. Repair queries only missing meeting-related group sources; 253 unit tests passed and the targeted device repair test passed (6.531s). Private before/after comparison confirmed seven added schedule records, zero lost originals, and a clean SQLite check. A repeated repair added zero records. Updated app installed and reopened. Historical Now alerts were not resent; actual new-message delivery was not simulated. See `docs/GROUP_MEETING_RECOVERY.md`.

## 2026-10-06 — Conversation source opening and shopping tabs

- Root cause of older meeting links: the opener only held snapshot-specific PendingIntents in a bounded process-local cache. It now also holds a bounded latest-token index keyed by source package and OS shortcut-derived conversation identity. It never substitutes a same-title room or reused Android notification key; canceled tokens are removed and older replays cannot replace newer room tokens. A failed explicit tap refreshes tokens from the connected listener's active notifications, without reclassifying or replaying them. When no usable token exists, the dialog explains the limitation and offers room-name copying and an explicit app launch. PendingIntents are not persisted or invented. Actual third-party conversation launch was not exercised against a private chat; synthetic tests cover routing/cancellation/isolation/reconnect behavior.
- Shopping now has Delivery / Refund / Ads tabs. Delivery includes completed shipments; returns share the refund tab. Explicitly marked shopping promotions are extracted locally and existing missing promotions are backfilled with paged queries, without external AI replay. Ads do not enter the main new/change count or shipment preview. Now policy behavior and original records are preserved.
- Validation: final suite 261 tests, zero failures/errors; APK builds passed. Synthetic physical-device shopping tab test passed in 4.682 seconds, covering in-progress and completed delivery, refund and ads separation, and switching back. Updated app installed and reopened. Private database verification found 44 shopping advertisement records, completed backfill marker, no lost original notification IDs, and a clean SQLite quick check. No real messages were sent. Private data and generated APKs remain outside Git.

## 2026-10-06 — NOWSET brand integration (device verification pending)

Source visual truth: `/Users/alexmoon/Downloads/NOWSET UI 디자인 시스템 보드.png` (1491 × 1055).
User-selected scope: integrate this branding in the existing native Android app; preserve the information architecture and behavior. SVG reconstruction is explicitly preferred in the attached user instructions, overriding the image-to-code skill's generic prohibition on hand-reconstructed vectors. No new web prototype, new onboarding flow, or whole-app dark redesign is in scope.

Asset comparison evidence:
- First combined comparison: `/private/tmp/nowset-logo-comparison.png`.
- Final combined comparison: `/private/tmp/nowset-logo-comparison-final.png` (940 × 530).
- Source brand region cropped to its logo area, rendered near 400px wide; implementation stacked SVG rendered at 1080 × 1032 then scaled to 420px wide to align the wordmark width. Transparent implementation is composited on Mist. These are asset comparisons, not native screen captures.
- Native implementation screenshot: unavailable. Connected phone disappeared before installation; `adb devices -l` returned no devices and no local AVD was available. `NowsetBrandDeviceTest` is ready to capture synthetic Now/messages/smart screens, the actual About entry/dialog, dark brand component, and platform-rendered launcher icon.

Comparison history:
- [P2, fixed] Initial slogan was too tightly set and too large relative to the wordmark. Added tracking to outlined slogan paths. N was too small relative to NOWSET; increased symbol scale to match the board's proportions. Initial vertical-leg gradients were too subdued; corrected their axes and stops. Reopened and compared the final combined asset image.
- [P3] Reconstructed symbol lacks the board's fine glossy highlight, and the existing Archivo-based outlined wordmark is not an official source logo master. Shapes, blue/navy direction, stacked composition and small-icon N recognition are preserved. Replace with official masters if supplied; no further speculative redesign.
- [Blocker] Native installation, viewport inspection and visual comparison remain unverified because the device is disconnected. Do not treat APK build success or vector previews as passing native UI QA.

Required fidelity surfaces:
- Typography: retain Archivo/system Korean fallback; outlined logo wordmark and tracked slogan; native large-font behavior awaits device.
- Spacing/layout: retain existing Modernist screen/menu structure; compact onboarding logo and About logo; Android 12+ uses OS icon/branding slots. No artificial splash hold.
- Colors/tokens: exact four requested brand colors in NowsetColors; readable muted/action derivatives for small text; semantic error/status colors retained.
- Image quality: SVG sources, high-resolution alpha PNG derivatives, opaque 512px icon export, adaptive foreground/background and monochrome, light/dark logos. No board pixels shipped.
- Copy/content: NOWSET label in app/service/settings, English slogan limited to brand surfaces, Korean brand line in onboarding/About. Package/application ID, DB and AI logic unchanged.

Validation: 261 unit tests passed, zero failures/errors; APK and androidTest APK builds passed. Compiled manifest label verified as NOWSET; package remains com.ainotification.inbox. Phone installation attempt failed with device not found. No device test result or screen fidelity pass is claimed.

Full requested implementation/asset/file report: `docs/NOWSET_BRAND_REPORT.txt`.

final result: blocked
