package pl.touk.nussknacker.engine.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion

case class DeploymentWithRuntimeParams[ProcessRep](
    processVersion: ProcessVersion,
    process: ProcessRep,
    inputConfigDuringExecutionJson: String,
    runtimeParams: RuntimeParams,
)

final case class RuntimeParams(params: Map[String, String])
