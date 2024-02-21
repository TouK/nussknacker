package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
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
  def customActions: List[CustomAction]

  def invokeCustomAction(
      actionRequest: ActionRequest,
      canonicalProcess: CanonicalProcess
  ): Future[ActionResult]

}

object EmptyPeriodicCustomActionsProvider extends PeriodicCustomActionsProvider {
  override def customActions: List[CustomAction] = Nil

  override def invokeCustomAction(
      actionRequest: ActionRequest,
      canonicalProcess: CanonicalProcess
  ): Future[ActionResult] =
    Future.failed(new NotImplementedError())

}
