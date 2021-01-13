package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion

case class DeploymentWithJarData(processVersion: ProcessVersion,
                                 processJson: String,
                                 modelConfig: String,
                                 buildInfoJson: String,
                                 jarFileName: String)
