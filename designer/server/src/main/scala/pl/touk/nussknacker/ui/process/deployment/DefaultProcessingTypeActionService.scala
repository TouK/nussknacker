package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessingTypeActionService}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessingType}

import scala.concurrent.{ExecutionContext, Future}

class DefaultProcessingTypeActionService(
    processingType: ProcessingType,
    actionService: ActionService,
) extends ProcessingTypeActionService {

  override def markActionExecutionFinished(actionId: ProcessActionId)(implicit ec: ExecutionContext): Future[Boolean] =
    actionService.markActionExecutionFinished(processingType, actionId)

  override def getLastStateAction(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessAction]] =
    actionService.getLastStateAction(processingType, processId)

}
