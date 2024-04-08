package pl.touk.nussknacker.ui.process.deployment

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.NodeId
import sttp.tapir.Schema

final case class SourcesEventsFilteringRules(rulesBySourceId: Map[NodeId, SourceEventsFilteringRules])

final case class SourceEventsFilteringRules(rules: List[FieldValueIsEqualToRule])

// TODO: In the future, we could add also range filtering and similar rules
// TODO (next PRs): support for other simple types for value
final case class FieldValueIsEqualToRule(fieldName: FieldName, value: String)

final case class FieldName(value: String) {
  override def toString: String = value
}

object SourceEventsFilteringRules {

  implicit val sourceEventsFilteringRulesCodec: Schema[SourceEventsFilteringRules] = Schema
    .schemaForMap[String, String](identity)
    .map[SourceEventsFilteringRules]((map: Map[String, String]) => Some(convertMapToSourceRules(map)))(
      convertSourceRulesToMap
    )

  implicit val sourceEventsFilteringRulesEncoder: Encoder[SourceEventsFilteringRules] = Encoder
    .encodeMap[String, String]
    .contramap(convertSourceRulesToMap)

  implicit val sourceEventsFilteringRulesDecoder: Decoder[SourceEventsFilteringRules] =
    Decoder.decodeMap[String, String].map(convertMapToSourceRules)

  private def convertSourceRulesToMap(rules: SourceEventsFilteringRules): Map[String, String] = {
    rules.rules.map { rule =>
      rule.fieldName.value -> rule.value
    }.toMap
  }

  private def convertMapToSourceRules(map: Map[String, String]): SourceEventsFilteringRules = {
    SourceEventsFilteringRules(map.toList.map { case (fieldName, value) =>
      FieldValueIsEqualToRule(FieldName(fieldName), value)
    })
  }

}

object SourcesEventsFilteringRules {

  implicit val sourcesEventsFilteringRulesCodec: Schema[SourcesEventsFilteringRules] = Schema
    .schemaForMap[NodeId, SourceEventsFilteringRules](_.id)
    .map[SourcesEventsFilteringRules]((map: Map[NodeId, SourceEventsFilteringRules]) =>
      Some(SourcesEventsFilteringRules(map))
    )(_.rulesBySourceId)

  implicit val sourcesEventsFilteringRulesEncoder: Encoder[SourcesEventsFilteringRules] = Encoder
    .encodeMap[NodeId, SourceEventsFilteringRules]
    .contramap(_.rulesBySourceId)

  implicit val sourcesEventsFilteringRulesDecoder: Decoder[SourcesEventsFilteringRules] =
    Decoder.decodeMap[NodeId, SourceEventsFilteringRules].map(SourcesEventsFilteringRules(_))

}
