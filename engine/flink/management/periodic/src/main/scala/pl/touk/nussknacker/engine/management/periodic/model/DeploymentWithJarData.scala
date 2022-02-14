package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

case class DeploymentWithJarData(processVersion: ProcessVersion,
                                 canonicalProcess: CanonicalProcess,
                                 inputConfigDuringExecutionJson: String,
                                 jarFileName: String)
