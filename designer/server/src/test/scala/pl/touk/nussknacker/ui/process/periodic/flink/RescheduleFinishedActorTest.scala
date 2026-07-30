package pl.touk.nussknacker.ui.process.periodic.flink

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{TestKit, TestKitBase, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.ha.DistributedLock
import pl.touk.nussknacker.ui.process.periodic.PeriodicDeploymentLock
import pl.touk.nussknacker.ui.process.periodic.RescheduleFinishedActor

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._

class RescheduleFinishedActorTest extends AnyFunSuite with TestKitBase with Matchers with BeforeAndAfterAll {

  private val interval    = 100 millis
  private val maxWaitTime = interval * 20

  override implicit lazy val system: ActorSystem = ActorSystem(suiteName)
  private implicit val ec: ExecutionContext      = system.dispatcher

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private def lockReturning(value: Boolean) = new PeriodicDeploymentLock(
    new DistributedLock {
      override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Option[Instant]] =
        Future.successful(if (value) Some(Instant.MAX) else None)
      override def release(name: String): Future[Boolean] = Future.successful(true)
    },
    None
  )

  test("should invoke handle finished repeatedly") {
    shouldInvokeHandleFinishedRepeatedly(Future.successful(()))
  }

  test("should invoke handle finished repeatedly even if it fails") {
    shouldInvokeHandleFinishedRepeatedly(Future.failed(new NullPointerException("failure")))
  }

  private def shouldInvokeHandleFinishedRepeatedly(result: Future[Unit]): Unit = {
    val probe   = TestProbe()
    var counter = 0
    def handleFinished: Future[Unit] = {
      counter += 1
      probe.ref ! s"invoked $counter"
      result
    }
    val actor = system.actorOf(RescheduleFinishedActor.props(handleFinished, lockReturning(true), interval))

    within(maxWaitTime) {
      probe.expectMsg("invoked 1")
      probe.expectMsg("invoked 2")
    }

    system.stop(actor)
  }

  test("should not invoke handle finished if lock is not acquired") {
    val probe = TestProbe()
    def handleFinished: Future[Unit] = {
      probe.ref ! "invoked"
      Future.successful(())
    }
    val actor = system.actorOf(RescheduleFinishedActor.props(handleFinished, lockReturning(false), interval))

    probe.expectNoMessage(maxWaitTime)

    system.stop(actor)
  }

  test("should renew lock on each tick while handle finished is running") {
    val acquireCount  = new AtomicInteger(0)
    val finishPromise = Promise[Unit]()
    val probe         = TestProbe()

    val lock = new PeriodicDeploymentLock(
      new DistributedLock {
        override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Option[Instant]] = {
          acquireCount.incrementAndGet()
          Future.successful(Some(Instant.MAX))
        }
        override def release(name: String): Future[Boolean] = Future.successful(true)
      },
      None
    )

    def handleFinished: Future[Unit] = { probe.ref ! "started"; finishPromise.future }

    val actor = system.actorOf(RescheduleFinishedActor.props(handleFinished, lock, interval))

    within(maxWaitTime) { probe.expectMsg("started") }
    Thread.sleep((interval * 5).toMillis)
    acquireCount.get() should be > 1

    finishPromise.success(())
    system.stop(actor)
  }

  test("should complete handle finished even if lock is lost during execution") {
    val acquireCount  = new AtomicInteger(0)
    val finishPromise = Promise[Unit]()
    val probe         = TestProbe()

    val lock = new PeriodicDeploymentLock(
      new DistributedLock {
        override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Option[Instant]] =
          Future.successful(if (acquireCount.getAndIncrement() == 0) Some(Instant.MAX) else None)
        override def release(name: String): Future[Boolean] = Future.successful(true)
      },
      None
    )

    def handleFinished: Future[Unit] = { probe.ref ! "started"; finishPromise.future }

    val actor = system.actorOf(RescheduleFinishedActor.props(handleFinished, lock, interval))

    within(maxWaitTime) { probe.expectMsg("started") }
    Thread.sleep((interval * 3).toMillis) // renewal ticks return false — lock lost
    finishPromise.success(())             // completes despite lock loss
    probe.expectNoMessage(interval * 3)   // actor alive, no second invocation (lock still lost)

    system.stop(actor)
  }

  test("should not invoke handle finished concurrently") {
    val invocationCount = new AtomicInteger(0)
    val finishPromise   = Promise[Unit]()
    val probe           = TestProbe()

    def handleFinished: Future[Unit] = {
      invocationCount.incrementAndGet()
      probe.ref ! "started"
      finishPromise.future
    }

    val actor = system.actorOf(RescheduleFinishedActor.props(handleFinished, lockReturning(true), interval))

    within(maxWaitTime) { probe.expectMsg("started") }
    Thread.sleep((interval * 5).toMillis) // multiple ticks fire — none should re-invoke handleFinished
    invocationCount.get() shouldBe 1

    finishPromise.success(())
    within(maxWaitTime) { probe.expectMsg("started") } // second invocation only after first completes
    invocationCount.get() shouldBe 2

    system.stop(actor)
  }

  test("should retry on next tick after lock acquisition failure") {
    val acquireCount = new AtomicInteger(0)
    val probe        = TestProbe()

    val lock = new PeriodicDeploymentLock(
      new DistributedLock {
        override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Option[Instant]] =
          if (acquireCount.getAndIncrement() < 2) Future.failed(new RuntimeException("db error"))
          else Future.successful(Some(Instant.MAX))
        override def release(name: String): Future[Boolean] = Future.successful(true)
      },
      None
    )

    def handleFinished: Future[Unit] = { probe.ref ! "invoked"; Future.successful(()) }

    val actor = system.actorOf(RescheduleFinishedActor.props(handleFinished, lock, interval))

    within(maxWaitTime) { probe.expectMsg("invoked") }

    system.stop(actor)
  }

}
