package pl.touk.nussknacker.ui.api.helpers

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import argonaut.{CodecJson, PrettyParams}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.process.ProcessToSave
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

class ProcessPosting {
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  implicit def processToSaveCodec: CodecJson[ProcessToSave] = CodecJson.derive[ProcessToSave]

  private val prettyParams = PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true)

  def toEntity(process: EspProcess): RequestEntity = {
    val displayable = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(process), TestProcessingTypes.Streaming)
    val json = displayable.asJson.pretty(prettyParams)
    HttpEntity(ContentTypes.`application/json`, json)
  }

  def toEntityAsProcessToSave(process: EspProcess): RequestEntity = {
    val displayable = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(process), TestProcessingTypes.Streaming)
    val json = ProcessToSave(displayable, comment = "").asJson.pretty(prettyParams)
    HttpEntity(ContentTypes.`application/json`, json)
  }

  def toEntity(properties: ProcessProperties): RequestEntity = {
    val json = properties.asJson.pretty(prettyParams)
    HttpEntity(ContentTypes.`application/json`, json)
  }

  def toEntity(process: DisplayableProcess): RequestEntity = {
    val json = process.asJson.pretty(prettyParams)
    HttpEntity(ContentTypes.`application/json`, json)
  }

  def toEntity(process: ProcessToSave): RequestEntity = {
    val json = process.asJson.pretty(prettyParams)
    HttpEntity(ContentTypes.`application/json`, json)
  }

  def toEntityAsProcessToSave(process: DisplayableProcess): RequestEntity = {
    val json = ProcessToSave(process, comment = "").asJson.pretty(prettyParams)
    HttpEntity(ContentTypes.`application/json`, json)
  }

}
