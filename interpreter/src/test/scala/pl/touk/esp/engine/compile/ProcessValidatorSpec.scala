package pl.touk.esp.engine.compile

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine._
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectDefinition, Parameter}
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.node.Case

class ProcessValidatorSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  private val baseDefinition = ProcessDefinition(
    Map.empty,
    Map("source" -> ObjectDefinition.noParam),
    Map("sink" -> ObjectDefinition.noParam),
    Set.empty,
    Map.empty,
    ObjectDefinition.noParam
  )

  it should "validated with success" in {
    val correctProcess = EspProcessBuilder.id("process1").exceptionHandler().source("id1", "source").sink("id2", "sink")
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
        .processorEnd("id2", missingServiceId, "foo" -> "'bar'")

    ProcessValidator.default(baseDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(MissingService(_, _), _)) =>
    }

    val validDefinition = baseDefinition.withService(missingServiceId, Parameter(name = "foo", typ = "String"))
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

    val definition = ProcessDefinition.empty.withService(missingServiceId, Parameter(name = "foo", typ = "String"))
    ProcessValidator.default(definition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(MissingSourceFactory(_, _), _)) =>
    }
  }

  it should "find missing parameter for exception handler" in {
    val process = EspProcessBuilder.id("process1").exceptionHandler().source("id1", "source").sink("id2", "sink")
    val definition = baseDefinition.withExceptionHandlerFactory(Parameter(name = "foo", typ = "String"))
    ProcessValidator.default(definition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(MissingParameters(_, _), _)) =>
    }
  }

}