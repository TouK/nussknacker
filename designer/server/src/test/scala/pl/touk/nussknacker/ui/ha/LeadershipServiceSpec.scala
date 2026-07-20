package pl.touk.nussknacker.ui.ha

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{TestKit, TestKitBase}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class LeadershipServiceSpec extends AnyFunSuite with TestKitBase with Matchers with BeforeAndAfterAll with Eventually {

  override implicit lazy val system: ActorSystem = ActorSystem(suiteName)

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(50, Millis)
  )

  private val heartbeatInterval = 100.millis

  private val config = HaMode.Enabled(
    instanceId = "test-instance",
    leaderLeaseDuration = 30.seconds,
    leaderHeartbeatInterval = heartbeatInterval,
    periodicLockDuration = 5.minutes,
    lockQueryTimeout = 5.seconds,
  )

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private def makeService(
      acquireOrRenewFn: () => Future[Boolean],
      initiallyLeader: Boolean = false
  ): LeadershipService = {
    val distributedLock = new DistributedLock {
      def acquireOrRenew(name: String, duration: FiniteDuration) = acquireOrRenewFn()
      def release(name: String)                                  = Future.unit
    }
    new LeadershipService(
      new LeaderLock(distributedLock, config.leaderLeaseDuration),
      config,
      system,
      initiallyLeader
    )
  }

  test("instanceId returns the configured value") {
    val service = makeService(() => Future.successful(true))
    service.instanceId shouldBe Some("test-instance")
  }

  test("isLeader becomes true after successful lock acquisition") {
    val service = makeService(() => Future.successful(true))
    eventually {
      service.isLeader() shouldBe true
    }
  }

  test("isLeader stays false when acquireOrRenew returns false") {
    val service = makeService(() => Future.successful(false))
    Thread.sleep(heartbeatInterval.toMillis * 3)
    service.isLeader() shouldBe false
  }

  test("isLeader drops to false when heartbeat starts returning false (lock stolen)") {
    @volatile var acquired = true
    val service            = makeService(() => Future.successful(acquired))
    eventually { service.isLeader() shouldBe true }
    acquired = false
    eventually { service.isLeader() shouldBe false }
  }

  test("isLeader drops to false when heartbeat fails with an exception (step-down on error)") {
    @volatile var shouldFail = false
    val service = makeService { () =>
      if (shouldFail) Future.failed(new RuntimeException("DB error"))
      else Future.successful(true)
    }
    eventually { service.isLeader() shouldBe true }
    shouldFail = true
    eventually { service.isLeader() shouldBe false }
  }

  test("onLeadershipAcquired fires immediately when already a leader at registration time") {
    val service         = makeService(() => Future.successful(true), initiallyLeader = true)
    @volatile var fired = false
    service.onLeadershipAcquired(() => fired = true)
    eventually { fired shouldBe true }
  }

  test("onLeadershipAcquired fires when node acquires leadership after starting as non-leader") {
    val service         = makeService(() => Future.successful(true))
    @volatile var fired = false
    service.onLeadershipAcquired(() => fired = true)
    eventually { fired shouldBe true }
  }

  test("onLeadershipAcquired fires again after re-acquiring leadership") {
    @volatile var acquired = true
    val service            = makeService(() => Future.successful(acquired))
    @volatile var count    = 0
    service.onLeadershipAcquired(() => count += 1)
    eventually { count shouldBe 1 }
    acquired = false
    eventually { service.isLeader() shouldBe false }
    acquired = true
    eventually { count shouldBe 2 }
  }

  test("onLeadershipAcquired fires exactly once when leadership is stable") {
    val service         = makeService(() => Future.successful(true), initiallyLeader = true)
    @volatile var count = 0
    service.onLeadershipAcquired(() => count += 1)
    eventually { count shouldBe 1 }
    Thread.sleep(heartbeatInterval.toMillis * 5)
    count shouldBe 1
  }

  test("onLeadershipAcquired fires exactly once when transitioning from non-leader to leader") {
    val service         = makeService(() => Future.successful(true))
    @volatile var count = 0
    service.onLeadershipAcquired(() => count += 1)
    eventually { count shouldBe 1 }
    Thread.sleep(heartbeatInterval.toMillis * 5)
    count shouldBe 1
  }

  test("slow onLeadershipAcquired callback does not block the heartbeat") {
    @volatile var heartbeatCount = 0
    val service = makeService { () =>
      heartbeatCount += 1
      Future.successful(true)
    }
    service.onLeadershipAcquired { () =>
      Thread.sleep(heartbeatInterval.toMillis * 10)
    }
    eventually { service.isLeader() shouldBe true }
    val countAtAcquisition = heartbeatCount
    // while the callback is still sleeping, heartbeats must keep firing
    Thread.sleep(heartbeatInterval.toMillis * 5)
    heartbeatCount should be > countAtAcquisition + 2
  }

  test("stop prevents further heartbeats") {
    @volatile var callCount = 0
    val service = makeService { () =>
      callCount += 1
      Future.successful(true)
    }
    eventually { service.isLeader() shouldBe true }
    service.stop()
    Thread.sleep(heartbeatInterval.toMillis * 3) // let any in-flight callback finish
    val countAfterStop = callCount
    Thread.sleep(heartbeatInterval.toMillis * 5) // verify no new calls are scheduled
    callCount shouldBe countAfterStop
  }

}
