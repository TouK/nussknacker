package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.GraphProcess

case class DeploymentWithJarData(processVersion: ProcessVersion,
                                 graphProcess: GraphProcess,
                                 inputConfigDuringExecutionJson: String,
                                 jarFileName: String)
