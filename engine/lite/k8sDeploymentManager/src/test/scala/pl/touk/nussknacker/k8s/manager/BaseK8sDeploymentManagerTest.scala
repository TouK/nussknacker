package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, BeforeAndAfterAll}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.{ExtremelyPatientScalaFutures, VeryPatientScalaFutures}
import skuber.LabelSelector.dsl._
import skuber.api.client.KubernetesClient
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.{ConfigMap, LabelSelector, ListResource, Pod, Resource, Secret, Service, k8sInit}

import scala.concurrent.Future

class BaseK8sDeploymentManagerTest extends AnyFunSuite with Matchers with ExtremelyPatientScalaFutures with BeforeAndAfterAll {

  protected implicit val system: ActorSystem = ActorSystem()
  import system.dispatcher
  protected lazy val k8s: KubernetesClient = k8sInit
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
    Future.sequence(List(
      k8s.deleteAllSelected[ListResource[Service]](selector),
      k8s.deleteAllSelected[ListResource[Deployment]](selector),
      k8s.deleteAllSelected[ListResource[ConfigMap]](selector),
      k8s.deleteAllSelected[ListResource[Secret]](selector),
      k8s.delete[Resource.Quota]("nu-pods-limit")
    )).futureValue
    assertNoGarbageLeft()
  }

  protected def assertNoGarbageLeft(): Assertion = {
    val selector = LabelSelector(K8sDeploymentManager.scenarioNameLabel)
    eventually {
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

}

class K8sDeploymentManagerTestFixture(val manager: K8sDeploymentManager, val scenario: EspProcess, val version: ProcessVersion) extends VeryPatientScalaFutures with Matchers {
  def withRunningScenario(action: => Unit): Unit = {
    manager.deploy(version, DeploymentData.empty, scenario.toCanonicalProcess, None).futureValue
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