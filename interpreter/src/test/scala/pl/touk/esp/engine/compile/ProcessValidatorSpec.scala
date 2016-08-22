package pl.touk.esp.engine.compile

import cats.data.OneAnd
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectDefinition, Parameter}
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.{EspProcess, service}

class ProcessValidatorSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  private val baseDefinition = ProcessDefinition(
    Map.empty,
    Map("source" -> ObjectDefinition(List.empty)),
    Map("sink" -> ObjectDefinition(List.empty)),
    Set.empty
  )

  it should "validated with success" in {
    val correctProcess = EspProcess(MetaData("process1"), GraphBuilder.source("id1", "source").sink("id2", "sink"))
    ProcessValidator.default(baseDefinition).validate(correctProcess) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "find duplicated ids" in {
    val duplicatedId = "id1"
    val processWithDuplicatedIds = EspProcess(MetaData("process1"), GraphBuilder.source(duplicatedId, "source").sink(duplicatedId, "sink"))
    ProcessValidator.default(baseDefinition).validate(processWithDuplicatedIds) should matchPattern {
      case Invalid(OneAnd(DuplicatedNodeIds(_), _)) =>
    }
  }

  it should "find expression parse error" in {
    val processWithInvalidExpresssion = EspProcess(
      MetaData("process1"),
      GraphBuilder.source("id1", "source")
        .sink("id2", "wtf!!!", "sink")
    )

    ProcessValidator.default(baseDefinition).validate(processWithInvalidExpresssion) should matchPattern {
      case Invalid(OneAnd(ExpressionParseError(_, _, _), _)) =>
    }
  }

  it should "find missing service error" in {
    val missingServiceId = "missingServiceId"
    val processWithRefToMissingService = EspProcess(
      MetaData("process1"),
      GraphBuilder.source("id1", "source")
        .processorEnd("id2", ServiceRef(missingServiceId, List(service.Parameter("foo", "'bar'"))))
    )

    ProcessValidator.default(baseDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(OneAnd(MissingService(_, _), _)) =>
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
    val processWithInvalidServiceInvocation = EspProcess(
      MetaData("process1"),
      GraphBuilder.source("id1", "source")
        .processorEnd("id2", ServiceRef(serviceId, List(service.Parameter(redundantServiceParameter, "'bar'"))))
    )

    ProcessValidator.default(definition).validate(processWithInvalidServiceInvocation) should matchPattern {
      case Invalid(OneAnd(RedundantParameters(_, _), _)) =>
    }
  }

  it should "find missing source" in {
    val missingServiceId = "missingServiceId"
    val processWithRefToMissingService = EspProcess(
      MetaData("process1"),
      GraphBuilder.source("id1", "source")
        .processorEnd("id2", ServiceRef(missingServiceId, List(service.Parameter("foo", "'bar'"))))
    )

    val definition = ProcessDefinition.empty.withService(missingServiceId, Parameter(name = "foo", typ = "String"))
    ProcessValidator.default(definition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(OneAnd(MissingSourceFactory(_, _), _)) =>
    }
  }

}