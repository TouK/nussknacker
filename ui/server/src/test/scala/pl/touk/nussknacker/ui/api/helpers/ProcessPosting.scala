package pl.touk.nussknacker.ui.api.helpers

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment

class ProcessPosting {

  private implicit val ptsEncoder: Encoder[UpdateProcessCommand] = deriveConfiguredEncoder

  def toRequest[T:Encoder](value: T): RequestEntity = HttpEntity(ContentTypes.`application/json`, value.asJson.spaces2)

  def toEntity(process: CanonicalProcess): RequestEntity = {
    toRequest(ProcessConverter.toDisplayable(process, TestProcessingTypes.Streaming))
  }

  def toEntityAsProcessToSave(process: CanonicalProcess, comment: String = ""): RequestEntity = {
    val displayable = ProcessConverter.toDisplayable(process, TestProcessingTypes.Streaming)
    toRequest(UpdateProcessCommand(displayable, UpdateProcessComment(comment)))
  }

  def toEntity(properties: ProcessProperties): RequestEntity = {
    toRequest(properties)
  }

  def toEntity(process: DisplayableProcess): RequestEntity = {
    toRequest(process)
  }

  def toEntity(process: UpdateProcessCommand): RequestEntity = {
    toRequest(process)
  }

  def toEntityAsProcessToSave(process: DisplayableProcess): RequestEntity = {
    toRequest(UpdateProcessCommand(process, UpdateProcessComment("")))
  }

}
