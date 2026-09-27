package com.ainotification.inbox

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable internal fun RuleBookPanel(book: JSONObject, busy: Boolean, onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit, onEdit: (String) -> Unit) {
    val items = ruleObjects(book.getJSONArray("items"))
    var deleteId by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("현재 선별 기준", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("적용 ${items.count { it.getBoolean("enabled") }}개 · 꺼짐 ${items.count { !it.getBoolean("enabled") }}개", Modifier.testTag("rule_counts"))
        Text("체크한 항목만 새 알림에 적용합니다. 예외가 연결된 조건은 함께 묶었어요.", style = MaterialTheme.typography.bodySmall)
        Text("휴대폰 시스템 상태 알림은 항상 수집에서 제외됩니다.", style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (items.isEmpty()) Text("항목이 없습니다. AI에게 새 기준을 추가해 달라고 요청해 보세요.")
        items.forEachIndexed { index, item ->
            val id = item.getString("id")
            Surface(shape = MaterialTheme.shapes.medium, color = if (item.getBoolean("enabled")) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row {
                        Checkbox(item.getBoolean("enabled"), { onToggle(id, it) }, enabled = !busy, modifier = Modifier.testTag("rule_toggle_$index"))
                        Column(Modifier.weight(1f).padding(top = 8.dp)) {
                            Text(if (item.getBoolean("enabled")) "적용 중" else "꺼짐", style = MaterialTheme.typography.labelMedium)
                            Text(item.getString("text"), Modifier.testTag("rule_text_$index"), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onEdit("이 항목을 수정해줘: ${item.getString("text")}\n원하는 변경: ") }, enabled = !busy) { Text("AI로 수정") }
                        TextButton(onClick = { deleteId = id }, enabled = !busy, modifier = Modifier.testTag("rule_delete_$index")) { Text("삭제") }
                    }
                }
            }
        }
        Button(onClick = { onEdit("") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("edit_rules")) { Text("AI에게 추가·수정 요청") }
    }
    val target = items.find { it.getString("id") == deleteId }
    if (target != null) AlertDialog(modifier = Modifier.semantics { testTagsAsResourceId = true }, onDismissRequest = { deleteId = null },
        title = { Text("이 항목을 삭제할까요?") }, text = { Text(target.getString("text") + "\n\n삭제하면 현재 기준 목록에서 없어집니다. 잠시 사용하지 않을 때는 체크만 해제하세요.") },
        confirmButton = { TextButton(onClick = { deleteId = null; onDelete(target.getString("id")) }, Modifier.testTag("confirm_rule_delete")) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("취소") } })
}

@Composable internal fun RuleEditPanel(book: JSONObject, proposal: JSONObject?, busy: Boolean, initial: String,
    onRequest: (String, Boolean) -> Unit, onApply: () -> Unit, onDiscard: () -> Unit) {
    var request by rememberSaveable(initial) { mutableStateOf(initial) }
    var answer by rememberSaveable(proposal?.optJSONObject("result")?.optString("question")) { mutableStateOf("") }
    val result = proposal?.optJSONObject("result")
    val stale = proposal != null && proposal.optString("base_revision") != book.getString("revision")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("기준을 어떻게 바꿀까요?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("현재 항목과 적용 여부를 함께 보고 변경할 부분만 정리합니다. 수정안을 확인하기 전에는 현재 기준이 유지됩니다.")
        Text("요청과 현재 기준을 OpenAI에 전달합니다. 알림 원문은 포함하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(request, { if (it.length <= 2000) request = it }, modifier = Modifier.fillMaxWidth().testTag("rule_edit_input"),
            minLines = 3, label = { Text("추가·수정·체크 해제·삭제 요청") }, placeholder = { Text("예: 문자 광고 제외는 꺼두고, 일정 변경 알림 기준을 추가해줘") }, enabled = !busy)
        Button(onClick = { onRequest(request.trim(), false) }, enabled = request.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().testTag("request_rule_edit")) { Text("AI에게 요청") }
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("나갔다 와도 요청과 수정안은 남아 있습니다.") }
        if (stale) Text("현재 기준이 바뀌었습니다. 최신 기준으로 수정 요청을 다시 보내 주세요.", color = MaterialTheme.colorScheme.error)
        if (result != null) {
            Text(result.getString("message"))
            when (result.getString("status")) {
                "needs_input" -> {
                    Text(result.getString("question"), style = MaterialTheme.typography.titleMedium)
                    val options = result.getJSONArray("options")
                    for (i in 0 until options.length()) OutlinedButton(onClick = { onRequest(options.getString(i), true) }, enabled = !busy && !stale,
                        modifier = Modifier.fillMaxWidth().testTag("rule_edit_option_$i")) { Text(options.getString(i)) }
                    OutlinedTextField(answer, { if (it.length <= 2000) answer = it }, label = { Text("직접 답변") }, modifier = Modifier.fillMaxWidth(), enabled = !busy && !stale)
                    Button(onClick = { onRequest(answer.trim(), true) }, enabled = answer.isNotBlank() && !busy && !stale) { Text("답변하고 다시 정리") }
                }
                "ready" -> {
                    Text("변경 내용 확인", style = MaterialTheme.typography.titleLarge)
                    val before = ruleObjects(book.getJSONArray("items")).associateBy { it.getString("id") }
                    val after = ruleObjects(result.getJSONArray("items"))
                    after.forEach { item ->
                        val old = before[item.getString("id")]
                        val label = when { old == null -> "추가"; old.getString("text") != item.getString("text") -> "내용 수정"; old.getBoolean("enabled") != item.getBoolean("enabled") -> "적용 변경"; else -> "유지" }
                        Surface(shape = MaterialTheme.shapes.medium) { Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("$label · ${if (item.getBoolean("enabled")) "적용" else "꺼짐"}", style = MaterialTheme.typography.labelLarge)
                            if (old != null && old.getString("text") != item.getString("text")) Text("이전: ${old.getString("text")}", style = MaterialTheme.typography.bodySmall)
                            Text(item.getString("text"))
                        } }
                    }
                    val retained = after.map { it.getString("id") }.toSet()
                    before.filterKeys { it !in retained }.values.forEach { Text("삭제 예정: ${it.getString("text")}", color = MaterialTheme.colorScheme.error) }
                    if (after.isEmpty()) Text("모든 항목을 삭제하고 자동 선별을 중지합니다.")
                    Button(onClick = onApply, enabled = !busy && !stale, modifier = Modifier.fillMaxWidth().testTag("apply_rule_edit")) { Text("확인한 변경 적용") }
                }
            }
            TextButton(onClick = onDiscard, enabled = !busy) { Text("수정안 버리기") }
        }
    }
}
