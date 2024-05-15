package pl.touk.nussknacker.ui.process.newdeployment

import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.security.api.LoggedUser

sealed trait DeploymentCommand {
  def id: DeploymentId
}

final case class RunDeploymentCommand(
    id: DeploymentId,
    scenarioName: ProcessName,
    nodesDeploymentData: NodesDeploymentData,
    user: LoggedUser,
    comment: Option[Comment]
) extends DeploymentCommand
