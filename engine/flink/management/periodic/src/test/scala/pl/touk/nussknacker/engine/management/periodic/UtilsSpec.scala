package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class UtilsSpec extends TestKit(ActorSystem("UtilsSpec")) with AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Utils" should {

    "gracefully stop actor and free actor path" in {
      class TestActor extends Actor {
        override def receive: Receive = { case _ =>
          ()
        }
      }
      val actorName = "actorName"

      val actorRef = system.actorOf(Props(new TestActor), actorName)

      Utils.stopActorAndWaitUntilItsNameIsFree(actorRef, system)

      // with normal system.stop(actorRef) or akka.pattern.gracefulStop this throws "actor name is not unique"
      system.actorOf(Props(new TestActor), actorName)
    }

    "ignore exceptions inside runSafely block" in {
      Utils.runSafely {
        throw new RuntimeException("dummy")
      }

      1 shouldBe 1
    }

  }

}
