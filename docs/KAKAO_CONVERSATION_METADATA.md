# 카카오톡 대화 식별·단톡방 메타데이터 실기기 조사

2026-09-21, Galaxy SM-S911N / Android 16(API 36) / 카카오톡 26.8.2.

## 실제 확인

NotificationListenerService가 접근할 수 있는 현재 카카오톡 알림 2개(요약 1개, 개별 대화 1개)를 기기 안에서 검사했다. 본문·방 이름·보낸 사람 이름·실제 식별값을 출력하지 않았다. 식별값은 기기 안에 보관하는 salt와 SHA-256으로 익명화하여 비교했다. 앱 내부 DB나 카카오톡 내부 데이터에는 접근하지 않았다.

|필드|개별 대화 알림에서 관찰|해석|
|---|---|---|
|shortcutId|있음|Android 대화 바로가기 식별값 후보|
|StatusBarNotification.tag|있음, shortcutId와 동일|이번 표본에서는 같은 대화 식별값을 사용|
|android.isGroupConversation|명시적으로 존재, true|알림 게시자가 단체 대화로 표시|
|MessagingStyle.isGroupConversation|true|위 플래그와 일치|
|MessagingStyle messages|2개, 서로 다른 sender 2개|현재 알림에 여러 발신자의 메시지 포함|
|Person.key|메시지 2개 모두 있음|발신자 식별용이며 방 ID와 구분해야 함|
|Ranking.isConversation|true|시스템도 대화 알림으로 분류|
|conversationTitle|없음|방 제목 유무만으로 단톡방을 추정하면 실패 가능|
|app group / system groupKey|요약 알림과 같은 값|알림 묶음 정보. 이 표본만으로 방 식별값이라고 볼 수 없음|
|channelId|요약과 같은 값|이번 표본에서 대화 전용 채널로 확인되지 않음|
|channel.conversationId / locusId|없음|이번 표본에서 제공되지 않음|

요약 알림은 MessagingStyle과 단톡방 플래그 및 shortcutId가 없다. **FLAG_GROUP_SUMMARY는 단톡방이라는 뜻이 아니다.** 요약은 여러 알림을 묶어 표시하는 역할이며 대화별 문맥에 섞지 않아야 한다.

## 아직 확인하지 못한 것

- 다양한 개인방에서의 플래그 일관성 (아래 개인방 1개에서 false 확인)
- 다수 대화방에서의 식별값 구분 (아래 개인방 1개와 단톡방 1개는 서로 다름 확인)
- 장기간 여러 차례 수신에서 ID 지속성 (아래 2026-09-22의 단일 업데이트는 확인)
- 알림 제거 후 재수신, 방 이름 변경, 앱/기기 재시작 이후 ID 유지
- 오픈채팅, 비밀채팅, 내용 숨김 설정, 다른 카카오톡/Android 버전의 차이

따라서 **대화별 식별에 쓸 수 있는 필드가 실제로 있음을 확인했지만, 영구적/전역적으로 고유한 카카오톡 내부 방 ID를 확보했다고 주장하지 않는다.** Android shortcut은 앱 범위 식별자다. 향후 키를 만들면 package + 사용자/프로필 범위 + shortcutId를 고려해야 한다.

## 구현에 대한 결론

현재 Room 정규화는 notificationKey, notificationId, groupKey, channelId, conversationTitle, 메시지 목록을 저장한다. shortcutId, tag, 명시적 그룹 플래그, Person.key는 아직 저장하지 않는다. 이번에는 관찰용 검사만 추가했고 DB 구조나 문맥 합치기 동작을 바꾸지 않았다.

향후 수집 확장 시 단톡방 여부는 true/false/unknown으로 구분한다. 플래그가 없는 알림을 개인방으로 단정하지 않는다. 가능하면 shortcutId를 우선 후보로 검증하고, groupKey나 제목만으로 다른 방의 문맥을 합치지 않는다. 불확실한 경우 대화 분리를 유지한다.

## 검증과 재현

빌드 및 Android 테스트 APK 생성 성공. 기존 단위 검사 9개 통과, lint 오류 0. Galaxy 메타데이터 instrumentation 1개 통과. 검사는 debuggable 앱의 연결된 리스너만 참조하며 해제/종료 시 참조를 지운다. 테스트는 알림을 지우거나 카카오톡 메시지를 보내지 않는다.

```sh
adb shell am instrument -w -r \
  -e class com.ainotification.inbox.KakaoMetadataTest \
  com.ainotification.inbox.test/androidx.test.runner.AndroidJUnitRunner
```

[개인정보 없는 관찰 기록](../evaluations/android/kakao-metadata-2026-09-21.json)

후속 비교: 카카오톡 화면을 닫은 상태에서 개인방 2개 메시지 및 기존 단톡방 1개 메시지를 수신하고 알림을 열거나 삭제하지 않은 상태에서 재실행한다. 본문 없이 플래그·익명화 식별값만 비교한다.

공식 근거: [MessagingStyle 그룹 대화 플래그](https://developer.android.com/reference/android/app/Notification.MessagingStyle#isGroupConversation()), [대화용 shortcut](https://developer.android.com/develop/ui/views/notifications/conversations), [알림 묶음과 요약](https://developer.android.com/develop/ui/views/notifications/group). 공식 규격의 존재와 카카오톡 구현의 실기기 관찰은 구분한다.

## 후속 수신 비교 — 2026-09-22

사용자가 단톡방 메시지 1개가 새로 왔다고 알려준 뒤 재검사했다. 실기기 검사 1개 통과. 개별 대화 알림의 메시지 수가 **2→3개**로 증가했으며 shortcutId, tag, notificationKey의 익명화 값은 모두 이전과 동일했다. 명시적 isGroupConversation=true도 유지됐다. 표시된 발신자는 2명으로 동일하고 Person.key는 메시지 3개 모두 있었다.

따라서 **이번 단톡방의 새 메시지 업데이트에서 대화 식별값 유지**를 확인했다. 개인방 false, 다른 방 간 식별값 구분, 알림 삭제 후 재수신·재부팅·재설치 안정성은 아직 검증하지 않았다.

[후속 관찰 기록](../evaluations/android/kakao-metadata-2026-09-22.json)

## 1:1 수신 비교 — 2026-09-22

사용자가 1:1 메시지 수신을 알려준 후 재검사했다. 활성 알림 3개(요약, 개인방, 기존 단톡방). 실기기 검사 1개 통과.

|항목|새 개인방|기존 단톡방|
|---|---|---|
|명시적 isGroupConversation|false|true|
|MessagingStyle 그룹 여부|false|true|
|shortcutId / tag|존재, 서로 동일|존재, 서로 동일|
|두 방의 shortcutId 비교|서로 다름|서로 다름|
|notificationKey|서로 다름|서로 다름|
|notificationId|두 방이 같은 값|두 방이 같은 값|
|groupKey / channelId|두 방이 같은 값|두 방이 같은 값|
|현재 알림 메시지 수|1|3|

**이번 표본에서는 개인방/단톡방을 명시적인 플래그로 구분할 수 있고, shortcutId 또는 tag로 두 방을 구분할 수 있었다. notificationId 단독, groupKey, channelId는 두 방이 같으므로 방 구분에 사용할 수 없다.** 기존 단톡방 shortcutId는 이전 검사와 동일했다. 개인방의 반복 수신 안정성과 재부팅 등은 미검증이다.

[개인방 비교 관찰 기록](../evaluations/android/kakao-metadata-2026-09-22-private.json)
