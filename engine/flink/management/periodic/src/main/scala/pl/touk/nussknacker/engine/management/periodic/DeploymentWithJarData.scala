package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion

case class DeploymentWithJarData(processVersion: ProcessVersion,
                                 processJson: String,
                                 modelConfig: String,
                                //TODO: this is redundant, as it's embedded in model
                                 buildInfoJson: String,
                                 jarFileName: String)
