package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionState, ScenarioActionName}
import pl.touk.nussknacker.engine.util.SeqUtil.maxByOption

case class LastFinishedActions(
    private val lastFinishedActions: Seq[ProcessAction]
) {

  val lastFinishedAction: Option[ProcessAction] = maxByOption(lastFinishedActions)(_.createdAt)

  val lastFinishedStateAction: Option[ProcessAction] =
    maxByOption(
      lastFinishedActions.filter { action => ScenarioActionName.StateActions.contains(action.actionName) }
    )(_.createdAt)

  // For last deploy action we are interested in Deploys that are Finished (not ExecutionFinished) and that are not Cancelled
  // so that the presence of such an action means that the process is currently deployed
  val lastFinishedDeployAction: Option[ProcessAction] =
    lastFinishedStateAction.find { action =>
      action.actionName == ScenarioActionName.Deploy && action.state == ProcessActionState.Finished
    }

}
