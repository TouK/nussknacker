package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.{
  DefaultVisibleActions,
  ScenarioStatusWithScenarioContext
}
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
      customVisibleActions = Some(DefaultVisibleActions ::: ScenarioActionName.RunOffSchedule :: Nil),
      customActionTooltips = Some(PeriodicStateStatus.customActionTooltips),
      delegate = delegate
    ) {

  override def statusActions(input: ScenarioStatusWithScenarioContext): Set[ScenarioActionName] = {
    super.statusActions(
      extractPeriodicStatus(input.scenarioStatus)
        .map(periodic => input.withScenarioStatus(periodic.mergedStatus))
        .getOrElse(input) // We have to handle also statuses resolved by core (for example NotDeployed)
    )
  }

  override def actionTooltips(input: ScenarioStatusWithScenarioContext): Map[ScenarioActionName, String] = {
    super.actionTooltips(
      extractPeriodicStatus(input.scenarioStatus)
        .map(periodic => input.withScenarioStatus(periodic.mergedStatus))
        .getOrElse(input) // We have to handle also statuses resolved by core (for example NotDeployed)
    )
  }

  override def statusIcon(input: ScenarioStatusWithScenarioContext): URI = {
    super.statusIcon(
      extractPeriodicStatus(input.scenarioStatus)
        .map(periodic => input.withScenarioStatus(periodic.mergedStatus))
        .getOrElse(input) // We have to handle also statuses resolved by core (for example NotDeployed)
    )
  }

  override def statusDescription(input: ScenarioStatusWithScenarioContext): String = {
    super.statusDescription(
      extractPeriodicStatus(input.scenarioStatus)
        .map(periodic => input.withScenarioStatus(periodic.mergedStatus))
        .getOrElse(input) // We have to handle also statuses resolved by core (for example NotDeployed)
    )
  }

  override def statusTooltip(input: ScenarioStatusWithScenarioContext): String = {
    extractPeriodicStatus(input.scenarioStatus)
      .map { periodicStatus =>
        PeriodicProcessStateDefinitionManager.statusTooltip(
          activeDeploymentsStatuses = periodicStatus.activeDeploymentsStatuses,
          inactiveDeploymentsStatuses = periodicStatus.inactiveDeploymentsStatuses
        )
      }
      .getOrElse(super.statusTooltip(input))
  }

  private def extractPeriodicStatus(stateStatus: StateStatus) = {
    Option(stateStatus) collect { case periodic: PeriodicProcessStatusWithMergedStatus =>
      periodic
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
