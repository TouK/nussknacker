package pl.touk.nussknacker.engine.management.streaming

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, OptionValues, Suite}
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.classloader.DeploymentManagersClassLoaderFactory
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.management.FlinkKafkaDockerTest

import java.util.UUID

trait StreamingDockerTest extends FlinkKafkaDockerTest with BeforeAndAfterAll with Matchers with OptionValues {
  // Warning: we need StrictLogging capability instead of LazyLogging because with LazyLogging we had a deadlock during kafkaClient allocation
  self: Suite with StrictLogging =>

  protected implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  protected lazy val (kafkaClient, releaseKafkaClient) =
    Resource
      .make(
        acquire = IO(new KafkaClient(hostKafkaAddress, self.suiteName))
          .map { client =>
            logger.info("Kafka client created")
            client
          }
      )(
        release = client => IO(client.shutdown()).map(_ => logger.info("Kafka client closed"))
      )
      .allocated
      .unsafeRunSync()

  protected lazy val (deploymentManagerClassLoader, releaseDeploymentManagerClassLoaderResources) =
    DeploymentManagersClassLoaderFactory.create(List.empty).allocated.unsafeRunSync()

  protected lazy val deploymentManager: DeploymentManager =
    FlinkDeploymentManagerProviderHelper.createDeploymentManager(
      ConfigWithUnresolvedVersion(rawProcessingTypeConfig),
      deploymentManagerClassLoader
    )

  override def afterAll(): Unit = {
    releaseKafkaClient.unsafeToFuture()
    deploymentManager.close()
    releaseDeploymentManagerClassLoaderResources.unsafeToFuture()
    super.afterAll()
  }

  protected def deployProcessAndWaitIfRunning(
      process: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentId: DeploymentId = DeploymentId(UUID.randomUUID().toString),
      stateRestoringStrategy: StateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
  ): Option[ExternalDeploymentId] = {
    val version              = processVersion.versionId
    val externalDeploymentId = deployProcess(process, processVersion, deploymentId, stateRestoringStrategy)
    eventually {
      val jobStatuses = deploymentManager.getScenarioDeploymentsStatuses(process.name).futureValue.value
      logger.debug(
        s"Waiting for deploy: ${process.name}, version: $version, deployment id: $deploymentId, $jobStatuses, "
      )

      atLeast(1, jobStatuses) should matchPattern {
        case DeploymentStatusDetails(SimpleStateStatus.Running(`version`, _), Some(`deploymentId`)) =>
      }
    }
    externalDeploymentId
  }

  protected def deployProcess(
      process: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentId: DeploymentId,
      stateRestoringStrategy: StateRestoringStrategy
  ): Option[ExternalDeploymentId] = {
    deploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          processVersion,
          DeploymentData.empty.copy(deploymentId = deploymentId),
          process,
          DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(stateRestoringStrategy)
        )
      )
      .futureValue
  }

  protected def cancelProcess(processName: ProcessName): Unit = {
    deploymentManager.processCommand(DMCancelScenarioCommand(processName, user = userToAct)).futureValue
    eventually {
      val statuses = deploymentManager
        .getScenarioDeploymentsStatuses(processName)
        .futureValue
        .value
      val runningOrDuringCancelJobs = statuses
        .filter { s =>
          s.status match {
            case _: SimpleStateStatus.Running | SimpleStateStatus.DuringCancel => true
            case _                                                             => false
          }
        }

      logger.debug(s"waiting for jobs: $processName, $statuses")
      if (runningOrDuringCancelJobs.nonEmpty) {
        throw new IllegalStateException("Job still exists")
      }
    }
  }

}
