package pl.touk.nussknacker.ui.integration

import java.io.File
import java.util.UUID
import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Json}
import io.dropwizard.metrics5.MetricRegistry
import org.apache.commons.io.FileUtils
import org.scalatest._
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, StreamMetaData}
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.RedundantParameters
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, FixedValuesValidator, LiteralParameterValidator, MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{Processor, SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.restmodel.definition.UiAdditionalPropertyConfig
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{ValidationErrors, ValidationResult}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.NodeValidationRequest
import pl.touk.nussknacker.ui.{NusskanckerDefaultAppRouter, NussknackerAppInitializer}
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.util.{ConfigWithScalaVersion, MultipartUtils}

import scala.concurrent.duration._
import scala.util.Properties

class BaseFlowTest extends FunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll {

  import io.circe.syntax._

  override def testConfig: Config = ConfigWithScalaVersion.TestsConfig

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val (mainRoute, _) = NusskanckerDefaultAppRouter.create(
    system.settings.config,
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
    Get("/api/processDefinitionData/streaming?isSubprocess=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      val settingsJson = responseAs[Json].hcursor.downField("componentsConfig").focus.get
      val settings = Decoder[Map[String, SingleComponentConfig]].decodeJson(settingsJson).right.get

      val underTest = Map(
        //docs url comes from reference.conf in devModel
        "filter" -> SingleComponentConfig(None, None, Some("https://touk.github.io/nussknacker/filter"), None, None),
        "test1" -> SingleComponentConfig(None, Some("/assets/components/Sink.svg"), None, None, None),
        "enricher" -> SingleComponentConfig(
          Some(Map("param" -> ParameterConfig(Some("'default value'"), Some(StringParameterEditor), None, None))),
          Some("/assets/components/Filter.svg"),
          //docs url comes from reference.conf in devModel
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
          Some("https://nussknacker.io/documentation/docs/installation_configuration_guide/ModelConfiguration/#additional-properties"), None, None)
      )

      val (relevant, other) = settings.partition { case (k, _) => underTest.keySet contains k }
      relevant shouldBe underTest
      other.values.forall(_.docsUrl.isEmpty) shouldBe true
    }
  }

  test("ensure additional properties config is properly applied") {
    Get("/api/processDefinitionData/streaming?isSubprocess=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      val settingsJson = responseAs[Json].hcursor.downField("additionalPropertiesConfig").focus.get
      val fixedPossibleValues = List(FixedExpressionValue("1", "1"), FixedExpressionValue("2", "2"))

      val settings = Decoder[Map[String, UiAdditionalPropertyConfig]].decodeJson(settingsJson).right.get

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
      )

      settings shouldBe underTest
    }
  }

  test("validate process additional properties") {
    val scenario = ProcessTestData.processWithInvalidAdditionalProperties
    Post(s"/api/processes/${scenario.id}/Category1?isSubprocess=${scenario.metaData.isSubprocess}") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
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
      properties = ProcessProperties(FragmentSpecificData(), subprocessVersions = Map()),
      nodes = List(SubprocessInputDefinition("input1", List(SubprocessParameter("badParam", SubprocessClazzRef("i.do.not.exist")))),
        SubprocessOutputDefinition("output1", "out1")),
      edges = List(Edge("input1", "output1", None)),
      processingType = TestProcessingTypes.Streaming
    )

    Post(s"$endpoint/Category1?isSubprocess=true") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
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
      .emptySink("end", "sendSms", "value" -> "''")

    saveProcess(process)

    val multiPart = MultipartUtils.prepareMultiParts("testData" -> "record1|field2", "processJson" -> TestProcessUtil.toJson(process).noSpaces)()
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
    def processWithService(params: (String, Expression)*): EspProcess = {
      ScenarioBuilder
        .streaming(processId)
        .additionalFields(properties = Map("environment" -> "someNotEmptyString"))
        .source("start", "csv-source")
        .processorEnd(nodeUsingDynamicServiceId, "dynamicService", params: _*)
    }

    def firstMockedResult(result: Json): Option[String] = result.hcursor
      .downField("results")
      .downField("mockedResults")
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
    firstMockedResult(testProcess(processWithService(), "field1|field2")) shouldBe Some("")


    //we generate random parameter
    val parameterUUID = UUID.randomUUID().toString
    FileUtils.writeStringToFile(dynamicServiceFile, parameterUUID, "UTF-8")

    dynamicServiceParametersBeforeReload.exists(_.contains(parameterUUID)) shouldBe false
    dynamicServiceParameters shouldBe dynamicServiceParametersBeforeReload
    //service still does not accept parameter
    updateProcess(processWithService(parameterUUID -> "'emptyString'")).errors shouldBe ValidationErrors(Map("end" -> List(
      PrettyValidationErrors.formatErrorMessage(RedundantParameters(Set(parameterUUID), "end"))
    )), List.empty, List.empty)

    reloadModel()

    val afterReload =  generationTime
    beforeReload should not be afterReload
    //now parameter is known and required
    dynamicServiceParameters shouldBe Some(List(parameterUUID))
    updateProcess(processWithService(parameterUUID -> "'emptyString'")).errors shouldBe ValidationErrors.success
    firstMockedResult(testProcess(processWithService(parameterUUID -> "#input.firstField"), "field1|field2")) shouldBe Some("field1")

  }

  test("should reload model config") {
    def invokeModelConfigReader(configPath: String): String = {
      val serviceParameters = List(Parameter("configPath", s"'$configPath'"))
      val entity = HttpEntity(MediaTypes.`application/json`, serviceParameters.asJson.noSpaces)

      Post("/api/service/streaming/modelConfigReader", entity) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
        status shouldEqual StatusCodes.OK
        val resultJson = entityAs[Json]
        resultJson
          .hcursor
          .downField("result")
          .as[String]
          .fold(error => throw error, identity)
      }
    }

    val configLoadedMsBeforeReload = invokeModelConfigReader("configLoadedMs").toLong
    val addedConstantPropertyBeforeReload = invokeModelConfigReader("duplicatedSignalsTopic")
    val propertyFromResourcesBeforeReload = invokeModelConfigReader("signalsTopic")

    configLoadedMsBeforeReload shouldBe < (System.currentTimeMillis())
    addedConstantPropertyBeforeReload shouldBe "nk.signals"
    propertyFromResourcesBeforeReload shouldBe "nk.signals"

    reloadModel()

    val configLoadedMsAfterReload = invokeModelConfigReader("configLoadedMs").toLong
    val addedConstantPropertyAfterReload = invokeModelConfigReader("duplicatedSignalsTopic")
    val propertyFromResourcesAfterReload = invokeModelConfigReader("signalsTopic")

    configLoadedMsAfterReload should (be < System.currentTimeMillis() and be > configLoadedMsBeforeReload)
    addedConstantPropertyAfterReload shouldBe addedConstantPropertyBeforeReload
    propertyFromResourcesAfterReload shouldBe propertyFromResourcesBeforeReload
  }

  private def saveProcess(process: EspProcess): ValidationResult = {
    Post(s"/api/processes/${process.id}/Category1?isSubprocess=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.Created
      updateProcess(process)
    }
  }

  private def updateProcess(process: EspProcess): ValidationResult = {
    val processId = process.id
    Put(s"/api/processes/$processId", TestFactory.posting.toEntityAsProcessToSave(process)) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      responseAs[ValidationResult]
    }
  }

  private def testProcess(process: EspProcess, data: String): Json = {
    val displayableProcess = ProcessConverter.toDisplayable(process.toCanonicalProcess, TestProcessingTypes.Streaming)
    val multiPart = MultipartUtils.prepareMultiParts("testData" -> data, "processJson" -> displayableProcess.asJson.noSpaces)()
    Post(s"/api/processManagement/test/${process.id}", multiPart)  ~> addCredentials(credentials) ~> mainRoute ~>  checkWithClue {
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
