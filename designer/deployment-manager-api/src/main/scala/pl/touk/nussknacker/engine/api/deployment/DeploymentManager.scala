package pl.touk.nussknacker.engine.api.deployment

import cats.effect.{Resource, SyncIO}
import com.typesafe.config.Config
import io.circe.Json
import pl.touk.nussknacker.engine.api.{ContextId, NodeId}
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported.{LiveData, LiveDataError}
import pl.touk.nussknacker.engine.api.deployment.scheduler.services._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.newdeployment

import java.time.Instant
import scala.concurrent.Future

trait DeploymentManager extends AutoCloseable {

  def deploymentSynchronisationSupport: DeploymentSynchronisationSupport

  def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport

  def schedulingSupport: SchedulingSupport

  def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result]

  /**
    * We provide a special wrapper called WithDataFreshnessStatus to ensure that fetched data is restored
    * from the cache or not. If you use any kind of cache in your DM implementation please wrap result data
    * with WithDataFreshnessStatus.cached(data) in opposite situation use WithDataFreshnessStatus.fresh(data)
    */
  def getScenarioDeploymentsStatuses(scenarioName: ProcessName)(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[List[DeploymentStatusDetails]]]

  def processStateDefinitionManager: ProcessStateDefinitionManager

  def scenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies]

  def liveDataPreviewSupport: LiveDataPreviewSupport

  protected final def notImplemented: Future[Nothing] =
    Future.failed(new NotImplementedError())
}

trait ManagerSpecificScenarioActivitiesStoredByManager { self: DeploymentManager =>

  def managerSpecificScenarioActivities(
      processIdWithName: ProcessIdWithName,
      after: Option[Instant],
  ): Future[List[ScenarioActivity]]

}

sealed trait DeploymentsStatusesQueryForAllScenariosSupport

trait DeploymentsStatusesQueryForAllScenariosSupported extends DeploymentsStatusesQueryForAllScenariosSupport {

  def getAllScenariosDeploymentsStatuses()(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[Map[ProcessName, List[DeploymentStatusDetails]]]]

}

case object NoDeploymentsStatusesQueryForAllScenariosSupport extends DeploymentsStatusesQueryForAllScenariosSupport

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

sealed trait LiveDataPreviewSupport

trait LiveDataPreviewSupported extends LiveDataPreviewSupport {

  def getLiveData(
      processIdWithName: ProcessIdWithName,
  ): Future[Either[LiveDataError, LiveData]]

}

object LiveDataPreviewSupported {

  final case class LiveData(
      timestamp: Instant,
      nodeTransitions: Map[NodeTransition, LiveDataForNodeTransition],
      invocationResults: Map[NodeId, List[InvocationResult]],
      externalInvocationResults: Map[NodeId, List[InvocationResult]],
      exceptions: Map[NodeId, List[ExceptionResult]]
  )

  final case class ExceptionResult(
      contextId: ContextId,
      timestamp: Instant,
      variables: Map[String, Json],
      throwable: Throwable,
  )

  final case class InvocationResult(
      contextId: ContextId,
      timestamp: Instant,
      name: String,
      value: Json,
  )

  final case class LiveDataForNodeTransition(
      samples: List[LiveDataSample],
      totalCount: Long,
      currentThroughput: BigDecimal,
  )

  case class LiveDataSample(
      contextId: ContextId,
      timestamp: Instant,
      variables: Map[String, Json],
  )

  final case class NodeTransition(sourceNodeId: String, destinationNodeId: Option[String])

  sealed trait LiveDataError

  object LiveDataError {
    case object NoLiveDataAvailableForScenario extends LiveDataError
  }

}

case object NoLiveDataPreviewSupport extends LiveDataPreviewSupport
