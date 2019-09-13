package pl.touk.nussknacker.ui.api.helpers

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import io.circe.Encoder
import io.circe.syntax._
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.process.ProcessToSave
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

class ProcessPosting {

  private implicit val ptsEncoder: Encoder[ProcessToSave] = io.circe.generic.semiauto.deriveEncoder

  private def toRequest[T:Encoder](value: T): RequestEntity = HttpEntity(ContentTypes.`application/json`, value.asJson.spaces2)

  def toEntity(process: EspProcess): RequestEntity = {
    toRequest(ProcessConverter.toDisplayable(ProcessCanonizer.canonize(process), TestProcessingTypes.Streaming))
  }

  def toEntityAsProcessToSave(process: EspProcess): RequestEntity = {
    val displayable = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(process), TestProcessingTypes.Streaming)
    toRequest(ProcessToSave(displayable, comment = ""))
  }

  def toEntity(properties: ProcessProperties): RequestEntity = {
    toRequest(properties)
  }

  def toEntity(process: DisplayableProcess): RequestEntity = {
    toRequest(process)
  }

  def toEntity(process: ProcessToSave): RequestEntity = {
    toRequest(process)
  }

  def toEntityAsProcessToSave(process: DisplayableProcess): RequestEntity = {
    toRequest(ProcessToSave(process, comment = ""))
  }

}
