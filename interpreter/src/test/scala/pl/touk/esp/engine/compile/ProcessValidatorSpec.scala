package pl.touk.esp.engine.compile

import cats.data._
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.Service
import pl.touk.esp.engine.api.lazyy.ContextWithLazyValuesProvider
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.definition.DefinitionExtractor.{ClazzRef, ObjectDefinition, Parameter}
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.node.Case
import pl.touk.esp.engine.types.EspTypeUtils

import scala.concurrent.{ExecutionContext, Future}

class ProcessValidatorSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  private val baseDefinition = ProcessDefinition(
    Map("sampleEnricher" -> ObjectDefinition(List.empty, classOf[SampleEnricher], Some(classOf[SimpleRecord]))),
    Map("source" -> ObjectDefinition(List.empty, classOf[SimpleRecord], None)),
    Map("sink" -> ObjectDefinition.noParam),
    Set.empty,
    Map("customTransformer" -> ObjectDefinition(List.empty, classOf[SimpleRecord], None)),
    ObjectDefinition.noParam,
    EspTypeUtils.clazzAndItsChildrenDefinition(List(classOf[SampleEnricher], classOf[SimpleRecord]))
  )

  it should "validated with success" in {
    val correctProcess = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .processor("sampleProcessor1", "sampleEnricher")
      .enricher("sampleProcessor2", "out", "sampleEnricher")
      .buildVariable("bv1", "vars", "v1" -> "42")
      .sink("sink", "#input.someMethod(#vars['v1'])", "sink")
    ProcessValidator.default(baseDefinition).validate(correctProcess) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "find duplicated ids" in {
    val duplicatedId = "id1"
    val processWithDuplicatedIds = EspProcessBuilder.id("process1").exceptionHandler().source(duplicatedId, "source").sink(duplicatedId, "sink")
    ProcessValidator.default(baseDefinition).validate(processWithDuplicatedIds) should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeIds(_), _)) =>
    }
  }

  it should "find duplicated ids in switch" in {
    val duplicatedId = "id1"
    val processWithDuplicatedIds =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("source", "source")
          .switch("switch", "''", "var",
            Case("'1'", GraphBuilder.sink(duplicatedId, "sink")),
            Case("'2'", GraphBuilder.sink(duplicatedId, "sink"))
          )
    ProcessValidator.default(baseDefinition).validate(processWithDuplicatedIds) should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeIds(_), _)) =>
    }
  }

  it should "find expression parse error" in {
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .sink("id2", "wtf!!!", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithInvalidExpresssion) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError(_, _, _), _)) =>
    }
  }

  it should "find missing service error" in {
    val missingServiceId = "missingServiceId"
    val processWithRefToMissingService =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "customTransformer")
        .processor("id2", missingServiceId, "foo" -> "'bar'")
        .sink("sink", "#event.plainValue", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(MissingService(_, _), _)) =>
    }

    val validDefinition = baseDefinition.withService(missingServiceId, Parameter(name = "foo", typ = ClazzRef(classOf[String])))
    ProcessValidator.default(validDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "find redundant service parameters" in {
    val serviceId = "serviceId"
    val definition = baseDefinition.withService(serviceId)

    val redundantServiceParameter = "foo"

    val processWithInvalidServiceInvocation =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .processorEnd("id2", serviceId, redundantServiceParameter -> "'bar'")

    ProcessValidator.default(definition).validate(processWithInvalidServiceInvocation) should matchPattern {
      case Invalid(NonEmptyList(RedundantParameters(_, _), _)) =>
    }
  }

  it should "find missing source" in {
    val missingServiceId = "missingServiceId"
    val processWithRefToMissingService =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .processorEnd("id2", missingServiceId, "foo" -> "'bar'")

    val definition = ProcessDefinition.empty.withService(missingServiceId, Parameter(name = "foo", typ = ClazzRef(classOf[String])))
    ProcessValidator.default(definition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(MissingSourceFactory(_, _), _)) =>
    }
  }

  it should "find missing parameter for exception handler" in {
    val process = EspProcessBuilder.id("process1").exceptionHandler().source("id1", "source").sink("id2", "sink")
    val definition = baseDefinition.withExceptionHandlerFactory(Parameter(name = "foo", typ = ClazzRef(classOf[String])))
    ProcessValidator.default(definition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(MissingParameters(_, _), _)) =>
    }
  }

  it should "find usage of unresolved plain variables" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildVariable("bv1", "doesExist", "v1" -> "42")
      .filter("sampleFilter", "#doesExist['v1'] + #doesNotExist1 + #doesNotExist2 > 10")
      .sink("id2", "sink")
    ProcessValidator.default(baseDefinition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved references doesNotExist1, doesNotExist2", _, _), _)) =>
    }
  }

  it should "find usage of non references" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValue > 10")
      .filter("sampleFilter2", "input.plainValue > 10")
      .sink("id2", "sink")
    ProcessValidator.default(baseDefinition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Non reference 'input' occurred. Maybe you missed '#' in front of it?", _, _), _)) =>
    }
  }

  it should "find usage of fields that does not exist in object" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.value1.value2 > 10")
      .filter("sampleFilter2", "#input.value1.value3 > 10")
      .sink("id2", "sink")
    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type 'pl.touk.esp.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'", _, _), _)) =>
    }
  }

  case class SimpleRecord(value1: AnotherSimpleRecord, plainValue: Int) {
    private val privateValue = "priv"
    def invoke1: Future[AnotherSimpleRecord] = ???
    def invoke2: State[ContextWithLazyValuesProvider, AnotherSimpleRecord] = ???
    def someMethod(a: Int): Int = ???
  }
  case class AnotherSimpleRecord(value2: Long)
  private val definitionWithTypedSource = baseDefinition.copy(sourceFactories = Map("source" -> ObjectDefinition.noParam.copy(clazz = ClazzRef(classOf[SimpleRecord]))))

  class SampleEnricher extends Service {
    def invoke()(implicit ec: ExecutionContext) = Future(SimpleRecord(AnotherSimpleRecord(1), 2))
  }

}