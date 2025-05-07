package pl.touk.nussknacker.ui.process.newdeployment

import pl.touk.nussknacker.engine.api.deployment.DeploymentStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.newdeployment.DeploymentId

final case class Deployment(id: DeploymentId, scenarioName: ProcessName, status: DeploymentStatus)
