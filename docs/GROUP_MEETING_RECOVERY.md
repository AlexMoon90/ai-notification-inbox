# Group meeting notices in schedule information

Group meeting announcements could remain in structured-processing retries after the daily contextual judgment allowance was exhausted. The contextual date candidate extractor also required an hour, so a day-only meeting mention could not pass the appointment date gate.

The structured pipeline now extracts explicit group meeting notices locally before contextual AI. It binds the date to the meeting clause and accepts attendance instructions on the immediately following line. Relative dates use the notification's posted date, not the date of reprocessing. Korean month/day and dotted month/day notation are supported. A bare future day number uses the current or next month. No AM/PM is invented for an unqualified hour, and an arrival deadline is not stored as the meeting start. Missing/ambiguous time and possibly truncated notices remain candidates with literal date/time information.

The local path intentionally does not decide personal attendance, process multiple meetings in one message, or promote casual availability questions. Those keep the contextual path. That path can now select day-only date candidates without weakening its evidence thresholds.

On upgrade, a paged, local-only repair inserts missing structured meeting records once. It preserves existing structured records, originals, seen state, user rules, and Now results. Retrying the repair inserts nothing again. The repair does not use the historical AI replay facility or change its budget.

For the existing exact CONTENT predicates for meeting-related information, a locally recognized group announcement is positive evidence. Rule scope, negation, time conditions, and exception priority still apply; a room hidden without a meeting exception stays hidden. This is not a global notification override. Historical notifications are not replayed into Now; its 48-hour retention remains in effect.

Verification uses synthetic unit tests for date-only and explicit-time notices, AM/PM ambiguity, arrival deadlines, adjacent attendance instructions, unrelated dates, multiple meetings, negative/quoted/casual statements, partial messages, exhausted AI budget, repair idempotence, and scoped Now exceptions. Device repair reports contain counts only. Private messages and database snapshots are not repository artifacts.

## Verified 2026-10-06

- Final JVM suite: 253 tests, zero failures/errors/skips; debug and instrumentation APK builds succeeded.
- Updated the connected device in place, preserving app data. Final targeted instrumentation: one test passed in 6.531 seconds, repeat repair added zero records, repair external AI calls zero.
- Private before/after DB comparison: seven previously missing group notice records now have schedule entries; all original notification IDs remain; SQLite quick check passed. Time ambiguity is retained as candidate state.
- Initial device verification exposed a slow full-history scan. That run was explicitly stopped, and the repair query was narrowed to unstructured group messages containing meeting/appointment terms (113 candidates instead of roughly 29,000 records). The final tests and device verification use that bounded query.
- No live message was sent and no historical notification was replayed into Now. Live arrival/sound behavior remains for the next real incoming notice; scoped rule selection was covered with synthetic unit tests.
