package pl.touk.nussknacker.engine.deployment

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph

@ConfiguredJsonCodec sealed trait ScenarioSource
case object LatestVersion extends ScenarioSource
@JsonCodec final case class FromGraph(scenarioGraph: ScenarioGraph, scenarioLabels: Option[List[String]])
    extends ScenarioSource
