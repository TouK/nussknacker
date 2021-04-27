package pl.touk.nussknacker.ui.validation

import org.scalatest.{FunSuite, Ignore, Matchers}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.api.{Group, MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, SplitNode}
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.{ProcessingTypeData, spel}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType.{NextSwitch, SwitchDefault}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.{Edge, EdgeType}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType, ValidationErrors, ValidationResult, ValidationWarnings}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{SampleSubprocessRepository, emptyProcessingTypeDataProvider, mapProcessingTypeDataProvider, possibleValues, sampleResolver}
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver

import scala.collection.immutable.ListMap

class ProcessValidationSpec extends FunSuite with Matchers {
  import spel.Implicits._
  import ProcessValidationSpec._

  test("check for notunique edges") {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        SubprocessInput("subIn", SubprocessRef("sub1", List())),
        Sink("out", SinkRef("barSink", List())),
        Sink("out2", SinkRef("barSink", List())),
        Sink("out3", SinkRef("barSink", List()))
      ),
      List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.SubprocessOutput("out1"))),
        Edge("subIn", "out2", Some(EdgeType.SubprocessOutput("out2"))),
        Edge("subIn", "out3", Some(EdgeType.SubprocessOutput("out2")))
      )

    )

    val result = validator.validate(process)

    result.errors.invalidNodes shouldBe Map(
      "subIn" -> List(PrettyValidationErrors.nonuniqeEdge(validator.uiValidationError, EdgeType.SubprocessOutput("out2")))
    )
  }

  test("check for duplicates) groups") {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Variable("var", "var1", Expression("spel", "0")),
        Sink("out", SinkRef("barSink", List()))
      ),
      List(
        Edge("in", "var", None),
        Edge("var", "out", None)
      ),
      groups = Set(Group("in", Set("in", "var1"), None, None))
    )


    val result = validator.validate(process)

    result.errors.globalErrors shouldBe List(PrettyValidationErrors.duplicatedNodeIds(validator.uiValidationError, List("in")))
  }

  test("check for loose nodes") {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Sink("out", SinkRef("barSink", List())),
        Filter("loose", Expression("spel", "true"))
      ),
      List(Edge("in", "out", None))

    )
    val result = validator.validate(process)

    result.errors.invalidNodes shouldBe Map("loose" -> List(PrettyValidationErrors.looseNode(validator.uiValidationError)))
  }

  test("check for duplicated ids") {
    val process = createProcess(
      List(
        Source("inID", SourceRef("barSource", List())),
        Filter("inID", Expression("spel", "''")),
        Sink("out", SinkRef("barSink", List()))
      ),
      List(Edge("inID", "inID", None), Edge("inID", "out", None))
    )
    val result = validator.validate(process)

    result.errors.globalErrors shouldBe List(PrettyValidationErrors.duplicatedNodeIds(validator.uiValidationError, List("inID")))
  }

  test("check for duplicated ids when duplicated id is switch id") {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Switch("switchID", Expression("spel", "''"), "expr1"),
        Sink("out", SinkRef("barSink", List())),
        Sink("switchID", SinkRef("barSink", List()))
      ),
      List(
        Edge("in", "switchID", None),
        Edge("switchID", "out", Some(SwitchDefault)),
        Edge("switchID", "switch", Some(NextSwitch(Expression("spel", "''"))))
      )
    )
    val result = validator.validate(process)

    result.errors.globalErrors shouldBe List(PrettyValidationErrors.duplicatedNodeIds(validator.uiValidationError, List("switchID")))
    result.errors.invalidNodes shouldBe empty
    result.warnings shouldBe ValidationWarnings.success
  }

  test("not fail with exception when no processtype validator present") {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Sink("out", SinkRef("barSink", List()))
      ),
      List(Edge("in", "out", None)), `type` = TestProcessingTypes.RequestResponse
    )
    validator.validate(process) should matchPattern {
      case ValidationResult(
      ValidationErrors(_, Nil, errors),
      ValidationWarnings.success,
      _
      ) if errors == List(PrettyValidationErrors.noValidatorKnown(TestProcessingTypes.RequestResponse)) =>
    }
  }

  test("not allow required process fields") {
    val processValidation = new ProcessValidation(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> ProcessTestData.validator),
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
        "field1" -> AdditionalPropertyConfig(None, None, Some(List(MandatoryParameterValidator)), Some("label1")),
        "field2" -> AdditionalPropertyConfig(None, None, None, Some("label2"))
      )), sampleResolver, emptyProcessingTypeDataProvider)

    processValidation.validate(validProcessWithFields(Map("field1" -> "a", "field2" -> "b"))) shouldBe 'ok

    processValidation.validate(validProcessWithFields(Map("field1" -> "a"))) shouldBe 'ok

    processValidation.validate(validProcessWithFields(Map("field1" -> "", "field2" -> "b")))
      .errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("EmptyMandatoryParameter", _, _, Some("field1"), ValidationResults.NodeValidationErrorType.SaveAllowed)) =>
    }
    processValidation.validate(validProcessWithFields(Map("field2" -> "b")))
      .errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("MissingRequiredProperty", _, _, Some("field1"), ValidationResults.NodeValidationErrorType.SaveAllowed)) =>
    }
  }

  test("don't validate properties on subprocess") {

    val processValidation = new ProcessValidation(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> ProcessTestData.validator),
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
        "field1" -> AdditionalPropertyConfig(None, None, Some(List(MandatoryParameterValidator)), Some("label1")),
        "field2" -> AdditionalPropertyConfig(None, None, Some(List(MandatoryParameterValidator)), Some("label2"))
      )), sampleResolver, emptyProcessingTypeDataProvider)

    val process = validProcessWithFields(Map())
    val subprocess = process.copy(properties = process.properties.copy(isSubprocess = true))

    processValidation.validate(subprocess) shouldBe 'ok

  }

  test("validate type) process field") {
    val possibleValues = List(FixedExpressionValue("true", "true"), FixedExpressionValue("false", "false"))
    val processValidation = new ProcessValidation(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> ProcessTestData.validator),
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
        "field1" -> AdditionalPropertyConfig(None, Some(FixedValuesParameterEditor(possibleValues)), Some(List(FixedValuesValidator(possibleValues))), Some("label")),
        "field2" -> AdditionalPropertyConfig(None, None, Some(List(LiteralParameterValidator.integerValidator)), Some("label"))
      )), sampleResolver, emptyProcessingTypeDataProvider)

    processValidation.validate(validProcessWithFields(Map("field1" -> "true"))) shouldBe 'ok
    processValidation.validate(validProcessWithFields(Map("field1" -> "false"))) shouldBe 'ok
    processValidation.validate(validProcessWithFields(Map("field1" -> "1"))) should not be 'ok

    processValidation.validate(validProcessWithFields(Map("field2" -> "1"))) shouldBe 'ok
    processValidation.validate(validProcessWithFields(Map("field2" -> "1.1"))) should not be 'ok
    processValidation.validate(validProcessWithFields(Map("field2" -> "true"))) should not be 'ok
  }

  test("handle unknown properties validation") {
    val processValidation = new ProcessValidation(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> ProcessTestData.validator),
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
        "field2" -> AdditionalPropertyConfig(None, None, Some(List(LiteralParameterValidator.integerValidator)), Some("label"))
      )), sampleResolver, emptyProcessingTypeDataProvider)

    val result = processValidation.validate(validProcessWithFields(Map("field1" -> "true")))

    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("UnknownProperty", _, _, Some("field1"), NodeValidationErrorType.SaveAllowed)) =>
    }
  }

  test("not allows save with incorrect characters in ids") {
    def process(nodeId: String) = createProcess(
      List(Source(nodeId, SourceRef("barSource", List()))),
      List()
    )

    validator.validate(process("a\"s")).saveAllowed shouldBe false
    validator.validate(process("a's")).saveAllowed shouldBe false
    validator.validate(process("a.s")).saveAllowed shouldBe false
    validator.validate(process("as")).saveAllowed shouldBe true

  }

  test("validates subprocess input definition") {
    val process = createProcess(
      nodes = List(
        Source("in", SourceRef("processSource", List())),
        SubprocessInput(
          "subIn",
          SubprocessRef("sub1", List(evaluatedparam.Parameter("param1", "'someString'"))), isDisabled = Some(false)),
        Sink("out", SinkRef("processSink", List()))),
      edges = List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.SubprocessOutput("output"))))
    )

    val invalidSubprocess = CanonicalProcess(
      MetaData("sub1", StreamMetaData(), isSubprocess = true),
      ExceptionHandlerRef(List.empty),
      nodes = List(
        FlatNode(
          SubprocessInputDefinition(
            "in", List(SubprocessParameter("param1", SubprocessClazzRef[Long])))),
        FlatNode(Variable(id = "subVar", varName = "subVar", value = "#nonExistingVar")),
        FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))
      ),
      additionalBranches = List.empty
    )
    val (processValidation, processWithSub) = mockProcessValidationAndProcess(process, invalidSubprocess)

    processValidation.validate(processWithSub) should matchPattern {
      case ValidationResult(ValidationErrors(invalidNodes, Nil, Nil), ValidationWarnings.success, _
      ) if invalidNodes("subIn").size == 1 && invalidNodes("subIn-subVar").size == 1 =>
    }
  }

  test("validates disabled subprocess with parameters") {
    val process = createProcess(
      nodes = List(
        Source("in", SourceRef("processSource", List())),
        SubprocessInput(
          "subIn",
          SubprocessRef("sub1", List(evaluatedparam.Parameter("param1", "'someString'"))), isDisabled = Some(true)),
        Sink("out", SinkRef("processSink", List()))),
      edges = List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.SubprocessOutput("output"))))
    )

    val invalidSubprocess = CanonicalProcess(
      MetaData("sub1", StreamMetaData(), isSubprocess = true),
      ExceptionHandlerRef(List.empty),
      nodes = List(
        FlatNode(
          SubprocessInputDefinition(
            "in", List(SubprocessParameter("param1", SubprocessClazzRef[Long])))),
        FlatNode(Variable(id = "subVar", varName = "subVar", value = "#nonExistingVar")),
        FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))
      ),
      additionalBranches = List.empty
    )
    val (processValidation, processWithSub) = mockProcessValidationAndProcess(process, invalidSubprocess)

    val validationResult = processValidation.validate(processWithSub)
    validationResult.errors.invalidNodes shouldBe 'empty
    validationResult.errors.globalErrors shouldBe 'empty
    validationResult.saveAllowed shouldBe true
  }

  test("validates and returns type info of subprocess output fields") {
    val process = createProcess(
      nodes = List(
        Source("source", SourceRef("processSource", Nil)),
        SubprocessInput(
          "subIn",
          SubprocessRef("sub1", List(evaluatedparam.Parameter("subParam1", "'someString'"))), isDisabled = Some(false)
        ),
        Variable(id = "var1", varName = "var1", value = "#subOut1.foo"),
        Variable(id = "var2", varName = "var2", value = "#subOut2.bar"),
        Sink("sink1", SinkRef("processSink", Nil)),
        Sink("sink2", SinkRef("processSink", Nil))
      ),
      edges = List(
        Edge("source", "subIn", None),
        Edge("subIn", "var1", Some(EdgeType.SubprocessOutput("subOut1"))),
        Edge("subIn", "var2", Some(EdgeType.SubprocessOutput("subOut2"))),
        Edge("var1", "sink1", None),
        Edge("var2", "sink2", None)
      )
    )
    val subprocess = CanonicalProcess(
      MetaData("sub1", StreamMetaData(), isSubprocess = true),
      ExceptionHandlerRef(Nil),
      nodes = List(
        FlatNode(SubprocessInputDefinition(
          "in", List(SubprocessParameter("subParam1", SubprocessClazzRef[String]))
        )),
        SplitNode(Split("split"), List(
          List(FlatNode(SubprocessOutputDefinition("subOut1", "subOut1", List(Field("foo", "42L"))))),
          List(FlatNode(SubprocessOutputDefinition("subOut2", "subOut2", List(Field("bar", "'42'")))))
        ))
      ), additionalBranches = List.empty)
    val (processValidation, processWithSub) = mockProcessValidationAndProcess(process, subprocess)
    val validationResult = processValidation.validate(processWithSub)
    validationResult.errors.invalidNodes shouldBe 'empty
    validationResult.nodeResults("sink2").variableTypes("input") shouldBe typing.Unknown
    validationResult.nodeResults("sink2").variableTypes("var2") shouldBe Typed(classOf[String])
    validationResult.nodeResults("sink2").variableTypes("subOut2") shouldBe TypedObjectTypingResult(ListMap(
      "bar" -> Typed(classOf[String])
    ))
  }

  test("check for no expression found in mandatory parameter") {
    val process = createProcess(
      List(
        Source("inID", SourceRef("barSource", List())),
        Enricher("custom", ServiceRef("fooService3", List(evaluatedparam.Parameter("expression", Expression("spel", "")))), "out"),
        Sink("out", SinkRef("barSink", List()))
      ),
      List(Edge("inID", "custom", None), Edge("custom", "out", None))
    )

    val result = validator.validate(process)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(List(NodeValidationError("EmptyMandatoryParameter", _, _, Some("expression"), NodeValidationErrorType.SaveAllowed))) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("check for wrong fixed expression value in node parameter") {
    val process: DisplayableProcess = createProcessWithParams(List(evaluatedparam.Parameter("expression", Expression("spel", "wrong fixed value"))), Map.empty)

    val result = validator.validate(process)

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

    val result = validator.validate(process)

    result.errors.globalErrors shouldBe empty
    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("InvalidPropertyFixedValue", _, _, Some("numberOfThreads"), NodeValidationErrorType.SaveAllowed)) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }
}

private object ProcessValidationSpec {

  val validator = new ProcessValidation(
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> ProcessTestData.validator),
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map(
      "requiredStringProperty" -> AdditionalPropertyConfig(None, Some(StringParameterEditor), Some(List(MandatoryParameterValidator)), Some("label")),
      "numberOfThreads" -> AdditionalPropertyConfig(None, Some(FixedValuesParameterEditor(possibleValues)), Some(List(FixedValuesValidator(possibleValues))), None),
      "maxEvents" -> AdditionalPropertyConfig(None, None, Some(List(LiteralParameterValidator.integerValidator)), Some("label"))
    )),
    sampleResolver,
    emptyProcessingTypeDataProvider
  )

  def validProcessWithFields(fields: Map[String, String]) = {
    createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Sink("out", SinkRef("barSink", List()))
      ),
      List(Edge("in", "out", None)), additionalFields = fields
    )
  }

  def createProcessWithParams(nodeParams: List[evaluatedparam.Parameter], additionalProperties: Map[String, String]) = {
    createProcess(
      List(
        Source("inID", SourceRef("barSource", List())),
        Enricher("custom", ServiceRef("fooService4", nodeParams), "out"),
        Sink("out", SinkRef("barSink", List()))
      ),
      List(Edge("inID", "custom", None), Edge("custom", "out", None)),
      TestProcessingTypes.Streaming,
      Set.empty,
      additionalProperties
    )
  }

  def createProcess(nodes: List[NodeData],
                    edges: List[Edge],
                    `type`: ProcessingTypeData.ProcessingType = TestProcessingTypes.Streaming,
                    groups: Set[Group] = Set(), additionalFields: Map[String, String] = Map()) = {
    DisplayableProcess("test", ProcessProperties(StreamMetaData(),
      ExceptionHandlerRef(List()), subprocessVersions = Map.empty, additionalFields = Some(ProcessAdditionalFields(None, groups, additionalFields))), nodes, edges, `type`)
  }

  def mockProcessValidationAndProcess(process: DisplayableProcess,
                                      subprocess: CanonicalProcess): (ProcessValidation, DisplayableProcess) = {
    import ProcessDefinitionBuilder._
    val processDefinition = ProcessDefinitionBuilder.empty.withSourceFactory("processSource").withSinkFactory("processSink")
    val validator = ProcessValidator.default(ProcessDefinitionBuilder.withEmptyObjects(processDefinition), new SimpleDictRegistry(Map.empty))
    val processValidation: ProcessValidation = new ProcessValidation(
      validators = mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> validator),
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map()),
      subprocessResolver = new SubprocessResolver(new SampleSubprocessRepository(Set(subprocess))),
      emptyProcessingTypeDataProvider)

    (processValidation, process)
  }
}
