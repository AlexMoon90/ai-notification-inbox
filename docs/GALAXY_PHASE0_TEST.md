# Phase 0 — Galaxy 실기기 검증

## 준비

1. 설정 → 휴대전화 정보 → 소프트웨어 정보에서 모델명, Android 버전, One UI 버전을 기록합니다.
2. 개발자 옵션이 없다면 빌드번호를 7회 눌러 활성화합니다. 개발자 옵션 → USB 디버깅을 켭니다.
3. Mac과 USB 연결 후 Galaxy에서 이 컴퓨터의 디버깅을 허용합니다.
4. Mac에서 `adb devices`를 실행해 상태가 `device`인지 확인합니다. `unauthorized`이면 휴대전화의 허용 창을 확인합니다.
5. 프로젝트에서 `./gradlew :app:assembleDebug` 후 `adb install -r app/build/outputs/apk/debug/app-debug.apk`로 설치합니다.
6. 앱을 열고 수집 안내를 읽은 뒤 ‘설정에서 접근 허용’을 누릅니다. AI Notification Inbox를 선택해 허용하고 앱으로 돌아옵니다.
7. ‘알림 수집 연결됨’을 확인합니다. 메뉴가 다르면 Galaxy 설정 검색에서 ‘알림 접근’을 검색합니다.
8. 설치 출처에 따른 ‘제한된 설정’ 경고가 있으면 신뢰하는 이 테스트 APK에 한해서 앱 정보의 더보기 → 제한된 설정 허용을 확인합니다. One UI 버전에 따라 메뉴가 없거나 다를 수 있습니다.

## 앱별 수집

민감하지 않은 가짜 메시지로 테스트합니다. 대상 앱을 백그라운드에 두고 다른 계정/기기로 전송합니다. 먼저 시스템 알림창에 실제 알림이 생겼는지 확인합니다.

| 앱/상황 | 테스트 원문 | 확인 항목 | 결과 |
|---|---|---|---|
| KakaoTalk 개인방 | 내일 19시 강남에서 만나자 | 제목, 본문, 발신자, 카드 탭 시 해당 방 | 미검증 |
| KakaoTalk 단톡 | 토요일 7시 / 강남역 5번 출구 | 연속 메시지, conversationTitle, messages, 묶음 요약 | 미검증 |
| Gmail | 제목: 회의 변경 / 본문: 14시 → 15시 | title, text/bigText, 원래 메일 이동 | 미검증 |
| Samsung Messages 또는 Google Messages | 금요일까지 회비 30,000원 | 본문, MessagingStyle, 원래 대화 이동 | 미검증 |
| Slack 또는 Telegram | 자료 확인 후 답변 부탁드려요 | 채널/대화 제목, 본문, 원래 대화 이동 | 미검증 |
| WhatsApp 또는 별도 다섯 번째 앱 | 예약 시간은 내일 10시입니다 | 본문, 업데이트, 원래 앱 이동 | 미검증 |

KakaoTalk 개인방과 단톡은 **한 앱**입니다. SMS 앱도 실제 사용 가능한 앱을 기준으로 계산하며 최소 5개의 서로 다른 packageName에서 수집을 확인합니다. 앱마다 5~10건 이상 전송하고 누락 수, 동일 키 업데이트, 본문 잘림을 기록합니다. 알림에 원래 제공되지 않는 필드는 ‘제공되지 않음’으로 기록하며 생성해서 채우지 않습니다.

## 공통 시나리오

1. **빈 본문:** 내용 미리보기를 끈 대상 앱에서 알림을 보냅니다. 앱이 죽지 않고 본문 부재를 표시하는지 확인합니다.
2. **상세:** ‘수집 필드 자세히 보기’에서 packageName, appLabel, notificationKey, notificationId, postedTime, title, text, bigText, subText, conversationTitle, messages, groupKey, channelId, contentIntent 유무를 확인합니다.
3. **업데이트:** 같은 대화방에 연속 전송합니다. 새 내용이 이력으로 남고 동일 스냅샷이 반복 저장되지 않는지 확인합니다.
4. **원본 이동:** 현재 활성 알림의 최신 카드를 누릅니다. 올바른 앱/대화로 이동하는지 확인합니다. 이전 업데이트나 알림창에서 제거된 기록도 기존 이동 정보가 유효하면 열려야 합니다. 연결 정보가 없거나 취소되었다면 안내 창의 ‘앱 열기’로 원래 앱을 실행할 수 있어야 합니다. 앱 실행과 해당 대화로의 직접 이동을 구분해서 기록합니다.
5. **저장 유지:** 접근을 끈 다음 앱을 강제 종료 후 다시 엽니다. 이전 기록이 유지되는지 확인합니다. 접근을 껐는데 새 알림이 저장되는지 확인합니다(권한 해제 전 이미 수신한 처리 대기 항목은 저장될 수 있음).
6. **재연결:** 접근을 다시 켜고 새 알림을 보냅니다. 연결 상태와 새 수집을 확인합니다.
7. **기기 재시작:** 재시작 후 잠금을 해제하고 새 알림을 보냅니다. 연결/수집 여부를 기록합니다.
8. **백그라운드:** 화면을 끄고 10분 후 알림 전송, 30분 이상 대기 후 재전송합니다. 절전 모드에서도 반복합니다.
9. **삭제:** 접근을 끈 뒤 ‘저장 기록 전체 삭제’를 누르고 확인합니다. Inbox는 비고 원래 앱의 알림은 유지되는지 확인합니다.
10. **원래 알림 유지:** 모든 시나리오에서 이 앱 때문에 원래 알림이 숨겨지거나 삭제되지 않는지 확인합니다.
11. **개인정보:** 화면 캡처가 차단되는지 확인합니다. release 빌드에서 합성 테스트 본문이 앱 로그에 출력되지 않는지 확인합니다. 실제 개인정보가 들어간 logcat은 공유하지 않습니다.
12. **가독성:** 글자 크기를 크게 하고 화면을 회전하여 목록·상세·설정 버튼이 접근 가능한지 확인합니다.

## OEM 의존성 및 제한

- Galaxy의 절전/초절전, 절전 앱, 강제 종료, 관리 정책은 수집 지속성에 영향을 줄 수 있습니다. 실패하면 설정 → 배터리 → 백그라운드 사용 제한에서 상태를 기록하고, 필요 시 해당 앱의 배터리 제한을 조정해 재시험합니다. 최초부터 제한 해제를 필수 조건으로 삼지 않습니다.
- 알림 접근 권한이 있어도 앱 내부 DB나 과거 전체 대화는 읽을 수 없습니다. 앱이 알림 자체를 게시하지 않거나 본문을 숨기면 복구할 수 없습니다.
- Android 15 이상에서는 OTP 등 민감한 알림 내용이 리스너에 가려질 수 있습니다. 이를 우회하지 않습니다.
- 업무 프로필·보안 폴더·잠긴 비공개 공간은 정책/OS에 따라 접근할 수 없을 수 있습니다.
- contentIntent는 원래 앱이 만든 동작입니다. 앱/OS 버전에 따라 이동이 차단되거나 정확한 대화가 아닌 앱 첫 화면이 열릴 수 있습니다. send 성공만으로 실제 화면 이동을 검증했다고 간주하지 않습니다.
- 발신 앱이 패키지 조회에 노출되지 않으면 appLabel은 packageName으로 대체됩니다.
- 그룹 알림과 메시지 누적은 발신 앱 구현에 따라 다릅니다. Phase 0는 의미 중복 제거를 하지 않습니다.
- 프로세스 강제 종료 직전 메모리에 대기 중인 항목은 유실될 수 있고, 처리 대기열 256건 초과 시 수집 오류를 표시합니다. 원래 알림은 건드리지 않습니다.

## 완료 기준과 기록

모델 / Android / One UI / 테스트 날짜 / APK 버전:

각 앱의 시도 건수 / 수집 건수 / 빈 본문 건수 / 원본 이동 결과 / 제한:

**최소 5개 앱의 안정적 본문 수집, 저장 유지, 권한 해제·재연결, 원본 이동의 가능/불가 구분을 실기기로 검증한 뒤에만 Phase 0 완료로 판정합니다.** 실패한 항목과 재현 방법을 PHASE0_STATUS.md에 기록합니다.

## 공식 근거

- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Android 15 OTP 보호 및 프로필 제한](https://developer.android.com/about/versions/15/behavior-changes-all)
- [Activity 및 PendingIntent 실행 제한](https://developer.android.com/guide/components/activities/secure-bal)

## 기기 내 자동 검사

알림 접근을 허용한 Galaxy가 연결되고 잠금 해제된 상태에서 실행합니다.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am start -n com.ainotification.inbox/.MainActivity
adb shell "cmd notification post -t 'Phase 0 capture test' phase0-test 'Synthetic notification capture 001'"
adb shell am instrument -w com.ainotification.inbox.test/androidx.test.runner.AndroidJUnitRunner
```

이 검사는 기기 안에서 합성 알림 존재와 Inbox 연결 표시를 확인하고 전체/대상 앱별 건수만 반환합니다. DB 파일이나 실제 알림 내용은 내보내지 않습니다. 합성 알림은 최소 5개 실제 앱 검증에 포함하지 않습니다. 테스트 후 휴대전화 알림창에서 ‘Phase 0 capture test’를 직접 지울 수 있습니다.
