package pl.touk.esp.ui.process.marshall

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.ui.api.ProcessValidation
import pl.touk.esp.ui.api.ProcessValidation.{NodeValidationError, ValidationResult}
import pl.touk.esp.ui.process.displayedgraph.displayablenode.Edge
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}

class ProcessConverterSpec extends FlatSpec with Matchers with TableDrivenPropertyChecks {

  val validation = {
    val processDefinition = ProcessDefinition[ObjectDefinition](Map("ref" -> ObjectDefinition.noParam),
      Map("sourceRef" -> ObjectDefinition.noParam), Map(), Map(), ObjectDefinition.noParam, Map(), List())
    val validator = ProcessValidator.default(processDefinition)
    new ProcessValidation(validator)
  }

  def canonicalDisplayable = (ProcessConverter.toDisplayable _).andThen(ProcessConverter.fromDisplayable)

  def displayableCanonical = (ProcessConverter.fromDisplayable _).andThen(ProcessConverter.toDisplayable).andThen(_.validated(validation))

  it should "be able to convert empty process" in {
    val emptyProcess = CanonicalProcess(MetaData(id = "t1"), ExceptionHandlerRef(List()), List())

    canonicalDisplayable(emptyProcess) shouldBe emptyProcess
  }

  it should "be able to handle different node order" in {
    val process = DisplayableProcess("t1", ProcessProperties(Some(2), Some(false), ExceptionHandlerRef(List()), None),
      List(
        Processor("e", ServiceRef("ref", List())),
        Source("s", SourceRef("sourceRef", List()))
      ), List(Edge("s", "e", None)))

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
      val process = DisplayableProcess("t1", ProcessProperties(Some(2), Some(false), ExceptionHandlerRef(List()), None),
        List(Source("s", SourceRef("sourceRef", List())), unexpectedEnd),
        List(Edge("s", "e", None)), Some(ValidationResult(Map(unexpectedEnd.id -> List(NodeValidationError("InvalidTailOfBranch",
          "Invalid end of process", "Process branch can only end with sink or processor", None, isFatal = false))), List(), List()))
      )
      displayableCanonical(process) shouldBe process
    }
  }


}
