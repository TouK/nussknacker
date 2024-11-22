package pl.touk.nussknacker.ui.process.periodic

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.TestKit
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import pl.touk.nussknacker.ui.process.periodic.Utils.createActorWithRetry

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class UtilsSpec
    extends TestKit(ActorSystem("UtilsSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with LazyLogging {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  class TestActor extends Actor {
    override def receive: Receive = { case _ => () }
  }

  "Utils" should {

    "create an actor if it's name is free" in {
      import system.dispatcher

      val actorName = "actorName1" // unique name in each test so that they don't interfere with each other

      createActorWithRetry(actorName, Props(new TestActor), system)
    }

    "create an actor if it's name isn't free but is freed before retrying gives up - idle actor" in {
      import system.dispatcher

      val actorName = "actorName2" // unique name in each test so that they don't interfere with each other

      val actorRef = createActorWithRetry(actorName, Props(new TestActor), system)

      val futureA = Future {
        createActorWithRetry(actorName, Props(new TestActor), system)
      }

      val futureB = Future {
        Thread.sleep(1000)
        system.stop(actorRef)
      }

      Await.result(Future.sequence(Seq(futureA, futureB)), Duration.Inf)
    }

    "create an actor if it's name isn't free but is freed before retrying gives up - busy actor" in {
      class BusyTestActor extends Actor {
        override def receive: Receive = { case msg =>
          logger.info(s"Sleeping on the job '$msg' ...")
          Thread.sleep(1000)
        }
      }

      import system.dispatcher

      val actorName = "actorName3" // unique name in each test so that they don't interfere with each other

      val actorRef = createActorWithRetry(actorName, Props(new BusyTestActor), system)

      var messageCounter = 0
      while (messageCounter < 1000) {
        actorRef ! s"message number $messageCounter"
        messageCounter += 1
      }

      val futureA = Future {
        createActorWithRetry(actorName, Props(new BusyTestActor), system)
      }

      val futureB = Future {
        Thread.sleep(1000)

        // if this was gracefulStop, it would take too long to stop the actor, as it would continue processing it's messages
        system.stop(actorRef)
      }

      Await.result(Future.sequence(Seq(futureA, futureB)), Duration.Inf)
    }

    "fail to create an actor if it's name isn't freed" in {
      import system.dispatcher

      val actorName = "actorName4" // unique name in each test so that they don't interfere with each other

      createActorWithRetry(actorName, Props(new TestActor), system)

      (the[IllegalStateException] thrownBy {
        createActorWithRetry(actorName, Props(new TestActor), system)
      }).getMessage shouldEqual s"Failed to create actor '$actorName' within allowed retries: akka.actor.InvalidActorNameException: actor name [$actorName] is not unique!"

    }

    "ignore exceptions inside runSafely block" in {
      Utils.runSafely {
        throw new RuntimeException("dummy")
      }

      1 shouldBe 1
    }

  }

}
