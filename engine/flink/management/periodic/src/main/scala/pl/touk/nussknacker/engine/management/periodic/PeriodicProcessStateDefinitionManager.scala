package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.defaultVisibleActions
import pl.touk.nussknacker.engine.api.deployment.{
  OverridingProcessStateDefinitionManager,
  ProcessStateDefinitionManager,
  ScenarioActionName,
  StateStatus
}
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessService.{DeploymentStatus, PeriodicProcessStatus}

class PeriodicProcessStateDefinitionManager(delegate: ProcessStateDefinitionManager)
    extends OverridingProcessStateDefinitionManager(
      statusActionsPF = PeriodicStateStatus.statusActionsPF,
      statusTooltipsPF = PeriodicStateStatus.statusTooltipsPF,
      statusDescriptionsPF = PeriodicStateStatus.statusDescriptionsPF,
      customStateDefinitions = PeriodicStateStatus.customStateDefinitions,
      customVisibleActions = Some(defaultVisibleActions ::: ScenarioActionName.PerformSingleExecution :: Nil),
      customActionTooltips = Some(PeriodicStateStatus.customActionTooltips),
      delegate = delegate
    ) {

  override def statusTooltip(stateStatus: StateStatus): String = {
    stateStatus match {
      case periodic: PeriodicProcessStatus => PeriodicProcessStateDefinitionManager.statusTooltip(periodic)
      case _                               => super.statusTooltip(stateStatus)
    }
  }

}

object PeriodicProcessStateDefinitionManager {

  def statusTooltip(processStatus: PeriodicProcessStatus): String = {
    processStatus.limitedAndSortedDeployments
      .map { case d @ DeploymentStatus(_, scheduleId, _, runAt, status, _, _) =>
        val refinedStatus = {
          if (d.isCanceled) {
            "Canceled"
          } else if (d.isWaitingForReschedule) {
            "WaitingForReschedule"
          } else {
            status.toString
          }
        }
        val prefix = scheduleId.scheduleName.value
          .map { definedScheduleName =>
            s"Schedule $definedScheduleName scheduled at:"
          }
          .getOrElse {
            s"Scheduled at:"
          }
        s"$prefix ${runAt.format(PeriodicStateStatus.Format)} status: $refinedStatus"
      }
      .mkString(",\n")
  }

}
