package pl.touk.nussknacker.test.utils.domain

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.syntax._
import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.ProcessService.UpdateScenarioCommand
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment

class ProcessPosting {

  private implicit val ptsEncoder: Encoder[UpdateScenarioCommand] = deriveConfiguredEncoder

  def toJsonAsProcessToSave(process: CanonicalProcess, comment: String = ""): Json = {
    val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(process)
    UpdateScenarioCommand(scenarioGraph, UpdateProcessComment(comment), None).asJson
  }

  def toJsonAsProcessToSave(scenarioGraph: ScenarioGraph): Json = {
    UpdateScenarioCommand(scenarioGraph, UpdateProcessComment(""), None).asJson
  }

  def toRequestEntity[T: Encoder](request: T): RequestEntity = {
    HttpEntity(ContentTypes.`application/json`, request.asJson.spaces2)
  }

}
