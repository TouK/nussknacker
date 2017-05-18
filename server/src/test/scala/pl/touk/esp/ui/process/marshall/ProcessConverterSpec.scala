package pl.touk.esp.ui.process.marshall

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api.{MetaData, StreamMetaData}
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.ui.api.helpers.TestFactory.sampleResolver
import pl.touk.esp.ui.validation.ProcessValidation
import pl.touk.esp.ui.db.entity.ProcessEntity.{ProcessType, ProcessingType}
import pl.touk.esp.ui.process.displayedgraph.displayablenode.Edge
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.esp.ui.validation.ValidationResults.{NodeValidationError, ValidationResult}

class ProcessConverterSpec extends FlatSpec with Matchers with TableDrivenPropertyChecks {

  val validation = {
    val processDefinition = ProcessDefinition[ObjectDefinition](Map("ref" -> ObjectDefinition.noParam),
      Map("sourceRef" -> ObjectDefinition.noParam), Map(), Map(), Map(), ObjectDefinition.noParam, Map(), List())
    val validator = ProcessValidator.default(processDefinition)
    new ProcessValidation(Map(ProcessingType.Streaming -> validator), sampleResolver)
  }

  def canonicalDisplayable(canonicalProcess: CanonicalProcess) = {
    val displayable = ProcessConverter.toDisplayable(canonicalProcess, ProcessingType.Streaming)
    ProcessConverter.fromDisplayable(displayable)
  }

  def displayableCanonical(process: DisplayableProcess) = {
   val canonical = ProcessConverter.fromDisplayable(process)
    ProcessConverter.toDisplayable(canonical, ProcessingType.Streaming).validated(validation)
  }

  it should "be able to convert empty process" in {
    val emptyProcess = CanonicalProcess(MetaData(id = "t1", StreamMetaData()), ExceptionHandlerRef(List()), List())

    canonicalDisplayable(emptyProcess) shouldBe emptyProcess
  }

  it should "be able to handle different node order" in {
    val process = DisplayableProcess("t1", ProcessProperties(StreamMetaData(Some(2), Some(false)), ExceptionHandlerRef(List())),
      List(
        Processor("e", ServiceRef("ref", List())),
        Source("s", SourceRef("sourceRef", List()))
      ), List(Edge("s", "e", None)), ProcessingType.Streaming)

    displayableCanonical(process).nodes.toSet shouldBe process.nodes.toSet
    displayableCanonical(process).edges.toSet shouldBe process.edges.toSet

  }

  it should "be able to convert process ending not properly" in {

    forAll(Table(
      "unexpectedEnd",
      Filter("e", Expression("spel", "0")),
      Switch("e", Expression("spel", "0"), "a"),
      Enricher("e", ServiceRef("ref", List()), "out"),
      Split("e")
    )) { (unexpectedEnd) =>
      val process = DisplayableProcess("t1", ProcessProperties(StreamMetaData(Some(2), Some(false)), ExceptionHandlerRef(List())),
        List(Source("s", SourceRef("sourceRef", List())), unexpectedEnd),
        List(Edge("s", "e", None)), ProcessingType.Streaming, Some(ValidationResult.errors(Map(unexpectedEnd.id -> List(NodeValidationError("InvalidTailOfBranch",
          "Invalid end of process", "Process branch can only end with sink or processor", None, isFatal = false))), List(), List()))
      )
      displayableCanonical(process) shouldBe process
    }
  }


}
