package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion

case class ProcessState(id: DeploymentId,
                        runningState: RunningState.Value,
                        status: String,
                        startTime: Long,
                        version: Option[ProcessVersion],
                        message: Option[String] = None)

object RunningState extends Enumeration {

  val Running, Error, Deploying, Finished = Value

}