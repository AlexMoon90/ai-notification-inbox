package com.ainotification.inbox

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Invoked explicitly for the user's requested historical replay; not part of routine device tests. */
class ReplayDeviceControlTest {
 @Test fun startRequestedReplay():Unit=runBlocking {
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("authorize_external_replay")=="yes")
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as InboxApplication
  app.structured.requestReplay()
 }
}
