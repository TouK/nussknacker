package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{DMScenarioCommand, ScenarioActionName}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.deployment.{CustomActionResult, ExternalDeploymentId}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future

sealed trait ScenarioCommand[Result] {
  def user: LoggedUser

  implicit def implicitUser: LoggedUser = user
}

// Inner Future in result allows to wait for deployment finish, while outer handles validation
// We split deploy process that way because we want to be able to split FE logic into two phases:
// - validations - it is quick part, the result will be displayed on deploy modal
// - deployment on engine side - it is longer part, the result will be shown as a notification
case class RunDeploymentCommand(
    processId: ProcessIdWithName,
    savepointPath: Option[String],
    comment: Option[String],
    user: LoggedUser
) extends ScenarioCommand[Future[Option[ExternalDeploymentId]]]

case class CustomActionCommand(
    actionName: ScenarioActionName,
    processIdWithName: ProcessIdWithName,
    params: Map[String, String],
    user: LoggedUser
) extends ScenarioCommand[CustomActionResult]

// TODO CancelScenarioCommand will be legacy in some future because it operates on the scenario level instead of deployment level -
//      we should replace it by command operating on deployment
case class CancelScenarioCommand(processId: ProcessIdWithName, comment: Option[String], user: LoggedUser)
    extends ScenarioCommand[Unit]
