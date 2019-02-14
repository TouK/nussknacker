package pl.touk.nussknacker.engine.api.deployment

case class ProcessState(id: DeploymentId,
                        runningState: RunningState.Value,
                        status: String,
                        startTime: Long,
                        message: Option[String] = None)

object RunningState extends Enumeration {

  val Running, Error, Finished = Value

}