package pl.touk.nussknacker.ui.process.deployment.deploymentstatus

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.util.WithDataFreshnessStatusUtils.WithDataFreshnessStatusMapOps
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.DeploymentManagerReliableStatusesWrapper.Ops
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class DeploymentStatusesProvider(dispatcher: DeploymentManagerDispatcher, scenarioStateTimeout: Option[FiniteDuration])(
    implicit system: ActorSystem
) extends LazyLogging {

  private implicit val ec: ExecutionContext = system.dispatcher

  // DeploymentManager's may support fetching state of all scenarios at once
  // State is prefetched only when:
  //  - DM has capability DeploymentsStatusesQueryForAllScenariosSupport
  //  - the query is about more than one scenario handled by that DM - for one scenario prefetching would be non-optimal
  //    and this is a common case for this method because it is invoked for Id Traverse - see usages
  def getPrefetchedDeploymentStatusesForSupportedManagers(
      scenarios: List[ScenarioWithDetailsEntity[_]],
  )(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[PrefetchedDeploymentStatuses] = {
    // We assume that prefetching gives profits for at least two scenarios
    val processingTypesWithMoreThanOneScenario = scenarios.groupBy(_.processingType).filter(_._2.size >= 2).keySet

    Future
      .sequence {
        processingTypesWithMoreThanOneScenario.map { processingType =>
          (for {
            manager <- dispatcher.deploymentManager(processingType)
            managerWithCapability <- manager.deploymentsStatusesQueryForAllScenariosSupport match {
              case supported: DeploymentsStatusesQueryForAllScenariosSupported => Some(supported)
              case NoDeploymentsStatusesQueryForAllScenariosSupport            => None
            }
          } yield getAllDeploymentStatuses(processingType, managerWithCapability))
            .getOrElse(Future.successful(None))
        }
      }
      .map(_.flatten.toMap)
      .map(new PrefetchedDeploymentStatuses(_))
  }

  def getDeploymentStatuses(
      processingType: ProcessingType,
      scenarioName: ProcessName,
      prefetchedDeploymentStatuses: Option[PrefetchedDeploymentStatuses],
  )(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[Either[GetDeploymentsStatusesError, WithDataFreshnessStatus[List[DeploymentStatusDetails]]]] = {
    prefetchedDeploymentStatuses
      .flatMap(_.get(processingType, scenarioName))
      .map { prefetchedStatusDetails =>
        Future.successful(Right(prefetchedStatusDetails))
      }
      .getOrElse {
        dispatcher.getScenarioDeploymentsStatusesWithErrorWrappingAndTimeoutOpt(
          processingType,
          scenarioName,
          scenarioStateTimeout
        )
      }
  }

  private def getAllDeploymentStatuses(
      processingType: ProcessingType,
      manager: DeploymentsStatusesQueryForAllScenariosSupported
  )(
      implicit freshnessPolicy: DataFreshnessPolicy,
  ): Future[Option[(ProcessingType, WithDataFreshnessStatus[Map[ProcessName, List[DeploymentStatusDetails]]])]] = {
    manager
      .getAllScenariosDeploymentsStatuses()
      .map(states => Some((processingType, states)))
      .recover { case NonFatal(e) =>
        logger.warn(
          s"Failed to get statuses of all scenarios in deployment manager for $processingType: ${e.getMessage}",
          e
        )
        None
      }
  }

}

class PrefetchedDeploymentStatuses(
    prefetchedStatusesByProcessingType: Map[ProcessingType, WithDataFreshnessStatus[
      Map[ProcessName, List[DeploymentStatusDetails]]
    ]]
) {

  def get(
      processingType: ProcessingType,
      scenarioName: ProcessName
  ): Option[WithDataFreshnessStatus[List[DeploymentStatusDetails]]] =
    for {
      prefetchedStatusesForProcessingType <- prefetchedStatusesByProcessingType.get(processingType)
      // Deployment statuses are prefetched for all scenarios for the given processing type.
      // If there is no information available for a specific scenario name,
      // then it means that DM is not aware of this scenario, and we should default to List.empty[StatusDetails] instead of None
      prefetchedStatusesForScenario = prefetchedStatusesForProcessingType.getOrElse(scenarioName, List.empty)
    } yield prefetchedStatusesForScenario

}
