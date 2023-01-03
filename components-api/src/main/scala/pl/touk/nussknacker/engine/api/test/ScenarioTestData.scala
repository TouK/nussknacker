package pl.touk.nussknacker.engine.api.test

import io.circe.Json
import pl.touk.nussknacker.engine.api.NodeId

case class ScenarioTestRecord(sourceId: NodeId, record: TestRecord)

object ScenarioTestRecord {

  def apply(sourceId: String, json: Json, timestamp: Option[Long] = None): ScenarioTestRecord = {
    ScenarioTestRecord(NodeId(sourceId), TestRecord(json, timestamp))
  }

}

case class ScenarioTestData(testRecords: List[ScenarioTestRecord])
