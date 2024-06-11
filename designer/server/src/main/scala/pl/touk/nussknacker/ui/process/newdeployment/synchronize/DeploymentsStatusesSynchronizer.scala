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
            logger.debug(s"Running synchronization of deployments statuses for processing type: $processingType")
            for {
              statusesByDeploymentId <- synchronisationSupported.getDeploymentStatusesToUpdate
              updateResult <- dbioActionRunner.run(repository.updateDeploymentStatuses(statusesByDeploymentId))
              _ = {
                logger.debug {
                  val updateSummary = Option(updateResult)
                    .filterNot(_.isEmpty)
                    .map(_.mkString("Deployments ", ", ", " statuses were changed"))
                    .getOrElse("No deployments status was changed")
                  s"Synchronization of deployments statuses for processing type processing type: $processingType finished. $updateSummary"
                }
              }
            } yield ()
          case NoDeploymentSynchronisationSupport =>
            logger.debug(
              s"Synchronization of deployments statuses for processing type: $processingType is not supported, skipping."
            )
            Future.unit
        }

      }
      .sequence
      .map(_ => ())
  }

}
