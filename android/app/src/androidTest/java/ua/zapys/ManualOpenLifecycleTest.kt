package ua.zapys

import android.test.InstrumentationTestCase
import ua.zapys.data.SQLiteRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

/** Authored UNRUN: drives the actual callback gate without any composer launch.
 * Direct lifecycle-method fixture, not proof of real Home/rotation framework ordering.
 */
class ManualOpenLifecycleTest:InstrumentationTestCase() {
 private lateinit var activity:MainActivity
 fun testPausedDelayedOpenIsDroppedAndFreshResumedRequestCanRetry() {
  val context=instrumentation.targetContext
  val db="manual-open-lifecycle.db"; context.deleteDatabase(db)
  val repository=SQLiteRepository(context,db)
  val release=CountDownLatch(1); val started=CountDownLatch(1); val drained=CountDownLatch(1)
  val launches=AtomicInteger()
  try {
   instrumentation.runOnMainSync {
    activity=MainActivity()
    MainActivity::class.java.getDeclaredField("repository").apply { isAccessible=true }.set(activity,repository)
    // Set initial resumed state without onCreate or automatic scheduling side effects.
    MainActivity::class.java.getDeclaredField("resumed").apply { isAccessible=true }.setBoolean(activity,true)
    activity.loadForExternal({ started.countDown(); check(release.await(5,TimeUnit.SECONDS)); Unit }) { launches.incrementAndGet() }
   }
   assertTrue(started.await(5,TimeUnit.SECONDS))
   // Detached Activity: invoke only this override, without framework lifecycle dispatch.
   instrumentation.runOnMainSync { MainActivity::class.java.getDeclaredMethod("onPause").apply { isAccessible=true }.invoke(activity) }
   release.countDown()
   instrumentation.runOnMainSync { activity.load({ Unit }) { drained.countDown() } }
   assertTrue(drained.await(5,TimeUnit.SECONDS)); assertEquals(0,launches.get())
   val retried=CountDownLatch(1)
   instrumentation.runOnMainSync {
    MainActivity::class.java.getDeclaredField("resumed").apply { isAccessible=true }.setBoolean(activity,true)
    activity.loadForExternal({ Unit }) { launches.incrementAndGet(); retried.countDown() }
   }
   assertTrue(retried.await(5,TimeUnit.SECONDS)); assertEquals(1,launches.get())
  } finally {
   release.countDown()
   if(::activity.isInitialized) {
    val io=MainActivity::class.java.getDeclaredField("io").apply { isAccessible=true }.get(activity) as ExecutorService
    io.shutdown(); io.awaitTermination(5,TimeUnit.SECONDS)
   }
   repository.close(); context.deleteDatabase(db)
  }
 }
}
