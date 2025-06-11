package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class PreliminaryScenarioRecords(records: NonEmptyList[PreliminaryScenarioRecord])

final case class PreliminaryScenarioRecord(
    sourceId: String,
    record: Json,
    timestamp: Option[Long]
)

object PreliminaryScenarioRecord {

  implicit val encoder: Encoder[PreliminaryScenarioRecord] =
    deriveEncoder[PreliminaryScenarioRecord].mapJson(_.dropNullValues)
  implicit val decoder: Decoder[PreliminaryScenarioRecord] = deriveDecoder[PreliminaryScenarioRecord]

}
