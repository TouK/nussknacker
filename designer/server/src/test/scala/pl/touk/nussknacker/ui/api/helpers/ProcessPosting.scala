package pl.touk.nussknacker.ui.api.helpers

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.syntax._
import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment

class ProcessPosting {

  private implicit val ptsEncoder: Encoder[UpdateProcessCommand] = deriveConfiguredEncoder

  def toEntity(process: CanonicalProcess): RequestEntity = {
    toRequest(toJson(process))
  }

  def toJson(process: CanonicalProcess): Json = {
    ProcessConverter.toDisplayable(process).asJson
  }

  def toEntityAsProcessToSave(process: CanonicalProcess, comment: String = ""): RequestEntity = {
    toRequest(toJsonAsProcessToSave(process, comment))
  }

  def toJsonAsProcessToSave(process: CanonicalProcess, comment: String = ""): Json = {
    val displayable = ProcessConverter.toDisplayable(process)
    UpdateProcessCommand(displayable, UpdateProcessComment(comment), None).asJson
  }

  def toEntity(properties: ProcessProperties): RequestEntity = {
    toRequest(properties)
  }

  def toJson(properties: ProcessProperties): Json = {
    properties.asJson
  }

  def toEntity(process: DisplayableProcess): RequestEntity = {
    toRequest(process)
  }

  def toJson(process: DisplayableProcess): Json = {
    process.asJson
  }

  def toEntity(process: UpdateProcessCommand): RequestEntity = {
    toRequest(process)
  }

  def toJson(process: UpdateProcessCommand): Json = {
    process.asJson
  }

  def toJsonAsProcessToSave(process: DisplayableProcess): Json = {
    UpdateProcessCommand(process, UpdateProcessComment(""), None).asJson
  }

  def toRequest[T: Encoder](value: T): RequestEntity = {
    toRequest(value.asJson)
  }

  private def toRequest(value: Json): RequestEntity = {
    HttpEntity(ContentTypes.`application/json`, value.spaces2)
  }

}
