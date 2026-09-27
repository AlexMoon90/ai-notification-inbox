"""Luna low experiment: explicit candidate availability and observable identities."""
import json
import policy_setup_reliable as previous

ROOT, MODEL = previous.ROOT, 'gpt-5.6-luna'
base, call_openai, load_key = previous.base, previous.call_openai, previous.load_key
new_session, answer, confirm = previous.new_session, previous.answer, previous.confirm
refresh_candidates, save_session = previous.refresh_candidates, previous.save_session
SetupError = previous.SetupError
CONTRACT = '''
OBSERVABLE STATE CONTRACT (applies to questions as well as final drafts)
The application provides candidate_availability from its actual current candidate list.
For a supported request requiring an unidentified conversation:
- no candidates: waiting;
- candidates available, target not selected: needs_input with a conversation question using supplied IDs and exact labels;
- selected target: use that target, never broaden it to the app.
Waiting for a user to choose is needs_input, not waiting for notification evidence. Re-evaluate the current candidates on every call, even if an earlier call was waiting. Do not choose a room from its name or preview yourself.
Candidate presence alone does not require selection for an app-wide rule. Unsupported membership or hidden-state requirements remain unsupported even if candidates exist.
All sender identity conditions in this product refer to OBSERVED DISPLAY NAMES. Do not introduce real/legal/authenticated identity, impersonation or ownership verification into a question or policy. A conflict between allowing and excluding the same observed sender requires choosing allow or exclude for that same name; no extra identity distinction.
Ask dependencies in order: first let the user choose a missing preference; ask names or channels only if their chosen preference needs them. Do not ask hypothetical follow-up questions for options not selected.
'''


def adapt(payload):
    payload = dict(payload, model=MODEL, reasoning={'effort':'low'})
    payload.pop('temperature', None)
    context = json.loads(payload['input'][1]['content'])
    context['candidate_availability'] = {'count':len(context['candidates']),
                                        'selection_recorded':bool(context['selected_candidate_ids'])}
    payload['input'] = [{'role':'developer','content':payload['input'][0]['content']+'\n'+CONTRACT},
                        {'role':'user','content':base.mask(json.dumps(context,ensure_ascii=False))}]
    return payload


def step(session, key, transport=call_openai):
    before = len(session['metrics'])
    def send(payload, actual_key):
        return transport(adapt(payload), actual_key)
    try:
        return previous.step(session,key,send)
    finally:
        for metric in session['metrics'][before:]:
            metric.update(model=MODEL,reasoning_effort='low',prompt_version='luna-v2-observable-state')
            it,ot,cached=(metric[k] for k in ('input_tokens','output_tokens','cached_tokens'))
            metric['estimated_usd']=((it-cached)*.2+cached*.02+ot*1.2)/1e6 if all(type(v) is int for v in (it,ot,cached)) else None
