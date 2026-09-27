# 최신 추가: 자동 알림 선별

2026-09-23 사용자 후속 요청에 따라 [자동 선별 테스트 경로](AUTOMATIC_SELECTION_TEST_APP.md)를 추가했다. 아래 기존 작업 기록의 자동 선별 미연결 설명은 당시 상태다.

# Android 디자인 및 기준 정리 통합 테스트

현재 방식: 사용자 추가 요청에 따라 개인 테스트 debug 앱에서 OpenAI를 직접 호출한다. PC·USB 중계 없이 인터넷만 필요하다. 기존 키를 debug APK에 포함하며 release에서는 제외한다. 아래 USB 설명은 이전 구현 기록이다.

사용자 요청(2026-09-23)에 따라 기존 네이티브 앱을 실제 테스트용 화면으로 확장했다. Phase 0 전체 통과 또는 Jev 신뢰성 통과를 선언하지 않는다.

## 구현 범위

- Material Compose 기반 밝은 파란색 테마, 알림 / 기준 / 설정 하단 탐색.
- 최신 알림 Timeline, 실제 수신 앱 필터, 긴 카카오톡 내용 확인 필터.
- 상세 화면 → 수신 본문 → 원래 앱. 유효한 PendingIntent가 없으면 기존 앱 열기 안내.
- 카카오톡 500자 이상 원본 확인 안내와 시스템 UI 제외 유지.
- 기준 입력 → OpenAI 질문 → 단일/복수/직접 입력 답변 → 재정리 → 검토 완료 저장.
- 작성 중 / 추가 확인 / 후보 대기 / 지원 범위 확인 / 검토 필요 / 검토 완료·미적용 상태.
- 초안 및 시도 중인 요청을 기기 전용 파일에 atomic 저장. 재진입·재시도 가능.
- 후보는 기본 미전송. 사용자가 포함/갱신할 때 전송할 최근 20개 알림의 앱·제목·본문 앞 200자를 표시. 묶음 요약 제외, 현재 notificationKey별 최근 스냅샷을 후보로 사용.
- 실제 대화 영구 ID가 아닌 **수신 알림 후보**다. 동일 대화의 영구 매핑·Watch 대상으로 사용할 수 없다.
- 현재 적용 중인 기준은 0개. 검토 완료는 실행 정책이나 활성화가 아니다.

## 이전 개발용 연결 — 현재는 아래 독립 실행 방식으로 대체

Android debug 앱 → adb reverse → PC loopback 서버 → 기존 `policy_setup_luna_v2` → OpenAI.

- 모델 `gpt-5.6-luna`, reasoning `low`, Responses, store=false. 기존 검증 코드 재사용.
- OpenAI 키는 기존 `.env.local`에서만 읽는다. APK 및 요청/응답 로그에 포함하지 않는다.
- 서버는 127.0.0.1:8766에만 바인딩. 시작마다 임의 인증 토큰을 기기에 전달한다. 브라우저 Origin 거부, 요청 크기 제한, 요청 본문 로그 없음.
- debug 빌드만 INTERNET 권한과 loopback HTTP 허용. release는 INTERNET 권한 없음.
- PC는 요청 처리 중 세션을 메모리로만 보유한다. 초안/답변/후보/호출 수·토큰·latency·추정 비용은 기기 앱 전용 파일에 저장한다.
- 재연결 또는 서버 재시작: 설치된 debug 앱을 USB 연결하고 아래 명령 실행.

```sh
python3 scripts/app_setup_bridge.py --provision-device
```

이는 개발용 전송 경로다. PC 또는 USB 연결이 끊기면 AI 정리는 실패 안내와 함께 초안을 보관한다. 이미 저장된 알림 확인은 계속 가능하다. 일반 배포용 계정 인증 서버·TLS 배포·사용량 제한은 아직 구현하지 않았다.

## 개인정보와 한계

기준 생성 시 사용자가 입력한 지시/답변은 OpenAI로 전송한다. 후보는 명시적으로 포함한 경우만 전송하며 기존 최소 마스킹은 완전한 개인정보 제거를 보장하지 않는다. 실기기 API 검증에는 합성 지시/후보만 사용한다. 기록 전체 삭제는 알림 DB를 지운다. 초안에 복사한 후보는 초안 삭제 시 제거한다. 자동 보관 기간은 미구현이다.

MainActivity의 FLAG_SECURE 유지. 디자인 캡처는 비공개 debug 전용 DesignPreviewActivity에서 합성 예시만 표시해 생성한다. preview Activity는 release에 포함하지 않는다.

미연결: Jev 정책 JSON 컴파일/실시간 분류, 활성 기준 편집·충돌 적용, Topic/Relationship 자동 분류, AI Watch 누적 문맥과 조건부 요약. 분류 오판 및 대화 식별 품질 게이트가 남아 있으며, 준비 중 기능을 실제 동작으로 가장하지 않는다.

## 검증 기록

검증 결과는 아래 후속 실행 기록에 갱신한다. 초기 UI 검사에서 상세 화면의 이전 스크롤 위치 유지와 자동화 버튼 식별 문제를 발견했다. 화면 경로별 스크롤 상태를 분리하고 테스트 식별자를 추가했다. 밝은 테마 상태 표시줄 대비도 수정했다.

### 최종 결과 — Galaxy S23 / Android 16

- Android 단위13개, 기존 Python 설정 회귀49개, 새 브리지4개 통과.
- 실기기5개 통과: 합성 Timeline·긴 내용 필터·상세·뒤로가기 / 실제 OpenAI 지시 입력·질문 선택·화면 재생성·검토 완료·파일 재로딩 / 시스템 소스 제외 / 수집 연결 / 연결 만료 시 명시적 카카오톡 앱 실행.
- 최종 API 통합 검사3회, 추정$0.00133688. 실제 개인 알림 후보는 보내지 않았다. 초기 UI 디버깅 API 호출 총량은 초안 삭제 이후 집계되지 않았으므로 이 값은 전체 작업 비용이 아니다. 이후 호출은 내용 없이 사용량만 `.local/setup-bridge-metrics.jsonl`에도 누적해 초안 삭제 후에도 보존한다.
- 초기 실패에는 스크롤 상태, 보안 화면 자동화 식별, 입력 포커스/키보드 처리와 단일 응답 완료 가정이 포함됐다. 코드/검사 수정 후 최종 사례를 통과했다. 중복 dex 생성 파일 오류는 clean 빌드로 해소했다.
- 최신 카카오톡 대화로의 직접 이동은 이번에 재검증하지 않았다. 만료된 연결의 안내 및 앱 열기는 실제 확인했다. 임의 OS 강제 종료 중 응답 복구·장시간 백그라운드·전체 앱별 수집은 미검증이다.
- [구조화 결과](../evaluations/android-app-integration/RESULT.json). [Timeline 캡처](design/timeline.png), [긴 알림 상세 캡처](design/detail.png)는 합성 데이터만 포함한다.

다음 단계는 이 테스트 앱에서 기준 정리 UX를 확인하면서, 남은 Jev 오판·실행 정책 검증을 통과한 뒤 실제 알림 자동 선별을 연결하는 것이다.

최종 정적 검사: lintDebug 통과. Java 17 JIT 런타임 충돌 후 설치된 Java 20으로 재실행했고, 개발용 네트워크 설정의 includeSubdomains=false 누락을 보완했다. release 병합 Manifest에 INTERNET 권한 및 DesignPreviewActivity가 없음을 확인했다. 최종 APK Galaxy 업데이트 설치 완료.

## 독립 실행 전환 — 사용자 API 키 포함 요청

사용자 요청에 따라 개인 테스트 debug APK에 기존 OpenAI 키를 포함한다. 빌드 시 `.env.local`에서 읽어 무시되는 `app/build/generated/standaloneDebugAssets/`에만 생성하며, 소스 코드와 로그에 키를 출력하지 않는다. Gradle debug source set만 이 자산을 패키징한다. 이 APK는 키를 추출할 수 있는 개인 테스트 산출물이므로 공유·배포 대상으로 취급하지 않는다. release APK에는 키·설정 자산이 없음을 파일 내용 검사로 확인했다.

휴대폰 → HTTPS Responses API로 직접 요청한다. PC 서버·adb reverse는 필요 없으며 cleartext HTTP 허용도 제거했다. `gpt-5.6-luna`, reasoning low, store=false, strict JSON Schema는 유지한다. 기존 Python Luna v2 프롬프트·스키마·capabilities를 빌드 시 가져오고, Kotlin에서 후보 ID/선택/상태 검증·명확한 원문 보존·답변 이후 최종 재검토·미적용 상태를 수행한다. 초안 저장 형식은 호환된다. 호출 수·입력/출력 토큰·시간·추정 비용은 세션과 기기 내 `setup-usage.jsonl`에 보관한다. API 오류 본문과 키는 로그에 남기지 않는다.

검증: Android 단위20개와 lintDebug 및 release 빌드 통과. 추가7개는 기존 Python 요청과의 의미상 JSON 일치, 명확한 원문 보존, 대화 선택·재검토·로컬 확인, 재검토 실패 시 ready 제거, 가짜 후보/스키마 거부, 사라진 후보 중단, 미완성 확인 금지를 검증한다. APK 키 포함 여부 검사는 [결과](../evaluations/android-app-integration/standalone-apk-check.json)에 기록했다. 최초 실기기 UI 검사는 휴대폰 잠금 화면 때문에 API 호출 전에 중단됐다. 직접 API 및 후속 실기기 결과는 아래에 기록한다.

API 형식 참고: [OpenAI Structured Outputs 공식 문서](https://developers.openai.com/api/docs/guides/structured-outputs). 구조화된 응답만으로 지시 의미의 정확도를 보장하지 않으므로 기존 검증과 최종 검토를 유지한다.

독립 실기기 최종 결과: PC 서버 종료·adb reverse 없음 상태에서2개 통과. 명확한 지시 직접 호출→원문 보존→저장/로컬 확인, 후보 없는 대기→합성 후보 도착→질문→명시적 선택→최종 재검토→디스크 재로딩을 확인했다. API 5회, 기록된 추정 비용 $0.00193904. 개인 알림을 보내지 않았다. 전체 화면 조작 재검사는 보안 잠금 때문에 API 호출 전 중단되어 별도로 미확인으로 남긴다. [구조화 결과](../evaluations/android-app-integration/STANDALONE_RESULT.json).
