package pl.touk.nussknacker.engine.testing

import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.SingleScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.properties.ScenarioProperties
import pl.touk.nussknacker.engine.deployment.CustomActionDefinition
import pl.touk.nussknacker.engine.newdeployment
import pl.touk.nussknacker.engine.{
  BaseModelData,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DeploymentManagerStub extends BaseDeploymentManager with StubbingCommands {

  // We map lastStateAction to state to avoid some corner/blocking cases with the deleting/canceling scenario on tests..
  override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction]
  ): Future[ProcessState] = {
    val lastStateActionStatus = lastStateAction match {
      case Some(action) if action.actionName == ScenarioActionName.Deploy =>
        SimpleStateStatus.Running
      case Some(action) if action.actionName == ScenarioActionName.Cancel =>
        SimpleStateStatus.Canceled
      case _ =>
        SimpleStateStatus.NotDeployed
    }
    Future.successful(processStateDefinitionManager.processState(StatusDetails(lastStateActionStatus, None)))
  }

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future.successful(
      WithDataFreshnessStatus.fresh(List.empty)
    )
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def customActionsDefinitions: List[CustomActionDefinition] = Nil

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

  override def close(): Unit = {}

}

trait StubbingCommands { self: DeploymentManager =>

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] = command match {
    case _: DMValidateScenarioCommand                        => Future.successful(())
    case _: DMRunDeploymentCommand                           => Future.successful(None)
    case _: DMStopDeploymentCommand                          => Future.successful(SavepointResult(""))
    case _: DMStopScenarioCommand                            => Future.successful(SavepointResult(""))
    case _: DMCancelDeploymentCommand                        => Future.successful(())
    case _: DMCancelScenarioCommand                          => Future.successful(())
    case _: DMMakeScenarioSavepointCommand                   => Future.successful(SavepointResult(""))
    case _: DMCustomActionCommand | _: DMTestScenarioCommand => notImplemented
  }

}

//This provider can be used for testing. Override methods to implement more complex behaviour
//Provider is registered via ServiceLoader, so it can be used e.g. to run simple docker configuration
class DeploymentManagerProviderStub extends DeploymentManagerProvider {

  override def createDeploymentManager(
      modelData: BaseModelData,
      deploymentManagerDependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = Validated.valid(new DeploymentManagerStub)

  override def name: String = "stub"

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    FlinkStreamingPropertiesConfig.metaDataInitializer

  override def scenarioPropertiesConfig(config: Config): ScenarioProperties =
    ScenarioProperties.fromParameterMap(FlinkStreamingPropertiesConfig.properties)

}

// This is copy-pasted from flink-manager package - the deployment-manager-api cannot depend on that package - it would create a circular dependency.
// TODO: Replace this class by a BaseDeploymentManagerProvider with default stubbed behavior
object FlinkStreamingPropertiesConfig {

  private val parallelismConfig: (String, SingleScenarioPropertyConfig) = StreamMetaData.parallelismName ->
    SingleScenarioPropertyConfig(
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

  private val spillStateConfig: (String, SingleScenarioPropertyConfig) = StreamMetaData.spillStateToDiskName ->
    SingleScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(FixedValuesParameterEditor(spillStatePossibleValues)),
      validators = Some(List(FixedValuesValidator(spillStatePossibleValues))),
      label = Some("Spill state to disk"),
      hintText = None
    )

  private val asyncInterpretationConfig: (String, SingleScenarioPropertyConfig) =
    StreamMetaData.useAsyncInterpretationName ->
      SingleScenarioPropertyConfig(
        defaultValue = None,
        editor = Some(FixedValuesParameterEditor(asyncPossibleValues)),
        validators = Some(List(FixedValuesValidator(asyncPossibleValues))),
        label = Some("IO mode"),
        hintText = None
      )

  private val checkpointIntervalConfig: (String, SingleScenarioPropertyConfig) =
    StreamMetaData.checkpointIntervalName ->
      SingleScenarioPropertyConfig(
        defaultValue = None,
        editor = Some(StringParameterEditor),
        validators = Some(List(LiteralIntegerValidator, MinimalNumberValidator(1))),
        label = Some("Checkpoint interval in seconds"),
        hintText = None
      )

  val properties: Map[String, SingleScenarioPropertyConfig] =
    Map(parallelismConfig, spillStateConfig, asyncInterpretationConfig, checkpointIntervalConfig)

  val metaDataInitializer: MetaDataInitializer = MetaDataInitializer(
    metadataType = StreamMetaData.typeName,
    overridingProperties = Map(StreamMetaData.parallelismName -> "1", StreamMetaData.spillStateToDiskName -> "true")
  )

}
