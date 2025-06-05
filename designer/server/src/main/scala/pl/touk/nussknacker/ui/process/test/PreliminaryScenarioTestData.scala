package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe.Json
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.test.ScenarioTestJsonRecord

case class PreliminaryScenarioTestData(testRecords: NonEmptyList[PreliminaryScenarioTestRecord])

@JsonCodec final case class PreliminaryScenarioTestRecord(
    sourceId: String,
    record: Json,
    timestamp: Option[Long] = None
)

object PreliminaryScenarioTestRecord {

  def apply(ScenarioTestJsonRecord: ScenarioTestJsonRecord): PreliminaryScenarioTestRecord = {
    PreliminaryScenarioTestRecord(
      ScenarioTestJsonRecord.sourceId.id,
      ScenarioTestJsonRecord.record.json,
      ScenarioTestJsonRecord.record.timestamp
    )
  }

}
