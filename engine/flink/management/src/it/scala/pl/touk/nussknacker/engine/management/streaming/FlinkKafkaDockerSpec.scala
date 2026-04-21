package pl.touk.nussknacker.engine.management.streaming

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.dimafeng.testcontainers.{Container, ForAllTestContainer, LazyContainer, MultipleContainers}
import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.StrictLogging
import org.apache.flink.util.FileUtils
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
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.flink.test.docker.{WithFlinkContainers, WithKafkaContainer}
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.management.WithProcessingTypeConfig
import pl.touk.nussknacker.engine.management.savepoint.FlinkSavepointLocator
import pl.touk.nussknacker.engine.management.savepoint.FlinkSavepointLocator.LocalSavepoint
import pl.touk.nussknacker.test.{ExtremelyPatientScalaFutures, KafkaConfigProperties}

import java.net.URI
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

trait FlinkKafkaDockerSpec
    extends BeforeAndAfterAll
    with ForAllTestContainer
    with WithFlinkContainers
    with WithKafkaContainer
    with WithProcessingTypeConfig
    with ExtremelyPatientScalaFutures
    with Matchers
    with OptionValues {
  // Warning: we need StrictLogging capability instead of LazyLogging because with LazyLogging we had a deadlock during kafkaClient allocation
  self: Suite with StrictLogging =>

  protected val userToAct: User = User("testUser", "Test User")

  protected def useMiniClusterForDeployment: Boolean

  override lazy val container: Container = MultipleContainers(
    (kafkaContainer: LazyContainer[_]) :: (if (useMiniClusterForDeployment) Nil else flinkContainers): _*
  )

  private var savepointDirInitialized: Boolean = false

  protected lazy val savepointDir: Path = {
    savepointDirInitialized = true
    val tempPath = Files.createTempDirectory(getClass.getSimpleName)
    tempPath.toFile.deleteOnExit()
    tempPath
  }

  protected val kafkaComponentsConfigPrefix = "modelConfig.components.kafka.config"

  override def resolveProcessingTypeConfig(config: Config): Config = {
    val baseConfig = super
      .resolveProcessingTypeConfig(config)
      .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(modelClassPath.asJava))
      .withValue("modelConfig.enableObjectReuse", fromAnyRef(false))
      .withValue(
        KafkaConfigProperties.property(kafkaComponentsConfigPrefix, "auto.offset.reset"),
        fromAnyRef("earliest")
      )
      .withValue("category", fromAnyRef("Category1"))
      .withValue(
        s"$kafkaComponentsConfigPrefix.topicsExistenceValidationConfig.enabled",
        ConfigValueFactory.fromAnyRef("false")
      )
    if (useMiniClusterForDeployment) {
      logger.debug(s"Using Flink MiniCluster - setting Kafka's bootstrap.servers to $hostKafkaAddress")
      baseConfig
        .withValue("deploymentConfig.useMiniClusterForDeployment", fromAnyRef(true))
        .withValue(
          "deploymentConfig.miniCluster.config.\"execution.checkpointing.savepoint-dir\"",
          fromAnyRef(savepointDir.toFile.toURI.toString)
        )
        .withValue(
          KafkaConfigProperties.bootstrapServersProperty("modelConfig.components.kafka.config"),
          fromAnyRef(hostKafkaAddress)
        )
    } else {
      logger.debug(
        s"Using Flink from docker - setting restUrl to $jobManagerRestUrl and Kafka's bootstrap.servers to $dockerKafkaAddress"
      )
      baseConfig
        .withValue("deploymentConfig.restUrl", fromAnyRef(jobManagerRestUrl))
        .withValue(
          KafkaConfigProperties.bootstrapServersProperty("modelConfig.components.kafka.config"),
          fromAnyRef(dockerKafkaAddress)
        )
        // We have to turn on this flag, because DM is created locally, and during scenario state verification,
        // it can't connect to Kafka at dockerKafkaAddress which is in docker network.
        .withValue(
          s"$kafkaComponentsConfigPrefix.allowNotSuggestedTopicUsage",
          ConfigValueFactory.fromAnyRef("true")
        )
    }
  }

  protected def modelClassPath: List[String]

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
      deploymentManagerClassLoader,
      new JobManagerSavepointLocator
    )

  override def afterAll(): Unit = {
    releaseKafkaClient.unsafeRunSync()
    deploymentManager.close()
    releaseDeploymentManagerClassLoaderResources.unsafeRunSync()
    if (savepointDirInitialized) {
      try {
        FileUtils.deleteDirectory(savepointDir.toFile)
      } catch {
        case NonFatal(e) => logger.warn(s"Failed to delete temp savepoint directory $savepointDir (${e.getMessage})")
      }
    }
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

  private class JobManagerSavepointLocator extends FlinkSavepointLocator {

    override def locateSavepoint(path: String): Future[FlinkSavepointLocator.Savepoint] = Future.successful {
      val uri = URI.create(path)
      if (uri.getScheme != "file") {
        throw new IllegalArgumentException(s"Invalid savepoint path, expected file URI but got: $path")
      }
      val targetDir = savepointDir.resolve(uri.getPath.split("/").last)
      copyDirectoryFromContainer(jobManagerContainer.container, uri.getPath, targetDir)
      LocalSavepoint(targetDir.toUri.toURL.toString)
    }

  }

}
