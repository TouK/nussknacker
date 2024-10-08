package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelData}
import pl.touk.nussknacker.test.{ExtremelyPatientScalaFutures, VeryPatientScalaFutures}
import skuber.LabelSelector.dsl._
import skuber.Pod.LogQueryParams
import skuber.api.client.KubernetesClient
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.networking.v1.Ingress
import skuber.{ConfigMap, Event, LabelSelector, ListResource, Pod, Resource, Secret, Service, k8sInit}
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

class BaseK8sDeploymentManagerTest
    extends AnyFunSuite
    with Matchers
    with VeryPatientScalaFutures
    with BeforeAndAfterAll {
  self: LazyLogging =>

  private implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
  private implicit val system: ActorSystem                  = ActorSystem(getClass.getSimpleName)
  import system.dispatcher
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
    val dependencies = DeploymentManagerDependencies(
      new ProcessingTypeDeployedScenariosProviderStub(List.empty),
      new ProcessingTypeActionServiceStub,
      new ScenarioActivityManagerStub,
      system.dispatcher,
      system,
      backend
    )
    new K8sDeploymentManager(modelData, K8sDeploymentManagerConfig.parse(deployConfig), deployConfig, dependencies)
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
  }

  class K8sDeploymentManagerTestFixture(
      val manager: K8sDeploymentManager,
      val scenario: CanonicalProcess,
      val version: ProcessVersion
  ) extends ExtremelyPatientScalaFutures
      with Matchers
      with LazyLogging {

    def withRunningScenario(action: => Unit): Unit = {
      manager
        .processCommand(
          DMRunDeploymentCommand(
            version,
            DeploymentData.empty,
            scenario,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            )
          )
        )
        .futureValue
      try {
        waitForRunning(version)
        action
      } catch {
        case NonFatal(ex) =>
          Try(printResourcesDetails()).failed.foreach { ex =>
            logger.warn("Failure during printResourcesDetails", ex)
          }
          throw ex
      } finally {
        manager.processCommand(DMCancelScenarioCommand(version.processName, DeploymentData.systemUser)).futureValue
        eventually {
          manager.getProcessStates(version.processName).futureValue.value shouldBe List.empty
        }
      }
    }

    def waitForRunning(version: ProcessVersion): Assertion = {
      eventually {
        val state = manager.getProcessStates(version.processName).map(_.value).futureValue
        state.flatMap(_.version) shouldBe List(version)
        state.map(_.status) shouldBe List(SimpleStateStatus.Running)
      }
    }

    private def printResourcesDetails(): Unit = {
      val pods = k8s.list[ListResource[Pod]]().futureValue.items
      logger.info("pods:\n" + pods.mkString("\n"))
      logger.info("services:\n" + k8s.list[ListResource[Service]]().futureValue.items.mkString("\n"))
      logger.info("deployments:\n" + k8s.list[ListResource[Deployment]]().futureValue.items.mkString("\n"))
      logger.info("ingresses:\n" + k8s.list[ListResource[Ingress]]().futureValue.items.mkString("\n"))
      logger.info("events:\n" + k8s.list[ListResource[Event]]().futureValue.items.mkString("\n"))
      pods.foreach { p =>
        logger.info(s"Printing logs for pod: ${p.name}")
        // It looks like it is a common situation that waiting for logs take a longer time than patient config.
        // I guess that for still running pods, it can wait forever. Even if futureValue failed, printing of logs would
        // still be continued. Because of that, I silently ignore this error
        Try(
          k8s.getPodLogSource(p.name, LogQueryParams()).futureValue.runForeach(bs => println(bs.utf8String)).futureValue
        )
        logger.info(s"Finished printing logs for pod: ${p.name}")
      }
    }

  }

}
