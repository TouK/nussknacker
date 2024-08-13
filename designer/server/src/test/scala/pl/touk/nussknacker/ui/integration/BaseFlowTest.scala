package pl.touk.nussknacker.ui.integration

import io.circe.Json.{Null, arr, fromFields, fromString, obj}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Json, JsonObject}
import org.apache.commons.io.FileUtils
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.typelevel.ci._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, StreamMetaData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentIcon
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, FragmentOutputDefinition, Processor}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.restmodel.definition.UiScenarioPropertyConfig
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  ValidationErrors,
  ValidationResult
}
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.TestAdditionalUIConfigProvider
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.ScenarioToJsonHelper.{ScenarioGraphToJson, ScenarioToJson}
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.toJson
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, WithTestHttpClient}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodeValidationRequest
import pl.touk.nussknacker.ui.api.ScenarioValidationRequest
import pl.touk.nussknacker.ui.definition.DefinitionsService.createUIScenarioPropertyConfig
import pl.touk.nussknacker.ui.process.ProcessService.CreateScenarioCommand
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.util.MultipartUtils.sttpPrepareMultiParts
import pl.touk.nussknacker.ui.util.{CorsSupport, SecurityHeadersSupport}
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
    with WithSimplifiedDesignerConfig
    with WithTestHttpClient
    with Matchers
    with OptionValues
    with EitherValuesDetailedMessage
    with TableDrivenPropertyChecks {

  import BaseFlowTest._

  // @see DevProcessConfigCreator.DynamicService, TODO: figure out how to make reload test more robust...
  // currently we delete file in beforeAll, because it's used *also* in initialization...
  val dynamicServiceFile = new File(Properties.tmpDir, "nk-dynamic-params.lst")

  override def beforeAll(): Unit = {
    super.beforeAll()
    dynamicServiceFile.delete()
  }

  override protected def afterAll(): Unit = {
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

  test("ensure components definition is enriched with components config") {
    val response = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}?isFragment=false")
        .auth
        .basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Ok

    val componentsResultJson = response.extractFieldJsonValue("components").asObject.value.toMap

    def encodeEditor(parameterEditor: ParameterEditor): Json =
      parameterEditor.asJson

    // docs url comes from defaultModelConf.conf in dev-model
    val expectedDefinition = Map(
      "builtin-filter" -> obj(
        "parameters" -> arr(),
        "icon"       -> fromString(DefaultsComponentIcon.FilterIcon),
        "docsUrl"    -> fromString("https://touk.github.io/nussknacker/filter"),
      ),
      "service-enricher" -> obj(
        "parameters" -> arr(
          obj(
            "name"         -> fromString("param"),
            "label"        -> fromString("param"),
            "defaultValue" -> Expression.spel("'default-from-additional-ui-config-provider'").asJson,
            "editor"       -> encodeEditor(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)),
            "hintText"     -> fromString("hint-text-from-additional-ui-config-provider"),
          ),
          obj(
            "name"  -> fromString("tariffType"),
            "label" -> fromString("tariffType"),
            "defaultValue" -> Expression
              .spel("T(pl.touk.nussknacker.engine.management.sample.TariffType).NORMAL")
              .asJson,
            "editor" -> encodeEditor(
              DualParameterEditor(
                FixedValuesParameterEditor(
                  List(
                    FixedExpressionValue("T(pl.touk.nussknacker.engine.management.sample.TariffType).NORMAL", "normal"),
                    FixedExpressionValue("T(pl.touk.nussknacker.engine.management.sample.TariffType).GOLD", "gold")
                  )
                ),
                DualEditorMode.SIMPLE
              )
            ),
            "hintText" -> Null,
          ),
        ),
        "icon"    -> fromString("/assets/components/Filter.svg"),
        "docsUrl" -> fromString("https://touk.github.io/nussknacker/enricher"),
      ),
      "service-multipleParamsService" -> obj(
        "parameters" -> arr(
          obj(
            "name"         -> fromString("foo"),
            "label"        -> fromString("foo"),
            "defaultValue" -> Expression.spel("'test'").asJson,
            "editor"       -> encodeEditor(FixedValuesParameterEditor(List(FixedExpressionValue("'test'", "test")))),
            "hintText"     -> Null,
          ),
          obj(
            "name"         -> fromString("bar"),
            "label"        -> fromString("bar"),
            "defaultValue" -> Expression.spel("''").asJson,
            "editor"       -> encodeEditor(StringParameterEditor),
            "hintText"     -> Null,
          ),
          obj(
            "name"         -> fromString("baz"),
            "label"        -> fromString("baz"),
            "defaultValue" -> Expression.spel("1").asJson,
            "editor" -> encodeEditor(
              FixedValuesParameterEditor(List(FixedExpressionValue("1", "1"), FixedExpressionValue("2", "2")))
            ),
            "hintText" -> fromString("some hint text"),
          ),
          obj(
            "name"         -> fromString("quax"),
            "label"        -> fromString("quax"),
            "defaultValue" -> Expression.spel("''").asJson,
            "editor"       -> encodeEditor(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)),
            "hintText"     -> Null,
          ),
        ),
        "icon"    -> fromString(DefaultsComponentIcon.ServiceIcon),
        "docsUrl" -> Null,
      ),
      "service-accountService" -> obj(
        "parameters" -> arr(),
        "icon"       -> fromString(DefaultsComponentIcon.ServiceIcon),
        "docsUrl"    -> fromString("accountServiceDocs"),
      ),
      "service-providedComponent-component-v1" -> obj(
        "parameters" -> arr(
          obj(
            "name"         -> fromString("fromConfig-v1"),
            "label"        -> fromString("fromConfig-v1"),
            "defaultValue" -> Expression.spel("''").asJson,
            "editor"       -> encodeEditor(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)),
            "hintText"     -> Null,
          )
        ),
        "icon"    -> fromString(DefaultsComponentIcon.ServiceIcon),
        "docsUrl" -> fromString("https://nussknacker.io/Configuration.html"),
      )
    )

    // We check only these specified in expectations to not overload this test
    componentsResultJson.keys.toList.sorted should contain allElementsOf expectedDefinition.keys.toList.sorted
    forAll(
      Table(
        ("componentName", "expectedDefinition"),
        expectedDefinition.toSeq: _*
      )
    ) { (componentName, expectedDefinition) =>
      val expectedDefinitionObj = expectedDefinition.asObject.value
      val returnedDefinition    = componentsResultJson.get(componentName).value.asObject.value

      returnedDefinition("icon").value shouldEqual expectedDefinitionObj("icon").value
      returnedDefinition("docsUrl").value shouldEqual expectedDefinitionObj("docsUrl").value
      checkParameters(expectedDefinitionObj, returnedDefinition)
    }
  }

  private def checkParameters(expectedDefinitionObj: JsonObject, returnedDefinition: JsonObject) = {
    def paramName(paramJson: Json) = paramJson.asObject.value("name").value.asString.value

    def toParamsMap(paramsJson: Json) = paramsJson.asArray.value.map { paramJson =>
      paramName(paramJson) -> paramJson
    }.toMap

    val expectedParameters = toParamsMap(expectedDefinitionObj("parameters").value)
    val returnedParameters = toParamsMap(returnedDefinition("parameters").value)

    returnedParameters.keys.toList shouldEqual expectedParameters.keys.toList
    forAll(Table(("paramName", "expectedParameter"), expectedParameters.toList: _*)) { (paramName, expectedParameter) =>
      val paramWithConfigurableFieldsOnly =
        fromFields(
          returnedParameters
            .get(paramName)
            .value
            .asObject
            .value
            .filterKeys(!Set("typ", "additionalVariables", "variablesToHide", "branchParam").contains(_))
            .toList
        )
      paramWithConfigurableFieldsOnly shouldEqual expectedParameter
    }
  }

  test("ensure scenario properties config is properly applied") {
    val response = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}?isFragment=false")
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
        label = Some("Environment"),
        hintText = None
      ),
      "maxEvents" -> UiScenarioPropertyConfig(
        defaultValue = None,
        editor = StringParameterEditor,
        label = Some("Max events"),
        hintText = Some("Maximum number of events")
      ),
      "numberOfThreads" -> UiScenarioPropertyConfig(
        defaultValue = Some("1"),
        editor = FixedValuesParameterEditor(fixedPossibleValues),
        label = Some("Number of threads"),
        hintText = None
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
    val scenario = ProcessTestData.scenarioGraphWithInvalidScenarioProperties
    createProcess(ProcessTestData.sampleProcessName)

    val validationResponse = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processValidation/${ProcessTestData.sampleProcessName}")
        .contentType(MediaType.ApplicationJson)
        .body(ScenarioValidationRequest(ProcessTestData.sampleProcessName, scenario).asJson.spaces2)
        .auth
        .basic("admin", "admin")
    )

    validationResponse.code shouldEqual StatusCode.Ok
    validationResponse.body should include("Configured property environment (Environment) is missing")
    validationResponse.body should include("This field value has to be an integer number")
    validationResponse.body should include("Unknown property unknown")
    validationResponse.body should include("Property numberOfThreads (Number of threads) has invalid value") //
  }

  test("be able to work with fragment with custom class inputs") {
    val processId = ProcessName(UUID.randomUUID().toString)
    createProcess(processId)

    val scenarioGraph = ScenarioGraph(
      properties = ProcessProperties(FragmentSpecificData()),
      nodes = List(
        FragmentInputDefinition(
          "input1",
          List(FragmentParameter(ParameterName("badParam"), FragmentClazzRef("i.do.not.exist")))
        ),
        FragmentOutputDefinition("output1", "out1")
      ),
      edges = List(Edge("input1", "output1", None)),
    )

    val updateResponse = httpClient.send(
      quickRequest
        .put(uri"$nuDesignerHttpAddress/api/processes/$processId")
        .contentType(MediaType.ApplicationJson)
        .body(scenarioGraph.toJsonAsProcessToSave.spaces2)
        .auth
        .basic("admin", "admin")
        .response(asJson[ValidationResult])
    )
    updateResponse.code shouldEqual StatusCode.Ok
    updateResponse.body.rightValue.errors.invalidNodes("input1") should matchPattern {
      case List(
            NodeValidationError(
              "FragmentParamClassLoadError",
              "Invalid parameter type.",
              "Failed to load i.do.not.exist",
              Some("$param.badParam.$typ"),
              NodeValidationErrorType.SaveAllowed,
              None
            )
          ) =>
    }

    val fetchResponse = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processes/$processId")
        .auth
        .basic("admin", "admin")
    )
    fetchResponse.code shouldEqual StatusCode.Ok
  }

  test("should test process with complexReturnObjectService") {
    val processId = "complexObjectProcess" + UUID.randomUUID().toString

    val process = ScenarioBuilder
      .streaming(processId)
      .source("source", "csv-source")
      .enricher("enricher", "out", "complexReturnObjectService")
      .emptySink("end", "sendSms", "Value" -> "''".spel)

    saveProcess(process)

    val testDataContent = """{"sourceId":"source","record":"field1|field2"}"""

    val response = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processManagement/test/${process.name}")
        .contentType(MediaType.MultipartFormData)
        .multipartBody(
          sttpPrepareMultiParts(
            "testData"      -> testDataContent,
            "scenarioGraph" -> toJson(process).noSpaces
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
        nodeData = Processor(nodeUsingDynamicServiceId, ServiceRef("dynamicService", List.empty)),
        processProperties = ProcessProperties(StreamMetaData()),
        variableTypes = Map.empty,
        branchVariableTypes = None,
        outgoingEdges = None
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
    val saveProcessResult = saveProcess(processWithService())
    saveProcessResult.errors shouldBe ValidationErrors.success
    val dynamicServiceParametersBeforeReload = dynamicServiceParameters
    val testDataContent                      = """{"sourceId":"start","record":"field1|field2"}"""

    firstInvocationResult(testProcess(processWithService(), testDataContent)) shouldBe Some("")

    // we generate random parameter
    val parameterUUID = UUID.randomUUID().toString
    FileUtils.writeStringToFile(dynamicServiceFile, parameterUUID, StandardCharsets.UTF_8)

    dynamicServiceParametersBeforeReload.exists(_.contains(parameterUUID)) shouldBe false
    dynamicServiceParameters shouldBe dynamicServiceParametersBeforeReload
    // service still does not accept parameter, redundant parameters for dynamic services are just skipped
    val resultBeforeReload = updateProcess(processWithService(parameterUUID -> "'emptyString'".spel))
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
    val resultAfterReload = updateProcess(processWithService(parameterUUID -> "'emptyString'".spel))
    resultAfterReload.errors shouldBe ValidationErrors.success
    resultAfterReload.nodeResults.get(nodeUsingDynamicServiceId).value.parameters.value.map(_.name).toSet shouldBe Set(
      parameterUUID
    )
    firstInvocationResult(
      testProcess(processWithService(parameterUUID -> "#input.firstField".spel), testDataContent)
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
    createProcess(process.name)
    updateProcess(process)
  }

  private def createProcess(name: ProcessName) = {
    val createCommand = CreateScenarioCommand(
      name,
      Some(Category1.stringify),
      processingMode = None,
      engineSetupName = None,
      isFragment = false,
      forwardedUserName = None
    )
    val response = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processes/${name.value}/${Category1.stringify}?isFragment=false")
        .auth
        .basic("admin", "admin")
        .post(uri"$nuDesignerHttpAddress/api/processes")
        .contentType(MediaType.ApplicationJson)
        .body(createCommand.asJson.spaces2)
    )
    response.code shouldEqual StatusCode.Created
  }

  private def updateProcess(process: CanonicalProcess) = {
    val processId = process.name
    val response = httpClient.send(
      quickRequest.auth
        .basic("admin", "admin")
        .put(uri"$nuDesignerHttpAddress/api/processes/$processId")
        .contentType(MediaType.ApplicationJson)
        .body(process.toJsonAsProcessToSave.spaces2)
        .response(asJson[ValidationResult])
    )
    response.code shouldEqual StatusCode.Ok
    response.body.rightValue
  }

  private def testProcess(process: CanonicalProcess, data: String): Json = {
    val scenarioGraph =
      CanonicalProcessConverter.toScenarioGraph(process)

    val response = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processManagement/test/${process.name}")
        .contentType(MediaType.MultipartFormData)
        .multipartBody(
          sttpPrepareMultiParts(
            "testData"      -> data,
            "scenarioGraph" -> scenarioGraph.asJson.noSpaces
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
