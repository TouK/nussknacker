package pl.touk.esp.engine.definition

import argonaut.PrettyParams
import cats.data.Validated.Valid
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectDefinition, Parameter}
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition

class ProcessDefinitionMarshallerTest extends FlatSpec with Matchers {

  it should "work round-trip" in {
    val definition = ProcessDefinition(
      Map("fooService" -> ObjectDefinition(List(Parameter(name = "foo", typ = "String")))),
      Map("fooSourceFactory" -> ObjectDefinition(List(Parameter(name = "foo", typ = "String")))),
      Map("fooSinkFactory" -> ObjectDefinition(List(Parameter(name = "foo", typ = "String")))),
      Set("fooFoldingFunction")
    )

    val json = ProcessDefinitionMarshaller.toJson(definition, PrettyParams.spaces2)

    ProcessDefinitionMarshaller.fromJson(json) shouldEqual Valid(definition)
  }

}
