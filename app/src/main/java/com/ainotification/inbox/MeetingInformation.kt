package com.ainotification.inbox

import java.time.*

/** Only self-contained group meeting notices bypass contextual AI. Never infer attendance. */
internal fun extractGroupMeeting(row:CapturedNotification):LifeEvent? {
 if(!messageLike(row) || row.isGroupConversation!=true || row.isGroupSummary)return null
 val text=row.currentMessageText()
 if(text.length>4000 || Regex("광고|예시|예를 들어|(?:미팅|회의|약속).*(?:안\\s*합니다|없습니다|하지\\s*않습니다)").containsMatchIn(text))return null
 // Bind dates to the meeting sentence, never to another topic elsewhere in the message.
 val clauses=text.split(Regex("[\\n!?。]|(?<=[가-힣])\\.\\s*"))
 val meeting=clauses.mapIndexedNotNull { index,clause ->
  val notice=clause+" "+clauses.getOrNull(index+1).orEmpty().take(100)
  clause.takeIf { Regex("미팅|회의|약속").containsMatchIn(it) &&
   Regex("합니다|있습니다|예정|확정|참석|필참|뵙겠습니다|진행|개최|모입니다|변경|취소|오시면").containsMatchIn(notice) &&
   !Regex("할까요|어때|괜찮|가능|했|였|예정이었|라고|다고|한다면").containsMatchIn(it) }
 }
 if(meeting.size!=1)return null // Multiple events need contextual separation.
 val sentence=meeting.single().trim()
 val dates=Regex("(?<![0-9.])(?:(?:20\\d{2})년\\s*)?\\d{1,2}(?:월\\s*|[./])\\d{1,2}일|(?<![0-9.])\\d{1,2}일|오늘|내일|모레|(?:이번\\s*주|다음\\s*주)?\\s*[월화수목금토일]요일").findAll(sentence).toList()
 if(dates.size!=1)return null
 val date=dates.single()
 val anchor=Instant.ofEpochMilli(row.postedTime).atZone(ZoneId.systemDefault()).toLocalDate()
 val day=meetingDate(date.value,anchor) ?: return null
 val times=Regex("(?:(오전|오후)\\s*)?(?<![0-9])([0-2]?\\d)(?:[:.]([0-5]\\d)(?:분)?|시(?:\\s*([0-5]?\\d)분)?)").findAll(sentence.replaceRange(date.range," ".repeat(date.value.length))).toList()
 val time=times.singleOrNull()
 val hour=time?.groupValues?.get(2)?.toIntOrNull()
 val meridiem=time?.groupValues?.get(1).orEmpty()
 // 10시 can mean AM or PM. Preserve the literal instead of silently choosing.
 val arrival=time!=null && sentence.substring(time.range.last+1).trimStart().startsWith("까지")
 val unambiguous=!arrival && hour!=null && ((meridiem.isNotEmpty() && hour in 1..12) || (meridiem.isEmpty() && (time.value.contains(':') || hour in 13..23)))
 val at=if(unambiguous)runCatching {
  val h=if(meridiem=="오후")hour!!%12+12 else if(meridiem=="오전")hour!!%12 else hour!!
  day.atTime(h,time!!.groupValues[3].ifEmpty{time.groupValues[4]}.toIntOrNull()?:0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
 }.getOrNull() else null
 val status=when { sentence.contains("취소")->"cancelled";at==null || row.needsOriginalReview()->"candidate";sentence.contains("변경")->"changed";else->"confirmed" }
 val subject=Regex("[가-힣A-Za-z]{0,8}(?:미팅|회의|약속)").find(sentence)?.value ?: "미팅"
 val label="$day · ${time?.value ?: "시간 미정"}"+(if(arrival)"까지 도착 안내" else "")+(if(time!=null && at==null)" (시각 확인 필요)" else "")
 return LifeEvent("event:${row.snapshotId}","schedule",status,subject,row.appLabel,
  dateText=label,scheduledAt=at,participant=notificationDisplayTitle(row))
}

internal fun meetingDate(raw:String,anchor:LocalDate):LocalDate?=runCatching {
 val s=raw.replace(" ","")
 when(s){"오늘"->anchor;"내일"->anchor.plusDays(1);"모레"->anchor.plusDays(2);else->{
  val full=Regex("(?:(20\\d{2})년)?(\\d{1,2})(?:월|[./])(\\d{1,2})일").matchEntire(s)
  val day=Regex("(\\d{1,2})일").matchEntire(s)
  when {
   full!=null->LocalDate.of(full.groupValues[1].toIntOrNull()?:anchor.year,full.groupValues[2].toInt(),full.groupValues[3].toInt())
   day!=null->{val n=day.groupValues[1].toInt();(if(n<anchor.dayOfMonth)anchor.plusMonths(1) else anchor).withDayOfMonth(n)}
   else->{val weekday="월화수목금토일".indexOf(s[s.length-3])+1;require(weekday in 1..7)
    val monday=anchor.minusDays(anchor.dayOfWeek.value.toLong()-1)
    when{ s.startsWith("다음주")->monday.plusDays(7+weekday.toLong()-1);s.startsWith("이번주")->monday.plusDays(weekday.toLong()-1);else->anchor.plusDays((weekday-anchor.dayOfWeek.value+7L)%7) }
   }
  }
 }}
}.getOrNull()

/** Local, bounded, idempotent repair; never replays Now or spends AI budget. */
internal suspend fun repairGroupMeetings(db:InboxDatabase,anchor:Long):Int {
 var after=Long.MIN_VALUE;var id="";var count=0
 while(true){
  val rows=db.structured().missingMeetingPage(anchor,after,id);if(rows.isEmpty())return count
  for(row in rows){
   if(db.structured().event("event:${row.snapshotId}")==null)extractGroupMeeting(row)?.let{persistLife(db,row,it,anchor);count++}
   after=row.capturedTime;id=row.snapshotId
  }
  kotlinx.coroutines.yield()
 }
}
