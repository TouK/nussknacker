package pl.touk.nussknacker.ui.process.newdeployment.synchronize

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentManager,
  DeploymentSynchronisationSupport,
  DeploymentSynchronisationSupported,
  NoDeploymentSynchronisationSupport
}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentRepository
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.security.api.NussknackerInternalUser

import scala.concurrent.{ExecutionContext, Future}

class DeploymentsStatusesSynchronizer(
    repository: DeploymentRepository,
    synchronizationSupport: ProcessingTypeDataProvider[DeploymentSynchronisationSupport, _],
    dbioActionRunner: DBIOActionRunner
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  def synchronizeAll(): Future[Unit] = {
    synchronizationSupport
      .all(NussknackerInternalUser.instance)
      .toList
      .map { case (processingType, manager) =>
        manager match {
          case synchronisationSupported: DeploymentSynchronisationSupported =>
            logger.trace(s"Running synchronization of deployments statuses for processing type: $processingType")
            for {
              statusesByDeploymentId <- synchronisationSupported.getDeploymentStatusesToUpdate
              updateResult <- dbioActionRunner.run(repository.updateDeploymentStatuses(statusesByDeploymentId))
              _ = {
                Option(updateResult).filterNot(_.isEmpty) match {
                  case None =>
                    logger.trace(
                      s"Synchronization of deployments statuses for processing type: $processingType finished. No deployment status was changed"
                    )
                  case Some(changes) =>
                    logger.debug(
                      changes.mkString(
                        s"Synchronization of deployments statuses for processing type: $processingType finished. Deployments ",
                        ", ",
                        " statuses were changed"
                      )
                    )
                }
              }
            } yield ()
          case NoDeploymentSynchronisationSupport =>
            logger.trace(
              s"Synchronization of deployments statuses for processing type: $processingType is not supported, skipping."
            )
            Future.unit
        }

      }
      .sequence
      .map(_ => ())
  }

}
