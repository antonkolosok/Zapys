package ua.zapys

import android.test.ActivityInstrumentationTestCase2
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

/** Real attached Activity and controlled lifecycle callbacks; never launches a composer. */
class ManualOpenLifecycleTest:ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private var activityIo:ExecutorService?=null

 private fun onMain(action:()->Unit) {
  var failure:Throwable?=null
  instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }
  failure?.let { throw it }
 }

 private fun drainIoAndMain(io:ExecutorService) {
  // Keep the generated no-argument Runnable helper outside JUnit3's test* method namespace.
  io.submit {}.get(5,TimeUnit.SECONDS)
  instrumentation.waitForIdleSync()
 }

 // Catches a delayed external callback escaping the pause gate, or a fresh resumed retry being lost.
 fun testPausedDelayedOpenIsDroppedAndFreshResumedRequestCanRetry() {
  val a=activity
  val io=MainActivity::class.java.getDeclaredField("io").apply { isAccessible=true }.get(a) as ExecutorService
  activityIo=io
  val release=CountDownLatch(1); val started=CountDownLatch(1); val drained=CountDownLatch(1)
  val launches=AtomicInteger()
  var paused=false
  try {
   // Drain startup reads/reconciliation before placing the controlled request on the real IO queue.
   drainIoAndMain(io)
   onMain {
    a.loadForExternal({ started.countDown(); check(release.await(5,TimeUnit.SECONDS)); Unit }) { launches.incrementAndGet() }
   }
   assertTrue("Delayed request must start before pausing",started.await(5,TimeUnit.SECONDS))
   onMain { instrumentation.callActivityOnPause(a); paused=true }
   release.countDown()
   onMain { a.load({ Unit }) { drained.countDown() } }
   assertTrue("Paused request and its callback must drain",drained.await(5,TimeUnit.SECONDS))
   assertEquals("A request completed after onPause must not launch",0,launches.get())
   val retried=CountDownLatch(1)
   onMain {
    instrumentation.callActivityOnResume(a); paused=false
    a.loadForExternal({ Unit }) { launches.incrementAndGet(); retried.countDown() }
   }
   assertTrue("A fresh request after onResume must complete",retried.await(5,TimeUnit.SECONDS))
   assertEquals("Only the fresh resumed request may launch",1,launches.get())
  } finally {
   release.countDown()
   try {
    drainIoAndMain(io)
   } finally {
    if(paused) onMain { instrumentation.callActivityOnResume(a) }
   }
  }
 }

 override fun tearDown() {
  try {
   // The real onDestroy queues repository.close and shuts down IO; do not shut it down beforehand.
   super.tearDown()
  } finally {
   activityIo?.let { assertTrue("Activity destruction must finish its IO cleanup",it.awaitTermination(5,TimeUnit.SECONDS)) }
  }
 }
}
