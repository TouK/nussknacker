package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.inconsistency.InconsistentStateDetector
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.CustomActionDefinition
import pl.touk.nussknacker.engine.newdeployment

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

  def deploymentSynchronisationSupport: DeploymentSynchronisationSupport

  def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result]

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

  /**
    * We provide a special wrapper called WithDataFreshnessStatus to ensure that fetched data is restored
    * from the cache or not. If you use any kind of cache in your DM implementation please wrap result data
    * with WithDataFreshnessStatus.cached(data) in opposite situation use WithDataFreshnessStatus.fresh(data)
    */
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

  def customActionsDefinitions: List[CustomActionDefinition]

  protected final def notImplemented: Future[Nothing] =
    Future.failed(new NotImplementedError())

  // todo NU-1772
  //  In the current implementation:
  //    - the DeploymentManager is a kind of plugin, and it may have its own data source (separate db, cache, etc.)
  //    - the DeploymentManager may return (by implementing this method) custom ScenarioActivities, that are associated with operations performed internally by the manager
  //  Why is it not the ideal solution:
  //    - we have different data sources for ScenarioActivities, and merging data from two sources may be problematic, e.g. when paginating results
  //  How can it be redesigned:
  //    - we could do it the other way round - we could provide the hook, that would allow the manager to save its custom activities to the main ScenarioActivity repository
  //    - only the ScenarioActivities repo would then be used, so it would require migrating old data from DeploymentManager-specific data source
  //    - we may need to refactor PeriodicDeploymentManager data source first, that is why it was not implemented that way in the first place
  def managerSpecificScenarioActivities(processIdWithName: ProcessIdWithName): Future[List[ScenarioActivity]]

}

sealed trait DeploymentSynchronisationSupport

trait DeploymentSynchronisationSupported extends DeploymentSynchronisationSupport {

  def getDeploymentStatusesToUpdate(
      deploymentIdsToCheck: Set[newdeployment.DeploymentId]
  ): Future[Map[newdeployment.DeploymentId, DeploymentStatus]]

}

case object NoDeploymentSynchronisationSupport extends DeploymentSynchronisationSupport
