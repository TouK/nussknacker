package pl.touk.nussknacker.ui.integration

import com.typesafe.config.Config
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil.RichACursor
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{ConfigWithScalaVersion, WithDesignerConfig}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.ScenarioToJsonHelper.ScenarioToJson
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.toJson
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, WithTestHttpClient}
import pl.touk.nussknacker.ui.api.ScenarioValidationRequest
import pl.touk.nussknacker.ui.process.ProcessService.CreateScenarioCommand
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.util.MultipartUtils.sttpPrepareMultiParts
import sttp.client3.{UriContext, quickRequest}
import sttp.model.{MediaType, StatusCode}

import java.util.UUID

class DictsFlowTest
    extends AnyFunSuiteLike
    with NuItTest
    with WithDesignerConfig
    with WithTestHttpClient
    with Matchers
    with OptionValues
    with EitherValuesDetailedMessage {

  private val VariableNodeId = "variableCheck"
  private val VariableName   = "variableToCheck"
  private val EndNodeId      = "end"
  private val Key            = "foo"
  private val Label          = "Foo"

  override def designerConfig: Config = ConfigWithScalaVersion.TestsConfigWithEmbeddedEngine

  test("create scenario with DictParameterEditor, save it and test it") {
    val DictId = "rgb"

    // Use dict suggestion endpoint
    val response1 = httpClient.send(
      quickRequest
        .get(
          uri"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dict/$DictId/entry?label=${"Black"
              .take(3)}"
        )
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Ok
    response1.bodyAsJson shouldEqual Json.arr(
      Json.obj(
        "key"   -> Json.fromString("H000000"),
        "label" -> Json.fromString("Black")
      )
    )

    // Create and test process that uses DictParameterEditor parameters
    val process = ScenarioBuilder
      .streaming("processWithDictParameterEditor")
      .source("source", "csv-source-lite")
      .enricher(
        "customNode",
        "data",
        "serviceWithDictParameterEditor",
        "RGBDict"     -> Expression.dictKeyWithLabel("H000000", Some("Black")),
        "BooleanDict" -> Expression.dictKeyWithLabel("true", Some("OLD LABEL")),
        "LongDict"    -> Expression.dictKeyWithLabel("-1500100900", Some("large (negative) number"))
      )
      .emptySink(EndNodeId, "dead-end-lite")

    saveProcessAndTestIt(
      process,
      expressionUsingDictWithLabel = None,
      expectedResult = """RGBDict: Some(H000000)
         |LongDict: Some(-1500100900)
         |BooleanDict: Some(true)""".stripMargin,
      variableToCheck = "data"
    )

    // Check that label bound to dictId-key is updated in BE response
    val response2 = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processes/${process.name}")
        .auth
        .basic("admin", "admin")
    )
    response2.code shouldEqual StatusCode.Ok

    response2.bodyAsJson.hcursor
      .downField("scenarioGraph")
      .downField("nodes")
      .downAt(_.hcursor.get[String]("id").rightValue == "customNode")
      .downField("service")
      .downField("parameters")
      .downAt(_.hcursor.get[String]("name").rightValue == "BooleanDict")
      .downField("expression")
      .downField("expression")
      .as[String]
      .rightValue shouldBe """{"key":"true","label":"ON"}""" // returns "ON" even though "OLD LABEL" was sent, because of ProcessDictSubstitutor
  }

  test("save process with expression using dicts and get it back") {
    val expressionUsingDictWithLabel = s"#DICT['$Label']"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
    saveProcessAndExtractValidationResult(process, Some(expressionUsingDictWithLabel)) shouldBe Json.obj()
  }

  test("save process with invalid expression using dicts and get it back with validation results") {
    val expressionUsingDictWithInvalidLabel = s"#DICT['invalid']"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithInvalidLabel)

    createEmptyScenario(process.name)

    val response1 = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processValidation/${process.name}")
        .contentType(MediaType.ApplicationJson)
        .body(
          ScenarioValidationRequest(process.name, CanonicalProcessConverter.toScenarioGraph(process)).asJson.spaces2
        )
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Ok
    val invalidNodesJson = response1.extractFieldJsonValue("errors", "invalidNodes")
    invalidNodesJson.asObject.value should have size 1
    invalidNodesJson.hcursor.downField(VariableNodeId).downN(0).downField("typ").as[String].rightValue shouldEqual {
      "ExpressionParserCompilationError"
    }

    val invalidNodesAfterSave = extractValidationResult(process)
    invalidNodesAfterSave.asObject.value should have size 1
    invalidNodesAfterSave.hcursor
      .downField(VariableNodeId)
      .downN(0)
      .downField("typ")
      .as[String]
      .rightValue shouldEqual {
      "ExpressionParserCompilationError"
    }

    val response2 = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processes/${process.name}")
        .auth
        .basic("admin", "admin")
    )
    response2.code shouldEqual StatusCode.Ok

    val returnedEndResultExpression = extractVariableExpressionFromGetProcessResponse(response2.bodyAsJson)
    returnedEndResultExpression shouldEqual expressionUsingDictWithInvalidLabel
    val invalidNodesAfterGet = response2.extractFieldJsonValue("validationResult", "errors", "invalidNodes")
    invalidNodesAfterGet.asObject.value should have size 1
    invalidNodesAfterGet.hcursor.downField(VariableNodeId).downN(0).downField("typ").as[String].rightValue shouldEqual {
      "ExpressionParserCompilationError"
    }
  }

  test("save process with expression using dicts and test it") {
    val expressionUsingDictWithLabel = s"#DICT['$Label']"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
    saveProcessAndTestIt(process, Some(expressionUsingDictWithLabel), Key)
  }

  test("save process with expression using dict values as property and test it") {
    val expressionUsingDictWithLabel = s"#DICT.$Label"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
    saveProcessAndTestIt(process, Some(expressionUsingDictWithLabel), Key)
  }

  test("export process with expression using dict") {
    val expressionUsingDictWithLabel = s"#DICT.$Label"
    val expressionUsingDictWithKey   = s"#DICT.$Key"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)

    createEmptyScenario(process.name)

    val exportResponse = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processesExport/${process.name}")
        .contentType(MediaType.ApplicationJson)
        .body(toJson(process).noSpaces)
        .auth
        .basic("admin", "admin")
    )
    exportResponse.code shouldEqual StatusCode.Ok
    val returnedEndResultExpression = extractVariableExpressionFromProcessExportResponse(exportResponse.bodyAsJson)
    returnedEndResultExpression shouldEqual expressionUsingDictWithKey
  }

  private def saveProcessAndTestIt(
      process: CanonicalProcess,
      expressionUsingDictWithLabel: Option[String],
      expectedResult: String,
      variableToCheck: String = VariableName
  ) = {
    saveProcessAndExtractValidationResult(process, expressionUsingDictWithLabel) shouldBe Json.obj()

    val response = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processManagement/test/${process.name}")
        .contentType(MediaType.MultipartFormData)
        .multipartBody(
          sttpPrepareMultiParts(
            "testData"      -> """{"sourceId":"source","record":"field1|field2"}""",
            "scenarioGraph" -> toJson(process).noSpaces
          )()
        )
        .auth
        .basic("admin", "admin")
    )

    response.code shouldEqual StatusCode.Ok
    val endInvocationResult = extractedVariableResultFrom(response.bodyAsJson, variableToCheck)
    endInvocationResult shouldEqual expectedResult
  }

  private def sampleProcessWithExpression(processId: String, variableExpression: String) =
    ScenarioBuilder
      .streaming(processId)
      .additionalFields(properties = Map("param1" -> "true"))
      .source("source", "csv-source-lite")
      .buildSimpleVariable(VariableNodeId, VariableName, variableExpression)
      .emptySink(EndNodeId, "dead-end-lite")

  private def saveProcessAndExtractValidationResult(
      process: CanonicalProcess,
      endResultExpressionToPost: Option[String]
  ): Json = {
    createEmptyScenario(process.name)

    val response1 = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processValidation/${process.name}")
        .contentType(MediaType.ApplicationJson)
        .body(
          ScenarioValidationRequest(process.name, CanonicalProcessConverter.toScenarioGraph(process)).asJson.spaces2
        )
        .auth
        .basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Ok
    response1.extractFieldJsonValue("errors", "invalidNodes").asObject.value shouldBe empty

    extractValidationResult(process).asObject.value shouldBe empty

    val response2 = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processes/${process.name}")
        .auth
        .basic("admin", "admin")
    )
    response2.code shouldEqual StatusCode.Ok
    endResultExpressionToPost.foreach { endResultExpressionToPost =>
      val returnedEndResultExpression = extractVariableExpressionFromGetProcessResponse(response2.bodyAsJson)
      returnedEndResultExpression shouldEqual endResultExpressionToPost
    }
    response2.extractFieldJsonValue("validationResult", "errors", "invalidNodes")
  }

  private def createEmptyScenario(processName: ProcessName) = {
    val command = CreateScenarioCommand(
      name = processName,
      category = Some(Category1.stringify),
      processingMode = None,
      engineSetupName = None,
      isFragment = false,
      forwardedUserName = None
    )
    val response = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processes")
        .contentType(MediaType.ApplicationJson)
        .body(command.asJson.spaces2)
        .auth
        .basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Created
  }

  private def extractValidationResult(process: CanonicalProcess): Json = {
    val response = httpClient.send(
      quickRequest
        .put(uri"$nuDesignerHttpAddress/api/processes/${process.name}")
        .contentType(MediaType.ApplicationJson)
        .body(process.toJsonAsProcessToSave.spaces2)
        .auth
        .basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Ok
    response.extractFieldJsonValue("errors", "invalidNodes")
  }

  private def extractVariableExpressionFromGetProcessResponse(json: Json) = {
    import pl.touk.nussknacker.engine.api.CirceUtil.RichACursor
    json.hcursor
      .downField("scenarioGraph")
      .downField("nodes")
      .downAt(_.hcursor.get[String]("id").rightValue == VariableNodeId)
      .downField("value")
      .downField("expression")
      .as[String]
      .rightValue
  }

  private def extractVariableExpressionFromProcessExportResponse(json: Json) = {
    import pl.touk.nussknacker.engine.api.CirceUtil.RichACursor
    json.hcursor
      .downField("nodes")
      .downAt(_.hcursor.get[String]("id").rightValue == VariableNodeId)
      .downField("value")
      .downField("expression")
      .as[String]
      .rightValue
  }

  private def extractedVariableResultFrom(
      json: Json,
      variableToCheck: String
  ) = {
    json.hcursor
      .downField("results")
      .downField("nodeResults")
      .downField(EndNodeId)
      .downArray
      .downField("variables")
      .downField(variableToCheck)
      .downField("pretty")
      .as[String]
      .rightValue
  }

}
