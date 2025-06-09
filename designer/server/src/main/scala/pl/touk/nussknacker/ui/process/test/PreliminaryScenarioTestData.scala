package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import pl.touk.nussknacker.engine.api.test.ScenarioTestJsonRecord

case class PreliminaryScenarioTestData(testRecords: NonEmptyList[PreliminaryScenarioTestRecord])

final case class PreliminaryScenarioTestRecord(
    sourceId: String,
    record: Json,
    timestamp: Option[Long] = None
)

object PreliminaryScenarioTestRecord {

  implicit val encoder: Encoder[PreliminaryScenarioTestRecord] =
    deriveEncoder[PreliminaryScenarioTestRecord].mapJson(_.dropNullValues)
  implicit val decoder: Decoder[PreliminaryScenarioTestRecord] = deriveDecoder[PreliminaryScenarioTestRecord]

  def apply(ScenarioTestJsonRecord: ScenarioTestJsonRecord): PreliminaryScenarioTestRecord = {
    PreliminaryScenarioTestRecord(
      ScenarioTestJsonRecord.sourceId.id,
      ScenarioTestJsonRecord.record.json,
      ScenarioTestJsonRecord.record.timestamp
    )
  }

}
