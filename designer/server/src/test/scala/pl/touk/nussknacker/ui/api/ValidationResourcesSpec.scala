package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.component.SingleScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition._
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
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.utils.domain.TestFactory.{mapProcessingTypeDataProvider, withPermissions}
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.scalas.AkkaHttpExtensions.toRequestEntity
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

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
      TestFactory.processValidator.withScenarioPropertiesConfig(
        Map(
          "requiredStringProperty" -> SingleScenarioPropertyConfig(
            None,
            Some(StringParameterEditor),
            Some(List(MandatoryParameterValidator)),
            Some("label"),
            None
          ),
          "numberOfThreads" -> SingleScenarioPropertyConfig(
            None,
            Some(FixedValuesParameterEditor(TestFactory.possibleValues)),
            Some(List(FixedValuesValidator(TestFactory.possibleValues))),
            None,
            None
          ),
          "maxEvents" -> SingleScenarioPropertyConfig(
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
      entity should include("This field is mandatory and can not be empty")
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
      entity should include("Unknown property unknown")
      entity should include("This field value has to be an integer number")
    }
  }

  it should "find errors in scenario with wrong fixed expression value" in {
    createAndValidateScenario(ProcessTestData.invalidProcessWithWrongFixedExpressionValue) {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include("Failed to parse expression")
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
        Source("s1", SourceRef(ProcessTestData.existingSourceFactory, List())),
        node.Sink("f1\"'", SinkRef(ProcessTestData.existingSinkFactory, List()), None)
      ),
      List(Edge("s1", "f1\"'", None))
    )

    createAndValidateScenario(invalidCharacters, ProcessName("p1")) {
      status shouldEqual StatusCodes.BadRequest
      val entity = entityAs[String]
      entity should include("Node name contains invalid characters")
    }

    val duplicateIds = newScenarioGraph(
      List(
        Source("s1", SourceRef(ProcessTestData.existingSourceFactory, List())),
        node.Sink("s1", SinkRef(ProcessTestData.existingSinkFactory, List()), None)
      ),
      List(Edge("s1", "s1", None))
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
        Source("s1", SourceRef(ProcessTestData.existingSourceFactory, List())),
        node.Filter("f1", Expression.spel("false"))
      ),
      List(Edge("s1", "f1", None))
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
    val emptyExpression = Expression.spel("")

    val process =
      ScenarioBuilder
        .streaming("process")
        .source("source", ProcessTestData.existingSourceFactory)
        .filter("filter", emptyExpression)
        .buildSimpleVariable("variable", "varName", emptyExpression)
        .emptySink("sink", ProcessTestData.existingSinkFactory)

    createAndValidateScenario(process) {
      status shouldEqual StatusCodes.OK
      val validation = responseAs[ValidationResult]
      validation.errors.invalidNodes("filter").head.message should include(
        "This field is required and can not be null"
      )
      validation.errors.invalidNodes("variable").head.message should include(
        "This field is mandatory and can not be empty"
      )
    }
  }

  it should "warn if scenario has disabled filter or processor" in {
    val nodes = List(
      node.Source("source1", SourceRef(ProcessTestData.existingSourceFactory, List())),
      node.Filter("filter1", Expression.spel("false"), isDisabled = Some(true)),
      node.Processor("proc1", ServiceRef(ProcessTestData.existingServiceId, List.empty), isDisabled = Some(true)),
      node.Sink("sink1", SinkRef(ProcessTestData.existingSinkFactory, List.empty))
    )
    val edges = List(Edge("source1", "filter1", None), Edge("filter1", "proc1", None), Edge("proc1", "sink1", None))
    val processWithDisabledFilterAndProcessor = newScenarioGraph(nodes, edges)

    createAndValidateScenario(processWithDisabledFilterAndProcessor, ProcessName("p1")) {
      status shouldEqual StatusCodes.OK
      val validation = responseAs[ValidationResult]
      validation.warnings.invalidNodes("filter1").head.message should include("Node filter1 is disabled")
      validation.warnings.invalidNodes("proc1").head.message should include("Node proc1 is disabled")
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
    createAndValidateScenario(CanonicalProcessConverter.toScenarioGraph(scenario), scenario.name)(testCode)

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
