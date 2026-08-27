package pl.touk.nussknacker.ui.process.periodic.flink

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{TestKit, TestKitBase, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.ha.{DistributedLock, TogglableLeadership}
import pl.touk.nussknacker.ui.process.periodic.DedicatedPeriodicDeploymentLock
import pl.touk.nussknacker.ui.process.periodic.DeploymentActor
import pl.touk.nussknacker.ui.process.periodic.LeaderPeriodicDeploymentLock
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeployment

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._

class DeploymentActorTest extends AnyFunSuite with TestKitBase with Matchers with BeforeAndAfterAll {

  private val interval    = 100 millis
  private val maxWaitTime = interval * 10

  override implicit lazy val system: ActorSystem = ActorSystem(suiteName)
  private implicit val ec: ExecutionContext      = system.dispatcher

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private def mockedLock(acquire: => Future[Option[Instant]]) = new DedicatedPeriodicDeploymentLock(
    new DistributedLock {
      override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Option[Instant]] = acquire
      override def release(name: String): Future[Boolean] = Future.successful(true)
    },
    1.minute
  )

  private def lockReturning(value: Boolean) = mockedLock { Future.successful(if (value) Some(Instant.MAX) else None) }

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

  test("should deploy found scenario and ignore ticks during deployment") {
    val probe                                                  = TestProbe()
    val waitingDeployment                                      = PeriodicProcessDeploymentGen()
    val deployPromise                                          = Promise[Unit]()
    @volatile var toBeDeployed: Seq[PeriodicProcessDeployment] = Seq(waitingDeployment)

    def deploy(deployment: PeriodicProcessDeployment): Future[Unit] = {
      toBeDeployed = Seq.empty
      probe.ref ! deployment
      deployPromise.future
    }

    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed = Future.successful(toBeDeployed),
        deploy,
        lockReturning(true),
        interval
      )
    )

    within(maxWaitTime) { probe.expectMsg(waitingDeployment) }
    Thread.sleep((interval * 3).toMillis) // ticks fire during deployment — none should re-deploy
    probe.expectNoMessage(Duration.Zero)

    deployPromise.success(())
    probe.expectNoMessage(interval * 3) // no second deployment after completion either

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

    // lock is checked before findToBeDeployed — if not acquired, finding is skipped entirely
    findProbe.expectNoMessage(maxWaitTime)
    deployProbe.expectNoMessage(Duration.Zero)

    system.stop(actor)
  }

  test("should deploy on next tick after lock acquisition failure") {
    val deployProbe       = TestProbe()
    val waitingDeployment = PeriodicProcessDeploymentGen()
    var failLock          = true
    val lock = mockedLock {
      if (failLock) Future.failed(new RuntimeException("db error"))
      else Future.successful(Some(Instant.MAX))
    }
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

  test("should deploy exactly once even if findToBeDeployed is slow and two results arrive") {
    val deployProbe                                            = TestProbe()
    val waitingDeployment                                      = PeriodicProcessDeploymentGen()
    val findPromise                                            = Promise[Seq[PeriodicProcessDeployment]]()
    @volatile var toBeDeployed: Seq[PeriodicProcessDeployment] = Seq(waitingDeployment)

    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed = if (toBeDeployed.isEmpty) Future.successful(Seq.empty) else findPromise.future,
        deploy = d => { deployProbe.ref ! d; toBeDeployed = Seq.empty; Future.unit },
        lockReturning(true),
        interval
      )
    )

    // Wait for multiple ticks — actor is in handleChecking state so CheckToBeDeployed is dropped
    Thread.sleep((interval * 3).toMillis)
    findPromise.success(Seq(waitingDeployment))

    within(maxWaitTime) {
      deployProbe.expectMsg(waitingDeployment)
    }
    deployProbe.expectNoMessage(interval * 3)

    system.stop(actor)
  }

  test("should deploy exactly once even if multiple ticks arrive before lock is acquired") {
    val deployProbe                                  = TestProbe()
    val waitingDeployment                            = PeriodicProcessDeploymentGen()
    var toBeDeployed: Seq[PeriodicProcessDeployment] = Seq(waitingDeployment)
    val lockPromise                                  = Promise[Option[Instant]]()

    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed = Future.successful(toBeDeployed),
        deploy = d => { deployProbe.ref ! d; toBeDeployed = Seq.empty; Future.unit },
        lock = mockedLock { lockPromise.future },
        interval
      )
    )

    // wait for multiple ticks while lock is still pending — only one acquireOrRenew should be in-flight
    Thread.sleep((interval * 3).toMillis)
    lockPromise.success(Some(Instant.MAX))

    within(maxWaitTime) {
      deployProbe.expectMsg(waitingDeployment)
    }
    deployProbe.expectNoMessage(interval * 3)

    system.stop(actor)
  }

  test("should retry deployment on next tick after deploy failure") {
    val deployProbe       = TestProbe()
    val waitingDeployment = PeriodicProcessDeploymentGen()
    var failDeploy        = true

    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed = Future.successful(Seq(waitingDeployment)),
        deploy = d => {
          deployProbe.ref ! d
          if (failDeploy) Future.failed(new RuntimeException("deploy error"))
          else Future.unit
        },
        lockReturning(true),
        interval
      )
    )

    within(maxWaitTime) { deployProbe.expectMsg(waitingDeployment) } // first attempt fails
    failDeploy = false
    within(maxWaitTime) { deployProbe.expectMsg(waitingDeployment) } // retried on next tick

    system.stop(actor)
  }

  test("should renew lock on each tick while deploying") {
    val acquireCount      = new AtomicInteger(0)
    val deployProbe       = TestProbe()
    val deployPromise     = Promise[Unit]()
    val waitingDeployment = PeriodicProcessDeploymentGen()

    val lock = mockedLock {
      acquireCount.incrementAndGet()
      Future.successful(Some(Instant.MAX))
    }

    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed = Future.successful(Seq(waitingDeployment)),
        deploy = d => { deployProbe.ref ! d; deployPromise.future },
        lock,
        interval
      )
    )

    within(maxWaitTime) { deployProbe.expectMsg(waitingDeployment) }
    Thread.sleep((interval * 5).toMillis)
    acquireCount.get() should be > 1

    deployPromise.success(())
    system.stop(actor)
  }

  test("should complete deployment even if lock is lost during execution") {
    val acquireCount      = new AtomicInteger(0)
    val deployProbe       = TestProbe()
    val deployPromise     = Promise[Unit]()
    val waitingDeployment = PeriodicProcessDeploymentGen()

    val lock = mockedLock {
      // first two calls succeed (pre-finding + pre-deployment acquire), then lock is lost
      Future.successful(if (acquireCount.getAndIncrement() < 2) Some(Instant.MAX) else None)
    }

    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed = Future.successful(Seq(waitingDeployment)),
        deploy = d => { deployProbe.ref ! d; deployPromise.future },
        lock,
        interval
      )
    )

    within(maxWaitTime) { deployProbe.expectMsg(waitingDeployment) }
    Thread.sleep((interval * 3).toMillis)     // renewal ticks return None — lock lost
    deployPromise.success(())                 // deployment completes despite lock loss
    deployProbe.expectNoMessage(interval * 3) // no second deployment (lock still lost)

    system.stop(actor)
  }

  test("should deploy only while being a leader when lock is backed by leadership") {
    val deployProbe       = TestProbe()
    val waitingDeployment = PeriodicProcessDeploymentGen()
    val leadership        = new TogglableLeadership(initiallyLeader = false)
    var toBeDeployed      = Seq(waitingDeployment)

    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed = Future.successful(toBeDeployed),
        deploy = d => { deployProbe.ref ! d; toBeDeployed = Seq.empty; Future.unit },
        new LeaderPeriodicDeploymentLock(leadership),
        interval
      )
    )

    deployProbe.expectNoMessage(interval * 3) // not a leader — nothing is deployed
    leadership.becomeLeader()

    within(maxWaitTime) { deployProbe.expectMsg(waitingDeployment) }

    system.stop(actor)
  }

  test("should skip deploy if lock is lost between finding and deploying, retry on next tick") {
    val deployProbe       = TestProbe()
    val waitingDeployment = PeriodicProcessDeploymentGen()
    val acquireCount      = new AtomicInteger(0)

    val lock = mockedLock {
      // call 0 (pre-find, tick 1): succeed; call 1 (pre-deploy, tick 1): fail; call 2+ (tick 2): succeed
      Future.successful(if (acquireCount.getAndIncrement() == 1) None else Some(Instant.MAX))
    }

    val actor = system.actorOf(
      DeploymentActor.props(
        findToBeDeployed = Future.successful(Seq(waitingDeployment)),
        deploy = d => { deployProbe.ref ! d; Future.unit },
        lock,
        interval
      )
    )

    within(maxWaitTime) { deployProbe.expectMsg(waitingDeployment) }

    system.stop(actor)
  }

}
