package pl.touk.nussknacker.ui.integration

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, EitherValues, OptionValues}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.test.{PatientScalaFutures, WithTestHttpClient}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.Streaming
import pl.touk.nussknacker.ui.api.helpers._
import sttp.client3.{UriContext, quickRequest}
import sttp.model.{MediaType, StatusCode}

import java.util.UUID

class DictsFlowTest
  extends AnyFunSuite
    with NuItTest
    with WithTestHttpClient
    with Matchers
    with PatientScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with OptionValues
    with EitherValues {

  private val DictId = "dict"
  private val VariableNodeId = "variableCheck"
  private val VariableName = "variableToCheck"
  private val EndNodeId = "end"
  private val Key = "foo"
  private val Label = "Foo"

  // todo: fixme
  override def afterAll(): Unit = {
    super.afterAll()
  }

  test("query dict entries by label pattern") {
    val response1 = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processDefinitionData/$Streaming/dict/$DictId/entry?label=fo")
        .auth.basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Ok
    response1.bodyAsJson shouldEqual Json.arr(
      Json.obj(
        "key" -> Json.fromString(Key),
        "label" -> Json.fromString(Label)
      )
    )

    val response2 = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processDefinitionData/$Streaming/dict/notExisting/entry?label=fo")
        .auth.basic("admin", "admin")
    )
    response2.code shouldEqual StatusCode.NotFound

    val response3 = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processDefinitionData/$Streaming/dict/$DictId/entry?label=notexisting")
        .auth.basic("admin", "admin")
    )
    response3.code shouldEqual StatusCode.Ok
    response3.bodyAsJson shouldEqual Json.arr()
  }

  test("save process with expression using dicts and get it back") {
    val expressionUsingDictWithLabel = s"#DICT['$Label']"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
    saveProcessAndExtractValidationResult(process, expressionUsingDictWithLabel) shouldBe Json.obj()
  }

  //  test("save process with invalid expression using dicts and get it back with validation results") {
  //    val expressionUsingDictWithInvalidLabel = s"#DICT['invalid']"
  //    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithInvalidLabel)
  //
  //    val processRootResource = s"/api/processes/${process.id}"
  //
  //    createEmptyScenario(processRootResource)
  //
  //    Post("/api/processValidation", TestFactory.posting.toEntity(process)) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
  //      status shouldEqual StatusCodes.OK
  //      val invalidNodes = extractInvalidNodes
  //      invalidNodes.asObject.value should have size 1
  //      invalidNodes.hcursor.downField(VariableNodeId).downN(0).downField("typ").as[String].rightValue shouldEqual "ExpressionParserCompilationError"
  //    }
  //
  //    val invalidNodesAfterSave = extractValidationResult(processRootResource, process)
  //    invalidNodesAfterSave.asObject.value should have size 1
  //    invalidNodesAfterSave.hcursor.downField(VariableNodeId).downN(0).downField("typ").as[String].rightValue shouldEqual "ExpressionParserCompilationError"
  //
  //    Get(processRootResource) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
  //      status shouldEqual StatusCodes.OK
  //      val returnedEndResultExpression = extractVariableExpression(responseAs[Json].hcursor.downField("json"))
  //      returnedEndResultExpression shouldEqual expressionUsingDictWithInvalidLabel
  //      val invalidNodesAfterGet = extractInvalidNodesFromValidationResult
  //      invalidNodesAfterGet.asObject.value should have size 1
  //      invalidNodesAfterGet.hcursor.downField(VariableNodeId).downN(0).downField("typ").as[String].rightValue shouldEqual "ExpressionParserCompilationError"
  //    }
  //  }
  //
  //  test("save process with expression using dicts and test it") {
  //    val expressionUsingDictWithLabel = s"#DICT['$Label']"
  //    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
  //    saveProcessAndTestIt(process, expressionUsingDictWithLabel, Key)
  //  }
  //
  //  test("save process with expression using dict values as property and test it") {
  //    val expressionUsingDictWithLabel = s"#DICT.$Label"
  //    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
  //    saveProcessAndTestIt(process, expressionUsingDictWithLabel, Key)
  //  }
  //
  //  test("export process with expression using dict") {
  //    val expressionUsingDictWithLabel = s"#DICT.$Label"
  //    val expressionUsingDictWithKey = s"#DICT.$Key"
  //    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
  //
  //    Post(s"/api/processes/${process.id}/Category1?isFragment=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
  //      status shouldEqual StatusCodes.Created
  //    }
  //
  //    Post(s"/api/processesExport", TestProcessUtil.toJson(process)) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
  //      status shouldEqual StatusCodes.OK
  //      val returnedEndResultExpression = extractVariableExpression(responseAs[Json].hcursor)
  //      returnedEndResultExpression shouldEqual expressionUsingDictWithKey
  //    }
  //  }
  //
  //  private def saveProcessAndTestIt(process: CanonicalProcess, expressionUsingDictWithLabel: String, expectedResult: String) = {
  //    saveProcessAndExtractValidationResult(process, expressionUsingDictWithLabel).asObject.value shouldBe empty
  //
  //    val testDataContent = """{"sourceId":"source","record":"field1|field2"}"""
  //    val multiPart = MultipartUtils.prepareMultiParts("testData" -> testDataContent, "processJson" -> TestProcessUtil.toJson(process).noSpaces)()
  //    Post(s"/api/processManagement/test/${process.id}", multiPart) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
  //      status shouldEqual StatusCodes.OK
  //      val endInvocationResult = extractedVariableResult()
  //      endInvocationResult shouldEqual expectedResult
  //    }
  //  }
  //
  private def sampleProcessWithExpression(processId: String, variableExpression: String) =
    ScenarioBuilder
      .streaming(processId)
      .additionalFields(properties = Map("param1" -> "true"))
      .source("source", "csv-source-lite")
      .buildSimpleVariable(VariableNodeId, VariableName, variableExpression)
      .emptySink(EndNodeId, "dead-end-lite")

  private def saveProcessAndExtractValidationResult(process: CanonicalProcess, endResultExpressionToPost: String): Json = {
    createEmptyScenario(process.id)

    val response1 = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processValidation")
        .contentType(MediaType.ApplicationJson)
        .body(TestFactory.posting.toJson(process).spaces2)
        .auth.basic("admin", "admin")
    )
    response1.code shouldEqual StatusCode.Ok
    response1.extractFieldJsonValue("errors", "invalidNodes").asObject.value shouldBe empty

    extractValidationResult(process).asObject.value shouldBe empty

    val response2 = httpClient.send(
      quickRequest
        .get(uri"$nuDesignerHttpAddress/api/processes/${process.id}")
        .auth.basic("admin", "admin")
    )
    response2.code shouldEqual StatusCode.Ok
    val returnedEndResultExpression = extractJsonVariableExpression(response2.bodyAsJson)
    returnedEndResultExpression shouldEqual endResultExpressionToPost

    response2.extractFieldJsonValue("json", "validationResult", "errors", "invalidNodes")
  }

  private def createEmptyScenario(processId: String) = {
    val response = httpClient.send(
      quickRequest
        .post(uri"$nuDesignerHttpAddress/api/processes/$processId/Category1?isFragment=false")
        .auth.basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Created
  }

  private def extractValidationResult(process: CanonicalProcess): Json = {
    val response = httpClient.send(
      quickRequest
        .put(uri"$nuDesignerHttpAddress/api/processes/${process.id}")
        .contentType(MediaType.ApplicationJson)
        .body(TestFactory.posting.toJsonAsProcessToSave(process).spaces2)
        .auth.basic("admin", "admin")
    )
    response.code shouldEqual StatusCode.Ok
    response.extractFieldJsonValue("errors", "invalidNodes")
  }

  private def extractJsonVariableExpression(json: Json) = {
    import pl.touk.nussknacker.engine.api.CirceUtil.RichACursor
    json.hcursor
      .downField("json")
      .downField("nodes")
      .downAt(_.hcursor.get[String]("id").value == VariableNodeId)
      .downField("value")
      .downField("expression")
      .as[String]
      .value
  }

  //
  //  private def extractVariableExpression(cursor: ACursor) = {
  //    cursor.downField("nodes")
  //      .downAt(_.hcursor.get[String]("id").rightValue == VariableNodeId)
  //      .downField("value")
  //      .downField("expression")
  //      .as[String].rightValue
  //  }
  //
  //  private def extractedVariableResult() = {
  //    val response = responseAs[Json]
  //    response.hcursor
  //      .downField("results")
  //      .downField("nodeResults")
  //      .downField(EndNodeId)
  //      .downArray
  //      .downField("context")
  //      .downField("variables")
  //      .downField(VariableName)
  //      .downField("pretty")
  //      .as[String].rightValue
  //  }
  //
  //    private def extractInvalidNodes(response: Response[String]): Json = {
  //
  //    }
  //
  //  private def extractInvalidNodesFromValidationResult: Json = {
  //    val response = responseAs[Json]
  //
  //    response.hcursor
  //      .downField("json")
  //      .downField("validationResult")
  //      .downField("errors")
  //      .downField("invalidNodes")
  //      .as[Json].rightValue
  //  }
  //
  //  def checkWithClue[T](body: => T): RouteTestResult => T = check {
  //    withClue(responseAs[String]) {
  //      body
  //    }
  //  }

}
