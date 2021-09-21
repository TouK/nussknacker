package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion

case class DeploymentWithJarData(processVersion: ProcessVersion,
                                 processJson: String,
                                 inputConfigDuringExecutionJson: String,
                                 jarFileName: String)
