package pl.touk.nussknacker.engine.testing

import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.Config
import io.circe.Json
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.ExceptionResult
import pl.touk.nussknacker.engine.{
  BaseModelData,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future

class DeploymentManagerStub extends DeploymentManager {

  override def validate(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess
  ): Future[Unit] = Future.successful(())

  override def deploy(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]] =
    Future.successful(None)

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] =
    Future.successful(SavepointResult(""))

  override def stop(
      name: ProcessName,
      deploymentId: DeploymentId,
      savepointDir: Option[String],
      user: User
  ): Future[SavepointResult] =
    Future.successful(SavepointResult(""))

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(())

  override def cancel(name: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit] = Future.successful(())

  override def test(
      name: ProcessName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
  ): Future[TestProcess.TestResults[Json]] = ???

  // We map lastStateAction to state to avoid some corner/blocking cases with the deleting/canceling scenario on tests..
  override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction]
  ): Future[ProcessState] = {
    val lastStateActionStatus = lastStateAction match {
      case Some(action) if action.actionType.equals(ProcessActionType.Deploy) =>
        SimpleStateStatus.Running
      case Some(action) if action.actionType.equals(ProcessActionType.Cancel) =>
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

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] =
    Future.successful(SavepointResult(""))

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def customActions: List[CustomAction] = Nil

  override def invokeCustomAction(
      actionRequest: CustomActionRequest,
      canonicalProcess: CanonicalProcess
  ): Future[CustomActionResult] =
    Future.failed(new NotImplementedError())

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
  ): ValidatedNel[String, DeploymentManager] = Validated.valid(new DeploymentManagerStub)

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
      label = Some("Parallelism")
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
      label = Some("Spill state to disk")
    )

  private val asyncInterpretationConfig: (String, ScenarioPropertyConfig) =
    StreamMetaData.useAsyncInterpretationName ->
      ScenarioPropertyConfig(
        defaultValue = None,
        editor = Some(FixedValuesParameterEditor(asyncPossibleValues)),
        validators = Some(List(FixedValuesValidator(asyncPossibleValues))),
        label = Some("IO mode")
      )

  private val checkpointIntervalConfig: (String, ScenarioPropertyConfig) = StreamMetaData.checkpointIntervalName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = Some(List(LiteralIntegerValidator, MinimalNumberValidator(1))),
      label = Some("Checkpoint interval in seconds")
    )

  val properties: Map[String, ScenarioPropertyConfig] =
    Map(parallelismConfig, spillStateConfig, asyncInterpretationConfig, checkpointIntervalConfig)

  val metaDataInitializer: MetaDataInitializer = MetaDataInitializer(
    metadataType = StreamMetaData.typeName,
    overridingProperties = Map(StreamMetaData.parallelismName -> "1", StreamMetaData.spillStateToDiskName -> "true")
  )

}
