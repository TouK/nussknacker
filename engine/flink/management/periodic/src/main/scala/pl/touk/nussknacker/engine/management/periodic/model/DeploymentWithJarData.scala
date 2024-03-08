package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion

case class DeploymentWithJarData[ProcessRep](
    processVersion: ProcessVersion,
    process: ProcessRep,
    inputConfigDuringExecutionJson: String,
    jarFileName: String
)
