package pl.touk.nussknacker.engine.api.deployment

import scala.concurrent.{ExecutionContext, Future}

trait ProcessingTypeActionService {

  // Marks action execution finished. Returns true if update has some effect
  def markActionExecutionFinished(actionId: ProcessActionId)(implicit ec: ExecutionContext): Future[Boolean]

}

// This stub is in API module because we don't want to extract deployment-manager-tests-utils module
class ProcessingTypeActionServiceStub extends ProcessingTypeActionService {

  @volatile
  var actionIds: List[ProcessActionId] = List.empty

  override def markActionExecutionFinished(
      actionId: ProcessActionId
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    actionIds = actionId :: actionIds
    Future.successful(true)
  }

  def sentActionIds: List[ProcessActionId] = actionIds

}
