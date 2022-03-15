package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.{CustomActionsProvider, EmptyCustomActionsProvider}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository

trait PeriodicCustomActionsProviderFactory {
  def create(periodicProcessesRepository: PeriodicProcessesRepository, periodicProcessService: PeriodicProcessService): CustomActionsProvider
}

object PeriodicCustomActionsProviderFactory {
  def noOp: PeriodicCustomActionsProviderFactory = (_: PeriodicProcessesRepository, _: PeriodicProcessService) => EmptyCustomActionsProvider
}
