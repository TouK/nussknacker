package pl.touk.nussknacker.engine.api.test

import io.circe.Json
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.test.TestParameters.TestParameterDefinitions

sealed trait ScenarioTestRecord

case class ScenarioTestJsonRecord(sourceId: NodeId, record: TestRecord) extends ScenarioTestRecord
case class ScenarioTestParametersRecord(sourceId: NodeId, testParameters: TestParameters) extends ScenarioTestRecord

object ScenarioTestJsonRecord {
  def apply(sourceId: String, json: Json, timestamp: Option[Long] = None): ScenarioTestJsonRecord = {
    ScenarioTestJsonRecord(NodeId(sourceId), TestRecord(json, timestamp))
  }
}

/**
 * Holds test records for a scenario. The difference to [[TestData]] is that records are assigned to the individual sources in the scenario.
 */
case class ScenarioTestData(testRecords: List[ScenarioTestRecord])

object ScenarioTestData {

  def apply(sourceId: String, testParameters: TestParameterDefinitions, timestamp: Option[Long] = None): ScenarioTestData = {
    ScenarioTestData(ScenarioTestParametersRecord(NodeId(sourceId), TestParameters(testParameters, timestamp)) :: Nil)
  }

}
