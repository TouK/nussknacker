package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}

case class DeploymentWithJarData[ProcessRep](
    processName: ProcessName,
    versionId: VersionId,
    process: ProcessRep,
    inputConfigDuringExecutionJson: String,
    jarFileName: String
)
