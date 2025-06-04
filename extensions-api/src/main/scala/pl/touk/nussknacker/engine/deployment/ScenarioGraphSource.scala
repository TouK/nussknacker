package pl.touk.nussknacker.engine.deployment

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.VersionId

@ConfiguredJsonCodec sealed trait ScenarioGraphSource
case object LatestVersion extends ScenarioGraphSource

@JsonCodec final case class FromGraph(
    scenarioGraph: ScenarioGraph,
    scenarioLabels: Option[List[String]],
    baseScenarioVersionId: VersionId
) extends ScenarioGraphSource
