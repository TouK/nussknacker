package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.inconsistency.InconsistentStateDetector
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

trait DeploymentManagerInconsistentStateHandlerMixIn {
  self: DeploymentManager =>

  final override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction]
  ): Future[ProcessState] = {
    val engineStateResolvedWithLastAction = flattenStatus(lastStateAction, statusDetails)
    Future.successful(processStateDefinitionManager.processState(engineStateResolvedWithLastAction))
  }

  // This method is protected to make possible to override it with own logic handling different edge cases like
  // other state on engine than based on lastStateAction
  protected def flattenStatus(
      lastStateAction: Option[ProcessAction],
      statusDetails: List[StatusDetails]
  ): StatusDetails = {
    InconsistentStateDetector.resolve(statusDetails, lastStateAction)
  }

}

trait DeploymentManager extends AutoCloseable {

  /**
    * This method is invoked separately before deploy, to be able to give user quick feedback, as deploy (e.g. on Flink) may take long time
    */
  def validate(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess
  ): Future[Unit]

  // TODO: savepointPath is very flink specific, we should handle this mode via custom action
  /**
    * We assume that validate was already called and was successful
    */
  def deploy(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]]

  def cancel(name: ProcessName, user: User): Future[Unit]

  def cancel(name: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit]

  def test(
      name: ProcessName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData
  ): Future[TestResults]

  final def getProcessState(idWithName: ProcessIdWithName, lastStateAction: Option[ProcessAction])(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[ProcessState]] = {
    for {
      statusDetailsWithFreshness <- getProcessStates(idWithName.name)
      stateWithFreshness <- resolve(idWithName, statusDetailsWithFreshness.value, lastStateAction).map(state =>
        statusDetailsWithFreshness.map(_ => state)
      )
    } yield stateWithFreshness
  }

  def getProcessStates(name: ProcessName)(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[StatusDetails]]]

  /**
    * Resolves possible inconsistency with lastAction and formats status using `ProcessStateDefinitionManager`
    */
  def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction]
  ): Future[ProcessState]

  def processStateDefinitionManager: ProcessStateDefinitionManager

  def customActions: List[CustomAction]

  def invokeCustomAction(
      actionRequest: CustomActionRequest,
      canonicalProcess: CanonicalProcess
  ): Future[Either[CustomActionError, CustomActionResult]]

  // TODO: this is very flink specific, we should handle it via custom action
  def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult]

  // TODO: savepointPath is very flink specific, we should handle it via custom action
  def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult]

  def stop(
      name: ProcessName,
      deploymentId: DeploymentId,
      savepointDir: Option[String],
      user: User
  ): Future[SavepointResult]

}

// This is Flink-specific but we have to abstract over this, to keep PeriodicDeploymentManager loosely coupled with Flink
// See comments in FlinkDeploymentManager
trait PostprocessingProcessStatus { self: DeploymentManager =>

  def postprocess(idWithName: ProcessIdWithName, statusDetailsList: List[StatusDetails]): Future[Option[ProcessAction]]

}

trait AlwaysFreshProcessState { self: DeploymentManager =>

  final override def getProcessStates(name: ProcessName)(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[StatusDetails]]] =
    getFreshProcessStates(name).map(WithDataFreshnessStatus(_, cached = false))

  protected def getFreshProcessStates(name: ProcessName): Future[List[StatusDetails]]

}
