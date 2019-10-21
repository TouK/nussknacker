package pl.touk.nussknacker.ui.process.marshall

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.LanguageConfiguration
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.variables.MetaVariables
import pl.touk.nussknacker.ui.api.helpers.TestFactory.sampleResolver
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType, ValidationResult}

class ProcessConverterSpec extends FunSuite with Matchers with TableDrivenPropertyChecks {

  val validation: ProcessValidation = {
    val processDefinition = ProcessDefinition[ObjectDefinition](Map("ref" -> ObjectDefinition.noParam),
      Map("sourceRef" -> ObjectDefinition.noParam), Map(), Map(), Map(), ObjectDefinition.noParam,
      ExpressionDefinition(Map.empty, List.empty, LanguageConfiguration.default, optimizeCompilation = false), List())
    val validator =  ProcessValidator.default(ProcessDefinitionBuilder.withEmptyObjects(processDefinition))
    new ProcessValidation(Map(TestProcessingTypes.Streaming -> validator), Map(TestProcessingTypes.Streaming -> Map()), sampleResolver, Map.empty)
  }

  def canonicalDisplayable(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    val displayable = ProcessConverter.toDisplayable(canonicalProcess, TestProcessingTypes.Streaming)
    ProcessConverter.fromDisplayable(displayable)
  }

  def displayableCanonical(process: DisplayableProcess): ValidatedDisplayableProcess = {
   val canonical = ProcessConverter.fromDisplayable(process)
    val displayable = ProcessConverter.toDisplayable(canonical, TestProcessingTypes.Streaming)
    new ValidatedDisplayableProcess(displayable, validation.validate(displayable))
  }

  test("be able to convert empty process") {
    val emptyProcess = CanonicalProcess(MetaData(id = "t1", StreamMetaData()), ExceptionHandlerRef(List()), List(), None)

    canonicalDisplayable(emptyProcess) shouldBe emptyProcess
  }

  test("be able to handle different node order") {
    val process = DisplayableProcess("t1", ProcessProperties(StreamMetaData(Some(2), Some(false)), ExceptionHandlerRef(List()), subprocessVersions = Map.empty),
      List(
        Processor("e", ServiceRef("ref", List())),
        Source("s", SourceRef("sourceRef", List()))
      ), List(Edge("s", "e", None)), TestProcessingTypes.Streaming)

    displayableCanonical(process).nodes.toSet shouldBe process.nodes.toSet
    displayableCanonical(process).edges.toSet shouldBe process.edges.toSet

  }

  test("be able to convert process ending not properly") {
    forAll(Table(
      "unexpectedEnd",
      Filter("e", Expression("spel", "0")),
      Switch("e", Expression("spel", "0"), "a"),
      Enricher("e", ServiceRef("ref", List()), "out"),
      Split("e")
    )) { unexpectedEnd =>
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


  test("return variable type information for process that cannot be canonized") {
    val meta = MetaData("process", StreamMetaData(Some(2), Some(false)), additionalFields = Some(ProcessAdditionalFields(None, Set.empty, Map.empty)))
    val process = ValidatedDisplayableProcess(
      meta.id,
      ProcessProperties(meta.typeSpecificData, ExceptionHandlerRef(List()), subprocessVersions = Map.empty),
      List(Source("s", SourceRef("sourceRef", List())), Variable("v", "test", Expression("spel", "''")), Filter("e", Expression("spel", "''"))),
      List(Edge("s", "v", None), Edge("v", "e", None)),
      TestProcessingTypes.Streaming,
      ValidationResult.errors(
        Map("e" -> List(NodeValidationError("InvalidTailOfBranch", "Invalid end of process", "Process branch can only end with sink or processor", None, errorType = NodeValidationErrorType.SaveAllowed))),
        List.empty,
        List.empty,
        Map(
          "s" -> Map("meta" -> MetaVariables.typingResult(meta)),
          "v" -> Map("input" -> Unknown, "meta" -> MetaVariables.typingResult(meta)),
          "e" -> Map("input" -> Unknown, "meta" -> MetaVariables.typingResult(meta), "test" -> Typed[String]))
      )
    )

    displayableCanonical(process.toDisplayable) shouldBe process
  }

  test("convert process with branches") {

    val process = DisplayableProcess("t1", ProcessProperties(StreamMetaData(Some(2), Some(false)), ExceptionHandlerRef(List()), subprocessVersions = Map.empty),
      List(
        Processor("e", ServiceRef("ref", List.empty)),
        Join("j1", None, "joinRef", List.empty, List.empty),
        Source("s2", SourceRef("sourceRef", List.empty)),
        Source("s1", SourceRef("sourceRef", List.empty))
      ),
      List(
        Edge("s1", "j1", None),
        Edge("s2", "j1", None),
        Edge("j1", "e", None)
      ), TestProcessingTypes.Streaming)

    

    displayableCanonical(process).nodes.sortBy(_.id) shouldBe process.nodes.sortBy(_.id)
    displayableCanonical(process).edges.toSet shouldBe process.edges.toSet

    val canonical = ProcessConverter.fromDisplayable(process)

    val normal = ProcessCanonizer.uncanonize(canonical).toOption.get
    ProcessCanonizer.canonize(normal) shouldBe canonical

  }
}
