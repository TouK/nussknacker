package pl.touk.nussknacker.engine.api.test

import io.circe.Json
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.expression.Expression

import java.time.Instant

sealed trait ScenarioTestRecord {
  val sourceId: NodeId
}

case class ScenarioTestSourceSpecificFormatJsonRecord(sourceId: NodeId, record: Json, timestamp: Option[Long] = None)
    extends ScenarioTestRecord

case class ScenarioTestCommonFormatJsonRecord(
    sourceId: NodeId,
    variables: Map[String, Json],
    upstreamTimestamp: Option[Instant]
) extends ScenarioTestRecord

case class ScenarioTestParametersRecord(sourceId: NodeId, parameterExpressions: Map[ParameterName, Expression])
    extends ScenarioTestRecord

/**
 * Holds test records for a scenario. The difference to [[TestData]] is that records are assigned to the individual sources in the scenario.
 */
case class ScenarioTestData(inputRecords: List[ScenarioTestRecord]) {

  def commonFormatRecords(nodeId: NodeId): List[(ScenarioTestCommonFormatJsonRecord, Int)] = {
    inputRecords.zipWithIndex.collect { case (record @ ScenarioTestCommonFormatJsonRecord(`nodeId`, _, _), index) =>
      (record, index)
    }
  }

}

object ScenarioTestData {

  def apply(sourceId: String, parameterExpressions: Map[ParameterName, Expression]): ScenarioTestData = {
    ScenarioTestData(List(ScenarioTestParametersRecord(NodeId(sourceId), parameterExpressions)))
  }

}
