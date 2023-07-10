package pl.touk.nussknacker.ui.integration

import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Json}
import io.dropwizard.metrics5.MetricRegistry
import org.apache.commons.io.FileUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, StreamMetaData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{Processor, FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, spel}
import pl.touk.nussknacker.restmodel.definition.UiAdditionalPropertyConfig
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{ValidationErrors, ValidationResult}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.NodeValidationRequest
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestCategories, TestFactory, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory.createUIAdditionalPropertyConfig
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.util.{ConfigWithScalaVersion, CorsSupport, MultipartUtils, SecurityHeadersSupport}
import pl.touk.nussknacker.ui.{NusskanckerDefaultAppRouter, NussknackerAppInitializer}

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.concurrent.duration._
import scala.util.Properties

class BaseFlowTest extends AnyFunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll with OptionValues {

  import io.circe.syntax._

  override def testConfig: Config = ConfigWithScalaVersion.TestsConfig

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val (mainRoute, _) = NusskanckerDefaultAppRouter.create(
    ConfigWithUnresolvedVersion(system.settings.config),
    NussknackerAppInitializer.initDb(system.settings.config),
    new MetricRegistry
  )

  private val credentials = HttpCredentials.createBasicHttpCredentials("admin", "admin")

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(1.minute)

  //@see DevProcessConfigCreator.DynamicService, TODO: figure out how to make reload test more robust...
  //currently we delete file in beforeAll, because it's used *also* in initialization...
  val dynamicServiceFile = new File(Properties.tmpDir, "nk-dynamic-params.lst")

  override def beforeAll(): Unit = {
    super.beforeAll()
    dynamicServiceFile.delete()
  }

  override def afterAll(): Unit = {
    dynamicServiceFile.delete()
    super.afterAll()
  }


  test("saves, updates and retrieves sample process") {
    val processId = UUID.randomUUID().toString

    val process = ScenarioBuilder
      .streaming(processId)
      .source("source", "csv-source")
      .processorEnd("end", "monitor")

    saveProcess(process)
  }

  test("ensure nodes config is properly parsed") {
    Get("/api/processDefinitionData/streaming?isFragment=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      val settingsJson = responseAs[Json].hcursor.downField("componentsConfig").focus.get
      val settings = Decoder[Map[String, SingleComponentConfig]].decodeJson(settingsJson).toOption.get

      //docs url comes from defaultModelConf.conf in dev-model
      val underTest = Map(
        "filter" -> SingleComponentConfig(None, None, Some("https://touk.github.io/nussknacker/filter"), None, None),
        "test1" -> SingleComponentConfig(None, Some("/assets/components/Sink.svg"), None, None, None),
        "enricher" -> SingleComponentConfig(
          Some(Map("param" -> ParameterConfig(Some("'default value'"), Some(StringParameterEditor), None, None))),
          Some("/assets/components/Filter.svg"),
          Some("https://touk.github.io/nussknacker/enricher"),
          None,
          None
        ),
        "multipleParamsService" -> SingleComponentConfig(
          Some(Map(
            "foo" -> ParameterConfig(None, Some(FixedValuesParameterEditor(List(FixedExpressionValue("'test'", "test")))), None, None),
            "bar" -> ParameterConfig(None, Some(StringParameterEditor), None, None),
            "baz" -> ParameterConfig(None, Some(FixedValuesParameterEditor(List(FixedExpressionValue("1", "1"), FixedExpressionValue("2", "2")))), None, None)
          )),
          None,
          None,
          None,
          None
        ),
        "accountService" -> SingleComponentConfig(None, None, Some("accountServiceDocs"), None, None),
        "sub1" -> SingleComponentConfig(
          Some(Map(
            "param1" -> ParameterConfig(None, Some(StringParameterEditor), None, None)
          )),
          None,
          Some("http://nussknacker.io"),
          None,
          None,
        ),
        "optionalTypesService" -> SingleComponentConfig(
          Some(Map(
            "overriddenByFileConfigParam" -> ParameterConfig(None, None, Some(List.empty), None),
            "overriddenByDevConfigParam" -> ParameterConfig(None, None, Some(List(MandatoryParameterValidator)), None)
          )),
          None,
          None,
          Some(ComponentGroupName("types")),
          None
        ),
        "providedComponent-component-v1" -> SingleComponentConfig(None, None, Some("https://nussknacker.io/Configuration.html"), None, None),
        "$properties" -> SingleComponentConfig(None, None,
          Some("https://nussknacker.io/documentation/docs/installation_configuration_guide/ModelConfiguration#scenarios-additional-properties"), None, None)
      )

      settings.collect { case (k, v) if underTest.keySet contains k => (k, v) } shouldBe underTest
    }
  }

  test("ensure additional properties config is properly applied") {
    Get("/api/processDefinitionData/streaming?isFragment=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      val settingsJson = responseAs[Json].hcursor.downField("additionalPropertiesConfig").focus.get
      val fixedPossibleValues = List(FixedExpressionValue("1", "1"), FixedExpressionValue("2", "2"))

      val settings = Decoder[Map[String, UiAdditionalPropertyConfig]].decodeJson(settingsJson).toOption.get
      val streamingDefaultPropertyConfig = FlinkStreamingPropertiesConfig.properties.map(p => p._1 -> createUIAdditionalPropertyConfig(p._2))

      val underTest = Map(
        "environment" -> UiAdditionalPropertyConfig(
          Some("test"),
          StringParameterEditor,
          List(MandatoryParameterValidator),
          Some("Environment")
        ),
        "maxEvents" -> UiAdditionalPropertyConfig(
          None,
          StringParameterEditor,
          List(LiteralParameterValidator.integerValidator),
          Some("Max events")
        ),
        "numberOfThreads" -> UiAdditionalPropertyConfig(
          Some("1"),
          FixedValuesParameterEditor(fixedPossibleValues),
          List(FixedValuesValidator(fixedPossibleValues)),
          Some("Number of threads")
        )
      ) ++ streamingDefaultPropertyConfig

      settings shouldBe underTest
    }
  }

  test("validate process additional properties") {
    val scenario = ProcessTestData.processWithInvalidAdditionalProperties
    Post(s"/api/processes/${scenario.id}/Category1?isFragment=${scenario.metaData.isFragment}") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      Post("/api/processValidation", HttpEntity(ContentTypes.`application/json`, scenario.asJson.spaces2)) ~> addCredentials(credentials) ~> mainRoute ~> check {
        status shouldEqual StatusCodes.OK
        val entity = responseAs[String]

        entity should include("Configured property environment (Environment) is missing")
        entity should include("This field value has to be an integer number")
        entity should include("Unknown property unknown")
        entity should include("Property numberOfThreads (Number of threads) has invalid value")
      }
    }
  }

  test("be able to work with fragment with custom class inputs") {
    val processId = UUID.randomUUID().toString
    val endpoint = s"/api/processes/$processId"

    val process = DisplayableProcess(
      id = processId,
      properties = ProcessProperties(FragmentSpecificData()),
      nodes = List(FragmentInputDefinition("input1", List(FragmentParameter("badParam", FragmentClazzRef("i.do.not.exist")))),
        FragmentOutputDefinition("output1", "out1")),
      edges = List(Edge("input1", "output1", None)),
      processingType = TestProcessingTypes.Streaming,
      TestCategories.Category1
    )

    Post(s"$endpoint/Category1?isFragment=true") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.Created
      Put(endpoint, TestFactory.posting.toEntityAsProcessToSave(process)) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
        status shouldEqual StatusCodes.OK

        val res = responseAs[ValidationResult]
        //TODO: in the future should be more local error
        res.errors.globalErrors.map(_.description) shouldBe List(
          "Fatal error: Failed to load scenario fragment parameter: i.do.not.exist for input1, please check configuration")

        Get(endpoint) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
          status shouldEqual StatusCodes.OK
        }
      }
    }
  }

  import spel.Implicits._

  test("should test process with complexReturnObjectService") {
    val processId = "complexObjectProcess" + UUID.randomUUID().toString

    val process = ScenarioBuilder
      .streaming(processId)
      .source("source", "csv-source")
      .enricher("enricher", "out", "complexReturnObjectService")
      .emptySink("end", "sendSms", "Value" -> "''")

    saveProcess(process)

    val testDataContent = """{"sourceId":"source","record":"field1|field2"}"""
    val multiPart = MultipartUtils.prepareMultiParts("testData" -> testDataContent, "processJson" -> TestProcessUtil.toJson(process).noSpaces)()
    Post(s"/api/processManagement/test/${process.id}", multiPart) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
    }
  }

  test("should reload ConfigCreator") {
    def generationTime: Option[String] = {
      Get("/api/app/buildInfo") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
        status shouldEqual StatusCodes.OK
        responseAs[Json].hcursor
          .downField("processingType")
          .downField("streaming")
          .downField("generation-time")
          .focus.flatMap(_.asString)
      }
    }

    val processId = "test"
    val nodeUsingDynamicServiceId = "end"

    def processWithService(params: (String, Expression)*): CanonicalProcess = {
      ScenarioBuilder
        .streaming(processId)
        .additionalFields(properties = Map("environment" -> "someNotEmptyString"))
        .source("start", "csv-source")
        .processorEnd(nodeUsingDynamicServiceId, "dynamicService", params: _*)
    }

    def firstInvocationResult(result: Json): Option[String] = result.hcursor
      .downField("results")
      .downField("externalInvocationResults")
      .downField("end")
      .downArray
      .downField("value")
      .downField("pretty").focus
      .flatMap(_.asString)

    def dynamicServiceParameters: Option[List[String]] = {
      val request = NodeValidationRequest(Processor(nodeUsingDynamicServiceId, ServiceRef("dynamicService", List.empty)), ProcessProperties(StreamMetaData()), Map.empty, None, None).asJson
      Post(s"/api/nodes/$processId/validation", request) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
        status shouldEqual StatusCodes.OK
        val responseJson = responseAs[Json]
        val parameters = responseJson.hcursor
          .downField("parameters")
          .focus.flatMap(_.asArray)
        parameters.map(_.flatMap(_.asObject).flatMap(_.apply("name")).flatMap(_.asString).toList)
      }
    }

    //we check that buildInfo does not change
    val beforeReload = generationTime
    val beforeReload2 = generationTime
    beforeReload shouldBe beforeReload2
    //process without errors - no parameter required
    saveProcess(processWithService()).errors shouldBe ValidationErrors.success
    val dynamicServiceParametersBeforeReload = dynamicServiceParameters
    val testDataContent = """{"sourceId":"start","record":"field1|field2"}"""

    firstInvocationResult(testProcess(processWithService(), testDataContent)) shouldBe Some("")

    //we generate random parameter
    val parameterUUID = UUID.randomUUID().toString
    FileUtils.writeStringToFile(dynamicServiceFile, parameterUUID, StandardCharsets.UTF_8)

    dynamicServiceParametersBeforeReload.exists(_.contains(parameterUUID)) shouldBe false
    dynamicServiceParameters shouldBe dynamicServiceParametersBeforeReload
    //service still does not accept parameter, redundant parameters for dynamic services are just skipped
    val resultBeforeReload = updateProcess(processWithService(parameterUUID -> "'emptyString'"))
    resultBeforeReload.errors shouldBe ValidationErrors.success
    resultBeforeReload.nodeResults.get(nodeUsingDynamicServiceId).value.parameters.value.map(_.name).toSet shouldBe Set.empty

    reloadModel()

    val afterReload = generationTime
    beforeReload should not be afterReload
    //now parameter is known and required
    dynamicServiceParameters shouldBe Some(List(parameterUUID))
    val resultAfterReload = updateProcess(processWithService(parameterUUID -> "'emptyString'"))
    resultAfterReload.errors shouldBe ValidationErrors.success
    resultAfterReload.nodeResults.get(nodeUsingDynamicServiceId).value.parameters.value.map(_.name).toSet shouldBe Set(parameterUUID)
    firstInvocationResult(testProcess(processWithService(parameterUUID -> "#input.firstField"), testDataContent)) shouldBe Some("field1")

  }

  test("should return response with required headers") {
    Get("/api/app/buildInfo") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      headers should contain allElementsOf (CorsSupport.headers ::: SecurityHeadersSupport.headers)
    }
  }

  test("should handle OPTIONS method request") {
    Options("/") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      headers should contain allElementsOf (CorsSupport.headers ::: SecurityHeadersSupport.headers)
    }
  }

  private def saveProcess(process: CanonicalProcess): ValidationResult = {
    Post(s"/api/processes/${process.id}/Category1?isFragment=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.Created
      updateProcess(process)
    }
  }

  private def updateProcess(process: CanonicalProcess): ValidationResult = {
    val processId = process.id
    Put(s"/api/processes/$processId", TestFactory.posting.toEntityAsProcessToSave(process)) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      responseAs[ValidationResult]
    }
  }

  private def testProcess(process: CanonicalProcess, data: String): Json = {
    val displayableProcess = ProcessConverter.toDisplayable(process, TestProcessingTypes.Streaming, TestCategories.Category1)
    val multiPart = MultipartUtils.prepareMultiParts("testData" -> data, "processJson" -> displayableProcess.asJson.noSpaces)()
    Post(s"/api/processManagement/test/${process.id}", multiPart) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      responseAs[Json]
    }
  }

  private def reloadModel(): Unit = {
    Post("/api/app/processingtype/reload") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.NoContent
    }
  }

  private def checkWithClue[T](body: => T): RouteTestResult => T = check {
    withClue(s"response: '${responseAs[String]}'") {
      body
    }
  }
}
