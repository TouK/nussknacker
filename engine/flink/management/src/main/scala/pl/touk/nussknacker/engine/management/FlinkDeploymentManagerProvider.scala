package pl.touk.nussknacker.engine.management

import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.readers.ValueReader
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode.LiveDataStorage
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.cache.CachingProcessStateDeploymentManager
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.management.FlinkConfig.RestUrlPath
import pl.touk.nussknacker.engine.management.jobrunner.{FlinkMiniClusterScenarioJobRunner, RemoteFlinkScenarioJobRunner}
import pl.touk.nussknacker.engine.management.rest.FlinkClient

import scala.collection.compat._
import scala.compat.java8.FutureConverters._
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.util.Try

class FlinkDeploymentManagerProvider extends DeploymentManagerProvider {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  private implicit val flinkConfigurationValueReader: ValueReader[Configuration] =
    Ficus.mapValueReader[String].map(map => Configuration.fromMap(map.asJava))

  override def createDeploymentManager(
      modelDataProvider: BaseModelDataProvider,
      dependencies: DeploymentManagerDependencies,
      deploymentConfig: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    val flinkConfig = deploymentConfig.rootAs[FlinkConfig]
    FlinkDeploymentManagerProvider
      .createDeploymentManager(modelDataProvider, dependencies, flinkConfig, scenarioStateCacheTTL)
      .toValidatedNel
  }

  override def name: String = "flinkStreaming"

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    FlinkStreamingPropertiesConfig.metaDataInitializer

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] =
    FlinkStreamingPropertiesConfig.properties

  override def defaultEngineSetupName: EngineSetupName = EngineSetupName("Flink")

  override def engineSetupIdentity(config: Config): Any = {
    // We don't parse the whole config because some other properties can be unspecified and it would
    // cause generation of wrong identity. We also use a Try to handle missing or invalid rest url path
    Try(config.getString(RestUrlPath)).toOption
  }

  override def jobsRecoverySettings(deploymentConfig: Config): JobsRecoverySettings = {
    val flinkConfig = deploymentConfig.rootAs[FlinkConfig]
    JobsRecoverySettings(recoverJobsOnStart = flinkConfig.useMiniClusterForDeployment)
  }

}

object FlinkDeploymentManagerProvider extends LazyLogging {

  private[management] def createDeploymentManager(
      modelDataProvider: BaseModelDataProvider,
      dependencies: DeploymentManagerDependencies,
      flinkConfig: FlinkConfig,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): Validated[String, DeploymentManager] = {
    logger.info("Creating FlinkStreamingDeploymentManager")
    import dependencies._
    val miniClusterWithServices = FlinkMiniClusterFactory.createMiniClusterWithServices(
      modelDataProvider.modelClassLoader,
      flinkConfig.miniCluster.config,
    )
    val miniClusterJobManagerUriOpt = Option.when(flinkConfig.useMiniClusterForDeployment) {
      Await.result(
        miniClusterWithServices.miniCluster.getRestAddress.toScala,
        flinkConfig.miniCluster.waitForJobManagerRestAPIAvailableTimeout
      )
    }
    flinkConfig
      .parseHttpClientConfig(miniClusterJobManagerUriOpt, scenarioStateCacheTTL)
      .map { parsedHttpClientConfig =>
        val client = FlinkClient.create(parsedHttpClientConfig)
        val jobRunner =
          if (flinkConfig.useMiniClusterForDeployment)
            new FlinkMiniClusterScenarioJobRunner(miniClusterWithServices, modelDataProvider)
          else
            new RemoteFlinkScenarioJobRunner(modelDataProvider, client)
        val liveDataPreviewSupport = createLiveDataPreviewSupport(
          flinkConfig.useMiniClusterForDeployment,
          modelDataProvider.getCurrentModelData().modelConfig.liveDataPreviewMode,
        )
        val underlying =
          new FlinkDeploymentManager(
            modelDataProvider,
            dependencies,
            flinkConfig,
            miniClusterWithServices,
            client,
            jobRunner,
            liveDataPreviewSupport,
          )
        CachingProcessStateDeploymentManager.wrapWithCachingIfNeeded(underlying, scenarioStateCacheTTL)
      }
  }

  private def createLiveDataPreviewSupport(
      useMiniClusterForDeployment: Boolean,
      liveDataPreviewMode: LiveDataPreviewMode
  ) = {
    if (useMiniClusterForDeployment) {
      liveDataPreviewMode match {
        case LiveDataPreviewMode.Enabled(_, _, _, _) =>
          LiveDataPreviewStoredInDesignerJvm
        case LiveDataPreviewMode.Disabled =>
          NoLiveDataPreviewSupport
      }
    } else {
      liveDataPreviewMode match {
        case LiveDataPreviewMode.Enabled(_, _, _, LiveDataStorage.DesignerJvm) =>
          throw new IllegalArgumentException(
            s"Invalid configuration of live data. Flink deployment manager is configured to use standalone flink, but the db storage is not configured for live data synchronization. Please check modelConfig.liveDataPreview.storage config section."
          )
        case LiveDataPreviewMode.Enabled(maxSamples, _, _, storage: LiveDataStorage.DesignerDb) =>
          LiveDataPreviewStoredInDesignerDb(maxSamples, storage.uploadIntervalInSeconds)
        case LiveDataPreviewMode.Disabled =>
          NoLiveDataPreviewSupport
      }
    }
  }

}

//Properties from this class should be public because implementing projects should be able to reuse them
object FlinkStreamingPropertiesConfig {

  val parallelismConfig: (String, ScenarioPropertyConfig) = StreamMetaData.parallelismName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(StaticStringParameterEditor),
      validators = Some(List(LiteralIntegerExpressionValidator, MinimalNumberValidator(1))),
      label = Some("Parallelism"),
      hintText = None
    )

  val spillStatePossibleValues = List(
    FixedExpressionValue("", "Server default"),
    FixedExpressionValue("false", "False"),
    FixedExpressionValue("true", "True")
  )

  val asyncPossibleValues = List(
    FixedExpressionValue("", "Server default"),
    FixedExpressionValue("false", "Synchronous"),
    FixedExpressionValue("true", "Asynchronous")
  )

  val spillStateConfig: (String, ScenarioPropertyConfig) = StreamMetaData.spillStateToDiskName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(FixedValuesParameterEditor(spillStatePossibleValues)),
      validators = Some(List(FixedValuesExpressionValidator(spillStatePossibleValues))),
      label = Some("Spill state to disk"),
      hintText = None
    )

  val asyncInterpretationConfig: (String, ScenarioPropertyConfig) = StreamMetaData.useAsyncInterpretationName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(FixedValuesParameterEditor(asyncPossibleValues)),
      validators = Some(List(FixedValuesExpressionValidator(asyncPossibleValues))),
      label = Some("IO mode"),
      hintText = None
    )

  val checkpointIntervalConfig: (String, ScenarioPropertyConfig) = StreamMetaData.checkpointIntervalName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(StaticStringParameterEditor),
      validators = Some(List(LiteralIntegerExpressionValidator, MinimalNumberValidator(1))),
      label = Some("Checkpoint interval in seconds"),
      hintText = None
    )

  val properties: Map[String, ScenarioPropertyConfig] =
    Map(parallelismConfig, spillStateConfig, asyncInterpretationConfig, checkpointIntervalConfig)

  val metaDataInitializer: MetaDataInitializer = MetaDataInitializer(
    metadataType = StreamMetaData.typeName,
    overridingProperties = Map(StreamMetaData.parallelismName -> "1", StreamMetaData.spillStateToDiskName -> "true")
  )

}
