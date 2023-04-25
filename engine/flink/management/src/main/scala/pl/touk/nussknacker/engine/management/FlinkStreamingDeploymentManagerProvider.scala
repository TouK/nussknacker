package pl.touk.nussknacker.engine.management

import _root_.sttp.client3.SttpBackend
import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{BoolParameterEditor, FixedExpressionValue, FixedValuesParameterEditor, FixedValuesValidator, LiteralIntegerValidator, MandatoryParameterValidator, MinimalNumberValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.cache.CachingProcessStateDeploymentManager
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}

import scala.concurrent.{ExecutionContext, Future}

class FlinkStreamingDeploymentManagerProvider extends DeploymentManagerProvider {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Any],
                                       deploymentService: ProcessingTypeDeploymentService): DeploymentManager = {
    val flinkConfig = config.rootAs[FlinkConfig]
    CachingProcessStateDeploymentManager.wrapWithCachingIfNeeded(
      new FlinkStreamingRestManager(flinkConfig, modelData),
      config)
  }

  override def name: String = "flinkStreaming"

  override def typeSpecificInitialData(config: Config): TypeSpecificInitialData = TypeSpecificInitialData(StreamMetaData(Some(1)))

  override def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig] = FlinkStreamingPropertiesConfig.properties

}

object FlinkStreamingDeploymentManagerProvider {

  def defaultDeploymentManager(config: ConfigWithUnresolvedVersion)
                              (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                               sttpBackend: SttpBackend[Future, Any], deploymentService: ProcessingTypeDeploymentService): DeploymentManager = {
    val typeConfig = ProcessingTypeConfig.read(config)
    new FlinkStreamingDeploymentManagerProvider().createDeploymentManager(ModelData(typeConfig), typeConfig.deploymentConfig)
  }

}

object FlinkStreamingPropertiesConfig {

  lazy val properties: Map[String, AdditionalPropertyConfig] =
    Map(parallelismConfig, spillStateConfig, asyncInterpretationConfig, checkpointIntervalConfig)

  private val parallelismConfig: (String, AdditionalPropertyConfig) = "parallelism" ->
    AdditionalPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = Some(List(LiteralIntegerValidator, MinimalNumberValidator(1))),
      label = Some("Parallelism"))

  private val spillStateConfig: (String, AdditionalPropertyConfig) = "spillStateToDisk" ->
    AdditionalPropertyConfig(
      defaultValue = None,
      editor = Some(BoolParameterEditor),
      validators = None,
      label = Some("Spill state to disk"))

  private val asyncPossibleValues = List(
    FixedExpressionValue("", "Server default"),
    FixedExpressionValue("false", "Synchronous"),
    FixedExpressionValue("true", "Asynchronous"))

  private val asyncInterpretationConfig: (String, AdditionalPropertyConfig) = "useAsyncInterpretation" ->
    AdditionalPropertyConfig(
      defaultValue = None,
      editor = Some(FixedValuesParameterEditor(asyncPossibleValues)),
      validators = Some(List(FixedValuesValidator(asyncPossibleValues))),
      label = Some("IO mode"))

  private val checkpointIntervalConfig: (String, AdditionalPropertyConfig) = "checkpointIntervalInSeconds" ->
    AdditionalPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = Some(List(LiteralIntegerValidator, MinimalNumberValidator(1))),
      label = Some("Checkpoint interval in seconds"))

}