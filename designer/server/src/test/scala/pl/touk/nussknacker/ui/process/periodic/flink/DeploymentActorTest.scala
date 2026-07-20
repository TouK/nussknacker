package pl.touk.nussknacker.ui.process.periodic.flink

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.apache.pekko.testkit.{TestKit, TestKitBase, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.ha.DistributedLock
import pl.touk.nussknacker.ui.process.periodic.DeploymentActor
import pl.touk.nussknacker.ui.process.periodic.DeploymentActor.CheckToBeDeployed
import pl.touk.nussknacker.ui.process.periodic.PeriodicLock
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeployment

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

class DeploymentActorTest extends AnyFunSuite with TestKitBase with Matchers with BeforeAndAfterAll {

  private val interval    = 100 millis
  private val maxWaitTime = interval * 10

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

  test("should find to be deployed scenarios repeatedly") {
    shouldFindToBeDeployedScenarios(Future.successful(Seq.empty))
  }

  test("should find to be deployed scenarios repeatedly even if it fails") {
    shouldFindToBeDeployedScenarios(Future.failed(new NullPointerException("failure")))
  }

  private def shouldFindToBeDeployedScenarios(
      result: Future[Seq[PeriodicProcessDeployment]]
  ): Unit = {
    val probe   = TestProbe()
    var counter = 0
    def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]] = {
      counter += 1
      probe.ref ! s"invoked $counter"
      result
    }
    val actor = system.actorOf(
      DeploymentActor.props(findToBeDeployed, deploy = _ => fail("should not be called"), lockReturning(true), interval)
    )

    within(maxWaitTime) {
      probe.expectMsg("invoked 1")
      probe.expectMsg("invoked 2")
    }

    system.stop(actor)
  }

  test("should deploy found scenario") {
    val probe                                        = TestProbe()
    val waitingDeployment                            = PeriodicProcessDeploymentGen()
    var toBeDeployed: Seq[PeriodicProcessDeployment] = Seq(waitingDeployment)
    var actor: ActorRef                              = null
    def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]] = {
      Future.successful(toBeDeployed)
    }
    def deploy(deployment: PeriodicProcessDeployment): Future[Unit] = {
      probe.ref ! deployment
      // Simulate periodic check for waiting scenarios while deploying a scenario.
      actor ! CheckToBeDeployed
      deployment shouldBe toBeDeployed.loneElement
      toBeDeployed = Seq.empty
      Future.successful(())
    }
    actor = system.actorOf(DeploymentActor.props(findToBeDeployed, deploy, lockReturning(true), interval))

    within(maxWaitTime) {
      probe.expectMsg(waitingDeployment)
    }

    system.stop(actor)
  }

  test("should not deploy if lock is not acquired") {
    val findProbe         = TestProbe()
    val deployProbe       = TestProbe()
    val waitingDeployment = PeriodicProcessDeploymentGen()
    def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]] = {
      findProbe.ref ! "invoked"
      Future.successful(Seq(waitingDeployment))
    }
    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed,
        deploy = d => { deployProbe.ref ! d; Future.unit },
        lockReturning(false),
        interval
      )
    )

    within(maxWaitTime) {
      findProbe.expectMsg("invoked")
    }
    deployProbe.expectNoMessage(interval * 3)

    system.stop(actor)
  }

  test("should deploy on next tick after lock acquisition failure") {
    val deployProbe       = TestProbe()
    val waitingDeployment = PeriodicProcessDeploymentGen()
    var failLock          = true
    val lock = new PeriodicLock(
      new DistributedLock {
        def acquireOrRenew(name: String, duration: FiniteDuration) =
          if (failLock) Future.failed(new RuntimeException("db error"))
          else Future.successful(true)
        def release(name: String) = Future.unit
      },
      0.seconds
    )
    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed = Future.successful(Seq(waitingDeployment)),
        deploy = d => { deployProbe.ref ! d; Future.unit },
        lock = lock,
        interval
      )
    )

    deployProbe.expectNoMessage(interval * 3)
    failLock = false

    within(maxWaitTime) {
      deployProbe.expectMsg(waitingDeployment)
    }

    system.stop(actor)
  }

  test("should deploy exactly once even if multiple ticks arrive before lock is acquired") {
    val deployProbe                                  = TestProbe()
    val waitingDeployment                            = PeriodicProcessDeploymentGen()
    var toBeDeployed: Seq[PeriodicProcessDeployment] = Seq(waitingDeployment)
    val lockPromise                                  = Promise[Boolean]()
    val lock = new PeriodicLock(
      new DistributedLock {
        def acquireOrRenew(name: String, duration: FiniteDuration) = lockPromise.future
        def release(name: String)                                  = Future.unit
      },
      0.seconds
    )
    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed = Future.successful(toBeDeployed),
        deploy = d => { deployProbe.ref ! d; toBeDeployed = Seq.empty; Future.unit },
        lock = lock,
        interval
      )
    )

    // wait for multiple ticks while lock is still pending — only one acquireOrRenew should be in-flight
    Thread.sleep((interval * 3).toMillis)
    lockPromise.success(true)

    within(maxWaitTime) {
      deployProbe.expectMsg(waitingDeployment)
    }
    deployProbe.expectNoMessage(interval * 3)

    system.stop(actor)
  }

}
