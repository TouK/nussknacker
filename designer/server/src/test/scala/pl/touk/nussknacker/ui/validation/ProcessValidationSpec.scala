package pl.touk.nussknacker.ui.validation

import cats.data.{Validated, ValidatedNel}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.{BeMatcher, MatchResult}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingSourceFactory, ScenarioNameValidationError, UnknownSubprocess}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, SplitNode}
import pl.touk.nussknacker.engine.graph.EdgeType.{NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.graph.{EdgeType, evaluatedparam}
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.{CustomProcessValidator, spel}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationErrorType.{RenderNotAllowed, SaveNotAllowed, SaveAllowed}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType, ValidationErrors, ValidationResult, ValidationWarnings}
import pl.touk.nussknacker.restmodel.validation.{PrettyValidationErrors, ValidationResults}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{mapProcessingTypeDataProvider, possibleValues}
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessResolver}

import scala.collection.immutable.ListMap

class ProcessValidationSpec extends AnyFunSuite with Matchers {

  import ProcessTestData._
  import ProcessValidationSpec._
  import TestCategories._
  import spel.Implicits._

  test("check for not unique edge types") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        SubprocessInput("subIn", SubprocessRef("sub1", List())),
        Sink("out", SinkRef(existingSinkFactory, List())),
        Sink("out2", SinkRef(existingSinkFactory, List())),
        Sink("out3", SinkRef(existingSinkFactory, List()))
      ),
      List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.SubprocessOutput("out1"))),
        Edge("subIn", "out2", Some(EdgeType.SubprocessOutput("out2"))),
        Edge("subIn", "out3", Some(EdgeType.SubprocessOutput("out2")))
      )
    )

    val result = validator.validate(process, Category1)

    result.errors.invalidNodes shouldBe Map(
      "subIn" -> List(NodeValidationError("NonUniqueEdgeType", "Edges are not unique",
        "Node subIn has duplicate outgoing edges of type: SubprocessOutput(out2), it cannot be saved properly", None, NodeValidationErrorType.SaveNotAllowed))
    )
  }

  test("switch edges do not have to be unique") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Switch("switch"),
        Sink("out", SinkRef(existingSinkFactory, List())),
        Sink("out2", SinkRef(existingSinkFactory, List())),
      ),
      List(
        Edge("in", "switch", None),
        Edge("switch", "out", Some(EdgeType.NextSwitch("true"))),
        Edge("switch", "out2", Some(EdgeType.NextSwitch("true"))),
      )
    )

    val result = validator.validate(process, Category1)
    result.errors.invalidNodes shouldBe Symbol("empty")
  }

  test("check for not unique edges") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        SubprocessInput("subIn", SubprocessRef("sub1", List())),
        Sink("out2", SinkRef(existingSinkFactory, List())),
      ),
      List(
        Edge("in", "subIn", None),
        Edge("subIn", "out2", Some(EdgeType.SubprocessOutput("out1"))),
        Edge("subIn", "out2", Some(EdgeType.SubprocessOutput("out2"))),
      )
    )

    val result = validator.validate(process, Category1)

    result.errors.invalidNodes shouldBe Map(
      "subIn" -> List(NodeValidationError("NonUniqueEdge", "Edges are not unique",
        "Node subIn has duplicate outgoing edges to: out2, it cannot be saved properly", None, SaveNotAllowed))
    )
  }

  test("check for loose nodes") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List())),
        Filter("loose", Expression("spel", "true"))
      ),
      List(Edge("in", "out", None))

    )
    val result = validator.validate(process, Category1)

    result.errors.invalidNodes shouldBe Map("loose" -> List(NodeValidationError("LooseNode", "Loose node", "Node loose is not connected to source, it cannot be saved properly", None, SaveNotAllowed)))
  }

  test("check for disabled nodes") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List())),
        Filter("filter", Expression("spel", "true"), isDisabled = Some(true))
      ),
      List(
        Edge("in", "filter", None),
        Edge("filter", "out", Some(EdgeType.FilterTrue)),
      )

    )
    val result = validator.validate(process, Category1)

    result.warnings.invalidNodes shouldBe Map("filter" -> List(NodeValidationError("DisabledNode", "Node filter is disabled", "Deploying scenario with disabled node can have unexpected consequences", None, SaveAllowed)))
  }

  test("check for empty ids") {
    val process = createProcess(
      List(
        Source("", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("", "out", None))
    )
    val result = validator.validate(process, Category1)

    result.errors.globalErrors shouldBe List(NodeValidationError("EmptyNodeId", "Nodes cannot have empty id", "Nodes cannot have empty id", None, RenderNotAllowed))
  }


  test("check for duplicated ids") {
    val process = createProcess(
      List(
        Source("inID", SourceRef(existingSourceFactory, List())),
        Filter("inID", Expression("spel", "''")),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("inID", "inID", None), Edge("inID", "out", None))
    )
    val result = validator.validate(process, Category1)

    result.errors.globalErrors shouldBe List(NodeValidationError("DuplicatedNodeIds", "Two nodes cannot have same id", "Duplicate node ids: inID", None, RenderNotAllowed))
  }

  test("check for duplicated ids when duplicated id is switch id") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Switch("switchID"),
        Sink("out", SinkRef(existingSinkFactory, List())),
        Sink("switchID", SinkRef(existingSinkFactory, List()))
      ),
      List(
        Edge("in", "switchID", None),
        Edge("switchID", "out", Some(SwitchDefault)),
        Edge("switchID", "switch", Some(NextSwitch(Expression("spel", "''"))))
      )
    )

    val result = validator.validate(process, Category1)

    result.errors.globalErrors shouldBe List((NodeValidationError("DuplicatedNodeIds", "Two nodes cannot have same id", "Duplicate node ids: switchID", None, RenderNotAllowed)))
    result.errors.invalidNodes shouldBe empty
    result.warnings shouldBe ValidationWarnings.success
  }

  test("not fail with exception when no processtype validator present") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("in", "out", None)), `type` = TestProcessingTypes.RequestResponse
    )
    validator.validate(process, Category1) should matchPattern {
      case ValidationResult(
      ValidationErrors(_, Nil, errors),
      ValidationWarnings.success,
      _
      ) if errors == List(PrettyValidationErrors.noValidatorKnown(TestProcessingTypes.RequestResponse)) =>
    }
  }

  test("not allow required scenario fields") {
    val processValidation = TestFactory.processValidation.withAdditionalPropertiesConfig(
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
        "field1" -> AdditionalPropertyConfig(None, None, Some(List(MandatoryParameterValidator)), Some("label1")),
        "field2" -> AdditionalPropertyConfig(None, None, None, Some("label2"))
      ))
    )

    processValidation.validate(validProcessWithFields(Map("field1" -> "a", "field2" -> "b")), Category1) shouldBe withoutErrorsAndWarnings

    processValidation.validate(validProcessWithFields(Map("field1" -> "a")), Category1) shouldBe withoutErrorsAndWarnings

    processValidation.validate(validProcessWithFields(Map("field1" -> "", "field2" -> "b")), Category1)
      .errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("EmptyMandatoryParameter", _, _, Some("field1"), ValidationResults.NodeValidationErrorType.SaveAllowed)) =>
    }
    processValidation.validate(validProcessWithFields(Map("field2" -> "b")), Category1)
      .errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("MissingRequiredProperty", _, _, Some("field1"), ValidationResults.NodeValidationErrorType.SaveAllowed)) =>
    }
  }

  test("don't validate properties on fragment") {
    val processValidation = TestFactory.processValidation.withAdditionalPropertiesConfig(
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
        "field1" -> AdditionalPropertyConfig(None, None, Some(List(MandatoryParameterValidator)), Some("label1")),
        "field2" -> AdditionalPropertyConfig(None, None, Some(List(MandatoryParameterValidator)), Some("label2"))
      ))
    )

    val process = validProcessWithFields(Map())
    val subprocess = process.copy(properties = process.properties.copy(typeSpecificProperties = FragmentSpecificData()))

    processValidation.validate(subprocess, Category1) shouldBe withoutErrorsAndWarnings

  }

  test("validate type) scenario field") {
    val possibleValues = List(FixedExpressionValue("true", "true"), FixedExpressionValue("false", "false"))
    val processValidation = TestFactory.processValidation.withAdditionalPropertiesConfig(
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
        "field1" -> AdditionalPropertyConfig(None, Some(FixedValuesParameterEditor(possibleValues)), Some(List(FixedValuesValidator(possibleValues))), Some("label")),
        "field2" -> AdditionalPropertyConfig(None, None, Some(List(LiteralParameterValidator.integerValidator)), Some("label"))
      ))
    )

    processValidation.validate(validProcessWithFields(Map("field1" -> "true")), Category1) shouldBe withoutErrorsAndWarnings
    processValidation.validate(validProcessWithFields(Map("field1" -> "false")), Category1) shouldBe withoutErrorsAndWarnings
    processValidation.validate(validProcessWithFields(Map("field1" -> "1")), Category1) should not be withoutErrorsAndWarnings

    processValidation.validate(validProcessWithFields(Map("field2" -> "1")), Category1) shouldBe withoutErrorsAndWarnings
    processValidation.validate(validProcessWithFields(Map("field2" -> "1.1")), Category1) should not be withoutErrorsAndWarnings
    processValidation.validate(validProcessWithFields(Map("field2" -> "true")), Category1) should not be withoutErrorsAndWarnings
  }

  test("handle unknown properties validation") {
    val processValidation = TestFactory.processValidation.withAdditionalPropertiesConfig(
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
        "field2" -> AdditionalPropertyConfig(None, None, Some(List(LiteralParameterValidator.integerValidator)), Some("label"))
      ))
    )

    val result = processValidation.validate(validProcessWithFields(Map("field1" -> "true")), Category1)

    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("UnknownProperty", _, _, Some("field1"), NodeValidationErrorType.SaveAllowed)) =>
    }
  }

  test("not allows save with incorrect characters in ids") {
    def process(nodeId: String) = createProcess(
      List(Source(nodeId, SourceRef(existingSourceFactory, List()))),
      List()
    )

    validator.validate(process("a\"s"), Category1).saveAllowed shouldBe false
    validator.validate(process("a's"), Category1).saveAllowed shouldBe false
    validator.validate(process("a.s"), Category1).saveAllowed shouldBe false
    validator.validate(process("as"), Category1).saveAllowed shouldBe true

  }

  test("validates fragment input definition") {
    val invalidSubprocess = CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      nodes = List(
        FlatNode(SubprocessInputDefinition("in", List(SubprocessParameter("param1", SubprocessClazzRef[Long])))),
        FlatNode(Variable(id = "subVar", varName = "subVar", value = "#nonExistingVar")),
        FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))
      ),
      additionalBranches = List.empty
    )

    val process = createProcess(
      nodes = List(
        Source("in", SourceRef(sourceTypeName, List())),
        SubprocessInput("subIn", SubprocessRef(
          invalidSubprocess.id,
          List(evaluatedparam.Parameter("param1", "'someString'"))),
          isDisabled = Some(false)
        ),
        Sink("out", SinkRef(sinkTypeName, List()))
      ),
      edges = List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.SubprocessOutput("output")))
      )
    )

    val processValidation = mockProcessValidation(invalidSubprocess)
    val validationResult = processValidation.validate(process, Category1)

    validationResult should matchPattern {
      case ValidationResult(ValidationErrors(invalidNodes, Nil, Nil), ValidationWarnings.success, _
      ) if invalidNodes("subIn").size == 1 && invalidNodes("subIn-subVar").size == 1 =>
    }
  }

  test("validates disabled fragment with parameters") {
    val invalidSubprocess = CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      nodes = List(
        FlatNode(SubprocessInputDefinition("sub1", List(SubprocessParameter("param1", SubprocessClazzRef[Long])))),
        FlatNode(Variable(id = "subVar", varName = "subVar", value = "#nonExistingVar")),
        FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))
      ),
      additionalBranches = List.empty
    )

    val process = createProcess(
      nodes = List(
        Source("in", SourceRef(sourceTypeName, List())),
        SubprocessInput("subIn", SubprocessRef(
          invalidSubprocess.id,
          List(evaluatedparam.Parameter("param1", "'someString'"))),
          isDisabled = Some(true)
        ),
        Sink("out", SinkRef(sinkTypeName, List()))
      ),
      edges = List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.SubprocessOutput("output")))
      )
    )

    val processValidation = mockProcessValidation(invalidSubprocess)

    val validationResult = processValidation.validate(process, Category1)
    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.errors.globalErrors shouldBe Symbol("empty")
    validationResult.saveAllowed shouldBe true
  }

  test("validates and returns type info of fragment output fields") {
    val subprocess = CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      nodes = List(
        FlatNode(SubprocessInputDefinition("in", List(SubprocessParameter("subParam1", SubprocessClazzRef[String])))),
        SplitNode(Split("split"), List(
          List(FlatNode(SubprocessOutputDefinition("subOut1", "subOut1", List(Field("foo", "42L"))))),
          List(FlatNode(SubprocessOutputDefinition("subOut2", "subOut2", List(Field("bar", "'42'")))))
        ))
      ),
      additionalBranches = List.empty
    )

    val process = createProcess(
      nodes = List(
        Source("source", SourceRef(sourceTypeName, Nil)),
        SubprocessInput("subIn", SubprocessRef(
          subprocess.id,
          List(evaluatedparam.Parameter("subParam1", "'someString'"))),
          isDisabled = Some(false)
        ),
        Variable(id = "var1", varName = "var1", value = "#subOut1.foo"),
        Variable(id = "var2", varName = "var2", value = "#subOut2.bar"),
        Sink("sink1", SinkRef(sinkTypeName, Nil)),
        Sink("sink2", SinkRef(sinkTypeName, Nil))
      ),
      edges = List(
        Edge("source", "subIn", None),
        Edge("subIn", "var1", Some(EdgeType.SubprocessOutput("subOut1"))),
        Edge("subIn", "var2", Some(EdgeType.SubprocessOutput("subOut2"))),
        Edge("var1", "sink1", None),
        Edge("var2", "sink2", None)
      )
    )

    val processValidation = mockProcessValidation(subprocess)
    val validationResult = processValidation.validate(process, Category1)

    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.nodeResults("sink2").variableTypes("input") shouldBe typing.Unknown
    validationResult.nodeResults("sink2").variableTypes("var2") shouldBe Typed.fromInstance("42")
    validationResult.nodeResults("sink2").variableTypes("subOut2") shouldBe TypedObjectTypingResult(ListMap(
      "bar" -> Typed.fromInstance("42")
    ))
  }

  test("check for no expression found in mandatory parameter") {
    val process = createProcess(
      List(
        Source("inID", SourceRef(existingSourceFactory, List())),
        Enricher("custom", ServiceRef("fooService3", List(evaluatedparam.Parameter("expression", Expression("spel", "")))), "out"),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("inID", "custom", None), Edge("custom", "out", None))
    )

    val result = validator.validate(process, Category1)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(List(NodeValidationError("EmptyMandatoryParameter", _, _, Some("expression"), NodeValidationErrorType.SaveAllowed))) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("check for wrong fixed expression value in node parameter") {
    val process: DisplayableProcess = createProcessWithParams(
      List(evaluatedparam.Parameter("expression", Expression("spel", "wrong fixed value"))), Map.empty
    )

    val result = validator.validate(process, Category1)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(List(NodeValidationError("InvalidPropertyFixedValue", _, _, Some("expression"), NodeValidationErrorType.SaveAllowed))) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("check for wrong fixed expression value in additional property") {
    val process = createProcessWithParams(List.empty, Map(
      "numberOfThreads" -> "wrong fixed value",
      "requiredStringProperty" -> "test"
    ))

    val result = validator.validate(process, Category1)

    result.errors.globalErrors shouldBe empty
    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("InvalidPropertyFixedValue", _, _, Some("numberOfThreads"), NodeValidationErrorType.SaveAllowed)) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("validates scenario with category") {
    val process = createProcess(
      nodes = List(
        Source("source", SourceRef(secretExistingSourceFactory, Nil)),
        Sink("sink", SinkRef(existingSinkFactory, Nil))
      ),
      edges = List(Edge("source", "sink", None))
    )

    val validationResult = processValidation.validate(process, SecretCategory)
    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.errors.globalErrors shouldBe Symbol("empty")
    validationResult.saveAllowed shouldBe true

    val validationResultWithCategory2 = processValidation.validate(process, Category1)
    validationResultWithCategory2.errors.invalidNodes shouldBe Map(
      "source" -> List(PrettyValidationErrors.formatErrorMessage(MissingSourceFactory(secretExistingSourceFactory, "source")))
    )
  }

  test("validates scenario with fragment with category") {
    val subprocess = CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      nodes = List(
        FlatNode(SubprocessInputDefinition("in", List(SubprocessParameter("subParam1", SubprocessClazzRef[String])))),
        FlatNode(SubprocessOutputDefinition("subOut1", "out", List(Field("foo", "42L"))))
      ),
      additionalBranches = List.empty
    )

    val process = createProcess(
      nodes = List(
        Source("source", SourceRef(sourceTypeName, Nil)),
        SubprocessInput("subIn", SubprocessRef(
          subprocess.id,
          List(evaluatedparam.Parameter("subParam1", "'someString'"))),
          isDisabled = Some(false)
        ),
        Sink("sink", SinkRef(sinkTypeName, Nil))
      ),
      edges = List(
        Edge("source", "subIn", None),
        Edge("subIn", "sink", Some(EdgeType.SubprocessOutput("out")))
      )
    )

    val processValidation = mockProcessValidation(subprocess)

    val validationResult = processValidation.validate(process, Category1)
    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.errors.globalErrors shouldBe Symbol("empty")
    validationResult.saveAllowed shouldBe true

    val validationResultWithCategory2 = processValidation.validate(process, Category2)
    validationResultWithCategory2.errors.invalidNodes shouldBe Map(
      "subIn" -> List(PrettyValidationErrors.formatErrorMessage(UnknownSubprocess(subprocess.id, "subIn")))
    )
  }

  test("validates with custom validator") {
    val process = ScenarioBuilder
      .streaming(SampleCustomProcessValidator.badName)
      .source("start", existingSourceFactory)
      .emptySink("sink", existingSinkFactory)

    val displayable = ProcessConverter.toDisplayable(process, TestProcessingTypes.Streaming, Category1)
    val result = mockProcessValidation(process).validate(displayable, Category1)

    result.errors.processPropertiesErrors shouldBe List(PrettyValidationErrors.formatErrorMessage(SampleCustomProcessValidator.badNameError))
  }

  test("check for invalid characters") {
    val process = createProcess(
      List(
        Source("in\"'.", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("in\"'.", "out", None))
    )
    val result = validator.validate(process, Category1)

    result.errors.invalidNodes shouldBe Map(
      "in\"'." -> List(NodeValidationError("InvalidCharacters", "Invalid characters", "Node in\"'. contains invalid characters: \", . and ' are not allowed in node id", None, RenderNotAllowed))
    )
  }
}

private object ProcessValidationSpec {

  import ProcessTestData._
  import TestCategories._

  val sourceTypeName: String = "processSource"
  val sinkTypeName: String = "processSink"

  val validator: ProcessValidation = TestFactory.processValidation.withAdditionalPropertiesConfig(
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
      "requiredStringProperty" -> AdditionalPropertyConfig(None, Some(StringParameterEditor), Some(List(MandatoryParameterValidator)), Some("label")),
      "numberOfThreads" -> AdditionalPropertyConfig(None, Some(FixedValuesParameterEditor(possibleValues)), Some(List(FixedValuesValidator(possibleValues))), None),
      "maxEvents" -> AdditionalPropertyConfig(None, None, Some(List(LiteralParameterValidator.integerValidator)), Some("label"))
    ))
  )

  def validProcessWithFields(fields: Map[String, String]): DisplayableProcess = {
    createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("in", "out", None)), additionalFields = fields
    )
  }

  private def createProcessWithParams(nodeParams: List[evaluatedparam.Parameter], additionalProperties: Map[String, String], category: String = Category1): DisplayableProcess = {
    createProcess(
      List(
        Source("inID", SourceRef(existingSourceFactory, List())),
        Enricher("custom", ServiceRef(otherExistingServiceId4, nodeParams), "out"),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("inID", "custom", None), Edge("custom", "out", None)),
      TestProcessingTypes.Streaming,
      category,
      additionalProperties
    )
  }

  private def createProcess(nodes: List[NodeData],
                            edges: List[Edge],
                            `type`: ProcessingType = TestProcessingTypes.Streaming,
                            category: String = Category1,
                            additionalFields: Map[String, String] = Map()): DisplayableProcess = {
    DisplayableProcess("test", ProcessProperties(StreamMetaData(), subprocessVersions = Map.empty, additionalFields = Some(ProcessAdditionalFields(None, additionalFields))), nodes, edges, `type`, Some(category))
  }

  def mockProcessValidation(subprocess: CanonicalProcess): ProcessValidation = {
    import ProcessDefinitionBuilder._

    val processDefinition = ProcessDefinitionBuilder.empty
      .withSourceFactory(sourceTypeName)
      .withSinkFactory(sinkTypeName)

    val mockedProcessValidation: ProcessValidation = ProcessValidation(
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> new StubModelDataWithProcessDefinition(processDefinition)),
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map()),
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> List(SampleCustomProcessValidator)),
      new SubprocessResolver(new StubSubprocessRepository(Set(
        SubprocessDetails(sampleSubprocessOneOut, Category1),
        SubprocessDetails(subprocess, Category1),
      )))
    )

    mockedProcessValidation
  }

  object SampleCustomProcessValidator extends CustomProcessValidator {
    val badName = "badName"

    val badNameError: ScenarioNameValidationError = ScenarioNameValidationError("BadName", "BadName")

    override def validate(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
      Validated.condNel(process.id != badName, (), badNameError)
    }
  }

  class WithoutErrorsAndWarnings extends BeMatcher[ValidationResult] {
    override def apply(left: ValidationResult): MatchResult = {
      MatchResult(
        !left.hasErrors && !left.hasWarnings,
        "ValidationResult should has neither errors nor warnings",
        "ValidationResult should has either errors or warnings"
      )
    }
  }

  val withoutErrorsAndWarnings: WithoutErrorsAndWarnings = new WithoutErrorsAndWarnings()

}
