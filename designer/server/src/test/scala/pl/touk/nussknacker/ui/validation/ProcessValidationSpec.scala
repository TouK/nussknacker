package pl.touk.nussknacker.ui.validation

import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.{BeMatcher, MatchResult}
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  MissingSourceFactory,
  NodeIdError,
  ScenarioNameValidationError,
  UnknownFragment
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, SplitNode}
import pl.touk.nussknacker.engine.compile.IdValidationTestData
import pl.touk.nussknacker.engine.graph.EdgeType.{NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.graph.{EdgeType, evaluatedparam}
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.{CustomProcessValidator, spel}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationErrorType.{
  RenderNotAllowed,
  SaveAllowed,
  SaveNotAllowed
}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  ValidationErrors,
  ValidationResult,
  ValidationWarnings
}
import pl.touk.nussknacker.restmodel.validation.{PrettyValidationErrors, ValidationResults}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{mapProcessingTypeDataProvider, possibleValues}
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.process.fragment.{FragmentDetails, FragmentResolver}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import scala.jdk.CollectionConverters._

class ProcessValidationSpec extends AnyFunSuite with Matchers {

  import ProcessTestData._
  import ProcessValidationSpec._
  import TestCategories._
  import spel.Implicits._

  test("check for not unique edge types") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        FragmentInput("subIn", FragmentRef("sub1", List())),
        Sink("out", SinkRef(existingSinkFactory, List())),
        Sink("out2", SinkRef(existingSinkFactory, List())),
        Sink("out3", SinkRef(existingSinkFactory, List()))
      ),
      List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.FragmentOutput("out1"))),
        Edge("subIn", "out2", Some(EdgeType.FragmentOutput("out2"))),
        Edge("subIn", "out3", Some(EdgeType.FragmentOutput("out2")))
      )
    )

    val result = configuredValidator.validate(process)

    result.errors.invalidNodes shouldBe Map(
      "subIn" -> List(
        NodeValidationError(
          "NonUniqueEdgeType",
          "Edges are not unique",
          "Node subIn has duplicate outgoing edges of type: FragmentOutput(out2), it cannot be saved properly",
          None,
          NodeValidationErrorType.SaveNotAllowed
        )
      )
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

    val result = configuredValidator.validate(process)
    result.errors.invalidNodes shouldBe Symbol("empty")
  }

  test("check for not unique edges") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        FragmentInput("subIn", FragmentRef("sub1", List())),
        Sink("out2", SinkRef(existingSinkFactory, List())),
      ),
      List(
        Edge("in", "subIn", None),
        Edge("subIn", "out2", Some(EdgeType.FragmentOutput("out1"))),
        Edge("subIn", "out2", Some(EdgeType.FragmentOutput("out2"))),
      )
    )

    val result = configuredValidator.validate(process)

    result.errors.invalidNodes shouldBe Map(
      "subIn" -> List(
        NodeValidationError(
          "NonUniqueEdge",
          "Edges are not unique",
          "Node subIn has duplicate outgoing edges to: out2, it cannot be saved properly",
          None,
          SaveNotAllowed
        )
      )
    )
  }

  test("check for loose nodes") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List())),
        Filter("loose", Expression.spel("true"))
      ),
      List(Edge("in", "out", None))
    )
    val result = configuredValidator.validate(process)

    result.errors.invalidNodes shouldBe Map(
      "loose" -> List(
        NodeValidationError(
          "LooseNode",
          "Loose node",
          "Node loose is not connected to source, it cannot be saved properly",
          None,
          SaveNotAllowed
        )
      )
    )
  }

  test("filter with only 'false' edge") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List())),
        Filter("filter", Expression.spel("true"))
      ),
      List(
        Edge("in", "filter", None),
        Edge("filter", "out", Some(EdgeType.FilterFalse)),
      ),
      additionalFields = Map(
        "requiredStringProperty" -> "test"
      )
    )
    val result = configuredValidator.validate(process)

    result.hasErrors shouldBe false
  }

  test("check for disabled nodes") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List())),
        Filter("filter", Expression.spel("true"), isDisabled = Some(true))
      ),
      List(
        Edge("in", "filter", None),
        Edge("filter", "out", Some(EdgeType.FilterTrue)),
      )
    )
    val result = configuredValidator.validate(process)

    result.warnings.invalidNodes shouldBe Map(
      "filter" -> List(
        NodeValidationError(
          "DisabledNode",
          "Node filter is disabled",
          "Deploying scenario with disabled node can have unexpected consequences",
          None,
          SaveAllowed
        )
      )
    )
  }

  test("check for duplicated ids") {
    val process = createProcess(
      List(
        Source("inID", SourceRef(existingSourceFactory, List())),
        Filter("inID", Expression.spel("''")),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("inID", "inID", None), Edge("inID", "out", None))
    )
    val result = configuredValidator.validate(process)

    result.errors.globalErrors shouldBe List(
      NodeValidationError(
        "DuplicatedNodeIds",
        "Two nodes cannot have same id",
        "Duplicate node ids: inID",
        None,
        RenderNotAllowed
      )
    )
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
        Edge("switchID", "switch", Some(NextSwitch(Expression.spel("''"))))
      )
    )

    val result = configuredValidator.validate(process)

    result.errors.globalErrors shouldBe List(
      NodeValidationError(
        "DuplicatedNodeIds",
        "Two nodes cannot have same id",
        "Duplicate node ids: switchID",
        None,
        RenderNotAllowed
      )
    )
    result.errors.invalidNodes shouldBe empty
    result.warnings shouldBe ValidationWarnings.success
  }

  test("not fail with exception when no processtype validator present") {
    val process = createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("in", "out", None)),
      `type` = TestProcessingTypes.RequestResponse
    )
    configuredValidator.validate(process) should matchPattern {
      case ValidationResult(
            ValidationErrors(_, Nil, errors),
            ValidationWarnings.success,
            _
          ) if errors == List(PrettyValidationErrors.noValidatorKnown(TestProcessingTypes.RequestResponse)) =>
    }
  }

  test("not allow required scenario fields") {
    val processValidation = TestFactory.processValidation.withScenarioPropertiesConfig(
      mapProcessingTypeDataProvider(
        TestProcessingTypes.Streaming -> (Map(
          "field1" -> ScenarioPropertyConfig(None, None, Some(List(MandatoryParameterValidator)), Some("label1")),
          "field2" -> ScenarioPropertyConfig(None, None, None, Some("label2"))
        ) ++ FlinkStreamingPropertiesConfig.properties)
      )
    )

    processValidation.validate(
      validProcessWithFields(Map("field1" -> "a", "field2" -> "b"))
    ) shouldBe withoutErrorsAndWarnings

    processValidation.validate(validProcessWithFields(Map("field1" -> "a"))) shouldBe withoutErrorsAndWarnings

    processValidation
      .validate(validProcessWithFields(Map("field1" -> "", "field2" -> "b")))
      .errors
      .processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "EmptyMandatoryParameter",
              _,
              _,
              Some("field1"),
              ValidationResults.NodeValidationErrorType.SaveAllowed
            )
          ) =>
    }
    processValidation
      .validate(validProcessWithFields(Map("field2" -> "b")))
      .errors
      .processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "MissingRequiredProperty",
              _,
              _,
              Some("field1"),
              ValidationResults.NodeValidationErrorType.SaveAllowed
            )
          ) =>
    }
  }

  test("don't validate properties on fragment") {
    val processValidation = TestFactory.processValidation.withScenarioPropertiesConfig(
      mapProcessingTypeDataProvider(
        TestProcessingTypes.Streaming -> (Map(
          "field1" -> ScenarioPropertyConfig(None, None, Some(List(MandatoryParameterValidator)), Some("label1")),
          "field2" -> ScenarioPropertyConfig(None, None, Some(List(MandatoryParameterValidator)), Some("label2"))
        ) ++ FlinkStreamingPropertiesConfig.properties)
      )
    )

    val process = validProcessWithFields(Map())
    val fragment = process.copy(properties =
      process.properties.copy(
        additionalFields = process.properties.additionalFields.copy(
          metaDataType = FragmentSpecificData.typeName
        )
      )
    )

    processValidation.validate(fragment) shouldBe withoutErrorsAndWarnings

  }

  test("validate type scenario field") {
    val possibleValues = List(FixedExpressionValue("true", "true"), FixedExpressionValue("false", "false"))
    val processValidation = TestFactory.processValidation.withScenarioPropertiesConfig(
      mapProcessingTypeDataProvider(
        TestProcessingTypes.Streaming -> (Map(
          "field1" -> ScenarioPropertyConfig(
            None,
            Some(FixedValuesParameterEditor(possibleValues)),
            Some(List(FixedValuesValidator(possibleValues))),
            Some("label")
          ),
          "field2" -> ScenarioPropertyConfig(
            None,
            None,
            Some(List(LiteralParameterValidator.integerValidator)),
            Some("label")
          )
        ) ++ FlinkStreamingPropertiesConfig.properties)
      )
    )

    processValidation.validate(validProcessWithFields(Map("field1" -> "true"))) shouldBe withoutErrorsAndWarnings
    processValidation.validate(validProcessWithFields(Map("field1" -> "false"))) shouldBe withoutErrorsAndWarnings
    processValidation.validate(validProcessWithFields(Map("field1" -> "1"))) should not be withoutErrorsAndWarnings

    processValidation.validate(validProcessWithFields(Map("field2" -> "1"))) shouldBe withoutErrorsAndWarnings
    processValidation.validate(validProcessWithFields(Map("field2" -> "1.1"))) should not be withoutErrorsAndWarnings
    processValidation.validate(validProcessWithFields(Map("field2" -> "true"))) should not be withoutErrorsAndWarnings
  }

  test("handle unknown properties validation") {
    val processValidation = TestFactory.processValidation.withScenarioPropertiesConfig(
      mapProcessingTypeDataProvider(
        TestProcessingTypes.Streaming -> (Map(
          "field2" -> ScenarioPropertyConfig(
            None,
            None,
            Some(List(LiteralParameterValidator.integerValidator)),
            Some("label")
          )
        ) ++ FlinkStreamingPropertiesConfig.properties)
      )
    )

    val result = processValidation.validate(validProcessWithFields(Map("field1" -> "true")))

    result.errors.processPropertiesErrors should matchPattern {
      case List(NodeValidationError("UnknownProperty", _, _, Some("field1"), NodeValidationErrorType.SaveAllowed)) =>
    }
  }

  test("not allows save with incorrect characters in ids") {
    def process(nodeId: String) = createProcess(
      List(Source(nodeId, SourceRef(existingSourceFactory, List()))),
      List()
    )

    configuredValidator.validate(process("a\"s")).saveAllowed shouldBe false
    configuredValidator.validate(process("a's")).saveAllowed shouldBe false
    configuredValidator.validate(process("a.s")).saveAllowed shouldBe false
    configuredValidator.validate(process("as")).saveAllowed shouldBe true

  }

  test("validates fragment input definition") {
    val invalidFragment = CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      nodes = List(
        FlatNode(FragmentInputDefinition("in", List(FragmentParameter("param1", FragmentClazzRef[Long])))),
        FlatNode(Variable(id = "subVar", varName = "subVar", value = "#nonExistingVar")),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      additionalBranches = List.empty
    )

    val process = createProcess(
      nodes = List(
        Source("in", SourceRef(sourceTypeName, List())),
        FragmentInput(
          "subIn",
          FragmentRef(invalidFragment.id, List(evaluatedparam.Parameter("param1", "'someString'"))),
          isDisabled = Some(false)
        ),
        Sink("out", SinkRef(sinkTypeName, List()))
      ),
      edges = List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.FragmentOutput("output")))
      )
    )

    val processValidation = mockedProcessValidation(invalidFragment)
    val validationResult  = processValidation.validate(process)

    validationResult should matchPattern {
      case ValidationResult(ValidationErrors(invalidNodes, Nil, Nil), ValidationWarnings.success, _)
          if invalidNodes("subIn").size == 1 && invalidNodes("subIn-subVar").size == 1 =>
    }
  }

  test("validates disabled fragment with parameters") {
    val invalidFragment = CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      nodes = List(
        FlatNode(FragmentInputDefinition("sub1", List(FragmentParameter("param1", FragmentClazzRef[Long])))),
        FlatNode(Variable(id = "subVar", varName = "subVar", value = "#nonExistingVar")),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      additionalBranches = List.empty
    )

    val process = createProcess(
      nodes = List(
        Source("in", SourceRef(sourceTypeName, List())),
        FragmentInput(
          "subIn",
          FragmentRef(invalidFragment.id, List(evaluatedparam.Parameter("param1", "'someString'"))),
          isDisabled = Some(true)
        ),
        Sink("out", SinkRef(sinkTypeName, List()))
      ),
      edges = List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.FragmentOutput("output")))
      )
    )

    val processValidation = mockedProcessValidation(invalidFragment)

    val validationResult = processValidation.validate(process)
    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.errors.globalErrors shouldBe Symbol("empty")
    validationResult.saveAllowed shouldBe true
  }

  test("validates and returns type info of fragment output fields") {
    val fragment = CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      nodes = List(
        FlatNode(FragmentInputDefinition("in", List(FragmentParameter("subParam1", FragmentClazzRef[String])))),
        SplitNode(
          Split("split"),
          List(
            List(FlatNode(FragmentOutputDefinition("subOut1", "subOut1", List(Field("foo", "42L"))))),
            List(FlatNode(FragmentOutputDefinition("subOut2", "subOut2", List(Field("bar", "'42'")))))
          )
        )
      ),
      additionalBranches = List.empty
    )

    val process = createProcess(
      nodes = List(
        Source("source", SourceRef(sourceTypeName, Nil)),
        FragmentInput(
          "subIn",
          FragmentRef(fragment.id, List(evaluatedparam.Parameter("subParam1", "'someString'"))),
          isDisabled = Some(false)
        ),
        Variable(id = "var1", varName = "var1", value = "#subOut1.foo"),
        Variable(id = "var2", varName = "var2", value = "#subOut2.bar"),
        Sink("sink1", SinkRef(sinkTypeName, Nil)),
        Sink("sink2", SinkRef(sinkTypeName, Nil))
      ),
      edges = List(
        Edge("source", "subIn", None),
        Edge("subIn", "var1", Some(EdgeType.FragmentOutput("subOut1"))),
        Edge("subIn", "var2", Some(EdgeType.FragmentOutput("subOut2"))),
        Edge("var1", "sink1", None),
        Edge("var2", "sink2", None)
      )
    )

    val processValidation = mockedProcessValidation(fragment)
    val validationResult  = processValidation.validate(process)

    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.nodeResults("sink2").variableTypes("input") shouldBe typing.Unknown
    validationResult.nodeResults("sink2").variableTypes("var2") shouldBe Typed.fromInstance("42")
    validationResult.nodeResults("sink2").variableTypes("subOut2") shouldBe TypedObjectTypingResult(
      Map(
        "bar" -> Typed.fromInstance("42")
      )
    )
  }

  test("check for no expression found in mandatory parameter") {
    val process = createProcess(
      List(
        Source("inID", SourceRef(existingSourceFactory, List())),
        Enricher(
          "custom",
          ServiceRef("fooService3", List(evaluatedparam.Parameter("expression", Expression.spel("")))),
          "out"
        ),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("inID", "custom", None), Edge("custom", "out", None))
    )

    val result = configuredValidator.validate(process)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "EmptyMandatoryParameter",
                _,
                _,
                Some("expression"),
                NodeValidationErrorType.SaveAllowed
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("check for wrong fixed expression value in node parameter") {
    val process: DisplayableProcess = createProcessWithParams(
      List(evaluatedparam.Parameter("expression", Expression.spel("wrong fixed value"))),
      Map.empty
    )

    val result = configuredValidator.validate(process)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "InvalidPropertyFixedValue",
                _,
                _,
                Some("expression"),
                NodeValidationErrorType.SaveAllowed
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("check for wrong fixed expression value in scenario property") {
    val process = createProcessWithParams(
      List.empty,
      Map(
        "numberOfThreads"        -> "wrong fixed value",
        "requiredStringProperty" -> "test"
      )
    )

    val result = configuredValidator.validate(process)

    result.errors.globalErrors shouldBe empty
    result.errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "InvalidPropertyFixedValue",
              _,
              _,
              Some("numberOfThreads"),
              NodeValidationErrorType.SaveAllowed
            )
          ) =>
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

    val validationResult = processValidation.validate(process.copy(category = SecretCategory))
    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.errors.globalErrors shouldBe Symbol("empty")
    validationResult.saveAllowed shouldBe true

    val validationResultWithCategory2 = processValidation.validate(process)
    validationResultWithCategory2.errors.invalidNodes shouldBe Map(
      "source" -> List(
        PrettyValidationErrors.formatErrorMessage(MissingSourceFactory(secretExistingSourceFactory, "source"))
      )
    )
  }

  test("validates scenario with fragment with category") {
    val fragment = CanonicalProcess(
      MetaData("sub1", FragmentSpecificData()),
      nodes = List(
        FlatNode(FragmentInputDefinition("in", List(FragmentParameter("subParam1", FragmentClazzRef[String])))),
        FlatNode(FragmentOutputDefinition("subOut1", "out", List(Field("foo", "42L"))))
      ),
      additionalBranches = List.empty
    )

    val process = createProcess(
      nodes = List(
        Source("source", SourceRef(sourceTypeName, Nil)),
        FragmentInput(
          "subIn",
          FragmentRef(fragment.id, List(evaluatedparam.Parameter("subParam1", "'someString'"))),
          isDisabled = Some(false)
        ),
        Sink("sink", SinkRef(sinkTypeName, Nil))
      ),
      edges = List(
        Edge("source", "subIn", None),
        Edge("subIn", "sink", Some(EdgeType.FragmentOutput("out")))
      )
    )

    val processValidation = mockedProcessValidation(fragment)

    val validationResult = processValidation.validate(process)
    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.errors.globalErrors shouldBe Symbol("empty")
    validationResult.saveAllowed shouldBe true

    val validationResultWithCategory2 = processValidation.validate(process.copy(category = Category2))
    validationResultWithCategory2.errors.invalidNodes shouldBe Map(
      "subIn" -> List(PrettyValidationErrors.formatErrorMessage(UnknownFragment(fragment.id, "subIn")))
    )
  }

  test("validates scenario with fragment parameters - P1 as mandatory param with some actual value") {
    val fragmentId = "fragment1"

    val configWithValidators: Config = defaultConfig
      .withValue(
        s"componentsUiConfig.$fragmentId.params.P1.validators",
        fromIterable(List(Map("type" -> "MandatoryParameterValidator").asJava).asJava)
      )

    val fragmentDefinition: CanonicalProcess =
      createFragmentDefinition(fragmentId, List(FragmentParameter("P1", FragmentClazzRef[Short])))
    val processWithFragment = createProcessWithFragmentParams(fragmentId, List(evaluatedparam.Parameter("P1", "123")))

    val processValidation = mockedProcessValidation(fragmentDefinition, configWithValidators)
    val result            = processValidation.validate(processWithFragment)
    result.hasErrors shouldBe false
    result.errors.invalidNodes shouldBe Symbol("empty")
    result.errors.globalErrors shouldBe Symbol("empty")
    result.saveAllowed shouldBe true
  }

  test("validates scenario with fragment parameters - P1 as mandatory param with with missing actual value") {
    val fragmentId = "fragment1"

    val configWithValidators: Config = defaultConfig
      .withValue(
        s"componentsUiConfig.$fragmentId.params.P1.validators",
        fromIterable(List(Map("type" -> "MandatoryParameterValidator").asJava).asJava)
      )

    val fragmentDefinition: CanonicalProcess =
      createFragmentDefinition(fragmentId, List(FragmentParameter("P1", FragmentClazzRef[Short])))
    val processWithFragment = createProcessWithFragmentParams(fragmentId, List(evaluatedparam.Parameter("P1", "")))

    val processValidation = mockedProcessValidation(fragmentDefinition, configWithValidators)
    val result            = processValidation.validate(processWithFragment)

    result.hasErrors shouldBe true
    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("subIn") should matchPattern {
      case Some(
            List(NodeValidationError("EmptyMandatoryParameter", _, _, Some("P1"), NodeValidationErrorType.SaveAllowed))
          ) =>
    }
  }

  test(
    "validates scenario with fragment parameters - P1 and P2 as mandatory params with missing actual values accumulated"
  ) {
    val fragmentId = "fragment1"

    val configWithValidators: Config = defaultConfig
      .withValue(
        s"componentsUiConfig.$fragmentId.params.P1.validators",
        fromIterable(List(Map("type" -> "MandatoryParameterValidator").asJava).asJava)
      )
      .withValue(
        s"componentsUiConfig.$fragmentId.params.P2.validators",
        fromIterable(List(Map("type" -> "MandatoryParameterValidator").asJava).asJava)
      )

    val fragmentDefinition: CanonicalProcess = createFragmentDefinition(
      fragmentId,
      List(
        FragmentParameter("P1", FragmentClazzRef[Short]),
        FragmentParameter("P2", FragmentClazzRef[String])
      )
    )

    val processWithFragment = createProcessWithFragmentParams(
      fragmentId,
      List(
        evaluatedparam.Parameter("P1", ""),
        evaluatedparam.Parameter("P2", "")
      )
    )

    val processValidation = mockedProcessValidation(fragmentDefinition, configWithValidators)
    val result            = processValidation.validate(processWithFragment)

    result.hasErrors shouldBe true
    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("subIn") should matchPattern {
      case Some(
            List(
              NodeValidationError("EmptyMandatoryParameter", _, _, Some("P1"), NodeValidationErrorType.SaveAllowed),
              NodeValidationError("EmptyMandatoryParameter", _, _, Some("P2"), NodeValidationErrorType.SaveAllowed)
            )
          ) =>
    }
  }

  test("validates with custom validator") {
    val process = ScenarioBuilder
      .streaming(SampleCustomProcessValidator.badName)
      .source("start", existingSourceFactory)
      .emptySink("sink", existingSinkFactory)

    val displayable = ProcessConverter.toDisplayable(process, TestProcessingTypes.Streaming, Category1)
    val result      = mockedProcessValidation(process).validate(displayable)

    result.errors.processPropertiesErrors shouldBe List(
      PrettyValidationErrors.formatErrorMessage(SampleCustomProcessValidator.badNameError)
    )
  }

  test("check for invalid characters") {
    val process = createProcess(
      List(
        Source("in\"'.", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("in\"'.", "out", None))
    )
    val result = configuredValidator.validate(process)

    result.errors.invalidNodes shouldBe Map(
      "in\"'." -> List(
        NodeValidationError(
          "InvalidCharacters",
          "Node in\"'. contains invalid characters: \", . and ' are not allowed in node id",
          "Node in\"'. contains invalid characters: \", . and ' are not allowed in node id",
          Some("$id"),
          RenderNotAllowed
        )
      )
    )
  }

  test("should validate scenario id") {
    forAll(IdValidationTestData.scenarioIdErrorCases) {
      (scenarioId: String, expectedErrors: List[ProcessCompilationError]) =>
        {
          val testedScenario = ProcessValidationSpec.validFlinkProcess.copy(id = scenarioId)
          val result = TestFactory.flinkProcessValidation.validate(testedScenario).errors.processPropertiesErrors
          result shouldBe expectedErrors.map(a => PrettyValidationErrors.formatErrorMessage(a))
        }
    }
  }

  test("should validate node ids") {
    forAll(IdValidationTestData.nodeIdErrorCases) { (nodeId: String, expectedErrors: List[NodeIdError]) =>
      {
        val testedScenario = createProcess(
          List(
            Source(nodeId, SourceRef(existingSourceFactory, List())),
            Sink("out", SinkRef(existingSinkFactory, List()))
          ),
          List(Edge(nodeId, "out", None))
        )
        val result = TestFactory.flinkProcessValidation.validate(testedScenario).errors.invalidNodes
        val nodeErrors = expectedErrors
          .flatMap(n => n.nodeIds.map(id => id -> PrettyValidationErrors.formatErrorMessage(n)))
          .groupMap(_._1)(_._2)
        result shouldBe nodeErrors
      }
    }
  }

}

private object ProcessValidationSpec {

  import ProcessTestData._
  import TestCategories._

  val sourceTypeName: String = "processSource"
  val sinkTypeName: String   = "processSink"

  val defaultConfig: Config = List("genericParametersSource", "genericParametersSink", "genericTransformer")
    .foldLeft(ConfigFactory.empty())((c, n) =>
      c.withValue(s"componentsUiConfig.$n.params.par1.defaultValue", fromAnyRef("'realDefault'"))
    )

  val configuredValidator: ProcessValidation = TestFactory.processValidation.withScenarioPropertiesConfig(
    mapProcessingTypeDataProvider(
      TestProcessingTypes.Streaming -> (Map(
        "requiredStringProperty" -> ScenarioPropertyConfig(
          None,
          Some(StringParameterEditor),
          Some(List(MandatoryParameterValidator)),
          Some("label")
        ),
        "numberOfThreads" -> ScenarioPropertyConfig(
          None,
          Some(FixedValuesParameterEditor(possibleValues)),
          Some(List(FixedValuesValidator(possibleValues))),
          None
        ),
        "maxEvents" -> ScenarioPropertyConfig(
          None,
          None,
          Some(List(LiteralParameterValidator.integerValidator)),
          Some("label")
        )
      ) ++ FlinkStreamingPropertiesConfig.properties)
    )
  )

  val validFlinkProcess: DisplayableProcess = createProcess(
    List(
      Source("in", SourceRef(existingSourceFactory, List())),
      Sink("out", SinkRef(existingSinkFactory, List()))
    ),
    List(Edge("in", "out", None))
  )

  val validFlinkFragment: DisplayableProcess = DisplayableProcess(
    "test",
    ProcessProperties.combineTypeSpecificProperties(
      StreamMetaData(),
      additionalFields = ProcessAdditionalFields(None, FragmentSpecificData().toMap, FragmentSpecificData.typeName)
    ),
    nodes = List(
      FragmentInputDefinition("in", List()),
      FragmentOutputDefinition("out", "outputName")
    ),
    edges = List(Edge("in", "out", None)),
    processingType = TestProcessingTypes.Streaming,
    category = Category1
  )

  def validProcessWithFields(fields: Map[String, String]): DisplayableProcess = {
    createProcess(
      List(
        Source("in", SourceRef(existingSourceFactory, List())),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("in", "out", None)),
      additionalFields = fields
    )
  }

  private def createProcessWithParams(
      nodeParams: List[evaluatedparam.Parameter],
      scenarioProperties: Map[String, String],
      category: String = Category1
  ): DisplayableProcess = {
    createProcess(
      List(
        Source("inID", SourceRef(existingSourceFactory, List())),
        Enricher("custom", ServiceRef(otherExistingServiceId4, nodeParams), "out"),
        Sink("out", SinkRef(existingSinkFactory, List()))
      ),
      List(Edge("inID", "custom", None), Edge("custom", "out", None)),
      TestProcessingTypes.Streaming,
      category,
      scenarioProperties
    )
  }

  private def createProcessWithFragmentParams(
      fragmentDefinitionId: String,
      nodeParams: List[evaluatedparam.Parameter]
  ): DisplayableProcess = {
    createProcess(
      nodes = List(
        Source("source", SourceRef(sourceTypeName, Nil)),
        FragmentInput("subIn", FragmentRef(fragmentDefinitionId, nodeParams), isDisabled = Some(false)),
        Sink("sink", SinkRef(sinkTypeName, Nil))
      ),
      edges = List(
        Edge("source", "subIn", None),
        Edge("subIn", "sink", Some(EdgeType.FragmentOutput("out1")))
      )
    )
  }

  private def createProcess(
      nodes: List[NodeData],
      edges: List[Edge],
      `type`: ProcessingType = TestProcessingTypes.Streaming,
      category: String = Category1,
      additionalFields: Map[String, String] = Map()
  ): DisplayableProcess = {
    DisplayableProcess(
      "test",
      ProcessProperties.combineTypeSpecificProperties(
        StreamMetaData(),
        additionalFields = ProcessAdditionalFields(None, additionalFields, StreamMetaData.typeName)
      ),
      nodes,
      edges,
      `type`,
      category
    )
  }

  private def createFragmentDefinition(
      fragmentDefinitionId: String,
      fragmentInputParams: List[FragmentParameter]
  ): CanonicalProcess = {
    CanonicalProcess(
      MetaData(fragmentDefinitionId, FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition("in", fragmentInputParams)),
        FlatNode(FragmentOutputDefinition("out", "out1", List(Field("strField", Expression("spel", "'value'"))))),
      ),
      additionalBranches = List.empty
    )
  }

  def mockedProcessValidation(
      fragment: CanonicalProcess,
      execConfig: Config = ConfigFactory.empty()
  ): ProcessValidation = {
    import ProcessDefinitionBuilder._

    val processDefinition = ProcessDefinitionBuilder.empty
      .withSourceFactory(sourceTypeName)
      .withSinkFactory(sinkTypeName)

    val processValidationWithConfig: ProcessValidation = ProcessValidation(
      mapProcessingTypeDataProvider(
        TestProcessingTypes.Streaming -> new StubModelDataWithProcessDefinition(processDefinition, execConfig)
      ),
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> FlinkStreamingPropertiesConfig.properties),
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> List(SampleCustomProcessValidator)),
      new FragmentResolver(
        new StubFragmentRepository(
          Set(
            FragmentDetails(fragment, Category1),
          )
        )
      )
    )
    processValidationWithConfig
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
