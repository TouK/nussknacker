package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe._
import io.circe.DecodingFailure.Reason.CustomReason
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class PreliminaryScenarioRecords(records: NonEmptyList[PreliminaryScenarioRecord])

sealed trait PreliminaryScenarioRecord {
  def sourceId: String
  def timestamp: Option[Long]
}

final case class SourceSpecificFormatPreliminaryScenarioRecord(
    override val sourceId: String,
    record: Json,
    override val timestamp: Option[Long]
) extends PreliminaryScenarioRecord

final case class CommonFormatPreliminaryScenarioRecord(
    override val sourceId: String,
    variables: Map[String, Json],
    override val timestamp: Option[Long]
) extends PreliminaryScenarioRecord

object PreliminaryScenarioRecord {

  implicit val sourceSpecificFormatEncoder: Encoder[SourceSpecificFormatPreliminaryScenarioRecord] =
    deriveEncoder[SourceSpecificFormatPreliminaryScenarioRecord].mapJson(_.dropNullValues)

  implicit val commonFormatEncoder: Encoder[CommonFormatPreliminaryScenarioRecord] =
    deriveEncoder[CommonFormatPreliminaryScenarioRecord].mapJson(_.dropNullValues)

  implicit val sourceSpecificFormatDecoder: Decoder[SourceSpecificFormatPreliminaryScenarioRecord] =
    deriveDecoder[SourceSpecificFormatPreliminaryScenarioRecord]

  implicit val commonFormatDecoder: Decoder[CommonFormatPreliminaryScenarioRecord] =
    deriveDecoder[CommonFormatPreliminaryScenarioRecord]

  implicit val encoder: Encoder[PreliminaryScenarioRecord] = {
    case sourceSpecificFormatRecord: SourceSpecificFormatPreliminaryScenarioRecord =>
      sourceSpecificFormatEncoder(sourceSpecificFormatRecord)
    case commonFormatRecord: CommonFormatPreliminaryScenarioRecord =>
      commonFormatEncoder(commonFormatRecord)
  }

  implicit val decoder: Decoder[PreliminaryScenarioRecord] = (cursor: HCursor) => {
    if (cursor.downField("record").succeeded) sourceSpecificFormatDecoder(cursor)
    else if (cursor.downField("variables").succeeded) commonFormatDecoder(cursor)
    else
      Left(
        DecodingFailure(
          CustomReason("Invalid record. Record should contain either 'variables' or 'record' field"),
          cursor
        )
      )
  }

}
