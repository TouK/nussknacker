package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessStateDefinitionManager, StateStatus}
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessService.{DeploymentStatus, PeriodicProcessStatus}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus

class PeriodicProcessStateDefinitionManager(delegate: ProcessStateDefinitionManager) extends OverridingProcessStateDefinitionManager(
  statusActionsPF = PeriodicStateStatus.statusActionsPF,
  statusTooltipsPF = PeriodicStateStatus.statusTooltipsPF,
  statusDescriptionsPF = PeriodicStateStatus.statusDescriptionsPF,
  customStateDefinitions = PeriodicStateStatus.customStateDefinitions,
  delegate = delegate
) {
  override def statusTooltip(stateStatus: StateStatus): String = {
    stateStatus match {
      case periodic: PeriodicProcessStatus => periodic.limitedAndSortedDeployments.map {
        case d@DeploymentStatus(_, scheduleId, runAt, status, processActive, _) =>
          val refinedStatus = {
            if (!processActive && status != PeriodicProcessDeploymentStatus.Finished) {
              "Canceled" // We don't have Canceled status - we only mark periodic_process inactive
            } else if (d.isWaitingForReschedule) {
              "WaitingForReschedule"
            } else {
              status.toString
            }
          }
          s"Schedule ${scheduleId.scheduleName.display} scheduled at: ${runAt.format(PeriodicStateStatus.Format)} status: $refinedStatus"
      }.mkString(",\n")
      case _ => super.statusTooltip(stateStatus)
    }
  }

}
