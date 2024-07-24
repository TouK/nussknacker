package pl.touk.nussknacker.engine.definition.test

import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.test.ScenarioTestJsonRecord

case class PreliminaryScenarioTestData(testRecords: NonEmptyList[PreliminaryScenarioTestRecord])

sealed trait PreliminaryScenarioTestRecord

object PreliminaryScenarioTestRecord {

  case class Simplified(record: Json) extends PreliminaryScenarioTestRecord
  @JsonCodec case class Standard(sourceId: String, record: Json, timestamp: Option[Long] = None)
      extends PreliminaryScenarioTestRecord

  implicit val simplifiedEncoder: Encoder[Simplified] = Encoder.instance(_.record)

  implicit val simplifiedDecoder: Decoder[Simplified] = Decoder.decodeJson.map(Simplified)

  implicit val encoder: Encoder[PreliminaryScenarioTestRecord] = Encoder.instance {
    case record: Standard   => implicitly[Encoder[Standard]].apply(record).dropNullValues
    case record: Simplified => implicitly[Encoder[Simplified]].apply(record)
  }

  implicit val decoder: Decoder[PreliminaryScenarioTestRecord] = {
    val standardDecoder: Decoder[PreliminaryScenarioTestRecord]   = implicitly[Decoder[Standard]].map(identity)
    val simplifiedDecoder: Decoder[PreliminaryScenarioTestRecord] = implicitly[Decoder[Simplified]].map(identity)
    standardDecoder.or(simplifiedDecoder)
  }

  def apply(ScenarioTestJsonRecord: ScenarioTestJsonRecord): PreliminaryScenarioTestRecord = {
    Standard(
      ScenarioTestJsonRecord.sourceId.id,
      ScenarioTestJsonRecord.record.json,
      ScenarioTestJsonRecord.record.timestamp
    )
  }

}
