package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.inconsistency.InconsistentStateDetector
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

trait DeploymentManager extends AutoCloseable {

  /**
    * This method is invoked separately before deploy, to be able to give user quick feedback, as deploy (e.g. on Flink) may take long time
    */
  def validate(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess): Future[Unit]

  //TODO: savepointPath is very flink specific, we should handle this mode via custom action
  /**
    * We assume that validate was already called and was successful
    */
  def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]]

  def cancel(name: ProcessName, user: User): Future[Unit]

  def test[T](name: ProcessName, canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData, variableEncoder: Any => T): Future[TestResults[T]]

  // TDDO: remove this method and use getProcessStates everywhere
  def getProcessState(name: ProcessName)(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[Option[StatusDetails]]]

  def getProcessStates(name: ProcessName)(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]]

  /**
    * Gets status from engine, resolves possible inconsistency with lastAction and formats status using `ProcessStateDefinitionManager`
    */
  def getProcessState(name: ProcessName, lastStateAction: Option[ProcessAction])(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[ProcessState]] =
    getProcessState(name).map(_.map(statusDetailsOpt => {
      val engineStateResolvedWithLastAction = InconsistentStateDetector.resolve(statusDetailsOpt, lastStateAction)
      processStateDefinitionManager.processState(engineStateResolvedWithLastAction)
    }))

  def processStateDefinitionManager: ProcessStateDefinitionManager

  def customActions: List[CustomAction]

  def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]]

  //TODO: this is very flink specific, we should handle it via custom action
  def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult]

  //TODO: savepointPath is very flink specific, we should handle it via custom action
  def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult]

}

trait AlwaysFreshProcessState { self: DeploymentManager =>

  final override def getProcessState(name: ProcessName)
                                    (implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[Option[StatusDetails]]] =
    getFreshProcessState(name).map(WithDataFreshnessStatus(_, cached = false))


  override def getProcessStates(name: ProcessName)(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] =
    getFreshProcessStates(name).map(WithDataFreshnessStatus(_, cached = false))

  // TDDO: remove this method and use getFreshProcessStates everywhere
  protected def getFreshProcessState(name: ProcessName): Future[Option[StatusDetails]]

  protected def getFreshProcessStates(name: ProcessName): Future[List[StatusDetails]]

}