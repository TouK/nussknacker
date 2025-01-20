package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.scalalogging.LazyLogging
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

trait StreamingDockerTest extends DockerTest with BeforeAndAfterAll with Matchers with OptionValues {
  self: Suite with LazyLogging =>

  protected implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  protected var kafkaClient: KafkaClient = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient = new KafkaClient(hostKafkaAddress, self.suiteName)
    logger.info("Kafka client created")
  }

  override def afterAll(): Unit = {
    kafkaClient.shutdown()
    logger.info("Kafka client closed")
    deploymentManager.close()
    super.afterAll()
  }

  protected lazy val deploymentManager: DeploymentManager =
    FlinkStreamingDeploymentManagerProviderHelper.createDeploymentManager(ConfigWithUnresolvedVersion(config))

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
