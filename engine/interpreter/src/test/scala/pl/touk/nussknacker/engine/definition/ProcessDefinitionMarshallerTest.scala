package pl.touk.nussknacker.engine.definition

import argonaut.PrettyParams
import cats.data.Validated.Valid
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ClazzRef, ObjectDefinition, Parameter}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ProcessDefinition}
import pl.touk.nussknacker.engine.types.EspTypeUtils

class ProcessDefinitionMarshallerTest extends FlatSpec with Matchers {

  it should "work round-trip" in {
    val definition = ProcessDefinition(
      services = Map("fooService" -> ObjectDefinition.withParamsAndCategories(List(Parameter(name = "foo", typ = ClazzRef(classOf[String]))), List("cat1"))),
      sourceFactories = Map("fooSourceFactory" -> ObjectDefinition.withParamsAndCategories(List(Parameter(name = "foo", typ = ClazzRef(classOf[String]))), List("cat1"))),
      sinkFactories = Map("fooSinkFactory" -> ObjectDefinition.withParamsAndCategories(List(Parameter(name = "foo", typ = ClazzRef(classOf[String]))), List("cat1", "cat2"))),
      customStreamTransformers = Map("fooExecutorServiceFactory" ->
        (ObjectDefinition.withParamsAndCategories(List(Parameter(name = "foo", typ = ClazzRef(classOf[String]))), List("cat1")), CustomTransformerAdditionalData(Set(), false))),
      signalsWithTransformers = Map.empty,
      exceptionHandlerFactory = ObjectDefinition.noParam,
      globalVariables = Map.empty,
      typesInformation = EspTypeUtils.clazzAndItsChildrenDefinition(List.empty)(ClassExtractionSettings.Default)
    )

    val json = ProcessDefinitionMarshaller.toJson(definition, PrettyParams.spaces2)

    ProcessDefinitionMarshaller.fromJson(json) shouldEqual Valid(definition)
  }

}
