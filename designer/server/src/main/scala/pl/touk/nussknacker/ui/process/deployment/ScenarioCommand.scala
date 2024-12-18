package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.deployment.{CustomActionResult, ExternalDeploymentId, RunOffScheduleResult}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future

sealed trait ScenarioCommand[Result] {
  val commonData: CommonCommandData
}

case class CommonCommandData(processIdWithName: ProcessIdWithName, comment: Option[Comment], user: LoggedUser) {
  implicit def implicitUser: LoggedUser = user
}

// Inner Future in result allows to wait for deployment finish, while outer handles validation
// We split deploy process that way because we want to be able to split FE logic into two phases:
// - validations - it is quick part, the result will be displayed on deploy modal
// - deployment on engine side - it is longer part, the result will be shown as a notification
case class RunDeploymentCommand(
    commonData: CommonCommandData,
    stateRestoringStrategy: StateRestoringStrategy,
    nodesDeploymentData: NodesDeploymentData
) extends ScenarioCommand[Future[Option[ExternalDeploymentId]]]

case class CustomActionCommand(
    commonData: CommonCommandData,
    actionName: ScenarioActionName,
    params: Map[String, String],
) extends ScenarioCommand[CustomActionResult]

case class RunOffScheduleCommand(
    commonData: CommonCommandData,
) extends ScenarioCommand[RunOffScheduleResult]

// TODO CancelScenarioCommand will be legacy in some future because it operates on the scenario level instead of deployment level -
//      we should replace it by command operating on deployment
case class CancelScenarioCommand(commonData: CommonCommandData) extends ScenarioCommand[Unit]
