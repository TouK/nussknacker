package pl.touk.nussknacker.engine.api.deployment

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.scheduler.services._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.newdeployment

import java.time.Instant
import scala.concurrent.Future

trait DeploymentManager extends AutoCloseable {

  def deploymentSynchronisationSupport: DeploymentSynchronisationSupport

  def stateQueryForAllScenariosSupport: StateQueryForAllScenariosSupport

  def schedulingSupport: SchedulingSupport

  def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result]

  /**
    * We provide a special wrapper called WithDataFreshnessStatus to ensure that fetched data is restored
    * from the cache or not. If you use any kind of cache in your DM implementation please wrap result data
    * with WithDataFreshnessStatus.cached(data) in opposite situation use WithDataFreshnessStatus.fresh(data)
    */
  def getScenarioDeploymentsStatuses(scenarioName: ProcessName)(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[StatusDetails]]]

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
      rawSchedulingConfig: Config,
  ): ScheduledExecutionPerformer

  def customSchedulePropertyExtractorFactory: Option[SchedulePropertyExtractorFactory] = None

  def customProcessConfigEnricherFactory: Option[ProcessConfigEnricherFactory] = None

  def customScheduledProcessListenerFactory: Option[ScheduledProcessListenerFactory] = None

  def customAdditionalDeploymentDataProvider: Option[AdditionalDeploymentDataProvider] = None

}

case object NoSchedulingSupport extends SchedulingSupport
