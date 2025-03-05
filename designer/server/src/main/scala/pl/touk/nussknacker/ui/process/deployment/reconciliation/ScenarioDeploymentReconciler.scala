package pl.touk.nussknacker.ui.process.deployment.reconciliation

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy.Fresh
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, ScenarioActionRepository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import slick.dbio.DBIOAction

import scala.concurrent.{ExecutionContext, Future}

class ScenarioDeploymentReconciler(
    allProcessingTypes: => Iterable[ProcessingType],
    deploymentStatusesProvider: EngineSideDeploymentStatusesProvider,
    actionRepository: ScenarioActionRepository,
    dbioActionRunner: DBIOActionRunner
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  // We have to synchronize these statuses because engines (for example Flink) might have jobs retention mechanism
  // and finished jobs will disappear eventually on their side
  def synchronizeEngineFinishedDeploymentsLocalStatuses(): Future[Unit] = {
    implicit val user: LoggedUser                     = NussknackerInternalUser.instance
    implicit val freshnessPolicy: DataFreshnessPolicy = Fresh
    logger.debug("Synchronization of local status of finished deployments...")
    for {
      // Currently, synchronization is supported only for DeploymentManagers that supports DeploymentsStatusesQueryForAllScenarios
      bulkQueriedStatuses <- deploymentStatusesProvider.getBulkQueriedDeploymentStatusesForSupportedManagers(
        allProcessingTypes
      )
      deploymentStatuses = bulkQueriedStatuses.getAllDeploymentStatuses
      // We compare status by instances instead of by names. Thanks to that, PeriodicStateStatus won't be handled.
      // It is an expected behaviour because schedules finished status is handled inside PeriodicProcessService
      finishedDeploymentIds = deploymentStatuses.filter(_.status == SimpleStateStatus.Finished).flatMap(_.deploymentId)
      actionsIds            = finishedDeploymentIds.flatMap(_.toActionIdOpt)
      actionsWithMarkingExecutionFinishedResult <- dbioActionRunner.run(DBIOAction.sequence(actionsIds.map { actionId =>
        actionRepository.markFinishedActionAsExecutionFinished(actionId).map(actionId -> _)
      }))
    } yield {
      val actionsMarkedAsExecutionFinished = actionsWithMarkingExecutionFinishedResult.collect {
        case (actionId, true) => actionId.toString
      }.toList
      if (actionsMarkedAsExecutionFinished.isEmpty) {
        logger.debug("None action marked as execution finished")
      } else {
        logger.debug(actionsMarkedAsExecutionFinished.mkString("Actions marked as execution finished: ", ", ", ""))
      }
    }
  }

}
