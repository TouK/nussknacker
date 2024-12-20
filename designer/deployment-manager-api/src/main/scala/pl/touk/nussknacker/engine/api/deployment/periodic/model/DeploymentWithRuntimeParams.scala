package pl.touk.nussknacker.engine.api.deployment.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion

final case class DeploymentWithRuntimeParams(
    processVersion: ProcessVersion,
    runtimeParams: RuntimeParams,
)

final case class RuntimeParams(params: Map[String, String])
