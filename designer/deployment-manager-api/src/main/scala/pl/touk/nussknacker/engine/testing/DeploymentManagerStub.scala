package pl.touk.nussknacker.engine.testing

import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.{
  BaseModelData,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class DeploymentManagerStub(implicit ec: ExecutionContext) extends BaseDeploymentManager {

  private val scenarioStatusMap = TrieMap.empty[ProcessName, StateStatus]

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] = command match {
    case _: DMValidateScenarioCommand => Future.successful(())
    case run: DMRunDeploymentCommand =>
      Future {
        scenarioStatusMap.put(run.processVersion.processName, SimpleStateStatus.Running)
        None
      }
    case cancel: DMCancelScenarioCommand =>
      Future.successful {
        scenarioStatusMap.put(cancel.scenarioName, SimpleStateStatus.Canceled)
        ()
      }
    case _: DMStopScenarioCommand | _: DMStopDeploymentCommand | _: DMCancelDeploymentCommand |
        _: DMMakeScenarioSavepointCommand | _: DMRunOffScheduleCommand | _: DMTestScenarioCommand =>
      notImplemented
  }

  override def getScenarioDeploymentsStatuses(
      scenarioName: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future.successful(
      WithDataFreshnessStatus.fresh(scenarioStatusMap.get(scenarioName).map(StatusDetails(_, None)).toList)
    )
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

  override def stateQueryForAllScenariosSupport: StateQueryForAllScenariosSupport = NoStateQueryForAllScenariosSupport

  override def schedulingSupport: SchedulingSupport = NoSchedulingSupport

  override def close(): Unit = {}

}

//This provider can be used for testing. Override methods to implement more complex behaviour
//Provider is registered via ServiceLoader, so it can be used e.g. to run simple docker configuration
class DeploymentManagerProviderStub extends DeploymentManagerProvider {

  override def createDeploymentManager(
      modelData: BaseModelData,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    import deploymentManagerDependencies._
    Validated.valid(new DeploymentManagerStub)
  }

  override def name: String = "stub"

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    FlinkStreamingPropertiesConfig.metaDataInitializer

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] =
    FlinkStreamingPropertiesConfig.properties

}

// This is copy-pasted from flink-manager package - the deployment-manager-api cannot depend on that package - it would create a circular dependency.
// TODO: Replace this class by a BaseDeploymentManagerProvider with default stubbed behavior
object FlinkStreamingPropertiesConfig {

  private val parallelismConfig: (String, ScenarioPropertyConfig) = StreamMetaData.parallelismName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = Some(List(LiteralIntegerValidator, MinimalNumberValidator(1))),
      label = Some("Parallelism"),
      hintText = None
    )

  private val spillStatePossibleValues = List(
    FixedExpressionValue("", "Server default"),
    FixedExpressionValue("false", "False"),
    FixedExpressionValue("true", "True")
  )

  private val asyncPossibleValues = List(
    FixedExpressionValue("", "Server default"),
    FixedExpressionValue("false", "Synchronous"),
    FixedExpressionValue("true", "Asynchronous")
  )

  private val spillStateConfig: (String, ScenarioPropertyConfig) = StreamMetaData.spillStateToDiskName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(FixedValuesParameterEditor(spillStatePossibleValues)),
      validators = Some(List(FixedValuesValidator(spillStatePossibleValues))),
      label = Some("Spill state to disk"),
      hintText = None
    )

  private val asyncInterpretationConfig: (String, ScenarioPropertyConfig) =
    StreamMetaData.useAsyncInterpretationName ->
      ScenarioPropertyConfig(
        defaultValue = None,
        editor = Some(FixedValuesParameterEditor(asyncPossibleValues)),
        validators = Some(List(FixedValuesValidator(asyncPossibleValues))),
        label = Some("IO mode"),
        hintText = None
      )

  private val checkpointIntervalConfig: (String, ScenarioPropertyConfig) = StreamMetaData.checkpointIntervalName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = Some(List(LiteralIntegerValidator, MinimalNumberValidator(1))),
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
