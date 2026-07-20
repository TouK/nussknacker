package pl.touk.nussknacker.ui.process.periodic.flink

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{TestKit, TestKitBase, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.ha.DistributedLock
import pl.touk.nussknacker.ui.process.periodic.PeriodicLock
import pl.touk.nussknacker.ui.process.periodic.RescheduleFinishedActor

import scala.concurrent.Future
import scala.concurrent.duration._

class RescheduleFinishedActorTest extends AnyFunSuite with TestKitBase with Matchers with BeforeAndAfterAll {

  private val interval    = 100 millis
  private val maxWaitTime = interval * 20

  override implicit lazy val system: ActorSystem = ActorSystem(suiteName)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private def lockReturning(value: Boolean) = new PeriodicLock(
    new DistributedLock {
      def acquireOrRenew(name: String, duration: FiniteDuration) = Future.successful(value)
      def release(name: String)                                  = Future.unit
    },
    0.seconds
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

}
