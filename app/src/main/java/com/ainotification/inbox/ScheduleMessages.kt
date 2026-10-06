package com.ainotification.inbox

/** Literal schedule evidence is useful even without agreement or one unambiguous start time. */
internal fun extractScheduleMessage(row:CapturedNotification):LifeEvent? {
 if(!messageLike(row) || row.isGroupSummary || row.packageName=="com.ainotification.inbox")return null
 val text=row.currentMessageText().trim()
 if(text.length !in 1..4000 || row.needsOriginalReview())return null
 if(Regex("광고|예시|예를 들|가정하면|만약|취소하려면|취소 방법|변경 방법").containsMatchIn(text))return null
 val noun="(?:미팅|회의|약속|일정|예약|스케줄|스케쥴|방문)"
 val day="""(?:(?:다음|이번)\s*주\s*)?[월화수목금토일]요일|(?:(?:20\d{2})년\s*)?(?:\d{1,2}월\s*)?\d{1,2}일|(?<!\d)(?:20\d{2}[-/])?\d{1,2}[-/]\d{1,2}(?![A-Za-z\d])|20\d{2}\.\d{1,2}\.\d{1,2}(?!\d)|오늘|내일|모레|(?:다음|이번)\s*주"""
 val time="""(?<!\d)(?:(?:오전|오후)\s*)?(?:[01]?\d|2[0-3])(?::[0-5]\d|시(?!간)(?:\s*(?:반|\d{1,2}분))?)(?:경)?"""
 val datePattern=Regex("(?:$day)(?:\\s*(?:$time))?|$time")
 // Ignore unrelated sentences; retain multiple offered slots rather than choosing one.
 val clauses=text.split(Regex("[\\n!?。]|(?<=[가-힣])\\.\\s*"))
 val relevant=clauses.mapIndexedNotNull{index,clause->index.takeIf{Regex(noun).containsMatchIn(clause) ||
  (datePattern.containsMatchIn(clause) && Regex("가능|괜찮|어떠|어때|될까요|뵙|만나|오세요").containsMatchIn(clause))}}
 if(relevant.isEmpty())return null
 val evidence=clauses.filterIndexed{index,clause->index in relevant || (
  relevant.any{k->kotlin.math.abs(k-index)<=2} &&
  (datePattern.matches(clause.trim().removeSuffix("이요").removeSuffix("요")) ||
   Regex("(?:오늘|내일|예약).*(?:안될|안\\s*되|어려)|(?:일정|예약).*(?:다시\\s*잡|다시\\s*정)").containsMatchIn(clause)))
 }.joinToString(" ")
 val hasSubject=Regex(noun).containsMatchIn(evidence)
 if(!hasSubject && Regex("배송|상품|구매|판매|영업|운영시간|쿠폰|포인트|혜택|소멸|유효기간|사용\\s*가능|이용\\s*가능|할인").containsMatchIn(text))return null
 // Negation and reported/hypothetical cancellation must never become an actual cancellation.
 if(Regex("취소.{0,8}(?:않|아니|없)|취소\\s*안|변경.{0,8}(?:않|없)|변경\\s*안|(?:취소|변경).{0,10}(?:다면|라고|다고)").containsMatchIn(evidence))return null
 val cancel=hasSubject && Regex("$noun.{0,60}취소|취소.{0,20}$noun").containsMatchIn(evidence)
 val reschedule=hasSubject && Regex("다시\\s*잡|다시\\s*정|다음에.{0,20}진행").containsMatchIn(evidence)
 val change=reschedule || hasSubject && Regex("$noun.{0,60}(?:변경|연기|미뤄)|(?:변경|연기).{0,20}$noun").containsMatchIn(evidence)
 val dates=datePattern.findAll(evidence).filter{m->
  // Do not present an explicitly unavailable slot as an offered date.
  !Regex("^(?:는|은|에)?\\s*(?:일정이\\s*)?(?:좀\\s*)?(?:안\\s*되|불가|어렵|어려|힘들)").containsMatchIn(evidence.substring(m.range.last+1).trimStart())
 }.map{it.value.trim()}.distinct().take(8).toList()
 val proposal=dates.isNotEmpty() && Regex("가능|괜찮|어떠|어때|될까요|뵙|만나|오세요|예약|예정|확정|진행|참석").containsMatchIn(evidence)
 if(!cancel && !change && !proposal)return null
 val requested=reschedule || Regex("해주세요|해\\s*주세요|부탁|해야|하려고|할까요|가능|원합니다|원해|바랍니다|요청|하고 싶").containsMatchIn(evidence)
 val finalCancel=Regex("취소(?:가)?\\s*(?:되었습니다|됐|되었|완료|합니다|했|하였습니다|하겠습니다|할게|합니다)|취소[ .]*$").containsMatchIn(evidence)
 val finalChange=Regex("(?:변경|연기)(?:가)?\\s*(?:되었습니다|됐|되었|완료|합니다|했|하였습니다)").containsMatchIn(evidence)
 if(cancel && !finalCancel && !requested)return null
 if(change && !finalChange && !requested)return null
 val status=when {
  cancel->if(finalCancel && !requested)"cancelled" else "cancellation_requested"
  change->if(finalChange && !requested)"changed" else "change_requested"
  else->"proposed"
 }
 val title=when(status){"cancelled"->"일정 취소";"cancellation_requested"->"일정 취소 요청";"changed"->"일정 변경";"change_requested"->"일정 변경 요청";else->"일정 제안·조율"}
 return LifeEvent("event:${row.snapshotId}","schedule",status,title,row.appLabel,
  dateText=dates.joinToString(" · ").ifEmpty{"일시 미정 · 원문 확인"},participant=notificationDisplayTitle(row))
}

/** Only fill absent events. No historical external AI calls, Now replay, or guessed linkage. */
internal suspend fun repairScheduleMessages(db:InboxDatabase,anchor:Long):Int {
 var after=Long.MIN_VALUE;var id="";var count=0
 while(true) {
  val rows=db.structured().missingSchedulePage(anchor,after,id)
  if(rows.isEmpty())return count
  for(row in rows) {
   // The page already contains only missing events. Parse first so ordinary chat does not
   // incur a separate database round trip for every notification in a long history.
   val life=extractScheduleInformation(row)
   if(life!=null && db.structured().event("event:${row.snapshotId}")==null) {
    persistLife(db,row,life,anchor);count++
   }
   after=row.capturedTime;id=row.snapshotId
  }
  kotlinx.coroutines.yield()
 }
}

internal fun extractScheduleInformation(row:CapturedNotification):LifeEvent? {
 val message=extractScheduleMessage(row)
 // Requests must never be turned into completed cancellations by the group notice parser.
 return message?.takeUnless{it.status=="proposed"} ?: extractGroupMeeting(row) ?: message
}
