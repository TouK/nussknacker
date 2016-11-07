package pl.touk.esp.engine.definition

import argonaut.PrettyParams
import cats.data.Validated.Valid
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.definition.DefinitionExtractor.{ClazzRef, ObjectDefinition, Parameter}
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.types.EspTypeUtils

class ProcessDefinitionMarshallerTest extends FlatSpec with Matchers {

  it should "work round-trip" in {
    val definition = ProcessDefinition(
      Map("fooService" -> ObjectDefinition.withParams(List(Parameter(name = "foo", typ = ClazzRef(classOf[String]))))),
      Map("fooSourceFactory" -> ObjectDefinition.withParams(List(Parameter(name = "foo", typ = ClazzRef(classOf[String]))))),
      Map("fooSinkFactory" -> ObjectDefinition.withParams(List(Parameter(name = "foo", typ = ClazzRef(classOf[String]))))),
      Map("fooExecutorServiceFactory" -> ObjectDefinition.withParams(List(Parameter(name = "foo", typ = ClazzRef(classOf[String]))))),
      ObjectDefinition.noParam,
      Map.empty,
      EspTypeUtils.clazzAndItsChildrenDefinition(List.empty)
    )

    val json = ProcessDefinitionMarshaller.toJson(definition, PrettyParams.spaces2)

    ProcessDefinitionMarshaller.fromJson(json) shouldEqual Valid(definition)
  }

}
