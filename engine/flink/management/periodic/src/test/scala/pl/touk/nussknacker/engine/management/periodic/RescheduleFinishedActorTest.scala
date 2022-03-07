package pl.touk.nussknacker.engine.management.periodic

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestKitBase, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._

class RescheduleFinishedActorTest extends FunSuite
  with TestKitBase
  with Matchers
  with BeforeAndAfterAll {

  private val interval = 100 millis
  private val maxWaitTime = interval * 10

  override implicit lazy val system: ActorSystem = ActorSystem(suiteName)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  test("should invoke handle finished repeatedly") {
    shouldInvokeHandleFinishedRepeatedly(Future.successful(()))
  }

  test("should invoke handle finished repeatedly even if it fails") {
    shouldInvokeHandleFinishedRepeatedly(Future.failed(new NullPointerException("failure")))
  }

  private def shouldInvokeHandleFinishedRepeatedly(result: Future[Unit]): Unit = {
    val probe = TestProbe()
    var counter = 0
    def handleFinished: Future[Unit] = {
      counter += 1
      probe.ref ! s"invoked $counter"
      result
    }
    val actor = system.actorOf(RescheduleFinishedActor.props(handleFinished, interval))

    within(maxWaitTime) {
      probe.expectMsg("invoked 1")
      probe.expectMsg("invoked 2")
    }

    system.stop(actor)
  }
}
