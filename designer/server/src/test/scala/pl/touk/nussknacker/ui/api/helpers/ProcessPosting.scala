package pl.touk.nussknacker.ui.api.helpers

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.syntax._
import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment

class ProcessPosting {

  private implicit val ptsEncoder: Encoder[UpdateProcessCommand] = deriveConfiguredEncoder

  def toJsonAsProcessToSave(process: CanonicalProcess, comment: String = ""): Json = {
    val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(process)
    UpdateProcessCommand(scenarioGraph, UpdateProcessComment(comment), None).asJson
  }

  def toJsonAsProcessToSave(scenarioGraph: ScenarioGraph): Json = {
    UpdateProcessCommand(scenarioGraph, UpdateProcessComment(""), None).asJson
  }

  def toRequestEntity[T: Encoder](request: T): RequestEntity = {
    HttpEntity(ContentTypes.`application/json`, request.asJson.spaces2)
  }

}
