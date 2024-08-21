package pl.touk.nussknacker.ui.validation

import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable, fromMap}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.{BeMatcher, MatchResult}
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterName,
  ParameterValueCompileTimeValidation,
  ValueInputWithDictEditor,
  ValueInputWithFixedValuesProvided
}
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.build.GraphBuilder.fragmentOutput
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, SplitNode}
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.component.parameter.validator.ValidationExpressionParameterValidator
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.{NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.testing.{LocalModelData, ModelDefinitionBuilder}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.service.EagerServiceWithStaticParametersAndReturnType
import pl.touk.nussknacker.engine.CustomProcessValidator
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationErrorType.{
  RenderNotAllowed,
  SaveAllowed,
  SaveNotAllowed
}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  UIGlobalError,
  ValidationErrors,
  ValidationResult,
  ValidationWarnings
}
import pl.touk.nussknacker.restmodel.validation.{PrettyValidationErrors, ValidationResults}
import pl.touk.nussknacker.test.config.ConfigWithScalaVersion
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.{
  StubFragmentRepository,
  StubModelDataWithModelDefinition,
  TestAdditionalUIConfigProvider
}
import pl.touk.nussknacker.test.utils.domain.ProcessTestData._
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.definition.ScenarioPropertiesConfigFinalizer
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class UIProcessValidatorSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks with OptionValues {

  import UIProcessValidatorSpec._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val validationExpression =
    Expression.spel(s"#${ValidationExpressionParameterValidator.variableName}.length() < 7")

  private val validationExpressionForRecord =
    Expression.spel(
      s"{'valid','otherValid'}.contains(#${ValidationExpressionParameterValidator.variableName}.get('field'))"
    )

  private val validationExpressionForList = Expression.spel(s"#value.size() == 2 && #value[0] == 'foo'")

  private val validationExpressionForLocalDateTime =
    Expression.spel(
      s"""#${ValidationExpressionParameterValidator.variableName}.dayOfWeek.name == 'FRIDAY'"""
    )

  test("check for not unique edge types") {
    val process = createGraph(
      List(
        Source("in", SourceRef(ProcessTestData.existingSourceFactory, List())),
        FragmentInput("subIn", FragmentRef("fragment1", List())),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List())),
        Sink("out2", SinkRef(ProcessTestData.existingSinkFactory, List())),
        Sink("out3", SinkRef(ProcessTestData.existingSinkFactory, List()))
      ),
      List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.FragmentOutput("out1"))),
        Edge("subIn", "out2", Some(EdgeType.FragmentOutput("out2"))),
        Edge("subIn", "out3", Some(EdgeType.FragmentOutput("out2")))
      )
    )

    val result = validateWithConfiguredProperties(process)

    result.errors.invalidNodes shouldBe Map(
      "subIn" -> List(
        NodeValidationError(
          "NonUniqueEdgeType",
          "Edges are not unique",
          "Node subIn has duplicate outgoing edges of type: FragmentOutput(out2), it cannot be saved properly",
          None,
          NodeValidationErrorType.SaveNotAllowed,
          None
        )
      )
    )
  }

  test("switch edges do not have to be unique") {
    val process = createGraph(
      List(
        Source("in", SourceRef(ProcessTestData.existingSourceFactory, List())),
        Switch("switch"),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List())),
        Sink("out2", SinkRef(ProcessTestData.existingSinkFactory, List())),
      ),
      List(
        Edge("in", "switch", None),
        Edge("switch", "out", Some(EdgeType.NextSwitch("true".spel))),
        Edge("switch", "out2", Some(EdgeType.NextSwitch("true".spel))),
      )
    )

    val result = validateWithConfiguredProperties(process)
    result.errors.invalidNodes shouldBe Symbol("empty")
  }

  test("check for not unique edges") {
    val process = createGraph(
      List(
        Source("in", SourceRef(ProcessTestData.existingSourceFactory, List())),
        FragmentInput("subIn", FragmentRef("fragment1", List())),
        Sink("out2", SinkRef(ProcessTestData.existingSinkFactory, List())),
      ),
      List(
        Edge("in", "subIn", None),
        Edge("subIn", "out2", Some(EdgeType.FragmentOutput("out1"))),
        Edge("subIn", "out2", Some(EdgeType.FragmentOutput("out2"))),
      )
    )

    val result = validateWithConfiguredProperties(process)

    result.errors.invalidNodes shouldBe Map(
      "subIn" -> List(
        NodeValidationError(
          typ = "NonUniqueEdge",
          message = "Edges are not unique",
          description = "Node subIn has duplicate outgoing edges to: out2, it cannot be saved properly",
          fieldName = None,
          errorType = SaveNotAllowed,
          details = None
        )
      )
    )
  }

  test("check for loose nodes") {
    val process = createGraph(
      List(
        Source("in", SourceRef(ProcessTestData.existingSourceFactory, List())),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List())),
        Filter("loose", Expression.spel("true"))
      ),
      List(Edge("in", "out", None))
    )
    val result = validateWithConfiguredProperties(process)

    result.errors.globalErrors shouldBe List(
      UIGlobalError(
        NodeValidationError(
          typ = "LooseNode",
          message = "Loose node",
          description = "Node loose is not connected to source, it cannot be saved properly",
          fieldName = None,
          errorType = SaveNotAllowed,
          details = None
        ),
        List("loose")
      )
    )
  }

  test("filter with only 'false' edge") {
    val process = createGraph(
      List(
        Source("in", SourceRef(ProcessTestData.existingSourceFactory, List())),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List())),
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
    val result = validateWithConfiguredProperties(process)

    result.hasErrors shouldBe false
  }

  test("check for disabled nodes") {
    val process = createGraph(
      List(
        Source("in", SourceRef(ProcessTestData.existingSourceFactory, List())),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List())),
        Filter("filter", Expression.spel("true"), isDisabled = Some(true))
      ),
      List(
        Edge("in", "filter", None),
        Edge("filter", "out", Some(EdgeType.FilterTrue)),
      )
    )
    val result = validateWithConfiguredProperties(process)

    result.warnings.invalidNodes shouldBe Map(
      "filter" -> List(
        NodeValidationError(
          typ = "DisabledNode",
          message = "Node filter is disabled",
          description = "Deploying scenario with disabled node can have unexpected consequences",
          fieldName = None,
          errorType = SaveAllowed,
          details = None
        )
      )
    )
  }

  test("check for duplicated ids") {
    val process = createGraph(
      List(
        Source("inID", SourceRef(ProcessTestData.existingSourceFactory, List())),
        Filter("inID", Expression.spel("''")),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List()))
      ),
      List(Edge("inID", "inID", None), Edge("inID", "out", None))
    )
    val result = validateWithConfiguredProperties(process)

    result.errors.globalErrors shouldBe List(
      UIGlobalError(
        NodeValidationError(
          typ = "DuplicatedNodeIds",
          message = "Two nodes cannot have same id",
          description = "Duplicate node ids: inID",
          fieldName = None,
          errorType = RenderNotAllowed,
          details = None
        ),
        List("inID")
      )
    )
  }

  test("check for duplicated ids when duplicated id is switch id") {
    val process = createGraph(
      List(
        Source("in", SourceRef(ProcessTestData.existingSourceFactory, List())),
        Switch("switchID"),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List())),
        Sink("switchID", SinkRef(ProcessTestData.existingSinkFactory, List()))
      ),
      List(
        Edge("in", "switchID", None),
        Edge("switchID", "out", Some(SwitchDefault)),
        Edge("switchID", "switch", Some(NextSwitch(Expression.spel("''"))))
      )
    )

    val result = validateWithConfiguredProperties(process)

    result.errors.globalErrors shouldBe List(
      UIGlobalError(
        NodeValidationError(
          typ = "DuplicatedNodeIds",
          message = "Two nodes cannot have same id",
          description = "Duplicate node ids: switchID",
          fieldName = None,
          errorType = RenderNotAllowed,
          details = None
        ),
        List("switchID")
      )
    )
    result.errors.invalidNodes shouldBe empty
    result.warnings shouldBe ValidationWarnings.success
  }

  test("validate missing required scenario properties") {
    val processValidator = TestFactory.processValidator.withScenarioPropertiesConfig(
      Map(
        "field1" -> ScenarioPropertyConfig(
          defaultValue = None,
          editor = None,
          validators = Some(List(MandatoryParameterValidator)),
          label = Some("label1"),
          hintText = None
        ),
        "field2" -> ScenarioPropertyConfig(
          defaultValue = None,
          editor = None,
          validators = None,
          label = Some("label2"),
          hintText = None
        )
      ) ++ FlinkStreamingPropertiesConfig.properties
    )

    def validate(scenarioGraph: ScenarioGraph) =
      processValidator.validate(scenarioGraph, ProcessTestData.sampleProcessName, isFragment = false)

    validate(
      validScenarioGraphWithFields(Map("field1" -> "a", "field2" -> "b"))
    ) shouldBe withoutErrorsAndWarnings

    validate(validScenarioGraphWithFields(Map("field1" -> "a"))) shouldBe withoutErrorsAndWarnings

    validate(
      validScenarioGraphWithFields(Map("field1" -> "", "field2" -> "b"))
    ).errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "EmptyMandatoryParameter",
              _,
              _,
              Some("field1"),
              ValidationResults.NodeValidationErrorType.SaveAllowed,
              _
            )
          ) =>
    }
    validate(validScenarioGraphWithFields(Map("field2" -> "b"))).errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "MissingRequiredProperty",
              _,
              _,
              Some("field1"),
              ValidationResults.NodeValidationErrorType.SaveAllowed,
              _
            )
          ) =>
    }
  }

  test("validate scenario properties according to validators from additional config provider") {
    val processValidator =
      TestFactory.processValidator.withScenarioPropertiesConfig(FlinkStreamingPropertiesConfig.properties)

    def validate(scenarioGraph: ScenarioGraph) =
      processValidator.validate(scenarioGraph, ProcessTestData.sampleProcessName, isFragment = false)

    validate(
      validScenarioGraphWithFields(
        Map(
          TestAdditionalUIConfigProvider.scenarioPropertyName -> TestAdditionalUIConfigProvider.scenarioPropertyPossibleValues.head.expression
        )
      )
    ) shouldBe withoutErrorsAndWarnings

    validate(
      validScenarioGraphWithFields(
        Map(TestAdditionalUIConfigProvider.scenarioPropertyName -> "some value not in possible values")
      )
    ).errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "InvalidPropertyFixedValue",
              _,
              _,
              Some(TestAdditionalUIConfigProvider.scenarioPropertyName),
              ValidationResults.NodeValidationErrorType.SaveAllowed,
              _
            )
          ) =>
    }
  }

  test("don't validate properties on fragment") {
    val processValidator = TestFactory.processValidator.withScenarioPropertiesConfig(
      Map(
        "field1" -> ScenarioPropertyConfig(
          defaultValue = None,
          editor = None,
          validators = Some(List(MandatoryParameterValidator)),
          label = Some("label1"),
          hintText = None
        ),
        "field2" -> ScenarioPropertyConfig(
          defaultValue = None,
          editor = None,
          validators = Some(List(MandatoryParameterValidator)),
          label = Some("label2"),
          hintText = None
        )
      ) ++ FlinkStreamingPropertiesConfig.properties
    )

    processValidator.validate(
      validScenarioGraphWithFields(Map.empty),
      ProcessTestData.sampleFragmentName,
      isFragment = true
    ) shouldBe withoutErrorsAndWarnings

  }

  test("validate type scenario field") {
    val possibleValues = List(FixedExpressionValue("true", "true"), FixedExpressionValue("false", "false"))
    val processValidator = TestFactory.processValidator.withScenarioPropertiesConfig(
      Map(
        "field1" -> ScenarioPropertyConfig(
          defaultValue = None,
          editor = Some(FixedValuesParameterEditor(possibleValues)),
          validators = Some(List(FixedValuesValidator(possibleValues))),
          label = Some("label"),
          hintText = None
        ),
        "field2" -> ScenarioPropertyConfig(
          defaultValue = None,
          editor = None,
          validators = Some(List(LiteralIntegerValidator)),
          label = Some("label"),
          hintText = None
        )
      ) ++ FlinkStreamingPropertiesConfig.properties
    )
    def validate(scenarioGraph: ScenarioGraph) =
      processValidator.validate(scenarioGraph, ProcessTestData.sampleProcessName, isFragment = false)

    validate(validScenarioGraphWithFields(Map("field1" -> "true"))) shouldBe withoutErrorsAndWarnings
    validate(validScenarioGraphWithFields(Map("field1" -> "false"))) shouldBe withoutErrorsAndWarnings
    validate(validScenarioGraphWithFields(Map("field1" -> "1"))) should not be withoutErrorsAndWarnings

    validate(validScenarioGraphWithFields(Map("field2" -> "1"))) shouldBe withoutErrorsAndWarnings
    validate(validScenarioGraphWithFields(Map("field2" -> "1.1"))) should not be withoutErrorsAndWarnings
    validate(validScenarioGraphWithFields(Map("field2" -> "true"))) should not be withoutErrorsAndWarnings
  }

  test("handle unknown properties validation") {
    val processValidator = TestFactory.processValidator.withScenarioPropertiesConfig(
      Map(
        "field2" -> ScenarioPropertyConfig(
          defaultValue = None,
          editor = None,
          validators = Some(List(CompileTimeEvaluableValueValidator)),
          label = Some("label"),
          hintText = None
        )
      ) ++ FlinkStreamingPropertiesConfig.properties
    )

    val result =
      processValidator.validate(
        validScenarioGraphWithFields(Map("field1" -> "true")),
        ProcessTestData.sampleProcessName,
        isFragment = false
      )

    result.errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError("UnknownProperty", _, _, Some("field1"), NodeValidationErrorType.SaveAllowed, None)
          ) =>
    }
  }

  test("not allows save with incorrect characters in ids") {
    def process(nodeId: String) = createGraph(
      List(Source(nodeId, SourceRef(ProcessTestData.existingSourceFactory, List()))),
      List()
    )

    validateWithConfiguredProperties(process("a\"s")).saveAllowed shouldBe false
    validateWithConfiguredProperties(process("a's")).saveAllowed shouldBe false
    validateWithConfiguredProperties(process("a.s")).saveAllowed shouldBe false
    validateWithConfiguredProperties(process("as")).saveAllowed shouldBe true
  }

  test("fails validation if cannot resolve fragment parameter type while validating fragment") {
    val fragmentWithInvalidParam =
      CanonicalProcess(
        MetaData("fragment1", FragmentSpecificData()),
        List(
          FlatNode(
            FragmentInputDefinition(
              "in",
              List(
                FragmentParameter(
                  ParameterName("subParam1"),
                  FragmentClazzRef("thisTypeDoesntExist"),
                  initialValue = None,
                  hintText = None,
                  valueEditor = None,
                  valueCompileTimeValidation = None
                )
              )
            )
          ),
          FlatNode(
            FragmentOutputDefinition("out", "out1", List.empty)
          )
        ),
        List.empty
      )

    val fragmentGraph = CanonicalProcessConverter.toScenarioGraph(fragmentWithInvalidParam)

    val validationResult = validateWithConfiguredProperties(fragmentGraph)

    validationResult.errors should not be empty
    validationResult.errors.invalidNodes("in") should matchPattern {
      case List(
            NodeValidationError(
              "FragmentParamClassLoadError",
              "Invalid parameter type.",
              "Failed to load thisTypeDoesntExist",
              Some("$param.subParam1.$typ"),
              NodeValidationErrorType.SaveAllowed,
              None
            )
          ) =>
    }
  }

  test("validates fragment input definition while validating fragment - ValueInputWithFixedValuesProvided") {
    val fragmentWithInvalidParam =
      CanonicalProcess(
        MetaData("fragment1", FragmentSpecificData()),
        List(
          FlatNode(
            FragmentInputDefinition(
              "in",
              List(
                FragmentParameter(
                  ParameterName("subParam1"),
                  FragmentClazzRef[String],
                  initialValue = Some(FixedExpressionValue("'outsidePreset'", "outsidePreset")),
                  hintText = None,
                  valueEditor = Some(
                    ValueInputWithFixedValuesProvided(
                      fixedValuesList = List(FixedExpressionValue("'someValue'", "someValue")),
                      allowOtherValue = false
                    )
                  ),
                  valueCompileTimeValidation = None
                ),
                FragmentParameter(
                  ParameterName("subParam2"),
                  FragmentClazzRef[java.lang.Boolean],
                  initialValue = None,
                  hintText = None,
                  valueEditor = Some(
                    ValueInputWithFixedValuesProvided(
                      fixedValuesList = List(FixedExpressionValue("'someValue'", "someValue")),
                      allowOtherValue = false
                    )
                  ),
                  valueCompileTimeValidation = None
                ),
                FragmentParameter(
                  ParameterName("subParam3_valid"),
                  FragmentClazzRef[java.lang.String],
                  initialValue = Some(FixedExpressionValue("'someValue'", "someValue")),
                  hintText = None,
                  valueEditor = Some(
                    ValueInputWithFixedValuesProvided(
                      fixedValuesList = List(
                        FixedExpressionValue("'someValue'", "someValue"),
                        FixedExpressionValue("", ""),
                      ),
                      allowOtherValue = false
                    )
                  ),
                  valueCompileTimeValidation = None
                )
              )
            )
          ),
          FlatNode(
            FragmentOutputDefinition("out", "out1", List.empty)
          )
        ),
        List.empty
      )

    val fragmentGraph =
      CanonicalProcessConverter.toScenarioGraph(fragmentWithInvalidParam)

    val validationResult = validateWithConfiguredProperties(fragmentGraph)

    validationResult.errors should not be empty
    validationResult.errors.invalidNodes("in") should matchPattern {
      case List(
            NodeValidationError(
              "InitialValueNotPresentInPossibleValues",
              "The initial value provided for parameter 'subParam1' is not present in the parameter's possible values list",
              _,
              Some("$param.subParam1.$initialValue"),
              NodeValidationErrorType.SaveAllowed,
              None
            ),
            NodeValidationError(
              "ExpressionParserCompilationErrorInFragmentDefinition",
              "Failed to parse expression: Bad expression type, expected: Boolean, found: String(someValue)",
              "There is a problem with expression: 'someValue'",
              Some("$param.subParam2.$fixedValuesList"),
              NodeValidationErrorType.SaveAllowed,
              None
            )
          ) =>
    }
  }

  test("validates fragment input definition while validating fragment - ValueInputWithDictEditor") {
    val fragmentWithInvalidParam =
      CanonicalProcess(
        MetaData("fragment1", FragmentSpecificData()),
        List(
          FlatNode(
            FragmentInputDefinition(
              "in",
              List(
                FragmentParameter(
                  ParameterName("subParam1"),
                  FragmentClazzRef[java.lang.Boolean],
                  initialValue = None,
                  hintText = None,
                  valueEditor = Some(
                    ValueInputWithDictEditor(
                      dictId = "thisDictDoesntExist",
                      allowOtherValue = false
                    )
                  ),
                  valueCompileTimeValidation = None
                ),
                FragmentParameter(
                  ParameterName("subParam2"),
                  FragmentClazzRef[java.lang.Boolean],
                  initialValue = None,
                  hintText = None,
                  valueEditor = Some(
                    ValueInputWithDictEditor(
                      dictId = "someDictId",
                      allowOtherValue = false
                    )
                  ),
                  valueCompileTimeValidation = None
                ),
                FragmentParameter(
                  ParameterName("subParam3"),
                  FragmentClazzRef[java.lang.String],
                  initialValue = None,
                  hintText = None,
                  valueEditor = Some(
                    ValueInputWithDictEditor(
                      dictId = "",
                      allowOtherValue = false
                    )
                  ),
                  valueCompileTimeValidation = None
                ),
                FragmentParameter(
                  ParameterName("subParam4_valid"),
                  FragmentClazzRef[java.lang.String],
                  initialValue =
                    Some(FixedExpressionValue("""{"key":"some string key","label":"some label"}""", "some label")),
                  hintText = None,
                  valueEditor = Some(
                    ValueInputWithDictEditor(
                      dictId = "someDictId",
                      allowOtherValue = false
                    )
                  ),
                  valueCompileTimeValidation = None
                ),
                FragmentParameter(
                  ParameterName("subParam5_valid"),
                  FragmentClazzRef[java.lang.String],
                  initialValue = Some(FixedExpressionValue("", "")),
                  hintText = None,
                  valueEditor = Some(
                    ValueInputWithDictEditor(
                      dictId = "someDictId",
                      allowOtherValue = false
                    )
                  ),
                  valueCompileTimeValidation = None
                )
              )
            )
          ),
          FlatNode(
            FragmentOutputDefinition("out", "out1", List.empty)
          )
        ),
        List.empty
      )

    val fragmentGraph =
      CanonicalProcessConverter.toScenarioGraph(fragmentWithInvalidParam)

    val validationResult = processValidatorWithDicts(
      Map("someDictId" -> EmbeddedDictDefinition(Map.empty))
    ).validate(fragmentGraph, sampleProcessName, isFragment = true)

    validationResult.errors should not be empty
    validationResult.errors.invalidNodes("in") should matchPattern {
      case List(
            NodeValidationError(
              "DictNotDeclared",
              "Dictionary not declared: thisDictDoesntExist",
              _,
              Some("$param.subParam1.$dictId"),
              NodeValidationErrorType.SaveAllowed,
              None
            ),
            NodeValidationError(
              "DictIsOfInvalidType",
              _,
              "Values in dictionary 'someDictId' are of type 'String @ dictValue:someDictId' and cannot be treated as expected type: 'Boolean'",
              Some("$param.subParam2.$dictId"),
              NodeValidationErrorType.SaveAllowed,
              None
            ),
            NodeValidationError(
              "EmptyMandatoryField",
              "This field is mandatory and cannot be empty",
              _,
              Some("$param.subParam3.$dictId"),
              NodeValidationErrorType.SaveAllowed,
              None
            )
          ) =>
    }
  }

  test("validates fragment input definition expression validator while validating fragment") {
    val fragmentWithInvalidParam =
      CanonicalProcess(
        MetaData("sub1", FragmentSpecificData()),
        List(
          FlatNode(
            FragmentInputDefinition(
              "in",
              List(
                FragmentParameter(
                  ParameterName("subParam1"),
                  FragmentClazzRef[java.lang.String],
                  initialValue = None,
                  hintText = None,
                  valueEditor = None,
                  valueCompileTimeValidation =
                    Some(ParameterValueCompileTimeValidation(Expression.spel("'a' + 'b'"), Some("some failed message")))
                ),
                FragmentParameter(
                  ParameterName("subParam2"),
                  FragmentClazzRef[java.lang.String],
                  initialValue = None,
                  hintText = None,
                  valueEditor = None,
                  valueCompileTimeValidation = Some(
                    ParameterValueCompileTimeValidation(
                      s"#${ValidationExpressionParameterValidator.variableName} < 7".spel, // invalid operation (comparing string with int)
                      None
                    )
                  )
                )
              )
            )
          ),
          FlatNode(
            FragmentOutputDefinition("out", "out1", List.empty)
          )
        ),
        List.empty
      )

    val fragmentGraph =
      CanonicalProcessConverter.toScenarioGraph(fragmentWithInvalidParam)

    val validationResult = validateWithConfiguredProperties(fragmentGraph, isFragment = true)

    validationResult.errors should not be empty
    validationResult.errors.invalidNodes("in") should matchPattern {
      case List(
            NodeValidationError(
              "InvalidValidationExpression",
              "Invalid validation expression: Bad expression type, expected: Boolean, found: String(ab)",
              "There is a problem with validation expression: 'a' + 'b'",
              Some("$param.subParam1.$validationExpression"),
              NodeValidationErrorType.SaveAllowed,
              None
            ),
            NodeValidationError(
              "InvalidValidationExpression",
              "Invalid validation expression: Wrong part types",
              "There is a problem with validation expression: #value < 7",
              Some("$param.subParam2.$validationExpression"),
              NodeValidationErrorType.SaveAllowed,
              None
            )
          ) =>
    }
  }

  test("validates fragment input definition while validating process that uses fragment") {
    val invalidFragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      nodes = List(
        FlatNode(
          FragmentInputDefinition("in", List(FragmentParameter(ParameterName("param1"), FragmentClazzRef[Long])))
        ),
        FlatNode(Variable(id = "subVar", varName = "subVar", value = "#nonExistingVar".spel)),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      additionalBranches = List.empty
    )

    val process = createGraph(
      nodes = List(
        Source("in", SourceRef(sourceTypeName, List())),
        FragmentInput(
          "subIn",
          FragmentRef(invalidFragment.name.value, List(NodeParameter(ParameterName("param1"), "'someString'".spel))),
          isDisabled = Some(false)
        ),
        Sink("out", SinkRef(sinkTypeName, List()))
      ),
      edges = List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.FragmentOutput("output")))
      )
    )

    val processValidator = mockedProcessValidator(Some(invalidFragment))
    val validationResult = processValidator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    validationResult should matchPattern {
      case ValidationResult(ValidationErrors(invalidNodes, Nil, Nil), ValidationWarnings.success, _)
          if invalidNodes("subIn").size == 1 && invalidNodes("subIn-subVar").size == 1 =>
    }
  }

  test("validates FragmentInput parameters according to FragmentInputDefinition") {
    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      nodes = List(
        FlatNode(
          FragmentInputDefinition(
            "in",
            List(
              FragmentParameter(
                ParameterName("subParam1"),
                FragmentClazzRef[String],
                required = true,
                initialValue = None,
                hintText = None,
                valueEditor = Some(
                  ValueInputWithFixedValuesProvided(
                    fixedValuesList = List(FixedExpressionValue("'someValue'", "someValue")),
                    allowOtherValue = false
                  )
                ),
                valueCompileTimeValidation = None
              ),
            )
          )
        ),
        FlatNode(FragmentOutputDefinition("subOut1", "subOut1", List(Field("foo", "42L".spel))))
      ),
      additionalBranches = List.empty
    )

    val process = createGraph(
      nodes = List(
        Source("source", SourceRef(sourceTypeName, Nil)),
        FragmentInput(
          "subIn1",
          FragmentRef(
            fragment.name.value,
            List(
              NodeParameter(ParameterName("subParam1"), "'outsideAllowedValues'".spel),
            )
          ),
          isDisabled = Some(false)
        ),
        FragmentInput(
          "subIn2",
          FragmentRef(
            fragment.name.value,
            List(
              NodeParameter(ParameterName("subParam1"), "".spel),
            )
          ),
          isDisabled = Some(false)
        ),
        Sink("sink1", SinkRef(sinkTypeName, Nil)),
      ),
      edges = List(
        Edge("source", "subIn1", None),
        Edge("subIn1", "subIn2", Some(EdgeType.FragmentOutput("subOut1"))),
        Edge("subIn2", "sink1", Some(EdgeType.FragmentOutput("subOut1")))
      )
    )

    val processValidation = mockedProcessValidator(Some(fragment))
    val validationResult  = processValidation.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    validationResult.errors should not be empty
    validationResult.errors.invalidNodes("subIn1") should matchPattern {
      case List(
            NodeValidationError(
              "InvalidPropertyFixedValue",
              _,
              _,
              Some("subParam1"),
              NodeValidationErrorType.SaveAllowed,
              None
            )
          ) =>
    }
    validationResult.errors.invalidNodes("subIn2") should matchPattern {
      case List(
            NodeValidationError(
              "EmptyMandatoryParameter",
              _,
              _,
              Some("subParam1"),
              NodeValidationErrorType.SaveAllowed,
              None
            )
          ) =>
    }
  }

  test("validates disabled fragment with parameters") {
    val invalidFragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      nodes = List(
        FlatNode(
          FragmentInputDefinition("fragment1", List(FragmentParameter(ParameterName("param1"), FragmentClazzRef[Long])))
        ),
        FlatNode(Variable(id = "subVar", varName = "subVar", value = "#nonExistingVar".spel)),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      additionalBranches = List.empty
    )

    val process = createGraph(
      nodes = List(
        Source("in", SourceRef(sourceTypeName, List())),
        FragmentInput(
          "subIn",
          FragmentRef(invalidFragment.name.value, List(NodeParameter(ParameterName("param1"), "'someString'".spel))),
          isDisabled = Some(true)
        ),
        Sink("out", SinkRef(sinkTypeName, List()))
      ),
      edges = List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.FragmentOutput("output")))
      )
    )

    val processValidator = mockedProcessValidator(Some(invalidFragment))

    val validationResult = processValidator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)
    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.errors.globalErrors shouldBe Symbol("empty")
    validationResult.saveAllowed shouldBe true
  }

  test("validates and returns type info of fragment output fields") {
    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      nodes = List(
        FlatNode(
          FragmentInputDefinition("in", List(FragmentParameter(ParameterName("subParam1"), FragmentClazzRef[String])))
        ),
        SplitNode(
          Split("split"),
          List(
            List(FlatNode(FragmentOutputDefinition("subOut1", "subOut1", List(Field("foo", "42L".spel))))),
            List(FlatNode(FragmentOutputDefinition("subOut2", "subOut2", List(Field("bar", "'42'".spel)))))
          )
        )
      ),
      additionalBranches = List.empty
    )

    val process = createGraph(
      nodes = List(
        Source("source", SourceRef(sourceTypeName, Nil)),
        FragmentInput(
          "subIn",
          FragmentRef(fragment.name.value, List(NodeParameter(ParameterName("subParam1"), "'someString'".spel))),
          isDisabled = Some(false)
        ),
        Variable(id = "var1", varName = "var1", value = "#subOut1.foo".spel),
        Variable(id = "var2", varName = "var2", value = "#subOut2.bar".spel),
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

    val processValidator = mockedProcessValidator(Some(fragment))
    val validationResult = processValidator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.nodeResults("sink2").variableTypes("input") shouldBe typing.Unknown
    validationResult.nodeResults("sink2").variableTypes("var2") shouldBe Typed.fromInstance("42")
    validationResult.nodeResults("sink2").variableTypes("subOut2") shouldBe Typed.record(
      Map(
        "bar" -> Typed.fromInstance("42")
      )
    )
  }

  test("check for no expression found in mandatory parameter") {
    val process = createGraph(
      List(
        Source("inID", SourceRef(ProcessTestData.existingSourceFactory, List())),
        Enricher(
          "custom",
          ServiceRef("fooService3", List(NodeParameter(ParameterName("expression"), Expression.spel("")))),
          "out"
        ),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List()))
      ),
      List(Edge("inID", "custom", None), Edge("custom", "out", None))
    )

    val result = validateWithConfiguredProperties(process)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "EmptyMandatoryParameter",
                _,
                _,
                Some("expression"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test(
    "validate service parameter (dynamic component) based on additional config from provider - MandatoryParameterValidator"
  ) {
    val process = processWithEagerServiceWithDynamicComponent("")

    val validator = validatorWithComponentsAndConfig(
      List(ComponentDefinition("eagerServiceWithDynamicComponent", EagerServiceWithDynamicComponent)),
      Map(
        DesignerWideComponentId("streaming-service-eagerServiceWithDynamicComponent") -> ComponentAdditionalConfig(
          parameterConfigs = Map(
            ParameterName("param") -> ParameterAdditionalUIConfig(required = true, None, None, None, None)
          )
        )
      )
    )

    val result = validator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "EmptyMandatoryParameter",
                _,
                _,
                Some("param"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test(
    "validate service parameter (static component) based on additional config from provider - MandatoryParameterValidator"
  ) {
    val process = processWithOptionalParameterService("")

    val validator = validatorWithComponentsAndConfig(
      List(ComponentDefinition("optionalParameterService", OptionalParameterService)),
      Map(
        DesignerWideComponentId("streaming-service-optionalParameterService") -> ComponentAdditionalConfig(
          parameterConfigs = Map(
            ParameterName("optionalParam") -> ParameterAdditionalUIConfig(required = true, None, None, None, None)
          )
        )
      )
    )

    val result = validator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "EmptyMandatoryParameter",
                _,
                _,
                Some("optionalParam"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test(
    "validate service parameter (static component) based on additional config from provider - ValidationExpressionParameterValidator"
  ) {
    val process = processWithOptionalParameterService("'Barabasz'")

    val validator = validatorWithComponentsAndConfig(
      List(ComponentDefinition("optionalParameterService", OptionalParameterService)),
      Map(
        DesignerWideComponentId("streaming-service-optionalParameterService") -> ComponentAdditionalConfig(
          parameterConfigs = Map(
            ParameterName("optionalParam") -> paramConfigWithValidationExpression(validationExpression)
          )
        )
      )
    )

    val result = validator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "CustomParameterValidationError",
                "some custom failure message",
                "Please provide value that satisfies the validation expression '#value.length() < 7'",
                Some("optionalParam"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test(
    "validate service parameter (dynamic component) based on additional config from provider - ValidationExpressionParameterValidator"
  ) {
    val process = processWithEagerServiceWithDynamicComponent("'Barabasz'")

    val validator = validatorWithComponentsAndConfig(
      List(ComponentDefinition("eagerServiceWithDynamicComponent", EagerServiceWithDynamicComponent)),
      Map(
        DesignerWideComponentId("streaming-service-eagerServiceWithDynamicComponent") -> ComponentAdditionalConfig(
          parameterConfigs = Map(
            ParameterName("param") -> paramConfigWithValidationExpression(validationExpression)
          )
        )
      )
    )

    val result = validator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "CustomParameterValidationError",
                "some custom failure message",
                "Please provide value that satisfies the validation expression '#value.length() < 7'",
                Some("param"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test(
    "validate Map service parameter based on additional config from provider - ValidationExpressionParameterValidator"
  ) {
    val process = processWithService(
      MapParameterService.serviceId,
      List(
        NodeParameter(
          ParameterName("mapParam1"),
          "{'field': 'valid'}".spel
        ),
        NodeParameter(
          ParameterName("mapParam2"),
          "{'field': 'invalid'}".spel
        ),
      )
    )

    val validator = validatorWithComponentsAndConfig(
      List(ComponentDefinition(MapParameterService.serviceId, MapParameterService)),
      Map(
        DesignerWideComponentId(s"streaming-service-${MapParameterService.serviceId}") -> ComponentAdditionalConfig(
          parameterConfigs = Map(
            ParameterName("mapParam1") -> paramConfigWithValidationExpression(validationExpressionForRecord),
            ParameterName("mapParam2") -> paramConfigWithValidationExpression(validationExpressionForRecord)
          )
        )
      )
    )

    val result = validator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "CustomParameterValidationError",
                "some custom failure message",
                "Please provide value that satisfies the validation expression '{'valid','otherValid'}.contains(#value.get('field'))'",
                Some("mapParam2"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test(
    "validate List service parameter based on additional config from provider - ValidationExpressionParameterValidator"
  ) {
    val process = processWithService(
      ListParameterService.serviceId,
      List(
        NodeParameter(
          ParameterName("listParam1"),
          "{'foo', 'bar'}".spel
        ),
        NodeParameter(
          ParameterName("listParam2"),
          "{'bar'}".spel
        ),
      )
    )

    val validator = validatorWithComponentsAndConfig(
      List(ComponentDefinition(ListParameterService.serviceId, ListParameterService)),
      Map(
        DesignerWideComponentId(s"streaming-service-${ListParameterService.serviceId}") -> ComponentAdditionalConfig(
          parameterConfigs = Map(
            ParameterName("listParam1") -> paramConfigWithValidationExpression(validationExpressionForList),
            ParameterName("listParam2") -> paramConfigWithValidationExpression(validationExpressionForList)
          )
        )
      )
    )

    val result = validator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "CustomParameterValidationError",
                "some custom failure message",
                "Please provide value that satisfies the validation expression '#value.size() == 2 && #value[0] == 'foo''",
                Some("listParam2"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test(
    "ValidationExpressionParameterValidator fails if expression value is not compile-time evaluable"
  ) {
    val process = processWithService(
      LocalDateTimeParameterService.serviceId,
      List(
        NodeParameter(
          ParameterName("localDateTimeParam"),
          Expression.spel("T(java.time.LocalDateTime).now")
        ),
      )
    )

    val validator = validatorWithComponentsAndConfig(
      List(ComponentDefinition(LocalDateTimeParameterService.serviceId, LocalDateTimeParameterService)),
      Map(
        DesignerWideComponentId(
          s"streaming-service-${LocalDateTimeParameterService.serviceId}"
        ) -> ComponentAdditionalConfig(
          parameterConfigs = Map(
            ParameterName("localDateTimeParam") -> paramConfigWithValidationExpression(
              validationExpressionForLocalDateTime
            ),
          )
        )
      )
    )

    val result = validator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "CompileTimeEvaluableParameterNotEvaluable",
                "This field's value has to be evaluable at deployment time",
                "Please provide a value that is evaluable at deployment time",
                Some("localDateTimeParam"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("validate service parameter based on input config - MandatoryParameterValidator") {
    val validator = new UIProcessValidator(
      processingType = "Streaming",
      validator = ProcessValidator.default(
        LocalModelData(
          ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved
            .getConfig("modelConfig")
            .withValue(
              "componentsUiConfig.optionalParameterService.params.optionalParam.validators",
              fromIterable(List(Map("type" -> "MandatoryParameterValidator").asJava).asJava)
            ),
          List(ComponentDefinition("optionalParameterService", OptionalParameterService)),
          additionalConfigsFromProvider = Map.empty
        )
      ),
      scenarioProperties = Map.empty,
      scenarioPropertiesConfigFinalizer =
        new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, Streaming.stringify),
      additionalValidators = List.empty,
      fragmentResolver = new FragmentResolver(new StubFragmentRepository(Map.empty))
    )

    val process = processWithOptionalParameterService("")

    val result = validator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "EmptyMandatoryParameter",
                _,
                _,
                Some("optionalParam"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("validate service parameter based on input config - ValidationExpressionParameterValidator") {
    val validator = new UIProcessValidator(
      processingType = "Streaming",
      validator = ProcessValidator.default(
        LocalModelData(
          ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved
            .getConfig("modelConfig")
            .withValue(
              "componentsUiConfig.optionalParameterService.params.optionalParam.validators",
              fromIterable(
                List(
                  Map(
                    "type" -> "ValidationExpressionParameterValidatorToCompile",
                    "validationExpression" -> fromMap(
                      Map(
                        "language"   -> "spel",
                        "expression" -> validationExpression.expression
                      ).asJava
                    ),
                    "validationFailedMessage" -> "some custom failure message",
                  ).asJava
                ).asJava
              )
            ),
          List(ComponentDefinition("optionalParameterService", OptionalParameterService)),
          additionalConfigsFromProvider = Map.empty
        )
      ),
      scenarioProperties = Map.empty,
      scenarioPropertiesConfigFinalizer =
        new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, Streaming.stringify),
      additionalValidators = List.empty,
      fragmentResolver = new FragmentResolver(new StubFragmentRepository(Map.empty))
    )

    val process = processWithOptionalParameterService("'Barabasz'")

    val result = validator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "CustomParameterValidationError",
                "some custom failure message",
                "Please provide value that satisfies the validation expression '#value.length() < 7'",
                Some("optionalParam"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("reports expression parsing error in DictParameterEditor") {
    val process = processWithDictParameterEditorService(
      Expression(
        Language.DictKeyWithLabel,
        "not parsable key with label expression"
      )
    )

    val result = processValidatorWithDicts(Map.empty).validate(process, sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "KeyWithLabelExpressionParsingError",
                "Error while parsing KeyWithLabel expression: not parsable key with label expression",
                _,
                Some("expression"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("checks for unknown dictId in DictParameterEditor") {
    val process = processWithDictParameterEditorService(Expression.dictKeyWithLabel("someKey", Some("someLabel")))

    val result = processValidatorWithDicts(Map.empty).validate(process, sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "DictNotDeclared",
                _,
                _,
                Some("expression"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("checks for unknown key in DictParameterEditor") {
    val process =
      processWithDictParameterEditorService(Expression.dictKeyWithLabel("thisKeyDoesntExist", Some("someLabel")))

    val result = processValidatorWithDicts(
      Map("someDictId" -> EmbeddedDictDefinition(Map.empty))
    ).validate(process, sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "DictEntryWithKeyNotExists",
                _,
                _,
                Some("expression"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("validate DictParameterEditor happy path") {
    val process = processWithDictParameterEditorService(Expression.dictKeyWithLabel("someKey", Some("someLabel")))

    val result = processValidatorWithDicts(
      Map("someDictId" -> EmbeddedDictDefinition(Map("someKey" -> "someLabel")))
    ).validate(process, sampleProcessName, isFragment = false)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes shouldBe Map.empty
  }

  test("check for wrong fixed expression value in node parameter") {
    val scenarioGraph: ScenarioGraph = createScenarioGraphWithParams(
      List(NodeParameter(ParameterName("expression"), Expression.spel("wrong fixed value"))),
      Map.empty
    )

    val result = validateWithConfiguredProperties(scenarioGraph)

    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("custom") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "ExpressionParserCompilationError",
                _,
                _,
                Some("expression"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("check for wrong fixed expression value in scenario property") {
    val process = createScenarioGraphWithParams(
      List.empty,
      Map(
        "numberOfThreads"        -> "wrong fixed value",
        "requiredStringProperty" -> "test"
      )
    )

    val result = validateWithConfiguredProperties(process)

    result.errors.globalErrors shouldBe empty
    result.errors.processPropertiesErrors should matchPattern {
      case List(
            NodeValidationError(
              "InvalidPropertyFixedValue",
              _,
              _,
              Some("numberOfThreads"),
              NodeValidationErrorType.SaveAllowed,
              None
            )
          ) =>
    }
    result.warnings shouldBe ValidationWarnings.success
  }

  test("validates scenario with fragment within other processingType") {
    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      nodes = List(
        FlatNode(
          FragmentInputDefinition("in", List(FragmentParameter(ParameterName("subParam1"), FragmentClazzRef[String])))
        ),
        FlatNode(FragmentOutputDefinition("subOut1", "out", List(Field("foo", "42L".spel))))
      ),
      additionalBranches = List.empty
    )

    val process = createGraph(
      nodes = List(
        Source("source", SourceRef(sourceTypeName, Nil)),
        FragmentInput(
          "subIn",
          FragmentRef(fragment.name.value, List(NodeParameter(ParameterName("subParam1"), "'someString'".spel))),
          isDisabled = Some(false)
        ),
        Sink("sink", SinkRef(sinkTypeName, Nil))
      ),
      edges = List(
        Edge("source", "subIn", None),
        Edge("subIn", "sink", Some(EdgeType.FragmentOutput("out")))
      )
    )

    val processValidator = mockedProcessValidator(Some(fragment))

    val validationResult = processValidator.validate(process, ProcessTestData.sampleProcessName, isFragment = false)
    validationResult.errors.invalidNodes shouldBe Symbol("empty")
    validationResult.errors.globalErrors shouldBe Symbol("empty")
    validationResult.saveAllowed shouldBe true

    val processValidatorWithFragmentInAnotherProcessingType =
      mockedProcessValidator(Map("Streaming2" -> fragment), ConfigFactory.empty())

    val validationResultWithCategory2 =
      processValidatorWithFragmentInAnotherProcessingType.validate(
        process,
        ProcessTestData.sampleProcessName,
        isFragment = false
      )
    validationResultWithCategory2.errors.invalidNodes shouldBe Map(
      "subIn" -> List(PrettyValidationErrors.formatErrorMessage(UnknownFragment(fragment.name.value, "subIn")))
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
      createFragmentDefinition(fragmentId, List(FragmentParameter(ParameterName("P1"), FragmentClazzRef[Short])))
    val processWithFragment =
      createScenarioGraphWithFragmentParams(fragmentId, List(NodeParameter(ParameterName("P1"), "123".spel)))

    val processValidator = mockedProcessValidator(Some(fragmentDefinition), configWithValidators)
    val result = processValidator.validate(processWithFragment, ProcessTestData.sampleProcessName, isFragment = false)
    result.hasErrors shouldBe false
    result.errors.invalidNodes shouldBe Symbol("empty")
    result.errors.globalErrors shouldBe Symbol("empty")
    result.saveAllowed shouldBe true
  }

  test("validates scenario with fragment parameters - P1 as mandatory param with with missing actual value") {
    val fragmentId = "fragment1"

    val fragmentDefinition: CanonicalProcess =
      createFragmentDefinition(
        fragmentId,
        List(FragmentParameter(ParameterName("P1"), FragmentClazzRef[Short]).copy(required = true))
      )
    val processWithFragment =
      createScenarioGraphWithFragmentParams(fragmentId, List(NodeParameter(ParameterName("P1"), "".spel)))

    val processValidator = mockedProcessValidator(Some(fragmentDefinition), defaultConfig)
    val result = processValidator.validate(processWithFragment, ProcessTestData.sampleProcessName, isFragment = false)

    result.hasErrors shouldBe true
    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("subIn") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "EmptyMandatoryParameter",
                _,
                _,
                Some("P1"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
  }

  test(
    "validates scenario with fragment parameters - P1 and P2 as mandatory params with missing actual values accumulated"
  ) {
    val fragmentId = "fragment1"

    val fragmentDefinition: CanonicalProcess = createFragmentDefinition(
      fragmentId,
      List(
        FragmentParameter(ParameterName("P1"), FragmentClazzRef[Short]).copy(required = true),
        FragmentParameter(ParameterName("P2"), FragmentClazzRef[String]).copy(required = true)
      )
    )

    val processWithFragment = createScenarioGraphWithFragmentParams(
      fragmentId,
      List(
        NodeParameter(ParameterName("P1"), "".spel),
        NodeParameter(ParameterName("P2"), "".spel)
      )
    )

    val processValidator = mockedProcessValidator(Some(fragmentDefinition), defaultConfig)
    val result = processValidator.validate(processWithFragment, ProcessTestData.sampleProcessName, isFragment = false)

    result.hasErrors shouldBe true
    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("subIn") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "EmptyMandatoryParameter",
                _,
                _,
                Some("P1"),
                NodeValidationErrorType.SaveAllowed,
                None
              ),
              NodeValidationError(
                "EmptyMandatoryParameter",
                _,
                _,
                Some("P2"),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
  }

  test("validates scenario with fragment parameters - with spel validation expression and valid value") {
    val fragmentId = "fragment1"
    val paramName  = "name"

    val fragmentDefinition: CanonicalProcess =
      createFragmentDefinition(
        fragmentId,
        List(
          FragmentParameter(
            ParameterName(paramName),
            FragmentClazzRef[java.lang.String],
            initialValue = None,
            hintText = None,
            valueEditor = None,
            valueCompileTimeValidation = Some(ParameterValueCompileTimeValidation(validationExpression, None))
          )
        )
      )
    val processWithFragment =
      createScenarioGraphWithFragmentParams(
        fragmentId,
        List(NodeParameter(ParameterName(paramName), "\"Tomasz\"".spel))
      )

    val processValidation = mockedProcessValidator(Some(fragmentDefinition), defaultConfig)
    val result = processValidation.validate(processWithFragment, ProcessTestData.sampleProcessName, isFragment = false)

    result.hasErrors shouldBe false
    result.errors.invalidNodes shouldBe Symbol("empty")
    result.errors.globalErrors shouldBe Symbol("empty")
    result.saveAllowed shouldBe true
  }

  test("validates scenario with fragment parameters - with spel validation expression and invalid value") {
    val fragmentId = "fragment1"
    val paramName  = "name"

    val configWithValidators: Config = defaultConfig

    val fragmentDefinition: CanonicalProcess =
      createFragmentDefinition(
        fragmentId,
        List(
          FragmentParameter(
            ParameterName(paramName),
            FragmentClazzRef[java.lang.String],
            initialValue = None,
            hintText = None,
            valueEditor = None,
            valueCompileTimeValidation = Some(
              ParameterValueCompileTimeValidation(validationExpression, Some("some failed message"))
            )
          )
        )
      )
    val processWithFragment =
      createScenarioGraphWithFragmentParams(
        fragmentId,
        List(NodeParameter(ParameterName(paramName), "\"Barabasz\"".spel))
      )

    val processValidation = mockedProcessValidator(Some(fragmentDefinition), configWithValidators)
    val result = processValidation.validate(processWithFragment, ProcessTestData.sampleProcessName, isFragment = false)
    result.hasErrors shouldBe true
    result.errors.globalErrors shouldBe empty
    result.errors.invalidNodes.get("subIn") should matchPattern {
      case Some(
            List(
              NodeValidationError(
                "CustomParameterValidationError",
                "some failed message",
                "Please provide value that satisfies the validation expression '#value.length() < 7'",
                Some(`paramName`),
                NodeValidationErrorType.SaveAllowed,
                None
              )
            )
          ) =>
    }
  }

  test("validates with custom validator") {
    val process = ScenarioBuilder
      .streaming("not-used-name")
      .source("start", ProcessTestData.existingSourceFactory)
      .emptySink("sink", ProcessTestData.existingSinkFactory)

    val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(process)
    val result =
      mockedProcessValidator(Some(process)).validate(
        scenarioGraph,
        SampleCustomProcessValidator.badName,
        isFragment = false
      )

    result.errors.processPropertiesErrors shouldBe List(
      PrettyValidationErrors.formatErrorMessage(SampleCustomProcessValidator.badNameError)
    )
  }

  test("should validate invalid scenario id") {
    val blankValue = ProcessName(" ")
    val result = TestFactory.flinkProcessValidator
      .validate(UIProcessValidatorSpec.validFlinkScenarioGraph, blankValue, isFragment = false)
      .errors
      .processPropertiesErrors
    result shouldBe List(
      PrettyValidationErrors.formatErrorMessage(ScenarioNameError(BlankId, blankValue, isFragment = false))
    )
  }

  test("should validate invalid node id") {
    val blankValue = " "
    val testedScenario = createGraph(
      List(
        Source(blankValue, SourceRef(ProcessTestData.existingSourceFactory, List())),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List()))
      ),
      List(Edge(blankValue, "out", None))
    )
    val result = TestFactory.flinkProcessValidator
      .validate(testedScenario, ProcessTestData.sampleProcessName, isFragment = false)
      .errors
      .invalidNodes
    val nodeErrors =
      Map(blankValue -> List(PrettyValidationErrors.formatErrorMessage(NodeIdValidationError(BlankId, blankValue))))
    result shouldBe nodeErrors
  }

  test("should validate scenario id with error preventing canonized form") {
    val incompleteScenarioWithBlankIds = createGraph(
      List(
        Variable(id = " ", varName = "var", value = "".spel)
      ),
      List.empty
    )
    val result =
      TestFactory.flinkProcessValidator.validate(incompleteScenarioWithBlankIds, ProcessName(" "), isFragment = false)
    inside(result) { case ValidationResult(errors, _, _) =>
      inside(errors) { case ValidationErrors(nodeErrors, propertiesErrors, _) =>
        nodeErrors should contain key " "
        nodeErrors(" ") should contain(
          PrettyValidationErrors.formatErrorMessage(NodeIdValidationError(BlankId, " "))
        )
        propertiesErrors shouldBe List(
          PrettyValidationErrors.formatErrorMessage(ScenarioNameError(BlankId, ProcessName(" "), isFragment = false))
        )
      }
    }
  }

  test("validates uniqueness of fragment output names when validating fragment") {
    val duplicatedOutputName = "output1"
    val fragment = ScenarioBuilder
      .fragment("frag1")
      .split(
        "splitId",
        fragmentOutput("outNode1", duplicatedOutputName),
        fragmentOutput("outNode2", duplicatedOutputName),
      )
    val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(fragment)
    val result        = TestFactory.flinkProcessValidator.validate(scenarioGraph, ProcessName(" "), isFragment = true)
    result.errors.globalErrors shouldBe List(
      UIGlobalError(
        PrettyValidationErrors.formatErrorMessage(
          DuplicateFragmentOutputNamesInFragment(`duplicatedOutputName`, Set("outNode1", "outNode2"))
        ),
        List("outNode1", "outNode2")
      )
    )
  }

  test("validates uniqueness of fragment output names when validating scenario") {
    val duplicatedOutputName = "output1"
    val fragment = ScenarioBuilder
      .fragment("fragment1")
      .split(
        "splitId",
        fragmentOutput("outNode1", duplicatedOutputName),
        fragmentOutput("outNode2", duplicatedOutputName),
      )
    val scenario = ScenarioBuilder
      .streaming("scenario1")
      .source("source", "source1")
      .fragmentOneOut("fragment", "fragment1", "output1", "outVar1")
      .emptySink("id1", "sink")

    val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(scenario)

    val processValidator = mockedProcessValidator(Some(fragment), defaultConfig)
    val result           = processValidator.validate(scenarioGraph, ProcessName(" "), isFragment = true)

    result.errors.invalidNodes shouldBe Map(
      "fragment" -> List(
        PrettyValidationErrors.formatErrorMessage(DuplicateFragmentOutputNamesInScenario("output1", "fragment"))
      )
    )
  }

  test("be able to convert process ending not properly") {
    forAll(
      Table(
        "unexpectedEnd",
        Filter("e", Expression.spel("0")),
        Switch("e"),
        Enricher("e", ServiceRef("ref", List()), "out"),
        Split("e")
      )
    ) { unexpectedEnd =>
      val scenarioGraph = createGraph(
        List(Source("s", SourceRef("sourceRef", List())), unexpectedEnd),
        List(Edge("s", "e", None)),
      )

      val result = validate(scenarioGraph)

      result.errors.globalErrors should matchPattern {
        case List(
              UIGlobalError(
                NodeValidationError("InvalidTailOfBranch", _, _, _, NodeValidationErrorType.SaveAllowed, None),
                List("e")
              )
            ) =>
      }
    }
  }

  test("return error with all invalid scenario ending node ids") {
    val invalidEndingNodeId1 = "endingSplit1"
    val invalidEndingNodeId2 = "endingSplit2"
    val scenario = ScenarioBuilder
      .streaming("scenario1")
      .source("source", "source1")
      .split("split", GraphBuilder.split(invalidEndingNodeId1), GraphBuilder.split(invalidEndingNodeId2))

    val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(scenario)
    val result        = processValidator.validate(scenarioGraph, ProcessName("name"), isFragment = false)

    inside(result.errors.globalErrors) { case UIGlobalError(error, nodeIds) :: Nil =>
      error shouldBe PrettyValidationErrors.formatErrorMessage(
        InvalidTailOfBranch(Set(invalidEndingNodeId1, invalidEndingNodeId2))
      )
      nodeIds should contain theSameElementsAs List(invalidEndingNodeId1, invalidEndingNodeId2)
    }
  }

  test("return variable type information for process that cannot be canonized") {
    val scenarioGraph = createGraph(
      List(
        Source("s", SourceRef("sourceRef", List())),
        Variable("v", "test", Expression.spel("''")),
        Filter("e", Expression.spel("''"))
      ),
      List(Edge("s", "v", None), Edge("v", "e", None)),
    )

    val result = validate(scenarioGraph)
    result.hasErrors shouldBe true

    val nodeVariableTypes = result.nodeResults.mapValuesNow(_.variableTypes)
    nodeVariableTypes.get("s").value shouldEqual Map.empty
    nodeVariableTypes.get("v").value shouldEqual Map("input" -> Unknown)
    nodeVariableTypes.get("e").value shouldEqual Map("input" -> Unknown, "test" -> Typed.fromInstance(""))
  }

  test("return variable type information for scenario with disabled filter") {
    val disabledFilterScenario = ScenarioBuilder
      .streaming("id")
      .source("start", ProcessTestData.existingSourceFactory)
      .buildSimpleVariable("variable", "varName", Expression.spel("'string'"))
      .filter("filter", "false".spel, disabled = Some(true))
      .emptySink("sink", ProcessTestData.existingSinkFactory)

    val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(disabledFilterScenario)
    val result        = validate(scenarioGraph)

    val nodeVariableTypes = result.nodeResults.mapValuesNow(_.variableTypes)
    nodeVariableTypes.get("filter").value shouldEqual Map("input" -> Unknown, "varName" -> Typed.fromInstance("string"))
  }

  test("return variable type information without variables from disabled node in subsequent nodes") {
    val fragmentId         = "fragmentId"
    val fragmentParameters = List.empty

    val fragment: CanonicalProcess =
      createFragmentDefinition(fragmentId, fragmentParameters)
    val processWithFragment =
      createGraph(
        nodes = List(
          Source("source", SourceRef(sourceTypeName, Nil)),
          FragmentInput("subIn", FragmentRef(fragmentId, fragmentParameters), isDisabled = Some(true)),
          Sink("sink", SinkRef(sinkTypeName, Nil))
        ),
        edges = List(
          Edge("source", "subIn", None),
          Edge("subIn", "sink", Some(EdgeType.FragmentOutput("out1")))
        )
      )

    val processValidator = mockedProcessValidator(Some(fragment), defaultConfig)
    val result           = processValidator.validate(processWithFragment, ProcessName("name"), isFragment = false)

    val nodeVariableTypes = result.nodeResults.mapValuesNow(_.variableTypes)
    nodeVariableTypes.get("sink").value shouldEqual Map("input" -> Unknown)
  }

  test("return empty process error for empty scenario") {
    val emptyScenario = createGraph(List.empty, List.empty)
    val result        = processValidator.validate(emptyScenario, ProcessName("name"), isFragment = false)
    result.errors.globalErrors shouldBe List(
      UIGlobalError(PrettyValidationErrors.formatErrorMessage(EmptyProcess), List.empty)
    )
  }

  private def validate(scenarioGraph: ScenarioGraph): ValidationResult = {
    TestFactory.processValidator.validate(scenarioGraph, ProcessTestData.sampleProcessName, isFragment = false)
  }

}

private object UIProcessValidatorSpec {
  private implicit val user: LoggedUser = AdminUser("admin", "admin")

  val sourceTypeName: String = "processSource"
  val sinkTypeName: String   = "processSink"

  val defaultConfig: Config = List("genericParametersSource", "genericParametersSink", "genericTransformer")
    .foldLeft(ConfigFactory.empty())((c, n) =>
      c.withValue(s"componentsUiConfig.$n.params.par1.defaultValue", fromAnyRef("'realDefault'"))
    )

  private val configuredValidator: UIProcessValidator = TestFactory.processValidator.withScenarioPropertiesConfig(
    Map(
      "requiredStringProperty" -> ScenarioPropertyConfig(
        defaultValue = None,
        editor = Some(StringParameterEditor),
        validators = Some(List(MandatoryParameterValidator)),
        label = Some("label"),
        hintText = None
      ),
      "numberOfThreads" -> ScenarioPropertyConfig(
        defaultValue = None,
        editor = Some(FixedValuesParameterEditor(TestFactory.possibleValues)),
        validators = Some(List(FixedValuesValidator(TestFactory.possibleValues))),
        label = None,
        hintText = None
      ),
      "maxEvents" -> ScenarioPropertyConfig(
        defaultValue = None,
        editor = None,
        validators = Some(List(CompileTimeEvaluableValueValidator)),
        label = Some("label"),
        hintText = None
      )
    ) ++ FlinkStreamingPropertiesConfig.properties
  )

  def validateWithConfiguredProperties(
      scenario: ScenarioGraph,
      processName: ProcessName = ProcessTestData.sampleProcessName,
      isFragment: Boolean = false
  ): ValidationResult =
    configuredValidator.validate(scenario, processName, isFragment)

  val validFlinkScenarioGraph: ScenarioGraph = createGraph(
    List(
      Source("in", SourceRef(ProcessTestData.existingSourceFactory, List())),
      Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List()))
    ),
    List(Edge("in", "out", None))
  )

  val validFlinkFragmentGraph: ScenarioGraph = ScenarioGraph(
    ProcessProperties.combineTypeSpecificProperties(
      StreamMetaData(),
      additionalFields = ProcessAdditionalFields(None, FragmentSpecificData().toMap, FragmentSpecificData.typeName)
    ),
    nodes = List(
      FragmentInputDefinition("in", List()),
      FragmentOutputDefinition("out", "outputName")
    ),
    edges = List(Edge("in", "out", None))
  )

  def validScenarioGraphWithFields(fields: Map[String, String]): ScenarioGraph = {
    createGraph(
      List(
        Source("in", SourceRef(ProcessTestData.existingSourceFactory, List())),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List()))
      ),
      List(Edge("in", "out", None)),
      additionalFields = fields
    )
  }

  private def createScenarioGraphWithParams(
      nodeParams: List[NodeParameter],
      scenarioProperties: Map[String, String],
  ): ScenarioGraph = {
    createGraph(
      List(
        Source("inID", SourceRef(ProcessTestData.existingSourceFactory, List())),
        Enricher("custom", ServiceRef(ProcessTestData.otherExistingServiceId4, nodeParams), "out"),
        Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List()))
      ),
      List(Edge("inID", "custom", None), Edge("custom", "out", None)),
      scenarioProperties
    )
  }

  private def createScenarioGraphWithFragmentParams(
      fragmentDefinitionId: String,
      nodeParams: List[NodeParameter]
  ): ScenarioGraph = {
    createGraph(
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

  object OptionalParameterService extends Service {

    @MethodToInvoke
    def method(
        @ParamName("optionalParam")
        optionalParam: Option[String],
    ): Future[String] = ???

  }

  object MapParameterService extends Service {

    val serviceId = "mapParameterService"

    @MethodToInvoke
    def method(
        @ParamName("mapParam1")
        mapParam1: Option[java.util.Map[String, String]],
        @ParamName("mapParam2")
        mapParam2: Option[java.util.Map[String, String]]
    ): Future[String] = ???

  }

  object ListParameterService extends Service {

    val serviceId = "listParameterService"

    @MethodToInvoke
    def method(
        @ParamName("listParam1")
        listParam1: Option[java.util.List[String]],
        @ParamName("listParam2")
        listParam2: Option[java.util.List[String]]
    ): Future[String] = ???

  }

  object LocalDateTimeParameterService extends Service {

    val serviceId = "localDateTimeParameterService"

    @MethodToInvoke
    def method(
        @ParamName("localDateTimeParam")
        localDateTimeParam: Option[LocalDateTime]
    ): Future[String] = ???

  }

  object EagerServiceWithDynamicComponent extends EagerServiceWithStaticParametersAndReturnType {

    override def parameters: List[Parameter] = List(
      Parameter[String](ParameterName("param")).copy(
        validators = List.empty
      )
    )

    override def returnType: typing.TypingResult = Typed[String]

    override def invoke(eagerParameters: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        contextId: ContextId,
        metaData: MetaData,
        componentUseCase: ComponentUseCase
    ): Future[Any] = {
      Future.successful(eagerParameters.head._2.toString)
    }

  }

  private def processWithEagerServiceWithDynamicComponent(paramValue: String) = createGraph(
    List(
      Source("inID", SourceRef(ProcessTestData.existingSourceFactory, List())),
      Enricher(
        "custom",
        ServiceRef(
          "eagerServiceWithDynamicComponent",
          List(NodeParameter(ParameterName("param"), Expression.spel(paramValue)))
        ),
        "out"
      ),
      Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List()))
    ),
    List(Edge("inID", "custom", None), Edge("custom", "out", None))
  )

  private def processWithOptionalParameterService(optionalParamValue: String) = createGraph(
    List(
      Source("inID", SourceRef(ProcessTestData.existingSourceFactory, List())),
      Enricher(
        "custom",
        ServiceRef(
          "optionalParameterService",
          List(NodeParameter(ParameterName("optionalParam"), Expression.spel(optionalParamValue)))
        ),
        "out"
      ),
      Sink("out", SinkRef(ProcessTestData.existingSinkFactory, List()))
    ),
    List(Edge("inID", "custom", None), Edge("custom", "out", None))
  )

  private def createGraph(
      nodes: List[NodeData],
      edges: List[Edge],
      additionalFields: Map[String, String] = Map.empty
  ): ScenarioGraph = {
    ScenarioGraph(
      ProcessProperties.combineTypeSpecificProperties(
        StreamMetaData(),
        additionalFields = ProcessAdditionalFields(None, additionalFields, StreamMetaData.typeName)
      ),
      nodes,
      edges
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
        FlatNode(
          FragmentOutputDefinition("out", "out1", List(Field("strField", Expression(Language.Spel, "'value'"))))
        ),
      ),
      additionalBranches = List.empty
    )
  }

  private def processWithDictParameterEditorService(expression: Expression) =
    processWithService(dictParameterEditorServiceId, List(NodeParameter(ParameterName("expression"), expression)))

  private def processWithService(serviceId: String, params: List[NodeParameter]) = createGraph(
    List(
      Source("inID", SourceRef(existingSourceFactory, List())),
      Enricher("custom", ServiceRef(serviceId, params), "out"),
      Sink("out", SinkRef(existingSinkFactory, List()))
    ),
    List(Edge("inID", "custom", None), Edge("custom", "out", None))
  )

  private def paramConfigWithValidationExpression(validationExpression: Expression) =
    ParameterAdditionalUIConfig(
      required = false,
      initialValue = None,
      hintText = None,
      valueEditor = None,
      valueCompileTimeValidation = Some(
        ParameterValueCompileTimeValidation(validationExpression, Some("some custom failure message"))
      )
    )

  private def validatorWithComponentsAndConfig(
      components: List[ComponentDefinition],
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
  ) = new UIProcessValidator(
    processingType = "Streaming",
    validator = ProcessValidator.default(
      LocalModelData(
        ConfigWithScalaVersion.StreamingProcessTypeConfig.resolved.getConfig("modelConfig"),
        components,
        additionalConfigsFromProvider = additionalConfigsFromProvider
      )
    ),
    scenarioProperties = Map.empty,
    scenarioPropertiesConfigFinalizer =
      new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, Streaming.stringify),
    additionalValidators = List.empty,
    fragmentResolver = new FragmentResolver(new StubFragmentRepository(Map.empty))
  )

  def mockedProcessValidator(
      fragmentInDefaultProcessingType: Option[CanonicalProcess],
      execConfig: Config = ConfigFactory.empty()
  ): UIProcessValidator = {
    mockedProcessValidator(
      fragmentsByProcessingType = fragmentInDefaultProcessingType match {
        case Some(frag) => Map("Streaming" -> frag)
        case None       => Map.empty
      },
      execConfig = execConfig
    )
  }

  def mockedProcessValidator(
      fragmentsByProcessingType: Map[ProcessingType, CanonicalProcess],
      execConfig: Config
  ): UIProcessValidator = {
    val modelDefinition = ModelDefinitionBuilder.empty
      .withUnboundedStreamSource(sourceTypeName)
      .withSink(sinkTypeName)
      .build

    new UIProcessValidator(
      processingType = "Streaming",
      validator = ProcessValidator.default(new StubModelDataWithModelDefinition(modelDefinition, execConfig)),
      scenarioProperties = FlinkStreamingPropertiesConfig.properties,
      scenarioPropertiesConfigFinalizer =
        new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, "Streaming"),
      additionalValidators = List(SampleCustomProcessValidator),
      fragmentResolver = new FragmentResolver(
        new StubFragmentRepository(
          fragmentsByProcessingType.mapValuesNow(List(_))
        )
      )
    )
  }

  object SampleCustomProcessValidator extends CustomProcessValidator {
    val badName: ProcessName = ProcessName("badName")

    val badNameError: ScenarioNameValidationError = ScenarioNameValidationError("BadName", "BadName")

    override def validate(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
      Validated.condNel(process.name != badName, (), badNameError)
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
