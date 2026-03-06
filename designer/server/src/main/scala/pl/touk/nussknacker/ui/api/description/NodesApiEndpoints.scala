package pl.touk.nussknacker.ui.api.description

import cats.data.NonEmptyList
import cats.implicits.toTraverseOps
import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.{Decoder, Encoder, Json, KeyDecoder, KeyEncoder}
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.additionalInfo.{AdditionalInfo, MarkdownAdditionalInfo}
import pl.touk.nussknacker.engine.api.{LayoutData, NodeId, NodeName, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedExpressionValueWithIcon,
  MultiSelectFixedValue,
  ParameterCategory,
  ParameterEditor
}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{
  CellError,
  ColumnDefinition,
  CoordinatesBasedTextRange,
  ErrorDetails,
  TextCoordinates
}
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.json.decoders.TypingResultDecoder
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterName,
  ParameterValueCompileTimeValidation,
  ParameterValueInput
}
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter => EvaluatedParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.node.{BranchEndDefinition, FragmentInput}
import pl.touk.nussknacker.engine.graph.node.{Enricher, Filter}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.graph.node.NodeData.nodeDataEncoder
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.spel.ExpressionSuggestion
import pl.touk.nussknacker.engine.test.testcase.{Assertion, EnricherMock, TestCaseId, TestCaseName}
import pl.touk.nussknacker.engine.test.testcase.Assertion.{AssertionOperator, PredicateAssertion}
import pl.touk.nussknacker.engine.util.CaretPosition2d
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.definition.{UIParameter, UIValueParameter}
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType}
import pl.touk.nussknacker.restmodel.validation.testcase.{
  AssertionIndex,
  AssertionValidationError,
  EnricherMockValidationError,
  NodeTestCasesValidationErrors,
  NodeTestCaseValidationErrors
}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioGraphCodec._
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.TestingApiErrorMessages
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodeDataSchemas.nodeDataSchema
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.{
  BadRequestNodesError,
  NoContentNodesError,
  NotFoundNodesError
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.BadRequestNodesError.{
  InvalidNodeType,
  MalformedTypingResult,
  SourceCompilation,
  TooManyCharactersGenerated,
  TooManyRecordsRequested,
  UnsupportedSourcePreview
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.NoContentNodesError
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.NotFoundNodesError.{
  NoLiveDataAvailable,
  NoProcessingType,
  NoScenario
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.ErrorOutputs._
import pl.touk.nussknacker.ui.api.description.TypingDtoSchemas._
import pl.touk.nussknacker.ui.api.description.TypingDtoSchemas.TypedClassSchemaHelper.typedClassTypeSchema
import pl.touk.nussknacker.ui.api.description.TypingDtoSchemas.TypedDictSchemaHelper.typedDictTypeSchema
import pl.touk.nussknacker.ui.api.description.TypingDtoSchemas.TypedNullSchemaHelper.typedNullTypeSchema
import pl.touk.nussknacker.ui.api.description.TypingDtoSchemas.TypedObjectSchemaHelper.typedObjectTypeSchema
import pl.touk.nussknacker.ui.api.description.TypingDtoSchemas.TypedObjectTypingResultSchemaHelper.typedObjectTypingResultTypeSchema
import pl.touk.nussknacker.ui.api.description.TypingDtoSchemas.TypedTaggedSchemaHelper.typedTaggedTypeSchema
import pl.touk.nussknacker.ui.api.description.TypingDtoSchemas.TypedUnionSchemaHelper.typedUnionTypeSchema
import pl.touk.nussknacker.ui.api.description.TypingDtoSchemas.UnknownSchemaHelper.unknownTypeSchema
import sttp.model.StatusCode.{BadRequest, NoContent, NotFound, Ok}
import sttp.tapir._
import sttp.tapir.EndpointIO.Example
import sttp.tapir.Schema.{SName, Typeclass}
import sttp.tapir.SchemaType.{SchemaWithValue, SProduct, SProductField, SString}
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

import java.time.Duration
import scala.language.implicitConversions

class NodesApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import NodesApiEndpoints.Dtos._
  lazy val encoder: Encoder[TypingResult] = TypingResult.encoder

  lazy val nodesAdditionalInfoEndpoint
      : SecuredEndpoint[(ProcessName, NodeData), NodesError, Option[AdditionalInfo], Any] = {
    baseNuApiEndpoint
      .summary("Additional info for provided node")
      .tag("Nodes")
      .post
      .in("nodes" / path[ProcessName]("scenarioName") / "additionalInfo")
      .in(
        jsonBody[NodeData]
          .example(
            Example.of(
              summary = Some("Basic node request"),
              value = Enricher(
                NodeId("99ae6a31-a55d-4d52-89af-58f471e7143d"),
                NodeName("enricher"),
                ServiceRef(
                  "paramService",
                  List(
                    EvaluatedParameter(ParameterName("id"), Expression(Language.Spel, "'a'"))
                  )
                ),
                "out",
                additionalFields = None
              )
            )
          )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[Option[AdditionalInfo]]
            .example(
              Example.of(
                summary = Some("Additional info for node"),
                value = Some(
                  MarkdownAdditionalInfo(
                    "\nSamples:\n\n| id  | value |\n| --- | ----- |\n| a   | generated |\n| b   | not existent |\n\nResults for a can be found [here](http://touk.pl?id=a)\n"
                  )
                )
              )
            )
        )
      )
      .errorOut(oneOf[NodesError](scenarioNotFoundErrorOutput))
      .withSecurity(auth)
  }

  lazy val nodesValidationEndpoint
      : SecuredEndpoint[(ProcessName, NodeValidationRequestDto), NodesError, NodeValidationResultDto, Any] = {
    baseNuApiEndpoint
      .summary("Validate provided Node")
      .tag("Nodes")
      .post
      .in("nodes" / path[ProcessName]("scenarioName") / "validation")
      .in(
        jsonBody[NodeValidationRequestDto]
          .examples(
            List(
              Example.of(
                value = NodeValidationRequestDto(
                  Filter(
                    NodeId("99ae6a31-a55d-4d52-89af-58f471e7143d"),
                    NodeName("id"),
                    Expression(Language.Spel, "#longValue > 1"),
                    isDisabled = None,
                    additionalFields = None
                  ),
                  ProcessProperties.apply(
                    ProcessAdditionalFields(description = None, properties = Map.empty, metaDataType = "")
                  ),
                  Map(
                    "existButString" -> TypingResultInJson(
                      encoder.apply(Typed[java.lang.String])
                    ),
                    "longValue" -> TypingResultInJson(encoder.apply(Typed[java.lang.Long]))
                  ),
                  branchVariableTypes = None,
                  outgoingEdges = None,
                  testCases = None,
                ),
                summary = Some("Validate correct Filter node")
              ),
              Example.of(
                summary = Some("Validate incorrect Filter node - wrong expression type"),
                value = NodeValidationRequestDto(
                  Filter(
                    NodeId("740bbc64-a688-4a50-9104-be61eadfa82a"),
                    NodeName("id"),
                    Expression(Language.Spel, "#existButString"),
                    isDisabled = None,
                    additionalFields = None
                  ),
                  ProcessProperties.apply(
                    ProcessAdditionalFields(description = None, properties = Map.empty, metaDataType = "")
                  ),
                  Map(
                    "existButString" -> TypingResultInJson(
                      encoder.apply(Typed[java.lang.String])
                    ),
                    "longValue" -> TypingResultInJson(encoder.apply(Typed[java.lang.Long]))
                  ),
                  branchVariableTypes = None,
                  outgoingEdges = None,
                  testCases = None,
                ),
              ),
              Example.of(
                summary = Some("Validate test cases for enricher node"),
                value = NodeValidationRequestDto(
                  nodeData = Enricher(
                    NodeId("513eab33-d9b3-4c67-a9e7-b84ae184eb64"),
                    NodeName("enricher"),
                    ServiceRef(
                      "paramService",
                      List(
                        EvaluatedParameter(ParameterName("param"), Expression(Language.Spel, "#input.id"))
                      )
                    ),
                    "out",
                    additionalFields = None
                  ),
                  processProperties = ProcessProperties.apply(
                    ProcessAdditionalFields(description = None, properties = Map.empty, metaDataType = "")
                  ),
                  variableTypes = Map(
                    "input" -> TypingResultInJson(
                      encoder.apply(
                        Typed.record(
                          Map(
                            "id" ->
                              TypedObjectWithValue.apply(
                                Typed[java.lang.String].asInstanceOf[TypedClass],
                                "a"
                              )
                          )
                        )
                      )
                    )
                  ),
                  branchVariableTypes = None,
                  outgoingEdges = None,
                  testCases = Some(
                    Map(
                      "test-case-1" -> NodeTestCase(
                        enricherMock = Some(EnricherMock(Expression.spel("42"))),
                        assertions = List(
                          PredicateAssertion(
                            operator = AssertionOperator.Equals,
                            actual = Expression.spel("#records.size"),
                            expected = Expression.spel("1"),
                            description = Some("exactly one event should pass through the node")
                          ),
                        ),
                      ),
                      "test-case-2" -> NodeTestCase(
                        enricherMock = Some(EnricherMock(Expression.spel("'sample value'"))),
                        assertions = List(
                          PredicateAssertion(
                            operator = AssertionOperator.Equals,
                            actual = Expression.spel("#records.size"),
                            expected = Expression.spel("1"),
                          ),
                          PredicateAssertion(
                            operator = AssertionOperator.Equals,
                            actual = Expression.spel("#records[0].doesNotExist"),
                            expected = Expression.spel("'expected'"),
                            description = Some("output field should match the expected value")
                          ),
                        ),
                      )
                    )
                  ),
                )
              )
            )
          )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[NodeValidationResultDto]
            .examples(
              List(
                Example.of(
                  summary = Some("Node validation without errors"),
                  value = NodeValidationResultDto(
                    parameters = None,
                    expressionType = Some(Typed[java.lang.Boolean]),
                    validationErrors = List.empty,
                    validationPerformed = true,
                    testCasesValidationErrors = None,
                  )
                ),
                Example.of(
                  summary = Some("Wrong parameter type"),
                  value = NodeValidationResultDto(
                    parameters = None,
                    expressionType = Some(Unknown),
                    validationErrors = List(
                      NodeValidationError(
                        "ExpressionParserCompilationError",
                        "Bad expression type, expected: Boolean, found: String",
                        "There is problem with expression in field [($expression)] - it could not be parsed.",
                        Some("$expression"),
                        NodeValidationErrorType.SaveAllowed,
                        details = None
                      )
                    ),
                    validationPerformed = true,
                    testCasesValidationErrors = None,
                  )
                ),
                Example.of(
                  summary = Some("Test cases validation errors"),
                  value = NodeValidationResultDto(
                    parameters = None,
                    expressionType = Some(Typed[java.lang.Boolean]),
                    validationErrors = List.empty,
                    validationPerformed = true,
                    testCasesValidationErrors = Some(
                      Map(
                        "test-case-1" -> NodeTestCaseValidationErrors(
                          enricherMockErrors = Some(
                            NonEmptyList.one(
                              EnricherMockValidationError(
                                typ = "ExpressionParserCompilationError",
                                message = "Bad expression type, expected: String, found: Integer(42)",
                                description =
                                  "There is problem with expression in field [mockExpression] - it could not be parsed.",
                                details = None
                              )
                            )
                          ),
                          assertionsErrors = None
                        )
                      )
                    )
                  )
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[NodesError](
          scenarioNotFoundErrorOutput,
          malformedTypingResultErrorOutput
        )
      )
      .withSecurity(auth)
  }

  lazy val propertiesAdditionalInfoEndpoint
      : SecuredEndpoint[(ProcessName, ProcessProperties), NodesError, Option[AdditionalInfo], Any] = {
    baseNuApiEndpoint
      .summary("Additional info for provided properties")
      .tag("Nodes")
      .post
      .in("properties" / path[ProcessName]("scenarioName") / "additionalInfo")
      .in(
        jsonBody[ProcessProperties]
          .example(
            Example.of(
              summary = Some("Proper process properties"),
              value = ProcessProperties.apply(
                validPropertiesAdditionalFields
              ),
            )
          )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[Option[AdditionalInfo]]
            .example(
              Example.of(
                summary = Some("Some additional info for parameters"),
                value = Some(MarkdownAdditionalInfo("2 threads will be used on environment '{scenarioName}'"))
              )
            )
        )
      )
      .errorOut(oneOf[NodesError](scenarioNotFoundErrorOutput))
      .withSecurity(auth)
  }

  lazy val propertiesValidationEndpoint
      : SecuredEndpoint[(ProcessName, PropertiesValidationRequestDto), NodesError, NodeValidationResultDto, Any] = {
    baseNuApiEndpoint
      .summary("Validate node properties")
      .tag("Nodes")
      .post
      .in("properties" / path[ProcessName]("scenarioName") / "validation")
      .in(
        jsonBody[PropertiesValidationRequestDto]
          .examples(
            List(
              Example.of(
                summary = Some("Validate proper properties"),
                value = PropertiesValidationRequestDto(
                  validPropertiesAdditionalFields,
                  ProcessName("test")
                )
              ),
              Example.of(
                summary = Some("Validate wrong 'number of threads' property"),
                value = PropertiesValidationRequestDto(
                  ProcessAdditionalFields(
                    description = None,
                    Map(
                      "parallelism"                 -> "",
                      "checkpointIntervalInSeconds" -> "",
                      "numberOfThreads"             -> "a",
                      "spillStateToDisk"            -> "true",
                      "environment"                 -> "test",
                      "useAsyncInterpretation"      -> ""
                    ),
                    "StreamMetaData"
                  ),
                  ProcessName("test")
                )
              )
            )
          )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[NodeValidationResultDto]
            .examples(
              List(
                Example.of(
                  summary = Some("Validation for proper node"),
                  value = NodeValidationResultDto(
                    parameters = None,
                    expressionType = None,
                    validationErrors = List.empty,
                    validationPerformed = true,
                    testCasesValidationErrors = None,
                  )
                ),
                Example.of(
                  summary = Some("Validation for properties with errors"),
                  value = NodeValidationResultDto(
                    parameters = None,
                    expressionType = None,
                    validationErrors = List(
                      NodeValidationError(
                        "InvalidPropertyFixedValue",
                        "Property numberOfThreads (Number of threads) has invalid value",
                        "Expected one of 1, 2, got: a.",
                        Some("numberOfThreads"),
                        NodeValidationErrorType.SaveAllowed,
                        details = None
                      ),
                      NodeValidationError(
                        "UnknownProperty",
                        "Unknown property parallelism",
                        "Property parallelism is not known",
                        Some("parallelism"),
                        NodeValidationErrorType.SaveAllowed,
                        details = None
                      )
                    ),
                    validationPerformed = true,
                    testCasesValidationErrors = None,
                  )
                )
              )
            )
        )
      )
      .errorOut(oneOf[NodesError](scenarioNotFoundErrorOutput))
      .withSecurity(auth)
  }

  lazy val recordsEndpoint: SecuredEndpoint[
    (ProcessName, Int, RecordsRequestDto),
    NodesError,
    String,
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Fetch records for specific node")
      .tag("Nodes")
      .post
      .in("nodes" / path[ProcessName]("scenarioName") / "records")
      .in(
        query[Int]("limit")
          .default(10)
          .description("Limit the number of records returned")
      )
      .in(
        jsonBody[RecordsRequestDto]
          .example(
            Example.of(
              summary = Some("Basic fetch request"),
              value = RecordsRequestDto(
                ProcessProperties(StreamMetaData()),
                Source(
                  NodeId("513eab33-d9b3-4c67-a9e7-b84ae184eb64"),
                  NodeName("sourceId"),
                  SourceRef("source", List.empty),
                  None
                )
              )
            )
          )
      )
      .out(
        statusCode(Ok).and(
          stringBody
            .examples(
              List(
                Example.of(
                  summary = Some("Simple scenario test data in json stringify form"),
                  value = "{name: John}"
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[NodesError](
          oneOfVariant[BadRequestNodesError](
            BadRequest,
            plainBody[BadRequestNodesError]
              .examples(
                List(
                  Example.of(
                    summary = Some("Source compilation error"),
                    value = SourceCompilation("sourceId", "source name", List("Invalid source configuration"))
                  ),
                  Example.of(
                    summary = Some("Unsupported source preview"),
                    value = UnsupportedSourcePreview("sourceId", "source name")
                  ),
                  Example.of(
                    summary = Some("Invalid node type"),
                    value = InvalidNodeType("Filter", "Source")
                  ),
                  Example.of(
                    summary = Some("Too many records requested"),
                    value = TooManyRecordsRequested(100)
                  ),
                  Example.of(
                    summary = Some("Too many characters generated"),
                    value = TooManyCharactersGenerated(length = 1000, limit = 500)
                  )
                )
              )
          ),
          oneOfVariant[NotFoundNodesError](
            NotFound,
            plainBody[NotFoundNodesError]
              .examples(
                List(
                  Example.of(
                    summary = Some("No scenario found"),
                    value = NoScenario(ProcessName("'example scenario'"))
                  ),
                  Example.of(
                    summary = Some("No live test data available"),
                    value = NoLiveDataAvailable
                  )
                )
              )
          )
        )
      )
      .withSecurity(auth)
  }

  lazy val parametersValidationEndpoint: SecuredEndpoint[
    (ProcessingType, ParametersValidationRequestDto),
    NodesError,
    ParametersValidationResultDto,
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Validate given parameters")
      .tag("Nodes")
      .post
      .in("parameters" / path[ProcessingType]("processingType") / "validate")
      .in(
        jsonBody[ParametersValidationRequestDto]
          .example(
            Example.of(
              summary = Some("Parameters validation"),
              value = ParametersValidationRequestDto(
                List(
                  UIValueParameterDto(
                    "condition",
                    TypingResultInJson(encoder.apply(Typed[java.lang.Boolean])),
                    Expression(Language.Spel, "#input.amount > 2")
                  )
                ),
                Map(
                  "input" ->
                    TypingResultInJson(
                      encoder.apply(
                        Typed.record(
                          Map(
                            "amount" ->
                              TypedObjectWithValue.apply(
                                Typed[java.lang.Long].asInstanceOf[TypedClass],
                                5L
                              )
                          )
                        )
                      )
                    )
                )
              )
            )
          )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[ParametersValidationResultDto]
            .examples(
              List(
                Example.of(
                  summary = Some("Validate correct parameters"),
                  value = ParametersValidationResultDto(
                    validationErrors = List.empty,
                    validationPerformed = true
                  )
                ),
                Example.of(
                  summary = Some("Validate incorrect parameters"),
                  value = ParametersValidationResultDto(
                    List(
                      NodeValidationError(
                        "ExpressionParserCompilationError",
                        "Bad expression type, expected: Boolean, found: Long(5)",
                        "There is problem with expression in field [condition] - it could not be parsed.",
                        Some("condition"),
                        NodeValidationErrorType.SaveAllowed,
                        details = None
                      )
                    ),
                    validationPerformed = true
                  ),
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[NodesError](
          processingTypeNotFoundErrorOutput,
          malformedTypingResultErrorOutput
        )
      )
      .withSecurity(auth)
  }

  lazy val parametersSuggestionsEndpoint: SecuredEndpoint[
    (ProcessingType, ExpressionSuggestionRequestDto),
    NodesError,
    List[ExpressionSuggestionDto],
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Suggest possible variables")
      .tag("Nodes")
      .post
      .in("parameters" / path[ProcessingType]("processingType") / "suggestions")
      .in(
        jsonBody[ExpressionSuggestionRequestDto]
          .example(
            Example.of(
              summary = Some("Get suggestions for given expression"),
              value = ExpressionSuggestionRequestDto(
                Expression(Language.Spel, "#inpu"),
                CaretPosition2d(0, 5),
                Map(
                  "input" ->
                    TypingResultInJson(
                      encoder.apply(
                        Typed.record(
                          Map(
                            "amount" ->
                              TypedObjectWithValue.apply(
                                Typed[java.lang.Long].asInstanceOf[TypedClass],
                                5L
                              )
                          )
                        )
                      )
                    )
                )
              )
            )
          )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[List[ExpressionSuggestionDto]]
            .examples(
              List(
                Example.of(
                  summary = Some("Found a suggestion for currently given expression"),
                  value = List(
                    ExpressionSuggestionDto(
                      "input",
                      Typed.record(
                        Map(
                          "amount" ->
                            TypedObjectWithValue.apply(
                              Typed[java.lang.Long].asInstanceOf[TypedClass],
                              5L
                            )
                        )
                      ),
                      fromClass = false,
                      description = None,
                      parameters = List.empty
                    )
                  )
                ),
                Example.of(
                  summary = Some("No suggestions found for given expression"),
                  value = List.empty
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[NodesError](
          processingTypeNotFoundErrorOutput,
          malformedTypingResultErrorOutput
        )
      )
      .withSecurity(auth)
  }

  lazy val testCaseAdditionalVariablesEndpoint: SecuredEndpoint[
    (ProcessingType, TestCaseAdditionalVariablesRequestDto),
    NodesError,
    TestCaseAdditionalVariablesResponseDto,
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Additional variables for a test case at node")
      .tag("Nodes")
      .post
      .in("parameters" / path[ProcessingType]("processingType") / "testCase" / "additionalVariables")
      .in(
        jsonBody[TestCaseAdditionalVariablesRequestDto]
          .example(
            Example.of(
              summary = Some("Node variables"),
              value = TestCaseAdditionalVariablesRequestDto(
                Map(
                  "amount" -> TypingResultInJson(
                    encoder.apply(
                      TypedObjectWithValue.apply(
                        Typed[java.lang.Long].asInstanceOf[TypedClass],
                        5L
                      )
                    )
                  ),
                  "name" -> TypingResultInJson(
                    encoder.apply(
                      TypedObjectWithValue.apply(
                        Typed[java.lang.String].asInstanceOf[TypedClass],
                        "Alice"
                      )
                    )
                  ),
                )
              )
            )
          )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[TestCaseAdditionalVariablesResponseDto]
            .example(
              Example.of(
                summary = Some("Additional variables for assertions in a test case"),
                value = TestCaseAdditionalVariablesResponseDto(
                  assertionsAdditionalVariables = Map(
                    "contexts" -> Typed.genericTypeClass(
                      classOf[java.util.List[_]],
                      List(
                        Typed.record(
                          List(
                            "amount" -> TypedObjectWithValue.apply(
                              Typed[java.lang.Long].asInstanceOf[TypedClass],
                              5L
                            ),
                            "name" -> TypedObjectWithValue.apply(
                              Typed[java.lang.String].asInstanceOf[TypedClass],
                              "Alice"
                            )
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[NodesError](
          scenarioNotFoundErrorOutput,
          malformedTypingResultErrorOutput
        )
      )
      .withSecurity(auth)
  }

  lazy val testCaseGenerateEnricherMockEndpoint: SecuredEndpoint[
    (ProcessName, GenerateEnricherMockRequestDto),
    NodesError,
    GenerateEnricherMockResponseDto,
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Generate an enricher mock expression")
      .tag("Nodes")
      .post
      .in("nodes" / path[ProcessName]("scenarioName") / "testCase" / "enricherMock")
      .in(
        jsonBody[GenerateEnricherMockRequestDto]
          .example(
            Example.of(
              value = GenerateEnricherMockRequestDto(
                variableTypes = Map(
                  "amount" -> TypingResultInJson(
                    encoder.apply(
                      TypedObjectWithValue.apply(
                        Typed[java.lang.Long].asInstanceOf[TypedClass],
                        5L
                      )
                    )
                  ),
                  "name" -> TypingResultInJson(
                    encoder.apply(
                      TypedObjectWithValue.apply(
                        Typed[java.lang.String].asInstanceOf[TypedClass],
                        "Alice"
                      )
                    )
                  ),
                ),
                processProperties = ProcessProperties(StreamMetaData()),
                enricher = Enricher(
                  NodeId("99ae6a31-a55d-4d52-89af-58f471e7143d"),
                  NodeName("enricher"),
                  ServiceRef(
                    "paramService",
                    List(
                      EvaluatedParameter(ParameterName("param"), Expression(Language.Spel, "#input.id"))
                    )
                  ),
                  "out",
                  additionalFields = None
                )
              )
            )
          )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[GenerateEnricherMockResponseDto]
            .example(
              Example.of(
                value = GenerateEnricherMockResponseDto(
                  enricherMockExpression = Expression.jsonTemplate("\"sample\"")
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[NodesError](
          oneOfVariant(
            BadRequest,
            plainBody[BadRequestNodesError]
              .examples(
                List(
                  Example.of(
                    summary = Some("Compilation errors during enricher mock generation"),
                    value = BadRequestNodesError.EnricherMockExpressionGenerationError.CompilationErrors(
                      NonEmptyList.one(
                        ProcessCompilationError.MissingParameters(
                          Set(ParameterName("param1")),
                          NodeId("99ae6a31-a55d-4d52-89af-58f471e7143d")
                        )
                      )
                    )
                  ),
                  Example.of(
                    summary = Some("Output variable not found during enricher mock generation"),
                    value =
                      BadRequestNodesError.EnricherMockExpressionGenerationError.OutputVariableNotFound("outputVar")
                  )
                )
              )
          ),
          oneOfVariant(
            NoContent,
            emptyOutputAs(
              NoContentNodesError.EnricherMockExpressionGenerationError.UnsupportedExpressionType(
                Typed.genericTypeClass(
                  classOf[java.util.List[_]],
                  List(Typed[java.lang.String])
                )
              )
            ).description("Unsupported expression type during enricher mock generation")
          )
        )
      )
      .withSecurity(auth)
  }

  private val validPropertiesAdditionalFields =
    ProcessAdditionalFields(
      description = None,
      Map(
        "parallelism"                 -> "",
        "checkpointIntervalInSeconds" -> "",
        "numberOfThreads"             -> "2",
        "spillStateToDisk"            -> "true",
        "environment"                 -> "test",
        "useAsyncInterpretation"      -> ""
      ),
      "StreamMetaData"
    )

}

object NodesApiEndpoints {

  object ErrorOutputs {

    val scenarioNotFoundErrorOutput: EndpointOutput.OneOfVariant[NoScenario] =
      oneOfVariantFromMatchType(
        NotFound,
        plainBody[NoScenario]
          .example(
            Example.of(
              summary = Some("No scenario {scenarioName} found"),
              value = NoScenario(ProcessName("'example scenario'"))
            )
          )
      )

    val malformedTypingResultErrorOutput: EndpointOutput.OneOfVariant[MalformedTypingResult] =
      oneOfVariantFromMatchType(
        BadRequest,
        plainBody[MalformedTypingResult]
          .example(
            Example.of(
              summary = Some("Malformed TypingResult sent in request"),
              value = MalformedTypingResult(
                "Couldn't decode value 'WrongType'. Allowed values: 'TypedUnion,TypedDict,TypedObjectTypingResult,TypedTaggedValue,TypedClass,TypedObjectWithValue,TypedNull,Unknown"
              )
            )
          )
      )

    val processingTypeNotFoundErrorOutput: EndpointOutput.OneOfVariant[NoProcessingType] =
      oneOfVariantFromMatchType(
        NotFound,
        plainBody[NoProcessingType]
          .example(
            Example.of(
              summary = Some("ProcessingType type: {processingType} not found"),
              value = NoProcessingType("'processingType'")
            )
          )
      )

  }

  object Dtos {

    implicit lazy val parameterNameSchema: Schema[ParameterName] = Schema.string

    case class TypingResultInJson(value: Json)

    object TypingResultInJson {
      implicit def apply(typingResultInJson: TypingResultInJson): Json = typingResultInJson.value
      implicit lazy val typingResultInJsonDecoder: Decoder[TypingResultInJson] =
        Decoder.decodeJson.map(TypingResultInJson.apply)
      implicit lazy val typingResultInJsonEncoder: Encoder[TypingResultInJson] =
        Encoder.instance(typingResultInJson => typingResultInJson.value)
      implicit lazy val typingResultInJsonSchema: Schema[TypingResultInJson] = TypingDtoSchemas.typingResult.as
    }

    implicit lazy val additionalInfoSchema: Schema[AdditionalInfo]                    = Schema.derived
    implicit lazy val scenarioAdditionalFieldsSchema: Schema[ProcessAdditionalFields] = Schema.derived
    implicit lazy val scenarioPropertiesSchema: Schema[ProcessProperties]             = Schema.derived.hidden(true)

    implicit lazy val parameterSchema: Schema[EvaluatedParameter]    = Schema.derived
    implicit lazy val edgeTypeSchema: Schema[EdgeType]               = Schema.derived
    implicit lazy val edgeSchema: Schema[Edge]                       = Schema.derived
    implicit lazy val cellErrorSchema: Schema[CellError]             = Schema.derived
    implicit lazy val textCoordinatesSchema: Schema[TextCoordinates] = Schema.derived
    import pl.touk.nussknacker.ui.api.TapirCodecs.ClassCodec._
    implicit lazy val columnDefinitionSchema: Schema[ColumnDefinition]         = Schema.derived
    implicit lazy val languageSchema: Schema[Language]                         = Schema.string[Language]
    implicit lazy val parameterEditorSchema: Schema[ParameterEditor]           = Schema.anyObject[ParameterEditor]
    implicit lazy val nodeIdSchema: Schema[NodeId]                             = Schema.string[NodeId]
    implicit lazy val errorDetailsSchema: Schema[ErrorDetails]                 = Schema.derived
    implicit lazy val nodeValidationErrorSchema: Schema[NodeValidationError]   = Schema.derived
    implicit lazy val fixedExpressionValueSchema: Schema[FixedExpressionValue] = Schema.derived
    implicit lazy val fixedExpressionValueWithIconSchema: Schema[FixedExpressionValueWithIcon] = Schema.derived

    implicit lazy val selectOptionValueSchema: Schema[MultiSelectFixedValue] = {
      import sttp.tapir.json.circe._
      Schema.derived
    }

    implicit lazy val expressionSchema: Schema[Expression] = Schema.derived

    implicit lazy val caretPosition2dSchema: Schema[CaretPosition2d] = Schema.derived

    object NodeDataSchemas {
      implicit lazy val fragmentRefSchema: Schema[FragmentRef]           = Schema.derived
      implicit lazy val fragmentClazzRefSchema: Schema[FragmentClazzRef] = Schema.derived
      implicit lazy val parameterValueCompileTimeValidationSchema: Schema[ParameterValueCompileTimeValidation] =
        Schema.derived
      implicit lazy val parameterValueInputSchema: Schema[ParameterValueInput]                         = Schema.derived
      implicit lazy val fragmentParameterSchema: Schema[FragmentParameter]                             = Schema.derived
      implicit lazy val serviceRefSchema: Schema[ServiceRef]                                           = Schema.derived
      implicit lazy val branchEndDefinitionSchema: Schema[BranchEndDefinition]                         = Schema.derived
      implicit lazy val userDefinedAdditionalNodeFieldsSchema: Schema[UserDefinedAdditionalNodeFields] = Schema.derived
      implicit lazy val layoutDataSchema: Schema[LayoutData]                                           = Schema.derived
      implicit lazy val branchParametersSchema: Schema[BranchParameters]                               = Schema.derived
      implicit lazy val fieldSchema: Schema[Field]                                                     = Schema.derived
      implicit lazy val fragmentOutputVarDefinitionSchema: Schema[FragmentOutputVarDefinition]         = Schema.derived

      //  Tapir currently supports only json schema v4 which has no way to declare discriminator
      //  We declare that each type of NodeData belongs to an enum with only one value as a workaround for this problem
      private object BranchEndDataSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object BranchEndData extends NodeTypes
        }

        implicit lazy val branchEndDataTypeSchema: Schema[NodeTypes] =
          Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val branchEndDataSchema: Schema[BranchEndData] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("definition"),
                branchEndDefinitionSchema,
                branchEndData => Some(branchEndData.definition)
              ),
              SProductField(
                FieldName("type"),
                BranchEndDataSchemaHelper.branchEndDataTypeSchema,
                _ => Some(BranchEndDataSchemaHelper.NodeTypes.BranchEndData)
              )
            )
          )
        )

      private object CustomNodeSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object CustomNode extends NodeTypes
        }

        implicit lazy val customNodeTypeSchema: Schema[NodeTypes] =
          Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val customNodeSchema: Schema[CustomNode] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                customNode => Some(customNode.additionalFields)
              ),
              SProductField(
                FieldName("branchParametersTemplate"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                _ => None
              ),
              SProductField(FieldName("id"), Schema.string, customNode => Some(customNode.id)),
              SProductField(FieldName("nodeType"), Schema.string, customNode => Some(customNode.nodeType)),
              SProductField(
                FieldName("outputVar"),
                Schema.schemaForOption[String],
                customNode => Some(customNode.outputVar)
              ),
              SProductField(
                FieldName("parameters"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                customNode => Some(customNode.parameters)
              ),
              SProductField(
                FieldName("type"),
                CustomNodeSchemaHelper.customNodeTypeSchema,
                _ => Some(CustomNodeSchemaHelper.NodeTypes.CustomNode)
              ),
            )
          )
        )

      private object EnricherSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object Enricher extends NodeTypes
        }

        implicit lazy val enricherTypeSchema: Schema[NodeTypes] =
          Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val enricherSchema: Schema[Enricher] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                enricher => Some(enricher.additionalFields)
              ),
              SProductField(
                FieldName("branchParametersTemplate"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                _ => None
              ),
              SProductField(FieldName("id"), Schema.string, enricher => Some(enricher.id)),
              SProductField(FieldName("output"), Schema.string, enricher => Some(enricher.output)),
              SProductField(FieldName("service"), serviceRefSchema, enricher => Some(enricher.service)),
              SProductField(
                FieldName("type"),
                EnricherSchemaHelper.enricherTypeSchema,
                _ => Some(EnricherSchemaHelper.NodeTypes.Enricher)
              ),
            )
          )
        )

      private object FilterSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object Filter extends NodeTypes
        }

        implicit lazy val filterTypeSchema: Schema[NodeTypes] = Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val filterSchema: Schema[Filter] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                filter => Some(filter.additionalFields)
              ),
              SProductField(
                FieldName("branchParametersTemplate"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                _ => None
              ),
              SProductField(FieldName("expression"), expressionSchema, filter => Some(filter.expression)),
              SProductField(FieldName("id"), Schema.string, filter => Some(filter.id)),
              SProductField(
                FieldName("isDisabled"),
                Schema.schemaForOption[Boolean],
                filter => Some(filter.isDisabled)
              ),
              SProductField(
                FieldName("type"),
                FilterSchemaHelper.filterTypeSchema,
                _ => Some(FilterSchemaHelper.NodeTypes.Filter)
              ),
            )
          )
        )

      private object FragmentInputSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object FragmentInput extends NodeTypes
        }

        implicit val fragmentInputTypeSchema: Schema[NodeTypes] =
          Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val fragmentInputSchema: Schema[FragmentInput] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                fragmentInput => Some(fragmentInput.additionalFields)
              ),
              SProductField(FieldName("id"), Schema.string, fragmentInput => Some(fragmentInput.id)),
              SProductField(
                FieldName("isDisabled"),
                Schema.schemaForOption[Boolean],
                fragmentInput => Some(fragmentInput.isDisabled)
              ),
              SProductField(
                FieldName("fragmentParams"),
                Schema.schemaForOption[List[FragmentParameter]],
                fragmentInput => Some(fragmentInput.fragmentParams)
              ),
              SProductField(FieldName("ref"), fragmentRefSchema, fragmentInput => Some(fragmentInput.ref)),
              SProductField(
                FieldName("type"),
                FragmentInputSchemaHelper.fragmentInputTypeSchema,
                _ => Some(FragmentInputSchemaHelper.NodeTypes.FragmentInput)
              ),
            )
          )
        )

      private object FragmentInputDefinitionSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object FragmentInputDefinition extends NodeTypes
        }

        implicit lazy val fragmentInputDefinitionTypeSchema: Schema[NodeTypes] =
          Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val fragmentInputDefinitionSchema: Schema[FragmentInputDefinition] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                fragmentInputDefinition => Some(fragmentInputDefinition.additionalFields)
              ),
              SProductField(
                FieldName("id"),
                Schema.string,
                fragmentInputDefinition => Some(fragmentInputDefinition.id)
              ),
              SProductField(
                FieldName("parameters"),
                Schema.derived[List[FragmentParameter]],
                fragmentInputDefinition => Some(fragmentInputDefinition.parameters)
              ),
              SProductField(
                FieldName("type"),
                FragmentInputDefinitionSchemaHelper.fragmentInputDefinitionTypeSchema,
                _ => Some(FragmentInputDefinitionSchemaHelper.NodeTypes.FragmentInputDefinition)
              ),
            )
          )
        )

      private object FragmentOutputDefinitionSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object FragmentOutputDefinition extends NodeTypes
        }

        implicit lazy val fragmentOutputDefinitionTypeSchema: Schema[NodeTypes] =
          Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val fragmentOutputDefinitionSchema: Schema[FragmentOutputDefinition] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                fragmentOutputDefinition => Some(fragmentOutputDefinition.additionalFields)
              ),
              SProductField(
                FieldName("id"),
                Schema.string,
                fragmentOutputDefinition => Some(fragmentOutputDefinition.id)
              ),
              SProductField(
                FieldName("fields"),
                Schema.derived[List[Field]],
                fragmentInputDefinition => Some(fragmentInputDefinition.fields)
              ),
              SProductField(
                FieldName("outputName"),
                Schema.string,
                fragmentOutputDefinition => Some(fragmentOutputDefinition.outputName)
              ),
              SProductField(
                FieldName("type"),
                FragmentOutputDefinitionSchemaHelper.fragmentOutputDefinitionTypeSchema,
                _ => Some(FragmentOutputDefinitionSchemaHelper.NodeTypes.FragmentOutputDefinition)
              ),
            )
          )
        )

      private object JoinSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object Join extends NodeTypes
        }

        implicit lazy val joinTypeSchema: Schema[NodeTypes] = Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val joinSchema: Schema[Join] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                join => Some(join.additionalFields)
              ),
              SProductField(
                FieldName("branchParameters"),
                Schema.schemaForIterable[BranchParameters, List],
                join => Some(join.branchParameters)
              ),
              SProductField(
                FieldName("branchParametersTemplate"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                _ => None
              ),
              SProductField(FieldName("id"), Schema.string, join => Some(join.id)),
              SProductField(FieldName("nodeType"), Schema.string, join => Some(join.nodeType)),
              SProductField(FieldName("outputVar"), Schema.schemaForOption[String], join => Some(join.outputVar)),
              SProductField(
                FieldName("parameters"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                join => Some(join.parameters)
              ),
              SProductField(
                FieldName("type"),
                JoinSchemaHelper.joinTypeSchema,
                _ => Some(JoinSchemaHelper.NodeTypes.Join)
              ),
            )
          )
        )

      private object ProcessorSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object Processor extends NodeTypes
        }

        implicit lazy val processorTypeSchema: Schema[NodeTypes] =
          Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val processorSchema: Schema[Processor] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                processor => Some(processor.additionalFields)
              ),
              SProductField(
                FieldName("branchParametersTemplate"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                _ => None
              ),
              SProductField(FieldName("id"), Schema.string, processor => Some(processor.id)),
              SProductField(
                FieldName("isDisabled"),
                Schema.schemaForOption[Boolean],
                processor => Some(processor.isDisabled)
              ),
              SProductField(FieldName("service"), serviceRefSchema, processor => Some(processor.service)),
              SProductField(
                FieldName("type"),
                ProcessorSchemaHelper.processorTypeSchema,
                _ => Some(ProcessorSchemaHelper.NodeTypes.Processor)
              ),
            )
          )
        )

      private object SinkSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object Sink extends NodeTypes
        }

        implicit val sinkTypeSchema: Schema[NodeTypes] = Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val sinkSchema: Schema[Sink] = {
        implicit lazy val sinkRefSchema: Schema[SinkRef] = Schema.derived
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                sink => Some(sink.additionalFields)
              ),
              SProductField(
                FieldName("branchParametersTemplate"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                _ => None
              ),
              SProductField(FieldName("id"), Schema.string, sink => Some(sink.id)),
              SProductField(FieldName("isDisabled"), Schema.schemaForOption[Boolean], sink => Some(sink.isDisabled)),
              SProductField(FieldName("ref"), sinkRefSchema, sink => Some(sink.ref)),
              SProductField(
                FieldName("type"),
                SinkSchemaHelper.sinkTypeSchema,
                _ => Some(SinkSchemaHelper.NodeTypes.Sink)
              ),
            )
          )
        )
      }

      private object SourceSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object Source extends NodeTypes
        }

        implicit val sourceTypeSchema: Schema[NodeTypes] = Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val sourceSchema: Schema[Source] = {
        implicit lazy val sourceRefSchema: Schema[SourceRef] = Schema.derived
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(FieldName("id"), Schema.string, source => Some(source.id)),
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                source => Some(source.additionalFields)
              ),
              SProductField(
                FieldName("type"),
                SourceSchemaHelper.sourceTypeSchema,
                _ => Some(SourceSchemaHelper.NodeTypes.Source)
              ),
              SProductField(
                FieldName("branchParametersTemplate"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                _ => None
              ),
              SProductField(FieldName("ref"), sourceRefSchema, source => Some(source.ref))
            )
          )
        )
      }

      private object SplitSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object Split extends NodeTypes
        }

        implicit lazy val splitTypeSchema: Schema[NodeTypes] = Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val splitSchema: Schema[Split] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(FieldName("id"), Schema.string, split => Some(split.id)),
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                split => Some(split.additionalFields)
              ),
              SProductField(
                FieldName("type"),
                SplitSchemaHelper.splitTypeSchema,
                _ => Some(SplitSchemaHelper.NodeTypes.Split)
              ),
              SProductField(
                FieldName("branchParametersTemplate"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                _ => None
              ),
            )
          )
        )

      private object SwitchSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object Switch extends NodeTypes
        }

        implicit lazy val switchTypeSchema: Schema[NodeTypes] = Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val switchSchema: Schema[Switch] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                switch => Some(switch.additionalFields)
              ),
              SProductField(
                FieldName("branchParametersTemplate"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                _ => None
              ),
              SProductField(
                FieldName("expression"),
                Schema.schemaForOption[Expression],
                switch => Some(switch.expression)
              ),
              SProductField(FieldName("exprVal"), Schema.schemaForOption[String], switch => Some(switch.exprVal)),
              SProductField(FieldName("id"), Schema.string, switch => Some(switch.id)),
              SProductField(
                FieldName("type"),
                SwitchSchemaHelper.switchTypeSchema,
                _ => Some(SwitchSchemaHelper.NodeTypes.Switch)
              ),
            )
          )
        )

      private object VariableSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object Variable extends NodeTypes
        }

        implicit val variableTypeSchema: Schema[NodeTypes] = Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val variableSchema: Schema[Variable] = {
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(FieldName("id"), Schema.string, variable => Some(variable.id)),
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                variable => Some(variable.additionalFields)
              ),
              SProductField(
                FieldName("type"),
                VariableSchemaHelper.variableTypeSchema,
                _ => Some(VariableSchemaHelper.NodeTypes.Variable)
              ),
              SProductField(FieldName("varName"), Schema.string, variable => Some(variable.varName)),
              SProductField(FieldName("value"), expressionSchema, variable => Some(variable.value))
            )
          ),
          Some(SName("Variable"))
        )
      }

      private object VariableBuilderSchemaHelper {
        sealed trait NodeTypes

        object NodeTypes {
          case object VariableBuilder extends NodeTypes
        }

        implicit lazy val variableBuilderTypeSchema: Schema[NodeTypes] =
          Schema.derivedEnumeration[NodeTypes].defaultStringBased
      }

      implicit lazy val variableBuilderSchema: Schema[VariableBuilder] =
        Schema(
          SchemaType.SProduct(
            List(
              SProductField(
                FieldName("additionalFields"),
                Schema.schemaForOption(userDefinedAdditionalNodeFieldsSchema),
                variableBuilder => Some(variableBuilder.additionalFields)
              ),
              SProductField(
                FieldName("branchParametersTemplate"),
                Schema.schemaForIterable[EvaluatedParameter, List],
                _ => None
              ),
              SProductField(FieldName("id"), Schema.string, variableBuilder => Some(variableBuilder.id)),
              SProductField(
                FieldName("fields"),
                Schema.schemaForIterable[Field, List],
                variableBuilder => Some(variableBuilder.fields)
              ),
              SProductField(
                FieldName("type"),
                VariableBuilderSchemaHelper.variableBuilderTypeSchema,
                _ => Some(VariableBuilderSchemaHelper.NodeTypes.VariableBuilder)
              ),
              SProductField(FieldName("varName"), Schema.string, variableBuilder => Some(variableBuilder.varName)),
            )
          ),
          Some(SName("VariableBuilder"))
        )

      implicit lazy val nodeDataSchema: Schema[NodeData] = {
        Schema(
          SchemaType.SCoproduct(
            List(
              branchEndDataSchema.title("BranchEndData"),
              customNodeSchema.title("CustomNode"),
              enricherSchema.title("Enricher"),
              filterSchema.title("Filter"),
              fragmentInputSchema.title("FragmentInput"),
              fragmentInputDefinitionSchema.title("FragmentInputDefinition"),
              fragmentOutputDefinitionSchema.title("FragmentOutputDefinition"),
              joinSchema.title("Join"),
              processorSchema.title("Processor"),
              sinkSchema.title("Sink"),
              sourceSchema.title("Source"),
              splitSchema.title("Split"),
              switchSchema.title("Switch"),
              variableSchema.title("Variable"),
              variableBuilderSchema.title("VariableBuilder")
            ),
            None
          ) {
            case branchEnd: BranchEndData     => Some(SchemaWithValue(branchEndDataSchema, branchEnd))
            case customNode: CustomNode       => Some(SchemaWithValue(customNodeSchema, customNode))
            case enricher: Enricher           => Some(SchemaWithValue(enricherSchema, enricher))
            case filter: Filter               => Some(SchemaWithValue(filterSchema, filter))
            case fragmentInput: FragmentInput => Some(SchemaWithValue(fragmentInputSchema, fragmentInput))
            case fragmentInputDefinition: FragmentInputDefinition =>
              Some(SchemaWithValue(fragmentInputDefinitionSchema, fragmentInputDefinition))
            case fragmentOutputDefinition: FragmentOutputDefinition =>
              Some(SchemaWithValue(fragmentOutputDefinitionSchema, fragmentOutputDefinition))
            case join: Join                       => Some(SchemaWithValue(joinSchema, join))
            case processor: Processor             => Some(SchemaWithValue(processorSchema, processor))
            case sink: Sink                       => Some(SchemaWithValue(sinkSchema, sink))
            case source: Source                   => Some(SchemaWithValue(sourceSchema, source))
            case split: Split                     => Some(SchemaWithValue(splitSchema, split))
            case switch: Switch                   => Some(SchemaWithValue(switchSchema, switch))
            case variable: Variable               => Some(SchemaWithValue(variableSchema, variable))
            case variableBuilder: VariableBuilder => Some(SchemaWithValue(variableBuilderSchema, variableBuilder))
//          This one is more of internal so we don't provide schema for it for outside world
            case _: FragmentUsageOutput => None
          },
          Some(SName("NodeData"))
        )
      }

    }

    // Request doesn't need valid encoder, apart from examples
    @derive(encoder, decoder, schema)
    final case class NodeValidationRequestDto(
        nodeData: NodeData,
        processProperties: ProcessProperties,
        variableTypes: Map[String, TypingResultInJson],
        branchVariableTypes: Option[Map[String, Map[String, TypingResultInJson]]],
        outgoingEdges: Option[List[Edge]],
        testCases: Option[NodeTestCases],
    )

    // Response doesn't need valid decoder
    @derive(encoder, schema)
    final case class NodeValidationResultDto(
        parameters: Option[List[UIParameter]],
        expressionType: Option[TypingResult],
        validationErrors: List[NodeValidationError],
        validationPerformed: Boolean,
        testCasesValidationErrors: Option[NodeTestCasesValidationErrors],
    )

    object NodeValidationResultDto {
      implicit val nodeValidationResultDtoDecoder: Decoder[NodeValidationResultDto] =
        Decoder.failedWithMessage("NodeValidationResultDto should never be decoded. Exists only for Tapir purposes")

      implicit lazy val parameterEditorSchema: Schema[ParameterEditor]     = Schema.derived
      implicit lazy val durationSchema: Schema[Duration]                   = Schema.schemaForJavaDuration
      implicit lazy val uiParameterSchema: Schema[UIParameter]             = Schema.derived
      implicit lazy val parameterCategorySchema: Schema[ParameterCategory] = Schema.derived

      implicit lazy val timeSchema: Schema[java.time.temporal.ChronoUnit] = Schema(
        SProduct(
          List(
            SProductField(FieldName("name"), Schema.schemaForString, chronoUnit => Some(chronoUnit.name())),
            SProductField(FieldName("duration"), durationSchema, chronoUnit => Some(chronoUnit.getDuration))
          )
        )
      )

      def apply(node: NodeValidationResult): NodeValidationResultDto = {
        new NodeValidationResultDto(
          parameters = node.parameters,
          expressionType = node.expressionType,
          validationErrors = node.validationErrors,
          validationPerformed = node.validationPerformed,
          testCasesValidationErrors = node.testCasesValidationErrors,
        )
      }

    }

    type NodeTestCases = Map[TestCaseName, NodeTestCase]

    @derive(schema, encoder, decoder)
    final case class NodeTestCase(
        enricherMock: Option[EnricherMock],
        assertions: List[Assertion],
    )

    implicit val enricherMockSchema: Schema[EnricherMock] = Schema.derived

    implicit val expressionAssertionSchema: Schema[Assertion.ExpressionAssertion] =
      Schema.derived[Assertion.ExpressionAssertion]
    implicit val operatorAssertionSchema: Schema[Assertion.AssertionOperator] =
      Schema.derivedEnumeration[Assertion.AssertionOperator](encode = Some(v => v.entryName))
    implicit val predicateAssertionSchema: Schema[Assertion.PredicateAssertion] =
      Schema.derived[Assertion.PredicateAssertion]

    implicit val assertionSchema: Schema[Assertion] = {
      Schema(
        SchemaType.SCoproduct(
          List(
            expressionAssertionSchema.title("ExpressionAssertion"),
            predicateAssertionSchema.title("PredicateAssertion")
          ),
          discriminator = None
        ) {
          case expr: Assertion.ExpressionAssertion => Some(SchemaWithValue(expressionAssertionSchema, expr))
          case pred: Assertion.PredicateAssertion  => Some(SchemaWithValue(predicateAssertionSchema, pred))
        },
        Some(SName("Assertion"))
      )
    }

    implicit val enricherMockValidationErrorSchema: Schema[EnricherMockValidationError] =
      Schema.derived[EnricherMockValidationError]
    implicit val enricherMockErrorsSchema: Schema[NonEmptyList[EnricherMockValidationError]] =
      Schema.derived[List[EnricherMockValidationError]].as[NonEmptyList[EnricherMockValidationError]]

    implicit val assertionValidationErrorSchema: Schema[AssertionValidationError] =
      Schema.derived[AssertionValidationError]
    implicit val assertionValidationErrorsSchema: Schema[NonEmptyList[AssertionValidationError]] =
      Schema.derived[List[AssertionValidationError]].as[NonEmptyList[AssertionValidationError]]
    implicit val assertionsErrorSchema: Schema[Map[AssertionIndex, NonEmptyList[AssertionValidationError]]] =
      Schema.schemaForMap[AssertionIndex, NonEmptyList[AssertionValidationError]](_.toString)

    implicit val nodeTestCaseValidationErrorsSchema: Schema[NodeTestCaseValidationErrors] =
      Schema.derived[NodeTestCaseValidationErrors]

    implicit val scenarioNameSchema: Schema[ProcessName] = Schema.string

    @derive(schema, encoder, decoder)
    final case class PropertiesValidationRequestDto(
        additionalFields: ProcessAdditionalFields,
        name: ProcessName
    )

    @derive(schema, encoder, decoder)
    final case class TestCaseAdditionalVariablesRequestDto(
        variableTypes: Map[String, TypingResultInJson],
    )

    @derive(schema, encoder, decoder)
    final case class TestCaseAdditionalVariablesResponseDto(
        assertionsAdditionalVariables: Map[String, TypingResult]
    )

    final case class GenerateEnricherMockRequestDto(
        variableTypes: Map[String, TypingResultInJson],
        processProperties: ProcessProperties,
        enricher: Enricher,
    )

    object GenerateEnricherMockRequestDto {
      import io.circe.generic.semiauto._

      implicit lazy val enricherEncoderImplicit: Encoder[Enricher] = Encoder[NodeData].contramap(identity)

      implicit lazy val enricherDecoderImplicit: Decoder[Enricher] = Decoder[NodeData].emap {
        case e: Enricher => Right(e)
        case other       => Left(s"Expected Enricher but got ${other.getClass.getSimpleName}")
      }

      implicit lazy val generateEnricherMockRequestDtoEncoder: Encoder[GenerateEnricherMockRequestDto] =
        deriveEncoder[GenerateEnricherMockRequestDto]
      implicit lazy val generateEnricherMockRequestDtoDecoder: Decoder[GenerateEnricherMockRequestDto] =
        deriveDecoder[GenerateEnricherMockRequestDto]

      implicit lazy val sampleEnricherMockRequestDtoSchema: Schema[GenerateEnricherMockRequestDto] = {
        import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodeDataSchemas._
        Schema.derived[GenerateEnricherMockRequestDto]
      }

    }

    @derive(schema, encoder, decoder)
    final case class GenerateEnricherMockResponseDto(
        enricherMockExpression: Expression,
    )

    // Request doesn't need valid encoder, apart from examples
    @derive(schema, encoder, decoder)
    final case class ParametersValidationRequestDto(
        parameters: List[UIValueParameterDto],
        variableTypes: Map[String, TypingResultInJson]
    )

    @derive(schema, encoder, decoder)
    final case class ParametersValidationResultDto(
        validationErrors: List[NodeValidationError],
        validationPerformed: Boolean
    )

    // Request doesn't need valid encoder, apart from examples
    @derive(schema, encoder, decoder)
    final case class UIValueParameterDto(
        name: String,
        typ: TypingResultInJson,
        expression: Expression
    )

    // Request doesn't need valid encoder, apart from examples
    @derive(schema, encoder, decoder)
    final case class ExpressionSuggestionRequestDto(
        expression: Expression,
        caretPosition2d: CaretPosition2d,
        variableTypes: Map[String, TypingResultInJson]
    )

    // Response doesn't need valid decoder
    @derive(schema, encoder)
    final case class ExpressionSuggestionDto(
        methodName: String,
        refClazz: TypingResult,
        fromClass: Boolean,
        description: Option[String],
        parameters: List[ParameterDto]
    )

    object ExpressionSuggestionDto {

      def apply(expr: ExpressionSuggestion): ExpressionSuggestionDto = {
        new ExpressionSuggestionDto(
          expr.methodName,
          expr.refClazz,
          expr.fromClass,
          expr.description,
          expr.parameters.map(param => ParameterDto(param.name, param.refClazz))
        )
      }

    }

    implicit val expressionSuggestionDtoDecoder: Decoder[ExpressionSuggestionDto] =
      Decoder.instance[ExpressionSuggestionDto](_ => throw new IllegalStateException)

    // Response doesn't need valid decoder
    @derive(schema, encoder)
    final case class ParameterDto(
        name: String,
        refClazz: TypingResult
    )

    def prepareTypingResultDecoder(classLoader: ClassLoader): Decoder[TypingResult] = {
      new TypingResultDecoder(name => ClassUtils.forName(name, classLoader)).decodeTypingResults
    }

    implicit val parameterNameCodec: KeyEncoder[ParameterName]   = KeyEncoder.encodeKeyString.contramap(_.value)
    implicit val parameterNameDecoder: KeyDecoder[ParameterName] = KeyDecoder.decodeKeyString.map(ParameterName.apply)

    implicit val mapParameterNameExpressionSchema: Typeclass[Map[ParameterName, Expression]] =
      Schema.schemaForMap[ParameterName, Expression](_.value)
    implicit val testSourceParametersDecoder: Decoder[TestSourceParameters] =
      deriveConfiguredDecoder[TestSourceParameters]

    @derive(schema, encoder, decoder)
    final case class TestSourceParameters(
        sourceId: String,
        parameterExpressions: Map[ParameterName, Expression]
    )

    final case class ParametersValidationRequest(
        parameters: List[UIValueParameter],
        variableTypes: Map[String, TypingResult]
    )

    final case class NodeValidationResult(
        // It it used for node parameter adjustment on FE side (see ParametersUtils.ts -> adjustParameters)
        parameters: Option[List[UIParameter]],
        // expressionType is returned to present inferred types of a single, hardcoded parameter of the node
        // We currently support only type inference for an expression in the built-in components: variable and switch
        // and fields of the record-variable and fragment output (we return TypedObjectTypingResult in this case)
        // TODO: We should keep this in a map, instead of TypedObjectTypingResult as it is done in ValidationResult.typingInfo
        //       Thanks to that we could remove some code on the FE side and be closer to support also not built-in components
        expressionType: Option[TypingResult],
        validationErrors: List[NodeValidationError],
        validationPerformed: Boolean,
        testCasesValidationErrors: Option[NodeTestCasesValidationErrors],
    )

    final case class NodeValidationRequest(
        nodeData: NodeData,
        processProperties: ProcessProperties,
        variableTypes: Map[String, TypingResult],
        branchVariableTypes: Option[Map[String, Map[String, TypingResult]]],
        // TODO: remove Option when FE is ready
        // In this request edges are not guaranteed to have the correct "from" field. Normally it's synced with node id but
        // when renaming node, it contains node's id before the rename.
        outgoingEdges: Option[List[Edge]],
        testCases: Option[NodeTestCases],
    )

    def decodeVariableTypes(
        variableTypes: Map[String, Dtos.TypingResultInJson],
        typingResultDecoder: Decoder[TypingResult]
    ): Either[MalformedTypingResult, Map[String, TypingResult]] = {
      variableTypes.toList
        .map { case (key, typingResult) =>
          (key, typingResultDecoder.decodeJson(typingResult))
        }
        .map { case (key, maybeValue) =>
          maybeValue.left.map(failure => MalformedTypingResult(failure.message)).map((key, _))
        }
        .sequence
        .map(_.toMap)
    }

    sealed trait NodesError

    object NodesError {
      sealed trait BadRequestNodesError extends NodesError
      sealed trait NotFoundNodesError   extends NodesError
      sealed trait ForbiddenNodesError  extends NodesError
      sealed trait NoContentNodesError  extends NodesError

      object BadRequestNodesError {
        case class SourceCompilation(nodeId: String, nodeName: String, errors: List[String])
            extends BadRequestNodesError
        case class UnsupportedSourcePreview(nodeId: String, nodeName: String) extends BadRequestNodesError
        case class InvalidNodeType(expectedType: String, actualType: String)  extends BadRequestNodesError
        case class TooManyRecordsRequested(maxRecordsCount: Int)              extends BadRequestNodesError
        case class MalformedTypingResult(msg: String)                         extends BadRequestNodesError
        case class TooManyCharactersGenerated(length: Int, limit: Int)        extends BadRequestNodesError

        object EnricherMockExpressionGenerationError {
          final case class CompilationErrors(errors: NonEmptyList[ProcessCompilationError]) extends BadRequestNodesError
          final case class OutputVariableNotFound(outputVariableName: String)               extends BadRequestNodesError
        }

        implicit val badRequestNodesErrorCodec: Codec[String, BadRequestNodesError, CodecFormat.TextPlain] =
          BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[BadRequestNodesError] {
            case SourceCompilation(nodeId, nodeName, errors) =>
              s"Cannot compile source '$nodeName' (id: $nodeId). Errors: ${errors.mkString(", ")}"
            case UnsupportedSourcePreview(nodeId, nodeName) =>
              s"Source '$nodeName' (id: $nodeId) doesn't support records preview"
            case InvalidNodeType(expectedType, actualType) => s"Expected $expectedType but got: ${actualType}"
            case TooManyRecordsRequested(maxRecordsCount) =>
              TestingApiErrorMessages.liveDataFetching.requestedTooManyRecordsToFetch(maxRecordsCount)
            case MalformedTypingResult(msg) => s"The request content was malformed:\n${msg}"
            case TooManyCharactersGenerated(length, limit) =>
              TestingApiErrorMessages.liveDataFetching.tooManyCharacters(length, limit)
            case EnricherMockExpressionGenerationError.CompilationErrors(errors) =>
              s"Cannot compile enricher: ${errors.toList.map(e => PrettyValidationErrors.formatErrorMessage(e).message).mkString(", ")}"
            case EnricherMockExpressionGenerationError.OutputVariableNotFound(outputVariableName) =>
              s"Enricher output variable '$outputVariableName' not found"
          }

        implicit val malformedTypingResultCodec: Codec[String, MalformedTypingResult, CodecFormat.TextPlain] = {
          BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[MalformedTypingResult](e =>
            s"The request content was malformed:\n${e.msg}"
          )
        }

      }

      object NotFoundNodesError {
        case class NoScenario(scenarioName: ProcessName)            extends NotFoundNodesError
        case object NoLiveDataAvailable                             extends NotFoundNodesError
        case class NoProcessingType(processingType: ProcessingType) extends NotFoundNodesError

        implicit val notFoundNodesErrorCodec: Codec[String, NotFoundNodesError, CodecFormat.TextPlain] =
          BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NotFoundNodesError] {
            case NoScenario(scenarioName)         => s"No scenario $scenarioName found"
            case NoLiveDataAvailable              => TestingApiErrorMessages.liveDataFetching.noLiveDataAvailable
            case NoProcessingType(processingType) => s"ProcessingType type: $processingType not found"
          }

        implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
          BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e =>
            s"No scenario ${e.scenarioName} found"
          )
        }

        implicit val noProcessingTypeCodec: Codec[String, NoProcessingType, CodecFormat.TextPlain] = {
          BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoProcessingType](e =>
            s"ProcessingType type: ${e.processingType} not found"
          )
        }

      }

      object ForbiddenNodesError {
        case object NoPermission extends ForbiddenNodesError with CustomAuthorizationError
      }

      object NoContentNodesError {

        object EnricherMockExpressionGenerationError {
          final case class UnsupportedExpressionType(typingResult: TypingResult) extends NoContentNodesError
        }

        implicit val noContentNodesErrorCodec: Codec[String, NoContentNodesError, CodecFormat.TextPlain] =
          BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoContentNodesError] {
            case EnricherMockExpressionGenerationError.UnsupportedExpressionType(typingResult) =>
              s"Cannot generate expression for type: $typingResult"
          }

      }

    }

    @derive(schema, encoder, decoder)
    final case class RecordsRequestDto(
        processProperties: ProcessProperties,
        nodeData: NodeData
    )

  }

}

object TypingDtoSchemas {

  import pl.touk.nussknacker.engine.api.typed.typing._
  import sttp.tapir.{FieldName, Schema, SchemaType}
  import sttp.tapir.Schema.SName
  import sttp.tapir.SchemaType.SProductField

  implicit lazy val typingResult: Schema[TypingResult] = {
    Schema(
      SchemaType.SCoproduct(
        List(
          unknownSchema,
          typedNullSchema,
          typedObjectTypingResultSchema,
          typedDictSchema,
          typedObjectSchema,
          typedClassSchema,
          typedUnionSchema,
          typedTaggedSchema
        ),
        None
      ) {
        case u: Unknown                           => Some(SchemaWithValue(unknownSchema, u))
        case TypedNull                            => Some(SchemaWithValue(typedNullSchema, TypedNull))
        case typedObject: TypedObjectTypingResult => Some(SchemaWithValue(typedObjectTypingResultSchema, typedObject))
        case typedDict: TypedDict                 => Some(SchemaWithValue(typedDictSchema, typedDict))
        case typedWithValue: TypedObjectWithValue => Some(SchemaWithValue(typedObjectSchema, typedWithValue))
        case typedClass: TypedClass               => Some(SchemaWithValue(typedClassSchema, typedClass))
        case union: TypedUnion                    => Some(SchemaWithValue(typedUnionSchema, union))
        case tagged: TypedTaggedValue             => Some(SchemaWithValue(typedTaggedSchema, tagged))
      }
    )
  }

  implicit lazy val singleTypingResultSchema: Schema[SingleTypingResult]   = Schema.derived
  implicit lazy val additionalDataValueSchema: Schema[AdditionalDataValue] = Schema.derived

//  Tapir currently supports only json schema v4 which has no way to declare discriminator
//  We declare that each type of TypingResult belongs to an enum with only one value as a workaround for this problem
  object TypedObjectTypingResultSchemaHelper {
    sealed trait Types

    object Types {
      case object TypedObjectTypingResult extends Types
    }

    implicit val typedObjectTypingResultTypeSchema: Schema[Types] = Schema.derivedEnumeration[Types].defaultStringBased
  }

  implicit lazy val typedObjectTypingResultSchema: Schema[TypedObjectTypingResult] = {
    Schema(
      SchemaType.SProduct(
        List(
          sProductFieldForDisplay,
          SProductField[TypingResult, TypedObjectTypingResultSchemaHelper.Types](
            FieldName("type"),
            typedObjectTypingResultTypeSchema,
            _ => Some(TypedObjectTypingResultSchemaHelper.Types.TypedObjectTypingResult)
          ),
          SProductField[TypingResult, Map[String, TypingResult]](
            FieldName("fields"),
            Schema.schemaForMap[TypingResult](Schema.derived[TypingResult]),
            _ => None
          )
        ) :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedObjectTypingResult"))
    )
      .title("TypedObjectTypingResult")
      .as
  }

  object TypedDictSchemaHelper {
    sealed trait Types

    object Types {
      case object TypedDict extends Types
    }

    implicit val typedDictTypeSchema: Schema[Types] = Schema.derivedEnumeration[Types].defaultStringBased

  }

  implicit lazy val typedDictSchema: Schema[TypedDict] = {
    final case class Dict(id: String, valueType: SingleTypingResult)
    lazy val dictSchema: Schema[Dict] = Schema.derived
    Schema(
      SchemaType.SProduct(
        List(
          sProductFieldForDisplay,
          SProductField[TypingResult, TypedDictSchemaHelper.Types](
            FieldName("type"),
            typedDictTypeSchema,
            _ => Some(TypedDictSchemaHelper.Types.TypedDict)
          ),
        ) :::
          List(SProductField[TypingResult, Dict](FieldName("dict"), dictSchema, _ => None))
      ),
      Some(SName("TypedDict"))
    )
      .title("TypedDict")
      .as
  }

  implicit lazy val typedObjectWithDataSchema: Schema[TypedObjectWithData] =
    Schema.derived.hidden(true)

  object TypedTaggedSchemaHelper {
    sealed trait Types

    object Types {
      case object TypedTaggedValue extends Types
    }

    implicit val typedTaggedTypeSchema: Schema[Types] = Schema.derivedEnumeration[Types].defaultStringBased
  }

  implicit lazy val typedTaggedSchema: Schema[TypedTaggedValue] = {
    Schema(
      SchemaType.SProduct(
        List(
          SProductField[TypingResult, String](FieldName("tag"), Schema.string, _ => None),
          sProductFieldForDisplay,
          SProductField[TypingResult, TypedTaggedSchemaHelper.Types](
            FieldName("type"),
            typedTaggedTypeSchema,
            _ => Some(TypedTaggedSchemaHelper.Types.TypedTaggedValue)
          ),
        )
      ),
      Some(SName("TypedTaggedValue"))
    )
      .title("TypedTaggedValue")
      .as
  }

  object TypedObjectSchemaHelper {
    sealed trait Types

    object Types {
      case object TypedObjectWithValue extends Types
    }

    implicit val typedObjectTypeSchema: Schema[Types] = Schema.derivedEnumeration[Types].defaultStringBased

  }

  implicit lazy val typedObjectSchema: Schema[TypedObjectWithValue] = {
    Schema(
      SchemaType.SProduct(
        List(
          SProductField[TypingResult, Any](FieldName("value"), Schema.any, value => Some(value)),
          sProductFieldForDisplay,
          SProductField[TypingResult, TypedObjectSchemaHelper.Types](
            FieldName("type"),
            typedObjectTypeSchema,
            _ => Some(TypedObjectSchemaHelper.Types.TypedObjectWithValue)
          ),
        ) :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedObjectWithValue"))
    )
      .title("TypedObjectWithValue")
      .as
  }

  object TypedNullSchemaHelper {
    sealed trait Types

    object Types {
      case object TypedNull extends Types
    }

    implicit val typedNullTypeSchema: Schema[Types] = Schema.derivedEnumeration[Types].defaultStringBased

  }

  implicit lazy val typedNullSchema: Schema[TypedNull.type] =
    Schema(
      SchemaType.SProduct(
        List(
          sProductFieldForDisplay,
          SProductField[TypingResult, TypedNullSchemaHelper.Types](
            FieldName("type"),
            typedNullTypeSchema,
            _ => Some(TypedNullSchemaHelper.Types.TypedNull)
          ),
          SProductField[TypingResult, String](
            FieldName("refClazzName"),
            Schema(SString(), isOptional = true),
            _ => None
          ),
          SProductField[TypingResult, List[TypingResult]](
            FieldName("params"),
            Schema.schemaForIterable[TypingResult, List](
              Schema.derived[TypingResult]
            ),
            _ => Some(List(Unknown))
          )
        )
      ),
      Some(SName("TypedNull")),
    )
      .title("TypedNull")
      .as

  object UnknownSchemaHelper {
    sealed trait Types

    object Types {
      case object Unknown extends Types
    }

    implicit val unknownTypeSchema: Schema[Types] = Schema.derivedEnumeration[Types].defaultStringBased

  }

  implicit lazy val unknownSchema: Schema[Unknown] =
    Schema(
      SchemaType.SProduct(
        List(
          sProductFieldForDisplay,
          SProductField[TypingResult, UnknownSchemaHelper.Types](
            FieldName("type"),
            unknownTypeSchema,
            _ => Some(UnknownSchemaHelper.Types.Unknown)
          ),
          SProductField[TypingResult, String](
            FieldName("refClazzName"),
            Schema(SString(), isOptional = true),
            _ => None
          ),
          SProductField[TypingResult, List[TypingResult]](
            FieldName("params"),
            Schema.schemaForIterable[TypingResult, List](
              Schema.derived[TypingResult]
            ),
            _ => Some(List(Unknown))
          )
        )
      ),
      Some(SName("Unknown")),
    )
      .title("Unknown")
      .as

  object TypedUnionSchemaHelper {
    sealed trait Types

    object Types {
      case object TypedUnion extends Types
    }

    implicit val typedUnionTypeSchema: Schema[Types] = Schema.derivedEnumeration[Types].defaultStringBased

  }

  implicit lazy val typedUnionSchema: Schema[TypedUnion] = {
    Schema(
      SchemaType.SProduct(
        List(
          sProductFieldForDisplay,
          SProductField[TypingResult, TypedUnionSchemaHelper.Types](
            FieldName("type"),
            typedUnionTypeSchema,
            _ => Some(TypedUnionSchemaHelper.Types.TypedUnion)
          ),
          SProductField[TypingResult, NonEmptyList[TypingResult]](
            FieldName("union"),
            Schema
              .schemaForArray[TypingResult](Schema.derived[TypingResult])
              .copy(isOptional = false)
              .as,
            _ => Some(NonEmptyList(Unknown, List.empty))
          )
        )
      ),
      Some(Schema.SName("TypedUnion"))
    )
      .title("TypedUnion")
      .as
  }

  object TypedClassSchemaHelper {
    sealed trait Types

    object Types {
      case object TypedClass extends Types
    }

    implicit val typedClassTypeSchema: Schema[Types] = Schema.derivedEnumeration[Types].defaultStringBased

  }

  implicit lazy val typedClassSchema: Schema[TypedClass] = {
    Schema(
      SchemaType.SProduct(
        List(
          sProductFieldForDisplay,
          SProductField[TypingResult, TypedClassSchemaHelper.Types](
            FieldName("type"),
            typedClassTypeSchema,
            _ => Some(TypedClassSchemaHelper.Types.TypedClass)
          ),
        ) :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedClass")),
    )
      .title("TypedClass")
      .as
  }

  private lazy val sProductFieldForDisplay: SProductField[TypingResult] =
    SProductField[TypingResult, String](
      FieldName("display"),
      Schema(SString(), isOptional = true),
      typingResult => Some(typingResult.display)
    )

  private lazy val sProductFieldForKlassAndParams: List[SProductField[TypingResult]] = {

    List(
      SProductField[TypingResult, String](FieldName("refClazzName"), Schema.string, _ => None),
      SProductField[TypingResult, List[TypingResult]](
        FieldName("params"),
        Schema.schemaForIterable[TypingResult, List](
          Schema.derived[TypingResult]
        ),
        _ => Some(List(Unknown))
      )
    )
  }

}
