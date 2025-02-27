package pl.touk.nussknacker.ui.process.deployment.deploymentstatus

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.engine.util.WithDataFreshnessStatusUtils.WithDataFreshnessStatusMapOps
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.DeploymentManagerReliableStatusesWrapper.Ops
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

// This class returns information about deployments basen on information from DeploymentManager's
// To have full information about DeploymentStatus'es, these information have to be merged with data from local store
// Data from local store are needed in certain situation:
// 1. when scenario deployment is requested but not yet seen on engine side (deploy action is in progress)
// 2. when scenario job was finished and was removed by retention mechanism
// 3. when scenario job have been canceled and was removed by retention mechanism
// Currently, for local store is used ActionRepository. It is quite problematic for determining the statuses. For example,
// case 3. is barely not possible to done accurately because cancel action is not correlated with deploy action
// so for two deploys done by one we won't know which one should be canceled
// TODO: Extract a new service that would should merged perspective for of deployment statuses. To do that,
//       we need to change (or refactor) the local storage
class EngineSideDeploymentStatusesProvider(
    dispatcher: DeploymentManagerDispatcher,
    scenarioStateTimeout: Option[FiniteDuration]
)(
    implicit system: ActorSystem
) extends LazyLogging {

  private implicit val ec: ExecutionContext = system.dispatcher

  // DeploymentManager's may support fetching state of all scenarios at once
  // State is prefetched only when:
  //  - DM has capability DeploymentsStatusesQueryForAllScenariosSupport
  //  - the query is about more than one scenario handled by that DM - for one scenario prefetching would be non-optimal
  //    and this is a common case for this method because it is invoked for Id Traverse - see usages
  def getBulkQueriedDeploymentStatusesForSupportedManagers(
      processingTypes: Iterable[ProcessingType]
  )(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[BulkQueriedDeploymentStatuses] = {
    Future
      .sequence {
        processingTypes.map { processingType =>
          (for {
            manager <- dispatcher.deploymentManager(processingType)
            managerWithCapability <- manager.deploymentsStatusesQueryForAllScenariosSupport match {
              case supported: DeploymentsStatusesQueryForAllScenariosSupported => Some(supported)
              case NoDeploymentsStatusesQueryForAllScenariosSupport            => None
            }
          } yield getAllDeploymentStatusesRecoveringFailure(processingType, managerWithCapability).map(
            _.map(processingType -> _)
          ))
            .getOrElse(Future.successful(None))
        }
      }
      .map(_.flatten.toMap)
      .map(new BulkQueriedDeploymentStatuses(_))
  }

  def getDeploymentStatuses(
      scenarioIdData: ScenarioIdData,
      prefetchedDeploymentStatuses: Option[BulkQueriedDeploymentStatuses]
  )(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[Either[GetDeploymentsStatusesError, WithDataFreshnessStatus[List[DeploymentStatusDetails]]]] = {
    prefetchedDeploymentStatuses
      .flatMap(_.getDeploymentStatuses(scenarioIdData))
      .map { prefetchedStatusDetails =>
        Future.successful(Right(prefetchedStatusDetails))
      }
      .getOrElse {
        dispatcher.getScenarioDeploymentsStatusesWithErrorWrappingAndTimeoutOpt(
          scenarioIdData,
          scenarioStateTimeout
        )
      }
  }

  private def getAllDeploymentStatusesRecoveringFailure(
      processingType: ProcessingType,
      manager: DeploymentsStatusesQueryForAllScenariosSupported
  )(
      implicit freshnessPolicy: DataFreshnessPolicy,
  ): Future[Option[WithDataFreshnessStatus[Map[ProcessName, List[DeploymentStatusDetails]]]]] = {
    manager
      .getAllScenariosDeploymentsStatuses()
      .map(Some(_))
      .recover { case NonFatal(e) =>
        logger.warn(
          s"Failed to get statuses of all scenarios in deployment manager for $processingType: ${e.getMessage}",
          e
        )
        None
      }
  }

}

class BulkQueriedDeploymentStatuses(
    statusesByProcessingType: Map[ProcessingType, WithDataFreshnessStatus[
      Map[ProcessName, List[DeploymentStatusDetails]]
    ]]
) {

  def getAllDeploymentStatuses: Iterable[DeploymentStatusDetails] = for {
    processingTypeStatusesWithFreshness <- statusesByProcessingType.values
    (_, deploymentStatuses)             <- processingTypeStatusesWithFreshness.value
    deploymentStatus                    <- deploymentStatuses
  } yield deploymentStatus

  def getDeploymentStatuses(
      scenarioIdData: ScenarioIdData
  ): Option[WithDataFreshnessStatus[List[DeploymentStatusDetails]]] =
    for {
      statusesByScenarioName <- statusesByProcessingType.get(scenarioIdData.processingType)
      // Deployment statuses are prefetched for all scenarios for the given processing type.
      // If there is no information available for a specific scenario name,
      // then it means that DM is not aware of this scenario, and we should default to List.empty[StatusDetails] instead of None
      scenarioDeploymentStatuses = statusesByScenarioName.getOrElse(scenarioIdData.name, List.empty)
    } yield scenarioDeploymentStatuses

}
