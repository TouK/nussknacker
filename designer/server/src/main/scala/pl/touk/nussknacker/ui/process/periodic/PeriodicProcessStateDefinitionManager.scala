package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.defaultVisibleActions
import pl.touk.nussknacker.engine.api.deployment.{
  OverridingProcessStateDefinitionManager,
  ProcessStateDefinitionManager,
  ScenarioActionName,
  StateStatus
}
import pl.touk.nussknacker.ui.process.periodic.PeriodicProcessService.{
  MaxDeploymentsStatus,
  PeriodicDeploymentStatus,
  PeriodicProcessStatusWithMergedStatus
}

import java.net.URI

class PeriodicProcessStateDefinitionManager(delegate: ProcessStateDefinitionManager)
    extends OverridingProcessStateDefinitionManager(
      statusActionsPF = PeriodicStateStatus.statusActionsPF,
      statusTooltipsPF = PeriodicStateStatus.statusTooltipsPF,
      statusDescriptionsPF = PeriodicStateStatus.statusDescriptionsPF,
      customStateDefinitions = PeriodicStateStatus.customStateDefinitions,
      customVisibleActions = Some(defaultVisibleActions ::: ScenarioActionName.RunOffSchedule :: Nil),
      customActionTooltips = Some(PeriodicStateStatus.customActionTooltips),
      delegate = delegate
    ) {

  override def statusActions(processStatus: ProcessStateDefinitionManager.ProcessStatus): List[ScenarioActionName] = {
    super.statusActions(processStatus.copy(stateStatus = extractPeriodicStatus(processStatus.stateStatus).mergedStatus))
  }

  override def actionTooltips(
      processStatus: ProcessStateDefinitionManager.ProcessStatus
  ): Map[ScenarioActionName, String] = {
    super.actionTooltips(
      processStatus.copy(stateStatus = extractPeriodicStatus(processStatus.stateStatus).mergedStatus)
    )
  }

  override def statusIcon(stateStatus: StateStatus): URI = {
    super.statusIcon(extractPeriodicStatus(stateStatus).mergedStatus)
  }

  override def statusDescription(stateStatus: StateStatus): String = {
    super.statusDescription(extractPeriodicStatus(stateStatus).mergedStatus)
  }

  override def statusTooltip(stateStatus: StateStatus): String = {
    val periodicStatus = extractPeriodicStatus(stateStatus)
    PeriodicProcessStateDefinitionManager.statusTooltip(
      activeDeploymentsStatuses = periodicStatus.activeDeploymentsStatuses,
      inactiveDeploymentsStatuses = periodicStatus.inactiveDeploymentsStatuses
    )
  }

  private def extractPeriodicStatus(stateStatus: StateStatus) = {
    stateStatus match {
      case periodic: PeriodicProcessStatusWithMergedStatus =>
        periodic
      case other => throw new IllegalStateException(s"Unexpected status: $other")
    }
  }

}

object PeriodicProcessStateDefinitionManager {

  def statusTooltip(
      activeDeploymentsStatuses: List[PeriodicDeploymentStatus],
      inactiveDeploymentsStatuses: List[PeriodicDeploymentStatus]
  ): String = {
    val limitedAndSortedDeployments: List[PeriodicDeploymentStatus] =
      (activeDeploymentsStatuses ++ inactiveDeploymentsStatuses.take(
        MaxDeploymentsStatus - activeDeploymentsStatuses.size
      )).sorted(PeriodicDeploymentStatus.ordering.reverse)
    limitedAndSortedDeployments
      .map { case d @ PeriodicDeploymentStatus(_, scheduleId, _, runAt, status, _, _) =>
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
