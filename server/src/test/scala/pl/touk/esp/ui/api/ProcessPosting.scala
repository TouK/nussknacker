package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import argonaut.PrettyParams
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter}
import argonaut.Argonaut._

trait ProcessPosting {

  protected def toEntity(process: EspProcess): RequestEntity = {
    val canonical = ProcessCanonizer.canonize(process)
    val displayable = ProcessConverter.toDisplayable(canonical)
    implicit val encode = DisplayableProcessCodec.encoder
    val json = displayable.asJson.pretty(PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true))
    HttpEntity(ContentTypes.`application/json`, json)
  }

}
