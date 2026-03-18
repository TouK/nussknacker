package pl.touk.nussknacker.k8s.manager

import cats.effect.unsafe.IORuntime
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable}
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.scalatest._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelData}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.{requirementForName, scenarioVersionLabel}
import pl.touk.nussknacker.test.{ExtremelyPatientScalaFutures, VeryPatientScalaFutures}
import skuber.{k8sInit, ConfigMap, LabelSelector, ListResource, Pod, Resource, Secret, Service}
import skuber.LabelSelector.dsl._
import skuber.api.client.KubernetesClient
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.networking.v1.Ingress
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.language.reflectiveCalls
import scala.util.control.NonFatal

class BaseK8sDeploymentManagerTest
    extends AnyFunSuite
    with Matchers
    with VeryPatientScalaFutures
    with BeforeAndAfterAll {
  self: LazyLogging =>

  import system.dispatcher

  private implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  protected implicit val system: ActorSystem      = ActorSystem(getClass.getSimpleName)
  protected val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()

  protected lazy val k8s: KubernetesClient = k8sInit(system)
  protected lazy val k8sTestUtils          = new K8sTestUtils(k8s)

  protected def baseDeployConfig(mode: String): Config = ConfigFactory.empty
    .withValue("mode", fromAnyRef(mode))
    .withValue(
      "k8sDeploymentConfig.spec.template.spec.containers",
      fromIterable(List(baseRuntimeContainerConfig.root()).asJava)
    )

  protected val baseRuntimeContainerConfig: Config = ConfigFactory.empty
    .withValue("name", fromAnyRef("runtime"))
    .withValue("imagePullPolicy", fromAnyRef("Never"))

  protected def prepareManager(modelData: ModelData, deployConfig: Config): K8sDeploymentManager = {
    val dependencies = new DeploymentManagerDependencies(
      system.dispatcher,
      IORuntime.global,
      system,
      backend
    )
    new K8sDeploymentManager(
      modelData.toModelDataProvider,
      K8sDeploymentManagerConfig.parse(deployConfig),
      deployConfig,
      dependencies
    )
  }

  override protected def beforeAll(): Unit = {
    // cleanup just in case...
    cleanup()
  }

  protected def cleanup(): Unit = {
    val selector = LabelSelector(K8sDeploymentManager.scenarioNameLabel)
    Future
      .sequence(
        k8s
          .listSelected[ListResource[Service]](selector)
          .futureValue
          .map(_.name)
          .map(k8s.delete[Service](_)) ++
          List(
            k8s.deleteAllSelected[ListResource[Ingress]](selector),
            k8s.deleteAllSelected[ListResource[Deployment]](selector),
            k8s.deleteAllSelected[ListResource[ConfigMap]](selector),
            k8s.deleteAllSelected[ListResource[Secret]](selector),
            k8sTestUtils.deleteIfExists[Resource.Quota]("nu-pods-limit")
          )
      )
      .futureValue
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
    backend.close()
  }

  def waitForRunning(
      manager: K8sDeploymentManager,
      version: ProcessVersion,
      waitForOldPodsTerminated: Boolean = false
  ): Unit = {
    eventually {
      val state = manager.getScenarioDeploymentsStatuses(version.processName).map(_.value).futureValue
      state.length shouldBe 1
      state.map(_.status).headOption should matchPattern {
        case Some(s: SimpleStateStatus.Running) if s.version == version.versionId =>
      }
    }

    if (waitForOldPodsTerminated) {
      // DM tells k8s to shut down old pods, but doesn't wait for this to happen
      eventually {
        k8s
          .listSelected[ListResource[Pod]](
            LabelSelector(
              requirementForName(version.processName),
              scenarioVersionLabel isNot version.versionId.value.toString
            )
          )
          .futureValue
          .items shouldBe empty
      }
    }
  }

  class K8sDeploymentManagerTestFixture(
      val manager: K8sDeploymentManager,
      val scenario: CanonicalProcess,
      val version: ProcessVersion
  ) extends ExtremelyPatientScalaFutures
      with Matchers
      with LazyLogging {

    final def withRunningScenario(action: => Unit): Unit = {
      withRunningScenarioWithDeployParams(nodesData = NodesDeploymentData.empty)(action)
    }

    def withRunningScenarioWithDeployParams(nodesData: NodesDeploymentData)(action: => Unit): Unit = {
      manager
        .processCommand(
          DMRunDeploymentCommand(
            version,
            DeploymentData.empty.copy(nodesData = nodesData),
            scenario,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            )
          )
        )
        .futureValue
      try {
        waitForRunning(manager, version)
        action
      } catch {
        case NonFatal(ex) =>
          k8sTestUtils.printResourcesDetails()
          throw ex
      } finally {
        manager.processCommand(DMCancelScenarioCommand(version.processName, DeploymentData.systemUser)).futureValue
        eventually {
          manager.getScenarioDeploymentsStatuses(version.processName).futureValue.value shouldBe List.empty
        }
      }
    }

  }

}
