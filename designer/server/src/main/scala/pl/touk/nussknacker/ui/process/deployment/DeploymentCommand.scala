package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.security.api.LoggedUser

sealed trait DeploymentCommand {
  def id: NewDeploymentId
}

final case class NewRunDeploymentCommand(
    id: NewDeploymentId,
    scenarioName: ProcessName,
    nodesDeploymentData: NodesDeploymentData,
    user: LoggedUser,
    comment: Option[Comment]
) extends DeploymentCommand
