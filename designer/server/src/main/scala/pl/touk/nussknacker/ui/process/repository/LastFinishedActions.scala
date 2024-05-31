package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ScenarioActionName}

case class LastFinishedActions(
    lastFinishedAction: Option[ProcessAction],
    lastExecutionFinishedAction: Option[ProcessAction]
) {
  private val actions = Seq(lastFinishedAction, lastExecutionFinishedAction).flatten

  val lastAction: Option[ProcessAction] = actions.maxByOption(_.createdAt)

  // for last deploy action we are not interested in ExecutionFinished deploys - we don't want to show them in the history
  val lastFinishedDeployAction: Option[ProcessAction] =
    lastFinishedAction.find { action =>
      action.actionName == ScenarioActionName.Deploy
    }

}
