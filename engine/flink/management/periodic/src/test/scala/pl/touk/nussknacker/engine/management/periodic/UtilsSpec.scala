package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.TestKit
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class UtilsSpec
    extends TestKit(ActorSystem("UtilsSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with LazyLogging {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Utils" should {

    "stop actor and free actor path" in {
      class TestActor extends Actor {
        override def receive: Receive = { case _ =>
          ()
        }
      }
      val actorName = "actorName"

      val actorRef = system.actorOf(Props(new TestActor), actorName)

      Utils.stopActorAndWaitUntilItsNameIsFree(actorRef, system)

      // with normal system.stop(actorRef) or akka.pattern.gracefulStop this throws "actor name is not unique"
      val actorRef2 = system.actorOf(Props(new TestActor), actorName)

      Utils.stopActorAndWaitUntilItsNameIsFree(actorRef2, system) // stop and free it so it doesn't impact other tests
    }

    "stop actor and free actor path without waiting for all of it's messages to be processed" in {
      class TestActor extends Actor {
        override def receive: Receive = { case msg =>
          logger.info(s"Sleeping on the job '$msg' ...")
          Thread.sleep(1000)
        }
      }
      val actorName = "actorName"

      val actorRef = system.actorOf(Props(new TestActor), actorName)

      var messageCounter = 0
      while (messageCounter < 1000) {
        actorRef ! s"message number $messageCounter"
        messageCounter += 1
      }

      Thread.sleep(1000)
      // with gracefulStop this times out, because the PoisonPill is queued after the many normal messages
      Utils.stopActorAndWaitUntilItsNameIsFree(actorRef, system)

      val actorRef2 = system.actorOf(Props(new TestActor), actorName)

      Utils.stopActorAndWaitUntilItsNameIsFree(actorRef2, system) // stop and free it so it doesn't impact other tests
    }

    "ignore exceptions inside runSafely block" in {
      Utils.runSafely {
        throw new RuntimeException("dummy")
      }

      1 shouldBe 1
    }

  }

}
