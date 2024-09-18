package pl.touk.nussknacker.test.utils.domain

import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil._
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.ProcessService.UpdateScenarioCommand
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

object ScenarioToJsonHelper {

  private implicit val ptsEncoder: Encoder[UpdateScenarioCommand] = deriveConfiguredEncoder

  implicit class ScenarioGraphToJson(scenarioGraph: ScenarioGraph) {

    def toJsonAsProcessToSave: Json =
      UpdateScenarioCommand(
        scenarioGraph,
        comment = None,
        scenarioLabels = Some(List.empty),
        forwardedUserName = None
      ).asJson

  }

  implicit class ScenarioToJson(scenario: CanonicalProcess) {
    def toJsonAsProcessToSave: Json = CanonicalProcessConverter.toScenarioGraph(scenario).toJsonAsProcessToSave
  }

}
