package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.ExtremelyPatientScalaFutures
import skuber.LabelSelector.dsl._
import skuber.api.client.KubernetesClient
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.networking.v1.Ingress
import skuber.{ConfigMap, LabelSelector, ListResource, Pod, Resource, Secret, Service, k8sInit}

import scala.concurrent.Future
import scala.util.control.NonFatal

class BaseK8sDeploymentManagerTest extends AnyFunSuite with Matchers with ExtremelyPatientScalaFutures with BeforeAndAfterAll {
  self: LazyLogging =>

  protected implicit val system: ActorSystem = ActorSystem()

  import system.dispatcher

  protected lazy val k8s: KubernetesClient = k8sInit
  protected lazy val k8sTestUtils = new K8sTestUtils(k8s)
  protected val dockerTag = sys.env.getOrElse("dockerTagName", BuildInfo.version)

  protected def baseDeployConfig(mode: String): Config = ConfigFactory.empty
    .withValue("dockerImageTag", fromAnyRef(dockerTag))
    .withValue("mode", fromAnyRef(mode))

  override protected def beforeAll(): Unit = {
    //cleanup just in case...
    cleanup()
  }

  protected def cleanup(): Unit = {
    val selector = LabelSelector(K8sDeploymentManager.scenarioNameLabel)
    Future.sequence(
      k8s.listSelected[ListResource[Service]](selector)
        .futureValue
        .map(_.name)
        .map(k8s.delete[Service](_)) ++
        List(
          k8s.deleteAllSelected[ListResource[Ingress]](selector),
          k8s.deleteAllSelected[ListResource[Deployment]](selector),
          k8s.deleteAllSelected[ListResource[ConfigMap]](selector),
          k8s.deleteAllSelected[ListResource[Secret]](selector),
          k8sTestUtils.deleteIfExists[Resource.Quota]("nu-pods-limit")
        )).futureValue
    assertNoGarbageLeft()
  }

  protected def assertNoGarbageLeft(): Assertion = {
    val selector = LabelSelector(K8sDeploymentManager.scenarioNameLabel)
    eventually {
      k8s.listSelected[ListResource[Ingress]](selector).futureValue.items shouldBe Nil
      k8s.listSelected[ListResource[Service]](selector).futureValue.items shouldBe Nil
      k8s.listSelected[ListResource[Deployment]](selector).futureValue.items shouldBe Nil
      k8s.listSelected[ListResource[ConfigMap]](selector).futureValue.items shouldBe Nil
      k8s.listSelected[ListResource[Secret]](selector).futureValue.items shouldBe Nil
      k8s.listSelected[ListResource[Pod]](selector).futureValue.items shouldBe Nil
    }
  }

  override protected def afterAll(): Unit = {
    cleanup()
  }

  override def run(testName: Option[String], args: Args): Status = {
    try {
      val status = super.run(testName, args)
      if (status == FailedStatus) {
        logger.error(s"Test: $testName failed. Dumping cluster-info")
        dumpClusterInfoSupressExceptions()
      }
      status
    } catch {
      case NonFatal(ex) =>
        dumpClusterInfoSupressExceptions()
        throw ex
    }
  }

  private def dumpClusterInfoSupressExceptions(): Unit = {
    try {
      k8sTestUtils.dumpClusterInfo()
    } catch {
      case NonFatal(ex) =>
        logger.warn("Some error during cluster-info dump occurred", ex)
    }
  }

}

class K8sDeploymentManagerTestFixture(val manager: K8sDeploymentManager, val scenario: CanonicalProcess, val version: ProcessVersion) extends ExtremelyPatientScalaFutures with Matchers {

  def withRunningScenario(action: => Unit): Unit = {
    manager.deploy(version, DeploymentData.empty, scenario, None).futureValue
    eventually {
      val state = manager.findJobStatus(version.processName).futureValue
      state.flatMap(_.version) shouldBe Some(version)
      state.map(_.status) shouldBe Some(SimpleStateStatus.Running)
    }

    try {
      action
    } finally {
      manager.cancel(version.processName, DeploymentData.systemUser).futureValue
      eventually {
        manager.findJobStatus(version.processName).futureValue shouldBe None
      }
    }
  }

}