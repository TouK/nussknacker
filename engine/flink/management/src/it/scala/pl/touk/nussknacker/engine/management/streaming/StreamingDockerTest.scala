package pl.touk.nussknacker.engine.management.streaming

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, BeforeAndAfterAll, OptionValues, Suite}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.management.DockerTest
import pl.touk.nussknacker.engine.util.loader.DeploymentManagersClassLoader

trait StreamingDockerTest extends DockerTest with BeforeAndAfterAll with Matchers with OptionValues {
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
    DeploymentManagersClassLoader.create(List.empty).allocated.unsafeRunSync()

  protected lazy val deploymentManager = FlinkStreamingDeploymentManagerProviderHelper.createDeploymentManager(
    ConfigWithUnresolvedVersion(config),
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
      stateRestoringStrategy: StateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
  ): Assertion = {
    deployProcess(process, processVersion, stateRestoringStrategy)
    eventually {
      val jobStatuses = deploymentManager.getProcessStates(process.name).futureValue.value
      logger.debug(s"Waiting for deploy: ${process.name}, $jobStatuses")

      jobStatuses.map(_.status) should contain(SimpleStateStatus.Running)
    }
  }

  protected def deployProcess(
      process: CanonicalProcess,
      processVersion: ProcessVersion,
      stateRestoringStrategy: StateRestoringStrategy = StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
  ): Option[ExternalDeploymentId] = {
    deploymentManager
      .processCommand(
        DMRunDeploymentCommand(
          processVersion,
          DeploymentData.empty,
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
        .getProcessStates(processName)
        .futureValue
        .value
      val runningOrDuringCancelJobs = statuses
        .filter(state => Set(SimpleStateStatus.Running, SimpleStateStatus.DuringCancel).contains(state.status))

      logger.debug(s"waiting for jobs: $processName, $statuses")
      if (runningOrDuringCancelJobs.nonEmpty) {
        throw new IllegalStateException("Job still exists")
      }
    }
  }

}
