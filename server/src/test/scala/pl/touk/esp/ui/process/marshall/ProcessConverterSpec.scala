package pl.touk.esp.ui.process.marshall

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
import pl.touk.esp.ui.process.displayedgraph.displayablenode.Edge
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}

class ProcessConverterSpec extends FlatSpec with Matchers {

  val converter = {
    val processDefinition = ProcessDefinition[ObjectDefinition](Map("ref" -> ObjectDefinition.noParam),
      Map("sourceRef" -> ObjectDefinition.noParam), Map(), Map(), ObjectDefinition.noParam, Map(), List())
    val validator = ProcessValidator.default(processDefinition)
    val processValidation = new ProcessValidation(validator)
    new ProcessConverter(processValidation)
  }

  def canonicalDisplayable = (converter.toDisplayable _).andThen(converter.fromDisplayable)

  def displayableCanonical = (converter.fromDisplayable _).andThen(converter.toDisplayable)

  it should "be able to convert empty process" in {
    val emptyProcess = CanonicalProcess(MetaData(id = "t1"), ExceptionHandlerRef(List()), List())

    canonicalDisplayable(emptyProcess) shouldBe emptyProcess
  }

  it should "be able to handle different node order" in {
    val process = DisplayableProcess("t1", ProcessProperties(Some(2), ExceptionHandlerRef(List()), None),
      List(
        Processor("e", ServiceRef("ref", List())),
        Source("s", SourceRef("sourceRef", List()))
      ), List(Edge("s", "e", None)), ProcessValidation.ValidationResult.success)

    displayableCanonical(process).nodes.toSet shouldBe process.nodes.toSet
    displayableCanonical(process).edges.toSet shouldBe process.edges.toSet

  }

  it should "be able to convert process ending not properly" in {

    val unexpectedEnds = List(
      Filter("e", Expression("spel", "0")),
      Switch("e", Expression("spel", "0"), "a"),
      Processor("e", ServiceRef("ref", List())),
      Enricher("e", ServiceRef("ref", List()), "out"),
      Split("e")
    )

    unexpectedEnds.foreach { unexpectedEnd =>
      val process = DisplayableProcess("t1", ProcessProperties(Some(2), ExceptionHandlerRef(List()), None),
        List(Source("s", SourceRef("sourceRef", List())), unexpectedEnd),
        List(Edge("s", "e", None)), ProcessValidation.ValidationResult.success
      )
      displayableCanonical(process) shouldBe process
    }
  }


}
