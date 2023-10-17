package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.{Decoder, Encoder}

final case class ProcessingMode(value: String) {
  override def toString: String = value
}

object ProcessingMode {

  val Streaming: ProcessingMode       = ProcessingMode("Streaming")
  val RequestResponse: ProcessingMode = ProcessingMode("Request-Response")
  val Batch: ProcessingMode           = ProcessingMode("Batch")

  implicit val encoder: Encoder[ProcessingMode] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[ProcessingMode] = Decoder.decodeString.map(ProcessingMode(_))

}
