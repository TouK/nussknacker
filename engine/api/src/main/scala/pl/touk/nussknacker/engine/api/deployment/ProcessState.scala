package pl.touk.nussknacker.engine.api.deployment

case class ProcessState(id: DeploymentId,
                        runningState: RunningState.Value,
                        status: String,
                        startTime: Long,
                        version: Option[Long],
                        message: Option[String] = None)

object RunningState extends Enumeration {

  val Running, Error, Deploying, Finished = Value

}