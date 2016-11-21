package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import argonaut.Argonaut._
import argonaut.PrettyParams
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node.NodeData
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter}

class ProcessPosting(processConverter: ProcessConverter) {

  def toEntity(process: EspProcess): RequestEntity = {
    val displayable = processConverter.toDisplayable(ProcessCanonizer.canonize(process))
    implicit val encode = DisplayableProcessCodec.codec
    val json = displayable.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
    HttpEntity(ContentTypes.`application/json`, json)
  }

  def toEntity(node: NodeData): RequestEntity = {
    implicit val encode = DisplayableProcessCodec.nodeEncoder
    val json = node.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
    HttpEntity(ContentTypes.`application/json`, json)
  }

  def toEntity(properties: ProcessProperties): RequestEntity = {
    implicit val codec = DisplayableProcessCodec.propertiesCodec
    val json = properties.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
    HttpEntity(ContentTypes.`application/json`, json)
  }

  def toEntity(process: DisplayableProcess): RequestEntity = {
    implicit val codec = DisplayableProcessCodec.codec
    val json = process.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
    HttpEntity(ContentTypes.`application/json`, json)
  }

}
