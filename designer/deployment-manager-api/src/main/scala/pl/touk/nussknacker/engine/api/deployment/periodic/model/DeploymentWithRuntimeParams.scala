package pl.touk.nussknacker.engine.api.deployment.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion

case class DeploymentWithRuntimeParams(
    processVersion: ProcessVersion,
    inputConfigDuringExecutionJson: String,
    runtimeParams: RuntimeParams,
)

final case class RuntimeParams(params: Map[String, String])
