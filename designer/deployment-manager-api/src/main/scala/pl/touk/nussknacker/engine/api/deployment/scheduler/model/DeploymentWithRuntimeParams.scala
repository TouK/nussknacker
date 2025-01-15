package pl.touk.nussknacker.engine.api.deployment.scheduler.model

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}

final case class DeploymentWithRuntimeParams(
    processId: Option[ProcessId],
    processName: ProcessName,
    versionId: VersionId,
    runtimeParams: RuntimeParams,
)

final case class RuntimeParams(params: Map[String, String])
