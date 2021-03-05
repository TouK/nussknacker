package pl.touk.nussknacker.engine.management.periodic.jar

import pl.touk.nussknacker.engine.api.ProcessVersion

case class DeploymentWithJarData(processVersion: ProcessVersion,
                                 processJson: String,
                                 modelConfig: String,
                                 jarFileName: String)
