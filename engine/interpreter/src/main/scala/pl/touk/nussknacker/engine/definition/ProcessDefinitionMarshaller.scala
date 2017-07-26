package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import argonaut.Argonaut._
import argonaut.PrettyParams
import cats.data.Validated
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition

object ProcessDefinitionMarshaller {

  import argonaut.ArgonautShapeless._

  def toJson(definition: ProcessDefinition[ObjectDefinition], prettyParams: PrettyParams): String = {
    definition.asJson.pretty(prettyParams.copy(dropNullKeys = true, preserveOrder = true))
  }

  def fromJson(json: String): Validated[String, ProcessDefinition[ObjectDefinition]] = {
    Validated.fromEither(json.decodeEither[ProcessDefinition[ObjectDefinition]])
  }

}
