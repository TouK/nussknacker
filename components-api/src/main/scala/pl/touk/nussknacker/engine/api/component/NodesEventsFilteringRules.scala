package pl.touk.nussknacker.engine.api.component

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.NodeId

final case class NodesEventsFilteringRules(rulesByNodeId: Map[NodeId, NodeEventsFilteringRules])

object NodesEventsFilteringRules {

  val PassAllEventsForEveryNode: NodesEventsFilteringRules = NodesEventsFilteringRules(Map.empty)

  implicit val nodesEventsFilteringRulesEncoder: Encoder[NodesEventsFilteringRules] = Encoder
    .encodeMap[NodeId, NodeEventsFilteringRules]
    .contramap(_.rulesByNodeId)

  implicit val nodesEventsFilteringRulesDecoder: Decoder[NodesEventsFilteringRules] =
    Decoder.decodeMap[NodeId, NodeEventsFilteringRules].map(NodesEventsFilteringRules(_))

}

final case class NodeEventsFilteringRules(fieldRules: Map[FieldName, IsEqualToFieldRule])

object NodeEventsFilteringRules {

  val PassAllEvents: NodeEventsFilteringRules = NodeEventsFilteringRules(Map.empty)

  implicit val nodeEventsFilteringRulesEncoder: Encoder[NodeEventsFilteringRules] = Encoder
    .encodeMap[String, String]
    .contramap(convertNodeRulesToMap)

  implicit val nodeEventsFilteringRulesDecoder: Decoder[NodeEventsFilteringRules] =
    Decoder.decodeMap[String, String].map(convertMapToNodeRules)

  def convertNodeRulesToMap(rules: NodeEventsFilteringRules): Map[String, String] = {
    rules.fieldRules.map { case (fieldName, IsEqualToFieldRule(expectedValue)) =>
      fieldName.value -> expectedValue
    }
  }

  def convertMapToNodeRules(map: Map[String, String]): NodeEventsFilteringRules = {
    NodeEventsFilteringRules(map.toList.map { case (fieldName, value) =>
      FieldName(fieldName) -> IsEqualToFieldRule(value)
    }.toMap)
  }

}

// TODO: In the future, we could add also range filtering and similar rules
// TODO (next PRs): support for other simple types for value
final case class IsEqualToFieldRule(expectedValue: String)

final case class FieldName(value: String) {
  override def toString: String = value
}
