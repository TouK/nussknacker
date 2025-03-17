package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{ProcessActionId, ProcessingTypeActionService}
import pl.touk.nussknacker.engine.api.process.ProcessingType

import scala.concurrent.{ExecutionContext, Future}

class DefaultProcessingTypeActionService(
    processingType: ProcessingType,
    actionService: ActionService,
) extends ProcessingTypeActionService {

  override def markActionExecutionFinished(actionId: ProcessActionId)(implicit ec: ExecutionContext): Future[Boolean] =
    actionService.markActionExecutionFinished(processingType, actionId)

}
