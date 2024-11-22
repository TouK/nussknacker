package pl.touk.nussknacker.ui.process.periodic

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestKitBase, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.periodic.DeploymentActor.CheckToBeDeployed
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeployment

import scala.concurrent.Future
import scala.concurrent.duration._

class DeploymentActorTest extends AnyFunSuite with TestKitBase with Matchers with BeforeAndAfterAll {

  private val interval    = 100 millis
  private val maxWaitTime = interval * 10

  override implicit lazy val system: ActorSystem = ActorSystem(suiteName)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  test("should find to be deployed scenarios repeatedly") {
    shouldFindToBeDeployedScenarios(Future.successful(Seq.empty))
  }

  test("should find to be deployed scenarios repeatedly even if it fails") {
    shouldFindToBeDeployedScenarios(Future.failed(new NullPointerException("failure")))
  }

  private def shouldFindToBeDeployedScenarios(
      result: Future[Seq[PeriodicProcessDeployment[CanonicalProcess]]]
  ): Unit = {
    val probe   = TestProbe()
    var counter = 0
    def findToBeDeployed: Future[Seq[PeriodicProcessDeployment[CanonicalProcess]]] = {
      counter += 1
      probe.ref ! s"invoked $counter"
      result
    }
    val actor =
      system.actorOf(DeploymentActor.props(findToBeDeployed, deploy = _ => fail("should not be called"), interval))

    within(maxWaitTime) {
      probe.expectMsg("invoked 1")
      probe.expectMsg("invoked 2")
    }

    system.stop(actor)
  }

  test("should deploy found scenario") {
    val probe                                                          = TestProbe()
    val waitingDeployment                                              = PeriodicProcessDeploymentGen()
    var toBeDeployed: Seq[PeriodicProcessDeployment[CanonicalProcess]] = Seq(waitingDeployment)
    var actor: ActorRef                                                = null
    def findToBeDeployed: Future[Seq[PeriodicProcessDeployment[CanonicalProcess]]] = {
      Future.successful(toBeDeployed)
    }
    def deploy(deployment: PeriodicProcessDeployment[CanonicalProcess]): Future[Unit] = {
      probe.ref ! deployment
      // Simulate periodic check for waiting scenarios while deploying a scenario.
      actor ! CheckToBeDeployed
      deployment shouldBe toBeDeployed.loneElement
      toBeDeployed = Seq.empty
      Future.successful(())
    }
    actor = system.actorOf(DeploymentActor.props(findToBeDeployed, deploy, interval))

    within(maxWaitTime) {
      probe.expectMsg(waitingDeployment)
    }

    system.stop(actor)
  }

}
