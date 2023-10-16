package pl.touk.nussknacker.ui.integration

import com.typesafe.config.Config
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Json}
import org.apache.commons.io.FileUtils
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci._
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, StreamMetaData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, FragmentOutputDefinition, Processor}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.restmodel.definition.UiScenarioPropertyConfig
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{ValidationErrors, ValidationResult}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, WithTestHttpClient}
import pl.touk.nussknacker.ui.api.NodeValidationRequest
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.definition.TestAdditionalUIConfigProvider
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory.createUIScenarioPropertyConfig
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.util.MultipartUtils.sttpPrepareMultiParts
import pl.touk.nussknacker.ui.util.{ConfigWithScalaVersion, CorsSupport, SecurityHeadersSupport}
import sttp.client3.circe.asJson
import sttp.client3.{UriContext, quickRequest}
import sttp.model.{Header, MediaType, StatusCode}

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.util.Properties

class BaseFlowTest
    extends AnyFunSuiteLike
    with NuItTest
    with WithTestHttpClient
    with Matchers
    with OptionValues
    with EitherValuesDetailedMessage {

  import BaseFlowTest._

  // @see DevProcessConfigCreator.DynamicService, TODO: figure out how to make reload test more robust...
  // currently we delete file in beforeAll, because it's used *also* in initialization...
  val dynamicServiceFile = new File(Properties.tmpDir, "nk-dynamic-params.lst")

  override def nuTestConfig: Config = ConfigWithScalaVersion.TestsConfig

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
    val response = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processDefinitionData/streaming?isFragment=false")
        .auth
        .basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Ok

    val settingsJson = response.extractFieldJsonValue("componentsConfig")
    val settings     = Decoder[Map[String, SingleComponentConfig]].decodeJson(settingsJson).toOption.get

    // docs url comes from defaultModelConf.conf in dev-model
    val underTest = Map(
      "filter" -> SingleComponentConfig(
        None,
        None,
        Some("https://touk.github.io/nussknacker/filter"),
        componentGroup = None,
        componentId = None
      ),
      "test1" -> SingleComponentConfig(
        params = None,
        icon = Some("/assets/components/Sink.svg"),
        docsUrl = None,
        componentGroup = None,
        componentId = None
      ),
      "enricher" -> SingleComponentConfig(
        params = Some(
          Map(
            "param" -> ParameterConfig(Some("'default value'"), Some(StringParameterEditor), None, None),
            "paramDualEditor" -> ParameterConfig(
              None,
              None,
              Some(
                List(FixedValuesValidator(possibleValues = List(FixedExpressionValue("someExpression", "someLabel"))))
              ),
              None
            )
          )
        ),
        icon = Some("/assets/components/Filter.svg"),
        docsUrl = Some("https://touk.github.io/nussknacker/enricher"),
        componentGroup = Some(TestAdditionalUIConfigProvider.componentGroupName),
        componentId = None
      ),
      "multipleParamsService" -> SingleComponentConfig(
        params = Some(
          Map(
            "foo" -> ParameterConfig(
              None,
              Some(FixedValuesParameterEditor(List(FixedExpressionValue("'test'", "test")))),
              None,
              None
            ),
            "bar" -> ParameterConfig(None, Some(StringParameterEditor), None, None),
            "baz" -> ParameterConfig(
              None,
              Some(FixedValuesParameterEditor(List(FixedExpressionValue("1", "1"), FixedExpressionValue("2", "2")))),
              None,
              None,
              Some("some hint text")
            )
          )
        ),
        icon = None,
        docsUrl = None,
        componentGroup = None,
        componentId = None
      ),
      "accountService" -> SingleComponentConfig(
        params = None,
        icon = None,
        docsUrl = Some("accountServiceDocs"),
        componentGroup = None,
        componentId = None
      ),
      "sub1" -> SingleComponentConfig(
        params = Some(
          Map(
            "param1" -> ParameterConfig(None, Some(StringParameterEditor), None, None)
          )
        ),
        icon = None,
        docsUrl = Some("http://nussknacker.io"),
        componentGroup = None,
        componentId = None,
      ),
      "optionalTypesService" -> SingleComponentConfig(
        params = Some(
          Map(
            "overriddenByFileConfigParam" -> ParameterConfig(None, None, Some(List.empty), None),
            "overriddenByDevConfigParam"  -> ParameterConfig(None, None, Some(List(MandatoryParameterValidator)), None)
          )
        ),
        icon = None,
        docsUrl = None,
        componentGroup = Some(ComponentGroupName("types")),
        componentId = None
      ),
      "providedComponent-component-v1" -> SingleComponentConfig(
        params = None,
        icon = None,
        docsUrl = Some("https://nussknacker.io/Configuration.html"),
        componentGroup = None,
        componentId = None
      ),
      "$properties" -> SingleComponentConfig(
        params = None,
        icon = None,
        docsUrl = Some(
          "https://nussknacker.io/documentation/docs/installation_configuration_guide/ModelConfiguration#scenarios-additional-properties"
        ),
        componentGroup = None,
        componentId = None
      )
    )

    settings.collect { case (k, v) if underTest.keySet contains k => (k, v) } shouldBe underTest
  }

  test("ensure scenario properties config is properly applied") {
    val response = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processDefinitionData/streaming?isFragment=false")
        .auth
        .basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Ok

    val settingsJson        = response.extractFieldJsonValue("scenarioPropertiesConfig")
    val fixedPossibleValues = List(FixedExpressionValue("1", "1"), FixedExpressionValue("2", "2"))

    val settings = Decoder[Map[String, UiScenarioPropertyConfig]].decodeJson(settingsJson).toOption.get
    val streamingDefaultPropertyConfig =
      FlinkStreamingPropertiesConfig.properties.map(p => p._1 -> createUIScenarioPropertyConfig(p._2))

    val underTest = Map(
      "environment" -> UiScenarioPropertyConfig(
        defaultValue = Some("test"),
        editor = StringParameterEditor,
        validators = List(MandatoryParameterValidator),
        label = Some("Environment")
      ),
      "maxEvents" -> UiScenarioPropertyConfig(
        defaultValue = None,
        editor = StringParameterEditor,
        validators = List(LiteralParameterValidator.integerValidator),
        label = Some("Max events")
      ),
      "numberOfThreads" -> UiScenarioPropertyConfig(
        defaultValue = Some("1"),
        editor = FixedValuesParameterEditor(fixedPossibleValues),
        validators = List(FixedValuesValidator(fixedPossibleValues)),
        label = Some("Number of threads")
      ),
      TestAdditionalUIConfigProvider.scenarioPropertyName -> createUIScenarioPropertyConfig(
        TestAdditionalUIConfigProvider.scenarioPropertyConfigOverride(
          TestAdditionalUIConfigProvider.scenarioPropertyName
        )
      )
    ) ++ streamingDefaultPropertyConfig

    settings shouldBe underTest
  }

  test("validate process scenario properties") {
    val scenario = ProcessTestData.processWithInvalidScenarioProperties
    val response1 = httpClient.send(
      quickRequest
        .post(
          uri"$nuDesignerHttpAddress/api/processes/${scenario.id}/Category1?isFragment=${scenario.metaData.isFragment}"
        )
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Created

    val response2 = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processValidation")
        .contentType(MediaType.ApplicationJson)
        .body(scenario.asJson.spaces2)
        .auth
        .basic("admin", "admin")
    )

    response2.code shouldEqual StatusCode.Ok
    response2.body should include("Configured property environment (Environment) is missing")
    response2.body should include("This field value has to be an integer number")
    response2.body should include("Unknown property unknown")
    response2.body should include("Property numberOfThreads (Number of threads) has invalid value") //
  }

  test("be able to work with fragment with custom class inputs") {
    val processId = UUID.randomUUID().toString

    val process = DisplayableProcess(
      id = processId,
      properties = ProcessProperties(FragmentSpecificData()),
      nodes = List(
        FragmentInputDefinition("input1", List(FragmentParameter("badParam", FragmentClazzRef("i.do.not.exist")))),
        FragmentOutputDefinition("output1", "out1")
      ),
      edges = List(Edge("input1", "output1", None)),
      processingType = TestProcessingTypes.Streaming,
      TestCategories.Category1
    )

    val response1 = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processes/$processId/Category1?isFragment=true")
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Created

    val response2 = httpClient.send(
      quickRequest
        .put(uri"$nuDesignerHttpAddress/api/processes/$processId")
        .contentType(MediaType.ApplicationJson)
        .body(TestFactory.posting.toJsonAsProcessToSave(process).spaces2)
        .auth
        .basic("admin", "admin")
        .response(asJson[ValidationResult])
    )
    response2.code shouldEqual StatusCode.Ok
    // TODO: in the future should be more local error
    response2.body.rightValue.errors.globalErrors.map(_.description) shouldBe List(
      "Fatal error: Failed to load scenario fragment parameter: i.do.not.exist for input1, please check configuration"
    )

    val response3 = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processes/$processId")
        .auth
        .basic("admin", "admin")
    )
    response3.code shouldEqual StatusCode.Ok
  }

  test("should test process with complexReturnObjectService") {
    val processId = "complexObjectProcess" + UUID.randomUUID().toString

    val process = ScenarioBuilder
      .streaming(processId)
      .source("source", "csv-source")
      .enricher("enricher", "out", "complexReturnObjectService")
      .emptySink("end", "sendSms", "Value" -> "''")

    saveProcess(process)

    val testDataContent = """{"sourceId":"source","record":"field1|field2"}"""

    val response = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processManagement/test/${process.id}")
        .contentType(MediaType.MultipartFormData)
        .multipartBody(
          sttpPrepareMultiParts(
            "testData"    -> testDataContent,
            "processJson" -> TestProcessUtil.toJson(process).noSpaces
          )()
        )
        .auth
        .basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Ok
  }

  test("should reload ConfigCreator") {
    def generationTime: Option[String] = {
      val response = httpClient.send(
        quickRequest
          .get(uri"$nuDesignerHttpAddress/api/app/buildInfo")
          .auth
          .basic("admin", "admin")
      )
      response.code shouldEqual StatusCode.Ok
      response.extractFieldJsonValue("processingType", "streaming", "generation-time").asString
    }

    val processId                 = "test"
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
      .downField("pretty")
      .focus
      .flatMap(_.asString)

    def dynamicServiceParameters: Option[List[String]] = {
      val request = NodeValidationRequest(
        Processor(nodeUsingDynamicServiceId, ServiceRef("dynamicService", List.empty)),
        ProcessProperties(StreamMetaData()),
        Map.empty,
        None,
        None
      )

      val response = httpClient.send(
        quickRequest
          .post(uri"$nuDesignerHttpAddress/api/nodes/$processId/validation")
          .contentType(MediaType.ApplicationJson)
          .body(request.asJson.spaces2)
          .auth
          .basic("admin", "admin")
      )
      response.code shouldEqual StatusCode.Ok

      val parameters = response.extractFieldJsonValue("parameters").asArray
      parameters.map(_.flatMap(_.asObject).flatMap(_.apply("name")).flatMap(_.asString).toList)
    }

    // we check that buildInfo does not change
    val beforeReload  = generationTime
    val beforeReload2 = generationTime
    beforeReload shouldBe beforeReload2
    // process without errors - no parameter required
    saveProcess(processWithService()).errors shouldBe ValidationErrors.success
    val dynamicServiceParametersBeforeReload = dynamicServiceParameters
    val testDataContent                      = """{"sourceId":"start","record":"field1|field2"}"""

    firstInvocationResult(testProcess(processWithService(), testDataContent)) shouldBe Some("")

    // we generate random parameter
    val parameterUUID = UUID.randomUUID().toString
    FileUtils.writeStringToFile(dynamicServiceFile, parameterUUID, StandardCharsets.UTF_8)

    dynamicServiceParametersBeforeReload.exists(_.contains(parameterUUID)) shouldBe false
    dynamicServiceParameters shouldBe dynamicServiceParametersBeforeReload
    // service still does not accept parameter, redundant parameters for dynamic services are just skipped
    val resultBeforeReload = updateProcess(processWithService(parameterUUID -> "'emptyString'"))
    resultBeforeReload.errors shouldBe ValidationErrors.success
    resultBeforeReload.nodeResults
      .get(nodeUsingDynamicServiceId)
      .value
      .parameters
      .value
      .map(_.name)
      .toSet shouldBe Set.empty

    reloadModel()

    val afterReload = generationTime
    beforeReload should not be afterReload
    // now parameter is known and required
    dynamicServiceParameters shouldBe Some(List(parameterUUID))
    val resultAfterReload = updateProcess(processWithService(parameterUUID -> "'emptyString'"))
    resultAfterReload.errors shouldBe ValidationErrors.success
    resultAfterReload.nodeResults.get(nodeUsingDynamicServiceId).value.parameters.value.map(_.name).toSet shouldBe Set(
      parameterUUID
    )
    firstInvocationResult(
      testProcess(processWithService(parameterUUID -> "#input.firstField"), testDataContent)
    ) shouldBe Some("field1")
  }

  test("should return response with required headers") {
    val response = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/app/buildInfo")
        .auth
        .basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Ok
    response.headers.toRawHeaders should contain allElementsOf (CorsSupport.headers ::: SecurityHeadersSupport.headers)
  }

  test("should handle OPTIONS method request") {
    val response = httpClient.send(
      quickRequest
        .options(uri"$nuDesignerHttpAddress/")
        .auth
        .basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Ok
    response.headers.toRawHeaders should contain allElementsOf (CorsSupport.headers ::: SecurityHeadersSupport.headers)
  }

  private def saveProcess(process: CanonicalProcess) = {
    val response = httpClient.send(
      quickRequest.auth
        .basic("admin", "admin")
        .post(uri"$nuDesignerHttpAddress/api/processes/${process.id}/Category1?isFragment=false")
    )
    response.code shouldEqual StatusCode.Created
    updateProcess(process)
  }

  private def updateProcess(process: CanonicalProcess) = {
    val processId = process.id
    val response = httpClient.send(
      quickRequest.auth
        .basic("admin", "admin")
        .put(uri"$nuDesignerHttpAddress/api/processes/$processId")
        .contentType(MediaType.ApplicationJson)
        .body(TestFactory.posting.toJsonAsProcessToSave(process).spaces2)
        .response(asJson[ValidationResult])
    )
    response.code shouldEqual StatusCode.Ok
    response.body.rightValue
  }

  private def testProcess(process: CanonicalProcess, data: String): Json = {
    val displayableProcess =
      ProcessConverter.toDisplayable(process, TestProcessingTypes.Streaming, TestCategories.Category1)

    val response = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processManagement/test/${process.id}")
        .contentType(MediaType.MultipartFormData)
        .multipartBody(
          sttpPrepareMultiParts(
            "testData"    -> data,
            "processJson" -> displayableProcess.asJson.noSpaces
          )()
        )
        .auth
        .basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Ok
    response.bodyAsJson
    io.circe.parser
      .parse(response.body)
      .toOption
      .getOrElse(throw new IllegalArgumentException(s"Cannot create JSON from [${response.body}]"))
  }

  private def reloadModel(): Unit = {
    val response = httpClient.send(
      quickRequest.auth
        .basic("admin", "admin")
        .post(uri"$nuDesignerHttpAddress/api/app/processingtype/reload")
    )
    response.code shouldEqual StatusCode.NoContent
  }

}

private object BaseFlowTest {

  implicit class SeqOfHeadersOps(val seq: Seq[Header]) extends AnyVal {
    def toRawHeaders: Seq[(CIString, String)] = seq.map(h => (CIString(h.name), h.value))
  }

}
