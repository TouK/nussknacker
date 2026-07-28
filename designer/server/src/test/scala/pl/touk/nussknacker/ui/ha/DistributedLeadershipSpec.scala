package pl.touk.nussknacker.ui.ha

import cats.effect.{IO, Resource}
import cats.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntimeAdapter

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class DistributedLeadershipSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll with Eventually {

  private implicit val executionContextWithIORuntime: ExecutionContextWithIORuntimeAdapter =
    ExecutionContextWithIORuntimeAdapter.unsafeCreateFrom(ExecutionContext.global)

  import executionContextWithIORuntime.ioRuntime

  override def afterAll(): Unit = executionContextWithIORuntime.close()

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(50, Millis)
  )

  private val clock = Clock.systemUTC()

  private val heartbeatInterval = 100.millis

  private val config = HaMode.Enabled(
    instanceId = "test-instance",
    leader = HaMode.LeaderConfig(
      heartbeatInterval = heartbeatInterval,
      leaseDuration = 30.seconds,
      releaseOnStop = true,
    ),
    periodicLockDuration = 5.minutes,
    lockQueryTimeout = 5.seconds,
  )

  // Creates the service only — does NOT start the heartbeat.
  // Use this when you need to register callbacks before startHeartbeat() is called.
  private def makeService(
      acquireOrRenewFn: () => Future[Boolean],
      releaseFn: () => Future[Boolean] = () => Future.successful(true),
      releaseOnStop: Boolean = true,
      leaseDuration: FiniteDuration = config.leader.leaseDuration,
  ): Resource[IO, DistributedLeadership] = {
    val distributedLock = new DistributedLock {
      override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Boolean] = acquireOrRenewFn()
      override def release(name: String): Future[Boolean]                                  = releaseFn()
    }
    DistributedLeadership.resource(
      distributedLock,
      config.copy(leader = config.leader.copy(releaseOnStop = releaseOnStop, leaseDuration = leaseDuration)),
      clock,
    )
  }

  // beforeHeartbeat allows registering callbacks before startHeartbeat() fires the initial acquisition.
  private def withService(
      acquireOrRenewFn: () => Future[Boolean],
      releaseFn: () => Future[Boolean] = () => Future.successful(true),
      releaseOnStop: Boolean = true,
      beforeHeartbeat: DistributedLeadership => IO[Unit] = _ => IO.unit,
  )(body: DistributedLeadership => Unit): Unit =
    makeService(acquireOrRenewFn, releaseFn, releaseOnStop)
      .flatMap(service => Resource.eval(beforeHeartbeat(service)) >> service.startHeartbeat().as(service))
      .use(service => IO(body(service)))
      .unsafeRunSync()

  private def withAllocatedService(
      acquireOrRenewFn: () => Future[Boolean],
      releaseFn: () => Future[Boolean] = () => Future.successful(true),
      releaseOnStop: Boolean = true,
  )(body: (DistributedLeadership, IO[Unit]) => Unit): Unit =
    makeService(acquireOrRenewFn, releaseFn, releaseOnStop)
      .flatMap(service => service.startHeartbeat().as(service))
      .allocated
      .flatMap { case (service, release) => IO(body(service, release)).guarantee(release) }
      .unsafeRunSync()

  test("instanceId returns the configured value") {
    withService(acquireOrRenewFn = () => Future.successful(true)) { service =>
      service.instanceId shouldBe "test-instance"
    }
  }

  test("isLeader returns false when lock validity has expired") {
    // leaseDuration = 1ms so validUntil expires immediately; heartbeat sleeps 100ms so won't renew
    makeService(acquireOrRenewFn = () => Future.successful(true), leaseDuration = 1.milli)
      .flatMap(service => service.startHeartbeat().as(service))
      .use(service => IO.sleep(10.millis) >> IO(service.isLeader() shouldBe false))
      .unsafeRunSync()
  }

  test("isLeader becomes true after successful lock acquisition") {
    withService(acquireOrRenewFn = () => Future.successful(true)) { service =>
      eventually { service.isLeader() shouldBe true }
    }
  }

  test("isLeader stays false when acquireOrRenew returns false") {
    withService(acquireOrRenewFn = () => Future.successful(false)) { service =>
      Thread.sleep(heartbeatInterval.toMillis * 3)
      service.isLeader() shouldBe false
    }
  }

  test("isLeader drops to false when heartbeat starts returning false (lock stolen)") {
    @volatile var acquired = true
    withService(acquireOrRenewFn = () => Future.successful(acquired)) { service =>
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { service.isLeader() shouldBe false }
    }
  }

  test("isLeader drops to false when heartbeat fails with an exception (step-down on error)") {
    @volatile var shouldFail = false
    withService(acquireOrRenewFn = () => {
      if (shouldFail) Future.failed(new RuntimeException("DB error"))
      else Future.successful(true)
    }) { service =>
      eventually { service.isLeader() shouldBe true }
      shouldFail = true
      eventually { service.isLeader() shouldBe false }
    }
  }

  // Callbacks must be registered before startHeartbeat(). When startHeartbeat() runs the initial
  // acquireOrRenew and succeeds, it fires all registered callbacks at that point.
  test("onLeadershipAcquired fires when node acquires leadership at startHeartbeat time") {
    @volatile var fired = false
    withService(
      acquireOrRenewFn = () => Future.successful(true),
      beforeHeartbeat = _.onLeadershipAcquired(() => IO { fired = true }),
    ) { _ => eventually { fired shouldBe true } }
  }

  test("onLeadershipAcquired fires when node acquires leadership after starting as non-leader") {
    @volatile var fired     = false
    @volatile var firstCall = true
    withService(
      acquireOrRenewFn = () => {
        if (firstCall) { firstCall = false; Future.successful(false) }
        else Future.successful(true)
      },
      beforeHeartbeat = _.onLeadershipAcquired(() => IO { fired = true }),
    ) { _ => eventually { fired shouldBe true } }
  }

  test("onLeadershipAcquired fires again after re-acquiring leadership") {
    @volatile var acquired = true
    @volatile var count    = 0
    withService(
      acquireOrRenewFn = () => Future.successful(acquired),
      beforeHeartbeat = _.onLeadershipAcquired(() => IO { count += 1 }),
    ) { service =>
      eventually { count shouldBe 1 }
      acquired = false
      eventually { service.isLeader() shouldBe false }
      acquired = true
      eventually { count shouldBe 2 }
    }
  }

  test("onLeadershipAcquired fires exactly once when leadership is stable") {
    @volatile var count = 0
    withService(
      acquireOrRenewFn = () => Future.successful(true),
      beforeHeartbeat = _.onLeadershipAcquired(() => IO { count += 1 }),
    ) { _ =>
      eventually { count shouldBe 1 }
      Thread.sleep(heartbeatInterval.toMillis * 5)
      count shouldBe 1
    }
  }

  test("onLeadershipAcquired skips re-fire when previous callback IO is still in-progress") {
    @volatile var acquired = true
    @volatile var count    = 0
    val promise            = scala.concurrent.Promise[Unit]()
    withService(
      acquireOrRenewFn = () => Future.successful(acquired),
      beforeHeartbeat = _.onLeadershipAcquired { () =>
        count += 1
        IO.fromFuture(IO(promise.future))
      },
    ) { service =>
      eventually { count shouldBe 1 }
      acquired = false
      eventually { service.isLeader() shouldBe false }
      acquired = true
      Thread.sleep(heartbeatInterval.toMillis * 3)
      count shouldBe 1 // skipped — promise not yet completed
      promise.success(())
      Thread.sleep(heartbeatInterval.toMillis * 3)
      count shouldBe 1 // no auto re-fire; re-fire only happens on next false→true transition
    }
  }

  test("slow onLeadershipAcquired callback does not block the heartbeat") {
    @volatile var heartbeatCount = 0
    withService(
      acquireOrRenewFn = () => { heartbeatCount += 1; Future.successful(true) },
      beforeHeartbeat = _.onLeadershipAcquired(() => IO.blocking(Thread.sleep(heartbeatInterval.toMillis * 10))),
    ) { service =>
      eventually { service.isLeader() shouldBe true }
      val countAtAcquisition = heartbeatCount
      Thread.sleep(heartbeatInterval.toMillis * 5)
      heartbeatCount should be > countAtAcquisition + 2
    }
  }

  test("onLeadershipLost fires when lock is stolen") {
    @volatile var acquired = true
    withService(acquireOrRenewFn = () => Future.successful(acquired)) { service =>
      @volatile var fired = false
      service.onLeadershipLost(() => IO { fired = true }).unsafeRunSync()
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { fired shouldBe true }
    }
  }

  test("onLeadershipLost fires when heartbeat fails with an exception") {
    @volatile var shouldFail = false
    withService(acquireOrRenewFn = () => {
      if (shouldFail) Future.failed(new RuntimeException("DB error"))
      else Future.successful(true)
    }) { service =>
      @volatile var fired = false
      service.onLeadershipLost(() => IO { fired = true }).unsafeRunSync()
      eventually { service.isLeader() shouldBe true }
      shouldFail = true
      eventually { fired shouldBe true }
    }
  }

  test("onLeadershipLost does not fire when node never acquires leadership") {
    withService(acquireOrRenewFn = () => Future.successful(false)) { service =>
      @volatile var fired = false
      service.onLeadershipLost(() => IO { fired = true }).unsafeRunSync()
      Thread.sleep(heartbeatInterval.toMillis * 3)
      fired shouldBe false
    }
  }

  test("onLeadershipLost fires exactly once when leadership is stable then lost") {
    @volatile var acquired = true
    withService(acquireOrRenewFn = () => Future.successful(acquired)) { service =>
      @volatile var count = 0
      service.onLeadershipLost(() => IO { count += 1 }).unsafeRunSync()
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { service.isLeader() shouldBe false }
      Thread.sleep(heartbeatInterval.toMillis * 5)
      count shouldBe 1
    }
  }

  test("onLeadershipLost fires again after re-acquiring leadership and losing it again") {
    @volatile var acquired = true
    withService(acquireOrRenewFn = () => Future.successful(acquired)) { service =>
      @volatile var count = 0
      service.onLeadershipLost(() => IO { count += 1 }).unsafeRunSync()
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { count shouldBe 1 }
      acquired = true
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { count shouldBe 2 }
    }
  }

  test("slow onLeadershipLost callback does not block the heartbeat") {
    @volatile var acquired       = true
    @volatile var heartbeatCount = 0
    withService(acquireOrRenewFn = () => {
      heartbeatCount += 1
      Future.successful(acquired)
    }) { service =>
      service
        .onLeadershipLost { () =>
          IO.blocking(Thread.sleep(heartbeatInterval.toMillis * 10))
        }
        .unsafeRunSync()
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { service.isLeader() shouldBe false }
      val countAtLoss = heartbeatCount
      Thread.sleep(heartbeatInterval.toMillis * 5)
      heartbeatCount should be > countAtLoss + 2
    }
  }

  test("stop releases the leader lock when releaseOnStop = true") {
    @volatile var released = false
    withAllocatedService(
      acquireOrRenewFn = () => Future.successful(true),
      releaseFn = () => { released = true; Future.successful(true) },
      releaseOnStop = true,
    ) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      stop.unsafeRunSync()
      released shouldBe true
    }
  }

  test("stop does not release the leader lock when releaseOnStop = false") {
    @volatile var released = false
    withAllocatedService(
      acquireOrRenewFn = () => Future.successful(true),
      releaseFn = () => { released = true; Future.successful(true) },
      releaseOnStop = false,
    ) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      stop.unsafeRunSync()
      released shouldBe false
    }
  }

  test("stop fires lost callbacks when leader and lock successfully released") {
    @volatile var fired = false
    withAllocatedService(
      acquireOrRenewFn = () => Future.successful(true),
      releaseFn = () => Future.successful(true),
      releaseOnStop = true,
    ) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      service.onLeadershipLost(() => IO { fired = true }).unsafeRunSync()
      stop.unsafeRunSync()
      fired shouldBe true
    }
  }

  test("stop does not fire lost callbacks when releaseOnStop = false") {
    @volatile var fired = false
    withAllocatedService(
      acquireOrRenewFn = () => Future.successful(true),
      releaseOnStop = false,
    ) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      service.onLeadershipLost(() => IO { fired = true }).unsafeRunSync()
      stop.unsafeRunSync()
      fired shouldBe false
    }
  }

  test("stop does not fire lost callbacks when lock was already stolen (release returns false)") {
    @volatile var fired = false
    withAllocatedService(
      acquireOrRenewFn = () => Future.successful(true),
      releaseFn = () => Future.successful(false),
      releaseOnStop = true,
    ) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      service.onLeadershipLost(() => IO { fired = true }).unsafeRunSync()
      stop.unsafeRunSync()
      fired shouldBe false
    }
  }

  test("stop sets isLeader to false") {
    withAllocatedService(acquireOrRenewFn = () => Future.successful(true)) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      stop.unsafeRunSync()
      service.isLeader() shouldBe false
    }
  }

  test("stop prevents further heartbeats") {
    @volatile var callCount = 0
    withAllocatedService(acquireOrRenewFn = () => {
      callCount += 1
      Future.successful(true)
    }) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      stop.unsafeRunSync()
      val countAfterStop = callCount
      Thread.sleep(heartbeatInterval.toMillis * 5)
      callCount shouldBe countAfterStop
    }
  }

  test("isHaEnabled returns true") {
    withService(acquireOrRenewFn = () => Future.successful(true)) { service =>
      service.isHaEnabled shouldBe true
    }
  }

  test("onLeadershipLost skips re-fire when previous callback IO is still in-progress") {
    @volatile var acquired = true
    withService(acquireOrRenewFn = () => Future.successful(acquired)) { service =>
      @volatile var count = 0
      val promise         = scala.concurrent.Promise[Unit]()
      service
        .onLeadershipLost { () =>
          count += 1
          IO.fromFuture(IO(promise.future))
        }
        .unsafeRunSync()
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { count shouldBe 1 }
      acquired = true
      eventually { service.isLeader() shouldBe true }
      acquired = false
      Thread.sleep(heartbeatInterval.toMillis * 3)
      count shouldBe 1 // skipped — promise not yet completed
      promise.success(())
      Thread.sleep(heartbeatInterval.toMillis * 3)
      count shouldBe 1 // no auto re-fire; re-fire only happens on next true→false transition
    }
  }

  test("onLeadershipAcquired fires all registered callbacks") {
    @volatile var count1 = 0
    @volatile var count2 = 0
    withService(
      acquireOrRenewFn = () => Future.successful(true),
      beforeHeartbeat = service =>
        service.onLeadershipAcquired(() => IO { count1 += 1 }) >>
          service.onLeadershipAcquired(() => IO { count2 += 1 }),
    ) { _ =>
      eventually { count1 shouldBe 1 }
      eventually { count2 shouldBe 1 }
    }
  }

  test("onLeadershipLost fires all registered callbacks") {
    @volatile var acquired = true
    withService(acquireOrRenewFn = () => Future.successful(acquired)) { service =>
      @volatile var count1 = 0
      @volatile var count2 = 0
      service.onLeadershipLost(() => IO { count1 += 1 }).unsafeRunSync()
      service.onLeadershipLost(() => IO { count2 += 1 }).unsafeRunSync()
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { count1 shouldBe 1 }
      eventually { count2 shouldBe 1 }
    }
  }

  test("error in one onLeadershipAcquired callback does not prevent other callbacks from firing") {
    @volatile var count = 0
    withService(
      acquireOrRenewFn = () => Future.successful(true),
      beforeHeartbeat = service =>
        service.onLeadershipAcquired(() => IO.raiseError(new RuntimeException("boom"))) >>
          service.onLeadershipAcquired(() => IO { count += 1 }),
    ) { _ => eventually { count shouldBe 1 } }
  }

  test("error in one onLeadershipLost callback does not prevent other callbacks from firing") {
    @volatile var acquired = true
    withService(acquireOrRenewFn = () => Future.successful(acquired)) { service =>
      @volatile var count = 0
      service.onLeadershipLost(() => IO.raiseError(new RuntimeException("boom"))).unsafeRunSync()
      service.onLeadershipLost(() => IO { count += 1 }).unsafeRunSync()
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { count shouldBe 1 }
    }
  }

  test("onLeadershipAcquired fires when initial acquireOrRenew fails but next heartbeat succeeds") {
    @volatile var fired     = false
    @volatile var firstCall = true
    withService(
      acquireOrRenewFn = () => {
        if (firstCall) { firstCall = false; Future.failed(new RuntimeException("DB error")) }
        else Future.successful(true)
      },
      beforeHeartbeat = _.onLeadershipAcquired(() => IO { fired = true }),
    ) { _ => eventually { fired shouldBe true } }
  }

  // Callbacks registered after startHeartbeat() only fire on the next false→true transition,
  // not immediately — callers must register before startHeartbeat() to catch the initial acquisition.
  test("onLeadershipAcquired does not fire when registered after startHeartbeat while already a leader") {
    withService(acquireOrRenewFn = () => Future.successful(true)) { service =>
      eventually { service.isLeader() shouldBe true }
      @volatile var fired = false
      service.onLeadershipAcquired(() => IO { fired = true }).unsafeRunSync()
      Thread.sleep(heartbeatInterval.toMillis * 5)
      fired shouldBe false
    }
  }

  test("stop waits for onLeadershipLost callback to complete before returning") {
    @volatile var callbackCompleted = false
    withAllocatedService(
      acquireOrRenewFn = () => Future.successful(true),
      releaseFn = () => Future.successful(true),
      releaseOnStop = true,
    ) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      service
        .onLeadershipLost { () =>
          IO.blocking { Thread.sleep(200); callbackCompleted = true }
        }
        .unsafeRunSync()
      stop.unsafeRunSync()
      callbackCompleted shouldBe true
    }
  }

  test("stop when never a leader does not throw and isLeader stays false") {
    withAllocatedService(acquireOrRenewFn = () => Future.successful(false)) { (service, stop) =>
      Thread.sleep(heartbeatInterval.toMillis * 2)
      service.isLeader() shouldBe false
      stop.unsafeRunSync()
      service.isLeader() shouldBe false
    }
  }

}
