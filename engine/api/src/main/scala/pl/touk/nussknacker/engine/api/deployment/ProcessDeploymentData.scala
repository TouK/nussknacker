package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion

sealed trait ProcessDeploymentData

case class GraphProcess(processAsJson: String) extends ProcessDeploymentData

case class CustomProcess(mainClass: String) extends ProcessDeploymentData
