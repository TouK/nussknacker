package pl.touk.nussknacker.ui.process.marshall

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.MetaVariables
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.ui.api.helpers.TestFactory.sampleResolver
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties, ValidatedDisplayableProcess}
import pl.touk.nussknacker.ui.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType, ValidationErrors, ValidationResult}

class ProcessConverterSpec extends FlatSpec with Matchers with TableDrivenPropertyChecks {

  val validation = {
    val processDefinition = ProcessDefinition[ObjectDefinition](Map("ref" -> ObjectDefinition.noParam),
      Map("sourceRef" -> ObjectDefinition.noParam), Map(), Map(), Map(), ObjectDefinition.noParam, ExpressionDefinition(Map.empty, List.empty, optimizeCompilation = false), List())
    val validator =  ProcessValidator.default(ProcessDefinitionBuilder.withEmptyObjects(processDefinition))
    new ProcessValidation(Map(TestProcessingTypes.Streaming -> validator), Map(TestProcessingTypes.Streaming -> Map()), sampleResolver)
  }

  def canonicalDisplayable(canonicalProcess: CanonicalProcess) = {
    val displayable = ProcessConverter.toDisplayable(canonicalProcess, TestProcessingTypes.Streaming)
    ProcessConverter.fromDisplayable(displayable)
  }

  def displayableCanonical(process: DisplayableProcess) = {
   val canonical = ProcessConverter.fromDisplayable(process)
    ProcessConverter.toDisplayable(canonical, TestProcessingTypes.Streaming).validated(validation)
  }

  it should "be able to convert empty process" in {
    val emptyProcess = CanonicalProcess(MetaData(id = "t1", StreamMetaData()), ExceptionHandlerRef(List()), List())

    canonicalDisplayable(emptyProcess) shouldBe emptyProcess
  }

  it should "be able to handle different node order" in {
    val process = DisplayableProcess("t1", ProcessProperties(StreamMetaData(Some(2), Some(false)), ExceptionHandlerRef(List()), subprocessVersions = Map.empty),
      List(
        Processor("e", ServiceRef("ref", List())),
        Source("s", SourceRef("sourceRef", List()))
      ), List(Edge("s", "e", None)), TestProcessingTypes.Streaming)

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
      val process = ValidatedDisplayableProcess(
        "t1",
        ProcessProperties(StreamMetaData(Some(2), Some(false)), ExceptionHandlerRef(List()), subprocessVersions = Map.empty),
        List(Source("s", SourceRef("sourceRef", List())), unexpectedEnd),
        List(Edge("s", "e", None)),
        TestProcessingTypes.Streaming,
        ValidationResult.errors(
          Map(unexpectedEnd.id -> List(
            NodeValidationError("InvalidTailOfBranch", "Invalid end of process", "Process branch can only end with sink or processor", None, errorType = NodeValidationErrorType.SaveAllowed))),
          List.empty,
          List.empty
        )
      )

      val validated = displayableCanonical(process.toDisplayable)
      val withoutTypes = validated.copy(validationResult = validated.validationResult.withTypes(Map.empty))
      withoutTypes shouldBe process
    }
  }


  it should "return variable type information for process that cannot be canonized" in {
    val process = ValidatedDisplayableProcess(
      "process",
      ProcessProperties(StreamMetaData(Some(2), Some(false)), ExceptionHandlerRef(List()), subprocessVersions = Map.empty),
      List(Source("s", SourceRef("sourceRef", List())), Variable("v", "test", Expression("spel", "''")), Filter("e", Expression("spel", "''"))),
      List(Edge("s", "v", None), Edge("v", "e", None)),
      TestProcessingTypes.Streaming,
      ValidationResult.errors(
        Map("e" -> List(NodeValidationError("InvalidTailOfBranch", "Invalid end of process", "Process branch can only end with sink or processor", None, errorType = NodeValidationErrorType.SaveAllowed))),
        List.empty,
        List.empty,
        Map("s" -> Map("input" -> Typed[Null], "meta" -> Typed[MetaVariables]), "v" -> Map("input" -> Typed[Null], "meta" -> Typed[MetaVariables]), "e" -> Map("input" -> Typed[Null], "meta" -> Typed[MetaVariables], "test" -> Typed[String]))
      )
    )

    displayableCanonical(process.toDisplayable) shouldBe process
  }
}
