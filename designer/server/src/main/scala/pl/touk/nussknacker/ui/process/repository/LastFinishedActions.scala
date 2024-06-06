package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionState, ScenarioActionName}

case class LastFinishedActions(
    private val lastFinishedActions: Seq[ProcessAction]
) {
  val lastFinishedAction: Option[ProcessAction] = lastFinishedActions.maxByOption(_.createdAt)

  val lastFinishedStateAction: Option[ProcessAction] =
    lastFinishedActions
      .filter { action =>
        ScenarioActionName.StateActions.contains(action.actionName)
      }
      .maxByOption(_.createdAt)

  // For last deploy action we are interested in Deploys that are Finished (not ExecutionFinished) and that are not Cancelled
  // so that the presence of such an action means that the process is currently deployed
  val lastFinishedDeployAction: Option[ProcessAction] =
    lastFinishedStateAction.find { action =>
      action.actionName == ScenarioActionName.Deploy && action.state == ProcessActionState.Finished
    }

}
