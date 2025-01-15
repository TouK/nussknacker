package pl.touk.nussknacker.engine.api.deployment

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.inconsistency.InconsistentStateDetector
import pl.touk.nussknacker.engine.api.deployment.scheduler.services._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.util.WithDataFreshnessStatusUtils.WithDataFreshnessStatusOps
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies, newdeployment}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

trait DeploymentManagerInconsistentStateHandlerMixIn {
  self: DeploymentManager =>

  final override def resolve(
      idWithName: ProcessIdWithName,
      statusDetails: List[StatusDetails],
      lastStateAction: Option[ProcessAction],
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId],
      currentlyPresentedVersionId: Option[VersionId],
  ): Future[ProcessState] = {
    val engineStateResolvedWithLastAction = flattenStatus(lastStateAction, statusDetails)
    Future.successful(
      processStateDefinitionManager.processState(
        engineStateResolvedWithLastAction,
        latestVersionId,
        deployedVersionId,
        currentlyPresentedVersionId
      )
    )
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

  def stateQueryForAllScenariosSupport: StateQueryForAllScenariosSupport

  def schedulingSupport: SchedulingSupport

  def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result]

  final def getProcessState(
      idWithName: ProcessIdWithName,
      lastStateAction: Option[ProcessAction],
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId],
      currentlyPresentedVersionId: Option[VersionId],
  )(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[ProcessState]] = {
    for {
      statusDetailsWithFreshness <- getProcessStates(idWithName.name)
      stateWithFreshness <- resolve(
        idWithName,
        statusDetailsWithFreshness.value,
        lastStateAction,
        latestVersionId,
        deployedVersionId,
        currentlyPresentedVersionId,
      ).map(statusDetailsWithFreshness.withValue)
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
      lastStateAction: Option[ProcessAction],
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId],
      currentlyPresentedVersionId: Option[VersionId],
  ): Future[ProcessState]

  def processStateDefinitionManager: ProcessStateDefinitionManager

  protected final def notImplemented: Future[Nothing] =
    Future.failed(new NotImplementedError())

}

trait ManagerSpecificScenarioActivitiesStoredByManager { self: DeploymentManager =>

  def managerSpecificScenarioActivities(
      processIdWithName: ProcessIdWithName,
      after: Option[Instant],
  ): Future[List[ScenarioActivity]]

}

sealed trait StateQueryForAllScenariosSupport

trait StateQueryForAllScenariosSupported extends StateQueryForAllScenariosSupport {

  def getAllProcessesStates()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[Map[ProcessName, List[StatusDetails]]]]

}

case object NoStateQueryForAllScenariosSupport extends StateQueryForAllScenariosSupport

sealed trait DeploymentSynchronisationSupport

trait DeploymentSynchronisationSupported extends DeploymentSynchronisationSupport {

  def getDeploymentStatusesToUpdate(
      deploymentIdsToCheck: Set[newdeployment.DeploymentId]
  ): Future[Map[newdeployment.DeploymentId, DeploymentStatus]]

}

case object NoDeploymentSynchronisationSupport extends DeploymentSynchronisationSupport

sealed trait SchedulingSupport

trait SchedulingSupported extends SchedulingSupport {

  def createScheduledExecutionPerformer(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      deploymentConfig: Config,
  ): ScheduledExecutionPerformer

  def customSchedulePropertyExtractorFactory: Option[SchedulePropertyExtractorFactory]

  def customProcessConfigEnricherFactory: Option[ProcessConfigEnricherFactory]

  def customPeriodicProcessListenerFactory: Option[ScheduledProcessListenerFactory]

  def customAdditionalDeploymentDataProvider: Option[AdditionalDeploymentDataProvider]

}

case object NoSchedulingSupport extends SchedulingSupport
