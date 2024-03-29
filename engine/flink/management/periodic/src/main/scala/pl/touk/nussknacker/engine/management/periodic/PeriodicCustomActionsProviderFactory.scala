package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment.CustomActionCommand
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionResult}
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository

import scala.concurrent.Future

trait PeriodicCustomActionsProviderFactory {

  def create(
      periodicProcessesRepository: PeriodicProcessesRepository,
      periodicProcessService: PeriodicProcessService
  ): PeriodicCustomActionsProvider

}

object PeriodicCustomActionsProviderFactory {
  def noOp: PeriodicCustomActionsProviderFactory = (_: PeriodicProcessesRepository, _: PeriodicProcessService) =>
    EmptyPeriodicCustomActionsProvider
}

trait PeriodicCustomActionsProvider {
  def customActions: List[CustomActionDefinition]

  def invokeCustomAction(
      actionRequest: CustomActionCommand
  ): Future[CustomActionResult]

}

object EmptyPeriodicCustomActionsProvider extends PeriodicCustomActionsProvider {
  override def customActions: List[CustomActionDefinition] = Nil

  override def invokeCustomAction(
      actionRequest: CustomActionCommand
  ): Future[CustomActionResult] =
    Future.failed(new NotImplementedError())

}
