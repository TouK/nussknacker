package pl.touk.nussknacker.restmodel

import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}

object ProcessType extends Enumeration {
  type ProcessType = Value
  val Graph: ProcessType = Value("graph")
  val Custom: ProcessType = Value("custom")

  def fromDeploymentData(processDeploymentData: ProcessDeploymentData): ProcessType = processDeploymentData match {
    case _: GraphProcess => ProcessType.Graph
    case _: CustomProcess => ProcessType.Custom
  }

}
