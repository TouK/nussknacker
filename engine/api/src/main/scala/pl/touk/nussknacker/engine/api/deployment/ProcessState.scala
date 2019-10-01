package pl.touk.nussknacker.engine.api.deployment

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec

@JsonCodec case class ProcessState(id: DeploymentId,
                                   runningState: RunningState.Value,
                                   status: String,
                                   startTime: Long,
                                   message: Option[String] = None)

object RunningState extends Enumeration {

  implicit val encoder: Encoder[RunningState.Value] = Encoder.enumEncoder(RunningState)
  implicit val decoder: Decoder[RunningState.Value] = Decoder.enumDecoder(RunningState)

  val Running, Error, Finished = Value

}