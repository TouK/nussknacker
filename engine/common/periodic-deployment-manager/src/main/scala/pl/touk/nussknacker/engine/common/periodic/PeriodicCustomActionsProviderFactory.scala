package pl.touk.nussknacker.engine.common.periodic

import pl.touk.nussknacker.engine.api.deployment.DMCustomActionCommand
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicProcessesManager
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionResult}

import scala.concurrent.Future

trait PeriodicCustomActionsProviderFactory {

  def create(
      periodicProcessesManager: PeriodicProcessesManager,
      periodicProcessService: PeriodicProcessService,
      processingType: String,
  ): PeriodicCustomActionsProvider

}

object PeriodicCustomActionsProviderFactory {
  def noOp: PeriodicCustomActionsProviderFactory =
    (_: PeriodicProcessesManager, _: PeriodicProcessService, _: String) => EmptyPeriodicCustomActionsProvider
}

trait PeriodicCustomActionsProvider {
  def customActions: List[CustomActionDefinition]

  def invokeCustomAction(
      actionRequest: DMCustomActionCommand
  ): Future[CustomActionResult]

}

object EmptyPeriodicCustomActionsProvider extends PeriodicCustomActionsProvider {
  override def customActions: List[CustomActionDefinition] = Nil

  override def invokeCustomAction(
      actionRequest: DMCustomActionCommand
  ): Future[CustomActionResult] =
    Future.failed(new NotImplementedError())

}
