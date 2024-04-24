package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentIdNG
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future

sealed trait DeploymentCommand {
  def id: DeploymentIdNG
}

final case class RunDeploymentCommandNG(
    id: DeploymentIdNG,
    scenarioName: ProcessName,
    nodesDeploymentData: NodesDeploymentData,
    user: LoggedUser,
    comment: Option[Comment]
) extends DeploymentCommand
