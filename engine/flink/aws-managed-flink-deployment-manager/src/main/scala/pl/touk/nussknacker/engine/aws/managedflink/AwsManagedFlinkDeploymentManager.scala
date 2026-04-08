package pl.touk.nussknacker.engine.aws.managedflink

import cats.effect.{IO, Resource, SyncIO}
import cats.effect.unsafe.IORuntime
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.BaseModelDataProvider
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.assembly.UberJarProvider
import pl.touk.nussknacker.engine.aws.S3Client
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.flink.FlinkScenarioCompilationDependencies
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.FlinkMiniClusterScenarioTestRunner
import pl.touk.nussknacker.engine.flink.minicluster.util.DurationToRetryPolicyConverterOps.DurationOps
import software.amazon.awssdk.services.kinesisanalyticsv2.model.RuntimeEnvironment

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class AwsManagedFlinkDeploymentManager(
    modelDataProvider: BaseModelDataProvider,
    config: AwsManagedFlinkDeploymentManagerConfig,
    additionalModelUrls: List[URL]
)(implicit executionContext: ExecutionContext, ioRuntime: IORuntime)
    extends DeploymentManager
    with LazyLogging {

  import FlinkApplicationName.ScenarioNameOps

  private val miniClusterWithServices = FlinkMiniClusterFactory.createMiniClusterWithServices(
    modelDataProvider.modelClassLoader,
    config.miniCluster.config
  )

  private val testRunner = new FlinkMiniClusterScenarioTestRunner(
    modelDataProvider,
    miniClusterWithServices,
    config.scenarioTesting.parallelism,
    config.scenarioTesting.timeout.toPausePolicy
  )

  private val modelJarProvider = new UberJarProvider(
    modelUrls = modelDataProvider.modelClassLoader.urls ++ additionalModelUrls,
    mergeRules = config.applicationJarMergeRules,
    mainClass = EngineSharedProperties.MainClassName,
    uberJarPrefix = "nussknacker-application-code"
  )

  private val s3Client = new S3Client(
    bucketName = config.bucketName,
    region = config.region,
    accessKeyId = config.awsAccessKeyId,
    secretAccessKey = config.awsSecretAccessKey
  )

  private val flinkClient = new AwsManagedFlinkClient(
    region = config.region,
    accessKeyId = config.awsAccessKeyId,
    secretAccessKey = config.awsSecretAccessKey,
    serviceExecutionRoleArn = config.serviceExecutionRoleArn,
    logStreamArn = config.cloudWatchLogStreamArn,
    runtimeEnviornment = RuntimeEnvironment.FLINK_2_2
  )

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] = {
    logger.trace(s"Received command: $command")
    command match {
      // TODO: handle stateRestoringStrategy
      // TODO: wait for deployment to finish
      case DMRunDeploymentCommand(scenarioVersion, deploymentData, scenario, updateStrategy) =>
        updateStrategy match {
          case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(_) =>
            deploy(scenarioVersion, deploymentData, scenario).unsafeToFuture()
          case DeploymentUpdateStrategy.DontReplaceDeployment =>
            throw new IllegalArgumentException(s"Deployment update strategy: $updateStrategy is not supported")
        }
      // TODO: before stop, check if scenario exists and if status it cancellable
      case DMCancelScenarioCommand(scenarioName, _) =>
        flinkClient.stopApplication(scenarioName.toFlinkApplicationName).unsafeToFuture()

      case DMTestScenarioCommand(_, canonicalProcess, scenarioTestData) =>
        testRunner.runTests(canonicalProcess, scenarioTestData)

      case DMValidateScenarioCommand(_, _, _, _) => Future.unit

      case _: DMCancelDeploymentCommand | _: DMStopDeploymentCommand | _: DMStopScenarioCommand |
          _: DMMakeScenarioSavepointCommand | _: DMRunOffScheduleCommand =>
        notImplemented
    }
  }

  private def deploy(
      scenarioVersion: ProcessVersion,
      deploymentData: DeploymentData,
      scenario: CanonicalProcess
  ): IO[Option[ExternalDeploymentId]] = {
    val applicationName = scenarioVersion.processName.toFlinkApplicationName
    val deploymentProperties = AwsManagedFlinkDeploymentPropertiesProvider.buildDeploymentProperties(
      scenario,
      scenarioVersion,
      deploymentData,
      modelDataProvider.getCurrentModelData()
    )
    for {
      jar                          <- IO.blocking(modelJarProvider.createOrGetUberJar())
      deploymentPropertiesLocation <- s3Client.upload(deploymentProperties.s3Key, deploymentProperties.content)
      jarLocation                  <- s3Client.upload(jar)
      applicationOpt               <- flinkClient.findApplication(applicationName)
      _ <- applicationOpt match {
        case Some(appDetails) =>
          logger.trace(s"Application: '${appDetails.applicationName()}' already exists. Updating.")
          flinkClient.updateApplication(
            applicationDetail = appDetails,
            applicationJarLocation = jarLocation,
            deploymentPropertiesLocation = deploymentPropertiesLocation
          )
        case None =>
          logger.trace(s"Creating application '$applicationName'")
          flinkClient.createApplication(
            name = applicationName,
            applicationJarLocation = jarLocation,
            deploymentPropertiesLocation = deploymentPropertiesLocation
          )
      }
      _ <- {
        logger.trace(s"Running application '$applicationName'")
        flinkClient.runApplication(applicationName)
      }
    } yield None
  }

  override def close(): Unit = {
    logger.info("Closing AWS Managed Flink Deployment Manager")
    miniClusterWithServices.close()
    s3Client.close()
    flinkClient.close()
  }

  override def scenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies] = {
    miniClusterWithServices
      .createDetachedStreamExecutionEnvironment[SyncIO]
      .map(new FlinkScenarioCompilationDependencies(_))
  }

  // TODO: implement
  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

  // TODO: implement
  override def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
    NoDeploymentsStatusesQueryForAllScenariosSupport

  override def schedulingSupport: SchedulingSupport = NoSchedulingSupport

  // TODO: implement
  override def getScenarioDeploymentsStatuses(scenarioName: ProcessName)(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[DeploymentStatusDetails]]] =
    Future.successful(WithDataFreshnessStatus.fresh(List.empty))

  // TODO: implement
  override def processStateDefinitionManager: ProcessStateDefinitionManager = ManagedFlinkStateDefinitionManager

  private object ManagedFlinkStateDefinitionManager
      extends OverridingProcessStateDefinitionManager(
        delegate = SimpleProcessStateDefinitionManager
      )

  override def liveDataPreviewSupport: LiveDataPreviewSupport = NoLiveDataPreviewSupport

}
