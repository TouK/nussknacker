package pl.touk.nussknacker.ui.ha

import cats.effect.{IO, Resource}
import cats.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntimeAdapter

import java.time.{Clock, Instant}
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

  test("instanceId returns the configured value") {
    withService(acquireOrRenewFn = () => lockAcquired()) { service =>
      service.instanceId shouldBe "test-instance"
    }
  }

  test("isLeader returns false when lock validity has expired") {
    makeService(acquireOrRenewFn = () => lockAcquired(Instant.EPOCH))
      .flatMap(service => service.startHeartbeat().as(service))
      .use(service => IO(service.isLeader() shouldBe false))
      .unsafeRunSync()
  }

  test("isLeader becomes true after successful lock acquisition") {
    withService(acquireOrRenewFn = () => lockAcquired()) { service =>
      eventually { service.isLeader() shouldBe true }
    }
  }

  test("isLeader stays false when acquireOrRenew returns false") {
    withService(acquireOrRenewFn = () => lockNotAcquired) { service =>
      Thread.sleep(heartbeatInterval.toMillis * 3)
      service.isLeader() shouldBe false
    }
  }

  test("isLeader drops to false when heartbeat starts returning false (lock stolen)") {
    @volatile var acquired = true
    withService(acquireOrRenewFn = () => if (acquired) lockAcquired() else lockNotAcquired) { service =>
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { service.isLeader() shouldBe false }
    }
  }

  test("isLeader stays true during transient DB heartbeat errors") {
    @volatile var shouldFail = false
    withService(acquireOrRenewFn = () => {
      if (shouldFail) Future.failed(new RuntimeException("DB error"))
      else lockAcquired()
    }) { service =>
      eventually { service.isLeader() shouldBe true }
      shouldFail = true
      Thread.sleep(heartbeatInterval.toMillis * 3)
      service.isLeader() shouldBe true
    }
  }

  test("onLeadershipAcquired fires when node acquires leadership at startHeartbeat time") {
    @volatile var fired = false
    withService(
      acquireOrRenewFn = () => lockAcquired(),
      beforeHeartbeat = _.onLeadershipAcquired(IO { fired = true }),
    ) { _ => eventually { fired shouldBe true } }
  }

  test("onLeadershipAcquired fires when node acquires leadership after starting as non-leader") {
    @volatile var fired     = false
    @volatile var firstCall = true
    withService(
      acquireOrRenewFn = () => {
        if (firstCall) { firstCall = false; lockNotAcquired }
        else lockAcquired()
      },
      beforeHeartbeat = _.onLeadershipAcquired(IO { fired = true }),
    ) { _ => eventually { fired shouldBe true } }
  }

  test("onLeadershipAcquired fires again after re-acquiring leadership") {
    @volatile var acquired = true
    @volatile var count    = 0
    withService(
      acquireOrRenewFn = () => if (acquired) lockAcquired() else lockNotAcquired,
      beforeHeartbeat = _.onLeadershipAcquired(IO { count += 1 }),
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
      acquireOrRenewFn = () => lockAcquired(),
      beforeHeartbeat = _.onLeadershipAcquired(IO { count += 1 }),
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
      acquireOrRenewFn = () => if (acquired) lockAcquired() else lockNotAcquired,
      beforeHeartbeat = _.onLeadershipAcquired(IO { count += 1 } >> IO.fromFuture(IO(promise.future))),
    ) { service =>
      eventually { count shouldBe 1 }
      acquired = false
      eventually { service.isLeader() shouldBe false }
      acquired = true
      Thread.sleep(heartbeatInterval.toMillis * 3)
      count shouldBe 1 // skipped — promise not yet completed
      promise.success(())
      Thread.sleep(heartbeatInterval.toMillis * 3)
      count shouldBe 1 // no auto re-fire; re-fire only on next false→true transition
    }
  }

  test("slow onLeadershipAcquired callback does not block the heartbeat") {
    @volatile var heartbeatCount = 0
    withService(
      acquireOrRenewFn = () => { heartbeatCount += 1; lockAcquired() },
      beforeHeartbeat = _.onLeadershipAcquired(IO.blocking(Thread.sleep(heartbeatInterval.toMillis * 10))),
    ) { service =>
      eventually { service.isLeader() shouldBe true }
      val countAtAcquisition = heartbeatCount
      // timeout shorter than the callback's 10-interval block, so a blocked heartbeat still fails the test
      eventually(Timeout(Span(heartbeatInterval.toMillis * 8, Millis))) {
        heartbeatCount should be > countAtAcquisition + 2
      }
    }
  }

  test("onLeadershipLost fires when lock is stolen") {
    @volatile var acquired = true
    @volatile var fired    = false
    withService(
      acquireOrRenewFn = () => if (acquired) lockAcquired() else lockNotAcquired,
      beforeHeartbeat = _.onLeadershipLost(IO { fired = true }),
    ) { service =>
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { fired shouldBe true }
    }
  }

  test("onLeadershipLost does not fire on transient DB heartbeat errors") {
    @volatile var shouldFail = false
    @volatile var fired      = false
    withService(
      acquireOrRenewFn = () => {
        if (shouldFail) Future.failed(new RuntimeException("DB error"))
        else lockAcquired()
      },
      beforeHeartbeat = _.onLeadershipLost(IO { fired = true }),
    ) { service =>
      eventually { service.isLeader() shouldBe true }
      shouldFail = true
      Thread.sleep(heartbeatInterval.toMillis * 3)
      fired shouldBe false
    }
  }

  test("onLeadershipLost does not fire when node never acquires leadership") {
    @volatile var fired = false
    withService(
      acquireOrRenewFn = () => lockNotAcquired,
      beforeHeartbeat = _.onLeadershipLost(IO { fired = true }),
    ) { _ =>
      Thread.sleep(heartbeatInterval.toMillis * 3)
      fired shouldBe false
    }
  }

  test("onLeadershipLost fires exactly once when leadership is stable then lost") {
    @volatile var acquired = true
    @volatile var count    = 0
    withService(
      acquireOrRenewFn = () => if (acquired) lockAcquired() else lockNotAcquired,
      beforeHeartbeat = _.onLeadershipLost(IO { count += 1 }),
    ) { service =>
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { service.isLeader() shouldBe false }
      Thread.sleep(heartbeatInterval.toMillis * 5)
      count shouldBe 1
    }
  }

  test("onLeadershipLost fires again after re-acquiring leadership and losing it again") {
    @volatile var acquired = true
    @volatile var count    = 0
    withService(
      acquireOrRenewFn = () => if (acquired) lockAcquired() else lockNotAcquired,
      beforeHeartbeat = _.onLeadershipLost(IO { count += 1 }),
    ) { service =>
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
    withService(
      acquireOrRenewFn = () => { heartbeatCount += 1; if (acquired) lockAcquired() else lockNotAcquired },
      beforeHeartbeat = _.onLeadershipLost(IO.blocking(Thread.sleep(heartbeatInterval.toMillis * 10))),
    ) { service =>
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
      acquireOrRenewFn = () => lockAcquired(),
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
      acquireOrRenewFn = () => lockAcquired(),
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
      acquireOrRenewFn = () => lockAcquired(),
      releaseFn = () => Future.successful(true),
      releaseOnStop = true,
      beforeHeartbeat = _.onLeadershipLost(IO { fired = true }),
    ) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      stop.unsafeRunSync()
      fired shouldBe true
    }
  }

  test("stop does not fire lost callbacks when releaseOnStop = false") {
    @volatile var fired = false
    withAllocatedService(
      acquireOrRenewFn = () => lockAcquired(),
      releaseOnStop = false,
      beforeHeartbeat = _.onLeadershipLost(IO { fired = true }),
    ) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      stop.unsafeRunSync()
      fired shouldBe false
    }
  }

  test("stop does not fire lost callbacks when lock was already stolen (release returns false)") {
    @volatile var fired = false
    withAllocatedService(
      acquireOrRenewFn = () => lockAcquired(),
      releaseFn = () => Future.successful(false),
      releaseOnStop = true,
      beforeHeartbeat = _.onLeadershipLost(IO { fired = true }),
    ) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      stop.unsafeRunSync()
      fired shouldBe false
    }
  }

  test("stop sets isLeader to false") {
    withAllocatedService(acquireOrRenewFn = () => lockAcquired()) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      stop.unsafeRunSync()
      service.isLeader() shouldBe false
    }
  }

  test("stop prevents further heartbeats") {
    @volatile var callCount = 0
    withAllocatedService(acquireOrRenewFn = () => { callCount += 1; lockAcquired() }) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      stop.unsafeRunSync()
      val countAfterStop = callCount
      Thread.sleep(heartbeatInterval.toMillis * 5)
      callCount shouldBe countAfterStop
    }
  }

  test("stop returns promptly without waiting for the next heartbeat interval") {
    val longHeartbeatInterval = 10.seconds
    makeService(
      acquireOrRenewFn = () => lockAcquired(),
      heartbeatInterval = longHeartbeatInterval,
      leaseDuration = longHeartbeatInterval * 3,
    ).flatMap(service => service.startHeartbeat().as(service))
      .allocated
      .flatMap { case (service, release) =>
        IO {
          eventually { service.isLeader() shouldBe true }
          val before = System.currentTimeMillis()
          release.unsafeRunSync()
          val elapsed = System.currentTimeMillis() - before
          elapsed should be < longHeartbeatInterval.toMillis
        }.guarantee(release)
      }
      .unsafeRunSync()
  }

  test("haEnabled returns true") {
    withService(acquireOrRenewFn = () => lockAcquired()) { service =>
      service.haEnabled shouldBe true
    }
  }

  test("onLeadershipLost skips re-fire when previous callback IO is still in-progress") {
    @volatile var acquired = true
    @volatile var count    = 0
    val promise            = scala.concurrent.Promise[Unit]()
    withService(
      acquireOrRenewFn = () => if (acquired) lockAcquired() else lockNotAcquired,
      beforeHeartbeat = _.onLeadershipLost(IO { count += 1 } >> IO.fromFuture(IO(promise.future))),
    ) { service =>
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
      count shouldBe 1 // no auto re-fire; re-fire only on next true→false transition
    }
  }

  test("onLeadershipAcquired fires all registered callbacks") {
    @volatile var count1 = 0
    @volatile var count2 = 0
    withService(
      acquireOrRenewFn = () => lockAcquired(),
      beforeHeartbeat = service =>
        service.onLeadershipAcquired(IO { count1 += 1 }) >>
          service.onLeadershipAcquired(IO { count2 += 1 }),
    ) { _ =>
      eventually { count1 shouldBe 1 }
      eventually { count2 shouldBe 1 }
    }
  }

  test("onLeadershipLost fires all registered callbacks") {
    @volatile var acquired = true
    @volatile var count1   = 0
    @volatile var count2   = 0
    withService(
      acquireOrRenewFn = () => if (acquired) lockAcquired() else lockNotAcquired,
      beforeHeartbeat = service =>
        service.onLeadershipLost(IO { count1 += 1 }) >>
          service.onLeadershipLost(IO { count2 += 1 }),
    ) { service =>
      eventually { service.isLeader() shouldBe true }
      acquired = false
      eventually { count1 shouldBe 1 }
      eventually { count2 shouldBe 1 }
    }
  }

  test("error in one onLeadershipAcquired callback does not prevent other callbacks from firing") {
    @volatile var count = 0
    withService(
      acquireOrRenewFn = () => lockAcquired(),
      beforeHeartbeat = service =>
        service.onLeadershipAcquired(IO.raiseError(new RuntimeException("boom"))) >>
          service.onLeadershipAcquired(IO { count += 1 }),
    ) { _ => eventually { count shouldBe 1 } }
  }

  test("error in one onLeadershipLost callback does not prevent other callbacks from firing") {
    @volatile var acquired = true
    @volatile var count    = 0
    withService(
      acquireOrRenewFn = () => if (acquired) lockAcquired() else lockNotAcquired,
      beforeHeartbeat = service =>
        service.onLeadershipLost(IO.raiseError(new RuntimeException("boom"))) >>
          service.onLeadershipLost(IO { count += 1 }),
    ) { service =>
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
        else lockAcquired()
      },
      beforeHeartbeat = _.onLeadershipAcquired(IO { fired = true }),
    ) { _ => eventually { fired shouldBe true } }
  }

  test("onLeadershipAcquired raises IllegalStateException when registered after startHeartbeat") {
    withService(acquireOrRenewFn = () => lockAcquired()) { service =>
      the[IllegalStateException] thrownBy service.onLeadershipAcquired(IO.unit).unsafeRunSync() should have message
        "Callbacks must be registered before startHeartbeat() is called"
    }
  }

  test("onLeadershipLost raises IllegalStateException when registered after startHeartbeat") {
    withService(acquireOrRenewFn = () => lockAcquired()) { service =>
      the[IllegalStateException] thrownBy service.onLeadershipLost(IO.unit).unsafeRunSync() should have message
        "Callbacks must be registered before startHeartbeat() is called"
    }
  }

  test("stop waits for onLeadershipLost callback to complete before returning") {
    @volatile var callbackCompleted = false
    withAllocatedService(
      acquireOrRenewFn = () => lockAcquired(),
      releaseFn = () => Future.successful(true),
      releaseOnStop = true,
      beforeHeartbeat = _.onLeadershipLost(IO.blocking { Thread.sleep(200); callbackCompleted = true }),
    ) { (service, stop) =>
      eventually { service.isLeader() shouldBe true }
      stop.unsafeRunSync()
      callbackCompleted shouldBe true
    }
  }

  test("stop cancels in-flight onLeadershipAcquired callbacks") {
    @volatile var callbackStarted   = false
    @volatile var callbackCompleted = false
    withAllocatedService(
      acquireOrRenewFn = () => lockAcquired(),
      beforeHeartbeat = _.onLeadershipAcquired(
        IO { callbackStarted = true } >> IO.sleep(10.seconds) >> IO { callbackCompleted = true }
      ),
    ) { (_, stop) =>
      eventually { callbackStarted shouldBe true }
      stop.unsafeRunSync()
      callbackCompleted shouldBe false
    }
  }

  test("stop when never a leader does not throw and isLeader stays false") {
    withAllocatedService(acquireOrRenewFn = () => lockNotAcquired) { (service, stop) =>
      Thread.sleep(heartbeatInterval.toMillis * 2)
      service.isLeader() shouldBe false
      stop.unsafeRunSync()
      service.isLeader() shouldBe false
    }
  }

  private def lockAcquired(until: Instant = Instant.MAX): Future[LeaderLockResult] =
    Future.successful(LeaderLockResult.Acquired(until))

  private val lockNotAcquired: Future[LeaderLockResult] =
    Future.successful(LeaderLockResult.NotAcquired)

  private def makeService(
      acquireOrRenewFn: () => Future[LeaderLockResult],
      releaseFn: () => Future[Boolean] = () => Future.successful(true),
      releaseOnStop: Boolean = true,
      leaseDuration: FiniteDuration = config.leader.leaseDuration,
      heartbeatInterval: FiniteDuration = config.leader.heartbeatInterval,
  ): Resource[IO, DistributedLeadership] = {
    val distributedLock = new DistributedLock {
      override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Option[Instant]] =
        acquireOrRenewFn().map {
          case LeaderLockResult.Acquired(validUntil) => Some(validUntil)
          case LeaderLockResult.NotAcquired          => None
        }(executionContextWithIORuntime)
      override def release(name: String): Future[Boolean] = releaseFn()
    }
    DistributedLeadership.resource(
      distributedLock,
      config.copy(leader =
        config.leader
          .copy(releaseOnStop = releaseOnStop, leaseDuration = leaseDuration, heartbeatInterval = heartbeatInterval)
      ),
      clock,
    )
  }

  private def withService(
      acquireOrRenewFn: () => Future[LeaderLockResult],
      releaseFn: () => Future[Boolean] = () => Future.successful(true),
      releaseOnStop: Boolean = true,
      beforeHeartbeat: DistributedLeadership => IO[Unit] = _ => IO.unit,
  )(body: DistributedLeadership => Unit): Unit =
    makeService(acquireOrRenewFn, releaseFn, releaseOnStop)
      .flatMap(service => Resource.eval(beforeHeartbeat(service)) >> service.startHeartbeat().as(service))
      .use(service => IO(body(service)))
      .unsafeRunSync()

  private def withAllocatedService(
      acquireOrRenewFn: () => Future[LeaderLockResult],
      releaseFn: () => Future[Boolean] = () => Future.successful(true),
      releaseOnStop: Boolean = true,
      beforeHeartbeat: DistributedLeadership => IO[Unit] = _ => IO.unit,
  )(body: (DistributedLeadership, IO[Unit]) => Unit): Unit =
    makeService(acquireOrRenewFn, releaseFn, releaseOnStop)
      .flatMap(service => Resource.eval(beforeHeartbeat(service)) >> service.startHeartbeat().as(service))
      .allocated
      .flatMap { case (service, release) => IO(body(service, release)).guarantee(release) }
      .unsafeRunSync()

}
