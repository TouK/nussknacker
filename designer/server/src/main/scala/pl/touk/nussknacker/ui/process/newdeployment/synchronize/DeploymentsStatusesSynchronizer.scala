package pl.touk.nussknacker.ui.process.newdeployment.synchronize

import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentStatus,
  DeploymentSynchronisationSupport,
  DeploymentSynchronisationSupported,
  NoDeploymentSynchronisationSupport,
  ProblemDeploymentStatus
}
import pl.touk.nussknacker.engine.newdeployment
import pl.touk.nussknacker.engine.util.logging.LazyLoggingWithTraces
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentRepository
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.security.api.NussknackerInternalUser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class DeploymentsStatusesSynchronizer(
    repository: DeploymentRepository,
    synchronizationSupport: ProcessingTypeDataProvider[DeploymentSynchronisationSupport, _],
    dbioActionRunner: DBIOActionRunner
)(implicit ec: ExecutionContext)
    extends LazyLoggingWithTraces {

  def synchronizeAll(): Future[Unit] = {
    val finalStatusesNames =
      Set(DeploymentStatus.Canceled.name, DeploymentStatus.Finished.name, ProblemDeploymentStatus.name)
    synchronizationSupport
      .all(NussknackerInternalUser.instance)
      .toList
      .map { case (processingType, manager) =>
        manager match {
          case synchronisationSupported: DeploymentSynchronisationSupported =>
            logger.trace(s"Running synchronization of deployments statuses for processing type: $processingType")
            for {
              deploymentIdsToCheck <- dbioActionRunner.run(
                repository.getProcessingTypeDeploymentsIdsInNotMatchingStatus(processingType, finalStatusesNames)
              )
              statusesByDeploymentId <- synchronisationSupported
                .getDeploymentStatusesToUpdate(deploymentIdsToCheck)
                .recover { case NonFatal(ex) =>
                  logger.debugWithTraceStack(
                    s"Error during fetching of deployment statuses for processing type [$processingType]: ${ex.getMessage}. Synchronisation will be skipped",
                    ex
                  )
                  Map.empty[newdeployment.DeploymentId, DeploymentStatus]
                }
              updateResult <- dbioActionRunner.run(repository.updateDeploymentStatuses(statusesByDeploymentId))
              _ = {
                Option(updateResult).filterNot(_.isEmpty) match {
                  case None =>
                    logger.trace(
                      s"Synchronization for processing type [$processingType] finished. Fetched deployment statuses: $statusesByDeploymentId. No deployment status was changed"
                    )
                  case Some(changes) =>
                    logger.debug(
                      changes.mkString(
                        s"Synchronization for processing type [$processingType] finished. Fetched deployment statuses: $statusesByDeploymentId. Statuses for deployments ",
                        ", ",
                        " were changed"
                      )
                    )
                }
              }
            } yield ()
          case NoDeploymentSynchronisationSupport =>
            logger.trace(
              s"Synchronization for processing type [$processingType] is not supported. Skipping."
            )
            Future.unit
        }

      }
      .sequence
      .map(_ => ())
  }

}
