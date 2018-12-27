package pl.touk.nussknacker.engine.api.deployment

case class ProcessState(id: DeploymentId,
                        isOK: Boolean,
                        status: String,
                        startTime: Long,
                        message: Option[String] = None)