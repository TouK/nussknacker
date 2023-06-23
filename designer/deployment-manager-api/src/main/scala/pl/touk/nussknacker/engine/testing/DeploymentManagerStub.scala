package pl.touk.nussknacker.engine.testing

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, FixedValuesValidator, LiteralIntegerValidator, MinimalNumberValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerProvider, MetaDataInitializer}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class DeploymentManagerStub extends DeploymentManager with AlwaysFreshProcessState {

  override def validate(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess): Future[Unit] = Future.successful(())

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] =
    Future.successful(None)

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] =
    Future.successful(SavepointResult(""))

  override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(())

  override def test[T](name: ProcessName, canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = ???

  //We map lastStateAction to state to avoid some corner/blocking cases with the deleting/canceling scenario on tests..
  override def getProcessState(name: ProcessName, lastStateAction: Option[ProcessAction])(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[ProcessState]] = {
    val lastStateActionStatus = lastStateAction match {
      case Some(state) if state.isDeployed =>
        SimpleStateStatus.Running
      case Some(state) if state.isCanceled =>
        SimpleStateStatus.Canceled
      case _ =>
        SimpleStateStatus.NotDeployed
    }

    Future.successful(WithDataFreshnessStatus(processStateDefinitionManager.processState(lastStateActionStatus), cached = false))
  }

  override def getFreshProcessState(name: ProcessName): Future[Option[StatusDetails]] =
    Future.successful(None)

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(""))

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def customActions: List[CustomAction] = Nil

  override def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful(Left(CustomActionNotImplemented(actionRequest)))

  override def close(): Unit = {}

}

//This provider can be used for testing. Override methods to implement more complex behaviour
//Provider is registered via ServiceLoader, so it can be used e.g. to run simple docker configuration
class DeploymentManagerProviderStub extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Any],
                                       deploymentService: ProcessingTypeDeploymentService): DeploymentManager = new DeploymentManagerStub

  override def name: String = "stub"

  override def metaDataInitializer(config: Config): MetaDataInitializer = FlinkStreamingPropertiesConfig.metaDataInitializer

  override def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig] = FlinkStreamingPropertiesConfig.properties

}

// This is copy-pasted from flink-manager package - the deployment-manager-api cannot depend on that package - it would create a circular dependency.
// TODO: Replace this class by a BaseDeploymentManagerProvider with default stubbed behavior
object FlinkStreamingPropertiesConfig {

  private val parallelismConfig: (String, AdditionalPropertyConfig) = StreamMetaData.parallelismName ->
    AdditionalPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = Some(List(LiteralIntegerValidator, MinimalNumberValidator(1))),
      label = Some("Parallelism"))

  private val spillStatePossibleValues = List(
    FixedExpressionValue("", "Server default"),
    FixedExpressionValue("false", "False"),
    FixedExpressionValue("true", "True"))

  private val asyncPossibleValues = List(
    FixedExpressionValue("", "Server default"),
    FixedExpressionValue("false", "Synchronous"),
    FixedExpressionValue("true", "Asynchronous"))

  private val spillStateConfig: (String, AdditionalPropertyConfig) = StreamMetaData.spillStateToDiskName ->
    AdditionalPropertyConfig(
      defaultValue = None,
      editor = Some(FixedValuesParameterEditor(spillStatePossibleValues)),
      validators = Some(List(FixedValuesValidator(spillStatePossibleValues))),
      label = Some("Spill state to disk"))

  private val asyncInterpretationConfig: (String, AdditionalPropertyConfig) = StreamMetaData.useAsyncInterpretationName ->
    AdditionalPropertyConfig(
      defaultValue = None,
      editor = Some(FixedValuesParameterEditor(asyncPossibleValues)),
      validators = Some(List(FixedValuesValidator(asyncPossibleValues))),
      label = Some("IO mode"))

  private val checkpointIntervalConfig: (String, AdditionalPropertyConfig) = StreamMetaData.checkpointIntervalName ->
    AdditionalPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = Some(List(LiteralIntegerValidator, MinimalNumberValidator(1))),
      label = Some("Checkpoint interval in seconds"))

  val properties: Map[String, AdditionalPropertyConfig] =
    Map(parallelismConfig, spillStateConfig, asyncInterpretationConfig, checkpointIntervalConfig)

  val metaDataInitializer: MetaDataInitializer = MetaDataInitializer(
    metadataType = StreamMetaData.typeName,
    overridingProperties = Map(
      StreamMetaData.parallelismName -> "1",
      StreamMetaData.spillStateToDiskName -> "true"))
}
