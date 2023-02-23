package pl.touk.nussknacker.ui.process.deployment

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessState}
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ProcessStateServiceImpl(processRepository: FetchingProcessRepository[Future],
                              dispatcher: DeploymentManagerDispatcher,
                              deploymentService: DeploymentServiceImpl) extends ProcessStateService with LazyLogging {

  override def getProcessState(processIdWithName: ProcessIdWithName)
                              (implicit user: LoggedUser, ec: ExecutionContext): Future[ProcessState] =
    for {
      actions <- processRepository.fetchProcessActions(processIdWithName.id)
      manager <- dispatcher.deploymentManager(processIdWithName.id)
      state <- findJobState(manager, processIdWithName)
      _ <- deploymentService.handleFinishedProcess(processIdWithName, state)
    } yield ObsoleteStateDetector.handleObsoleteStatus(state, actions.headOption)

  private def findJobState(deploymentManager: DeploymentManager, processIdWithName: ProcessIdWithName)
                          (implicit user: LoggedUser, ec: ExecutionContext): Future[Option[ProcessState]] =
    deploymentManager.findJobStatus(processIdWithName.name).recover {
      case NonFatal(e) =>
        logger.warn(s"Failed to get status of ${processIdWithName}: ${e.getMessage}", e)
        Some(SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.FailedToGet))
    }

}
