package pl.touk.nussknacker.ui.api

import cats.data.NonEmptyList
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.apache.pekko.http.scaladsl.model.{ContentTypeRange, StatusCodes, Uri}
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.scalatest.{Assertion, BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{NodeId, NodeName, StreamMetaData}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.dict.{ProcessDictSubstitutor, SimpleDictRegistry}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{NodeData, Source}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.test.testcase._
import pl.touk.nussknacker.engine.test.testcase.Assertion.{AssertionOperator, PredicateAssertion}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.restmodel.validation.testcase.{
  AssertionValidationError,
  EnricherMockValidationError,
  NodeTestCaseValidationErrors
}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.domain.TestFactory.{mapProcessingTypeDataProvider, withPermissions}
import pl.touk.nussknacker.test.utils.scalas.PekkoHttpExtensions.toRequestEntity
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

import java.util.UUID

class ValidationResourcesSpec
    extends AnyFlatSpec
    with ScalatestRouteTest
    with FailFastCirceSupport
    with Matchers
    with PatientScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuResourcesTest {

  private implicit final val string: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val processValidatorByProcessingType = mapProcessingTypeDataProvider(
    Streaming.stringify -> new UIProcessResolver(
      ProcessTestData.testProcessValidator(scenarioProperties =
        Map(
          "requiredStringProperty" -> ScenarioPropertyConfig(
            None,
            Some(StaticStringParameterEditor),
            Some(List(MandatoryParameterValidator)),
            Some("label"),
            None
          ),
          "numberOfThreads" -> ScenarioPropertyConfig(
            None,
            Some(FixedValuesParameterEditor(TestFactory.possibleValues)),
            Some(List(FixedValuesValidator(TestFactory.possibleValues))),
            None,
            None
          ),
          "maxEvents" -> ScenarioPropertyConfig(
            None,
            None,
            Some(List(LiteralIntegerValidator)),
            Some("label"),
            None
          )
        )
      ),
      ProcessDictSubstitutor(new SimpleDictRegistry(Map.empty))
    )
  )

  private val route: Route = withPermissions(
    new ValidationResources(
      processService,
      processValidatorByProcessingType
    ),
    Permission.Read
  )

  it should "find errors in a bad scenario" in {
    createAndValidateScenario(ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("MissingSourceFactory")
    }
  }

  it should "find errors in scenario with Mandatory parameters" in {
    createAndValidateScenario(ProcessTestData.invalidProcessWithEmptyMandatoryParameter) {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("Field: expression is mandatory and can not be empty")
    }
  }

  it should "find errors in scenario with NotBlank parameters" in {
    createAndValidateScenario(ProcessTestData.invalidProcessWithBlankParameter) {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("This field value is required and can not be blank")
    }
  }

  it should "find errors in scenario properties" in {
    createAndValidateScenario(ProcessTestData.scenarioGraphWithInvalidScenarioProperties) {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("Configured property requiredStringProperty (label) is missing")
      entity should include("Property numberOfThreads has invalid value")
      entity should include("This field value has to be an integer number")
    }
  }

  it should "find errors in scenario with wrong fixed expression value" in {
    createAndValidateScenario(ProcessTestData.invalidProcessWithWrongFixedExpressionValue) {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("Unexpected text")
    }
  }

  it should "find errors in scenario id" in {
    createAndValidateScenario(ProcessTestData.validProcessWithName(ProcessName(" "))) {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("Scenario name cannot be blank")
    }
  }

  it should "find errors in node id" in {
    createAndValidateScenario(ProcessTestData.validProcessWithNodeId(" ")) {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("Node name cannot be blank")
    }
  }

  it should "return fatal error for bad ids" in {
    val invalidCharacters = newScenarioGraph(
      List(
        Source(NodeId("s1"), NodeName("s1"), SourceRef(ProcessTestData.existingSourceFactory, List())),
        node.Sink(NodeId("f1\"'"), NodeName("f1\"'"), SinkRef(ProcessTestData.existingSinkFactory, List()), None)
      ),
      List(Edge(NodeId("s1"), NodeId("f1\"'"), None))
    )

    createAndValidateScenario(invalidCharacters, ProcessName("p1")) {
      status shouldEqual StatusCodes.BadRequest
      val entity = entityAs[String]
      entity should include("Node name contains invalid characters")
    }

    val duplicateIds = newScenarioGraph(
      List(
        Source(NodeId("s1"), NodeName("s1"), SourceRef(ProcessTestData.existingSourceFactory, List())),
        node.Sink(NodeId("s1"), NodeName("s1"), SinkRef(ProcessTestData.existingSinkFactory, List()), None)
      ),
      List(Edge(NodeId("s1"), NodeId("s1"), None))
    )

    createAndValidateScenario(duplicateIds, ProcessName("p2")) {
      status shouldEqual StatusCodes.BadRequest
      val entity = entityAs[String]
      entity should include("Duplicate node ids: s1")
    }
  }

  it should "find errors in scenario of bad shape" in {
    val invalidShapeProcess = newScenarioGraph(
      List(
        Source(NodeId("s1"), NodeName("s1"), SourceRef(ProcessTestData.existingSourceFactory, List())),
        node.Filter(NodeId("f1"), NodeName("f1"), Expression.spel("false"))
      ),
      List(Edge(NodeId("s1"), NodeId("f1"), None))
    )

    createAndValidateScenario(invalidShapeProcess, ProcessName("p1")) {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("InvalidTailOfBranch")
    }
  }

  it should "find no errors in a good scenario" in {
    createAndValidateScenario(ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
  }

  it should "find missing mandatory parameter errors in built-in components" in {
    val process =
      ScenarioBuilder
        .streaming("process")
        .source("source", ProcessTestData.existingSourceFactory)
        .filter("filter", Expression.spel(""))
        .buildSimpleVariable("variable1", "varName1", Expression.spel(""))
        .buildSimpleVariable(
          "variable2",
          "varName2",
          Expression.spelTemplate("")
        ) // empty spelTemplate expression should be valid
        .emptySink("sink", ProcessTestData.existingSinkFactory)

    createAndValidateScenario(process) {
      status shouldEqual StatusCodes.OK
      val validation = responseAs[ValidationResult]
      validation.errors.invalidNodes(NodeId("filter")).head.message should include(
        "This field is required and can not be null"
      )
      validation.errors.invalidNodes(NodeId("variable1")).head.message should include(
        "Field: $expression is mandatory and can not be empty"
      )
      validation.errors.invalidNodes.get(NodeId("variable2")) shouldBe None
    }
  }

  it should "warn if scenario has disabled filter or processor" in {
    val nodes = List(
      node.Source(NodeId("source1"), NodeName("source1"), SourceRef(ProcessTestData.existingSourceFactory, List())),
      node.Filter(NodeId("filter1"), NodeName("filter1"), Expression.spel("false"), isDisabled = Some(true)),
      node.Processor(
        NodeId("proc1"),
        NodeName("proc1"),
        ServiceRef(ProcessTestData.existingServiceId, List.empty),
        isDisabled = Some(true)
      ),
      node.Sink(NodeId("sink1"), NodeName("sink1"), SinkRef(ProcessTestData.existingSinkFactory, List.empty))
    )
    val edges = List(
      Edge(NodeId("source1"), NodeId("filter1"), None),
      Edge(NodeId("filter1"), NodeId("proc1"), None),
      Edge(NodeId("proc1"), NodeId("sink1"), None)
    )
    val processWithDisabledFilterAndProcessor = newScenarioGraph(nodes, edges)

    createAndValidateScenario(processWithDisabledFilterAndProcessor, ProcessName("p1")) {
      status shouldEqual StatusCodes.OK
      val validation = responseAs[ValidationResult]
      validation.warnings.invalidNodes(NodeId("filter1")).head.message should include("Node filter1 is disabled")
      validation.warnings.invalidNodes(NodeId("proc1")).head.message should include("Node proc1 is disabled")
    }
  }

  it should "find no errors in valid test case" in {
    val scenarioGraph = ScenarioBuilder
      .streaming("testCaseValidationScenario")
      .additionalFields(properties = Map("requiredStringProperty" -> "some value"))
      .source("source", ProcessTestData.existingSourceFactory)
      .enricher("enricher", "output", ProcessTestData.otherExistingServiceId2, "expression" -> "'value'".spel)
      .emptySink("sink", ProcessTestData.existingSinkFactory)
      .toScenarioGraph
      .copy(
        testCases = Some(
          TestCases.Single(
            TestCase(
              id = UUID.randomUUID(),
              name = "test-case-1",
              inputs = "{}",
              mocks = Map(
                NodeId("enricher") -> EnricherMock("'mocked value'".spel),
              ),
              assertions = Map(
                NodeId("enricher") -> List(
                  PredicateAssertion(
                    operator = AssertionOperator.Equals,
                    actual = "#contexts.size".spel,
                    expected = "3".spel,
                  ),
                  PredicateAssertion(
                    operator = AssertionOperator.Equals,
                    actual = "#contexts[0].input".spel,
                    expected = "'abc'".spel,
                  ),
                ),
                NodeId("sink") -> List(
                  PredicateAssertion(
                    operator = AssertionOperator.Equals,
                    actual = "#contexts[0].output".spel,
                    expected = "'def'".spel,
                  ),
                )
              )
            )
          )
        )
      )

    createAndValidateScenario(scenarioGraph, ProcessName("testCaseValidation")) {
      status shouldEqual StatusCodes.OK
      val validation = responseAs[ValidationResult]
      validation.hasErrors shouldBe false
      validation.hasWarnings shouldBe false
      validation.errors.globalErrors shouldBe empty
      validation.errors.invalidNodes shouldBe empty
      validation.errors.testCasesValidationErrors shouldBe None
    }
  }

  it should "find errors in invalid test case" in {
    val scenarioGraph = ScenarioBuilder
      .streaming("testCaseValidationScenario")
      .additionalFields(properties = Map("requiredStringProperty" -> "some value"))
      .source("source", ProcessTestData.existingSourceFactory)
      .enricher("enricher", "output", ProcessTestData.otherExistingServiceId2, "expression" -> "'value'".spel)
      .emptySink("sink", ProcessTestData.existingSinkFactory)
      .toScenarioGraph
      .copy(
        testCases = Some(
          TestCases.Single(
            TestCase(
              id = UUID.randomUUID(),
              name = "test-case-1",
              inputs = "{}",
              mocks = Map(
                NodeId("enricher") -> EnricherMock("42".spel),
                NodeId("sink")     -> EnricherMock("'some mock'".spel) // mock for non-enricher node
              ),
              assertions = Map(
                NodeId("enricher") -> List(
                  PredicateAssertion(
                    operator = AssertionOperator.Equals,
                    actual = "#contexts.size".spel,
                    expected = "3".spel,
                  ),
                  PredicateAssertion(
                    operator = AssertionOperator.Equals,
                    actual = "#contexts[0].doesNotExist".spel,
                    expected = "'abc'".spel,
                  ),
                  PredicateAssertion(
                    operator = AssertionOperator.Equals,
                    actual = "#contexts[0].input".spel,
                    expected = "'abc'".spel,
                  ),
                ),
                NodeId("sink") -> List(
                  PredicateAssertion(
                    operator = AssertionOperator.Equals,
                    actual = "#contexts[0].doesNotExist2".spel,
                    expected = "'def'".spel,
                  ),
                  PredicateAssertion(
                    operator = AssertionOperator.Equals,
                    actual = "#contexts[0].output".spel,
                    expected = "'def'".spel,
                  ),
                  PredicateAssertion(
                    operator = AssertionOperator.Equals,
                    actual = "#contexts[0].doesNotExist3".spel,
                    expected = "'ghi'".spel,
                  ),
                )
              )
            )
          )
        )
      )

    createAndValidateScenario(scenarioGraph, ProcessName("testCaseValidation")) {
      status shouldEqual StatusCodes.OK
      val validation = responseAs[ValidationResult]
      // For now test cases validation errors do not affect overall validation status
      validation.hasErrors shouldBe false
      validation.hasWarnings shouldBe false
      validation.errors.globalErrors shouldBe empty
      validation.errors.invalidNodes shouldBe empty
      validation.errors.testCasesValidationErrors.value shouldBe Map(
        NodeId("enricher") -> Map(
          "test-case-1" -> NodeTestCaseValidationErrors(
            enricherMockErrors = Some(
              NonEmptyList.one(
                EnricherMockValidationError(
                  "ExpressionParserCompilationError",
                  "Bad expression type, expected: String, found: Integer(42)",
                  "There is problem with expression in field [mockExpression] - it could not be parsed.",
                  None
                )
              )
            ),
            assertionsErrors = Some(
              Map(
                1 -> NonEmptyList.one(
                  AssertionValidationError(
                    typ = "ExpressionParserCompilationError",
                    message = "There is no property 'doesNotExist' in type: Record{input: Unknown}",
                    description = "There is problem with expression in field [actual] - it could not be parsed.",
                    details = Some(CoordinatesBasedTextRange(TextCoordinates(13, 0), TextCoordinates(25, 0))),
                    fieldName = Some("actual"),
                  )
                )
              )
            )
          )
        ),
        NodeId("sink") -> Map(
          "test-case-1" -> NodeTestCaseValidationErrors(
            enricherMockErrors = Some(
              NonEmptyList.one(
                EnricherMockValidationError(
                  typ = "MockForNonEnricherNode",
                  message = "Mock configured for non-enricher node 'sink'",
                  description = "Mocks can only be configured for enricher nodes",
                  details = None,
                )
              )
            ),
            assertionsErrors = Some(
              Map(
                0 -> NonEmptyList.one(
                  AssertionValidationError(
                    typ = "ExpressionParserCompilationError",
                    message = "There is no property 'doesNotExist2' in type: Record{input: Unknown, output: String}",
                    description = "There is problem with expression in field [actual] - it could not be parsed.",
                    details = Some(CoordinatesBasedTextRange(TextCoordinates(13, 0), TextCoordinates(26, 0))),
                    fieldName = Some("actual"),
                  )
                ),
                2 -> NonEmptyList.one(
                  AssertionValidationError(
                    typ = "ExpressionParserCompilationError",
                    message = "There is no property 'doesNotExist3' in type: Record{input: Unknown, output: String}",
                    description = "There is problem with expression in field [actual] - it could not be parsed.",
                    details = Some(CoordinatesBasedTextRange(TextCoordinates(13, 0), TextCoordinates(26, 0))),
                    fieldName = Some("actual"),
                  )
                )
              )
            )
          )
        )
      )
    }
  }

  private def newScenarioGraph(nodes: List[NodeData], edges: List[Edge]): ScenarioGraph = {
    ScenarioGraph(
      properties = ProcessProperties(StreamMetaData(Some(2), Some(false))),
      nodes = nodes,
      edges = edges
    )
  }

  private def createAndValidateScenario(scenario: CanonicalProcess)(testCode: => Assertion): Assertion =
    createAndValidateScenario(scenario.toScenarioGraph, scenario.name)(testCode)

  private def createAndValidateScenario(
      scenarioGraph: ScenarioGraph,
      name: ProcessName = ProcessTestData.sampleProcessName
  )(testCode: => Assertion): Assertion = {
    createEmptyProcess(name)
    validateScenario(scenarioGraph, name)(testCode)
  }

  private def validateScenario(scenarioGraph: ScenarioGraph, name: ProcessName = ProcessTestData.sampleProcessName)(
      testCode: => Assertion
  ) = {
    // TODO: Test for the rename (name in path other then name in request)
    val request = ScenarioValidationRequest(name, scenarioGraph)
    Post(
      Uri(path = Path.Empty / "processValidation" / name.value),
      request.toJsonRequestEntity()
    ) ~> route ~> check {
      testCode
    }
  }

}
