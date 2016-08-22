package pl.touk.esp.engine.definition

import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import argonaut.Argonaut._
import argonaut.PrettyParams
import cats.data.Validated

object ProcessDefinitionMarshaller {

  import argonaut.ArgonautShapeless._

  def toJson(definition: ProcessDefinition, prettyParams: PrettyParams): String = {
    definition.asJson.pretty(prettyParams.copy(dropNullKeys = true, preserveOrder = true))
  }

  def fromJson(json: String): Validated[String, ProcessDefinition] = {
    Validated.fromEither(json.decodeEither[ProcessDefinition])
  }

}
