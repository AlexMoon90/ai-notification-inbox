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
