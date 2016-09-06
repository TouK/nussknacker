package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import argonaut.Argonaut._
import argonaut.PrettyParams
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.ui.process.displayedgraph.displayablenode.DisplayableNode
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter}

trait ProcessPosting {

  protected def toEntity(process: EspProcess): RequestEntity = {
    val displayable = ProcessConverter.toDisplayable(process)
    implicit val encode = DisplayableProcessCodec.encoder
    val json = displayable.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
    HttpEntity(ContentTypes.`application/json`, json)
  }

  protected def toEntity(node: DisplayableNode): RequestEntity = {
    implicit val encode = DisplayableProcessCodec.nodeEncoder
    val json = node.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
    HttpEntity(ContentTypes.`application/json`, json)
  }

}
