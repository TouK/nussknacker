package pl.touk.nussknacker.ui.api.helpers

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.syntax._
import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.api.ScenarioValidationRequest
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment

class ProcessPosting {

  private implicit val ptsEncoder: Encoder[UpdateProcessCommand] = deriveConfiguredEncoder

  def toScenarioGraphJson(process: CanonicalProcess): Json = {
    ProcessConverter.toDisplayable(process).asJson
  }

  def toJsonAsProcessToSave(process: CanonicalProcess, comment: String = ""): Json = {
    val displayable = ProcessConverter.toDisplayable(process)
    UpdateProcessCommand(displayable, UpdateProcessComment(comment), None).asJson
  }

  def toJsonAsProcessToSave(process: DisplayableProcess): Json = {
    UpdateProcessCommand(process, UpdateProcessComment(""), None).asJson
  }

  def toRequestEntity[T: Encoder](request: T): RequestEntity = {
    HttpEntity(ContentTypes.`application/json`, request.asJson.spaces2)
  }

}
