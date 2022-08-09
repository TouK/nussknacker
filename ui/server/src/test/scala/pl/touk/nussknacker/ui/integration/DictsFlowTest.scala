package pl.touk.nussknacker.ui.integration

import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{ACursor, Json}
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest._
import pl.touk.nussknacker.engine.api.CirceUtil.RichACursor
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, VeryPatientScalaFutures}
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.util.{ConfigWithScalaVersion, MultipartUtils}
import pl.touk.nussknacker.ui.{NusskanckerDefaultAppRouter, NussknackerAppInitializer}

import java.util.UUID
import scala.concurrent.duration._

class DictsFlowTest extends FunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with VeryPatientScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll with EitherValuesDetailedMessage with OptionValues {

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val (mainRoute, _) = NusskanckerDefaultAppRouter.create(
    system.settings.config,
    NussknackerAppInitializer.initDb(system.settings.config),
    new MetricRegistry
  )

  private val credentials = HttpCredentials.createBasicHttpCredentials("admin", "admin")

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(2.minutes)

  override def testConfig: Config = ConfigWithScalaVersion.TestsConfig

  private val DictId = "dict"
  private val VariableNodeId = "variableCheck"
  private val VariableName = "variableToCheck"
  private val EndNodeId = "end"
  private val Key = "foo"
  private val Label = "Foo"

  test("query dict entries by label pattern") {
    Get(s"/api/processDefinitionData/${TestProcessingTypes.Streaming}/dict/$DictId/entry?label=fo") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      val response = responseAs[Json]

      response shouldEqual Json.arr(Json.obj(
        "key" -> Json.fromString(Key),
        "label" -> Json.fromString(Label)))
    }

    Get(s"/api/processDefinitionData/${TestProcessingTypes.Streaming}/dict/notExisting/entry?label=fo") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.NotFound
    }

    Get(s"/api/processDefinitionData/${TestProcessingTypes.Streaming}/dict/$DictId/entry?label=notexisting") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK

      val response = responseAs[Json]

      response shouldEqual Json.arr()
    }
  }

  test("save process with expression using dicts and get it back") {
    val expressionUsingDictWithLabel = s"#DICT['$Label']"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
    saveProcessAndExtractValidationResult(process, expressionUsingDictWithLabel).asObject.value shouldBe empty
  }

  test("save process with invalid expression using dicts and get it back with validation results") {
    val expressionUsingDictWithInvalidLabel = s"#DICT['invalid']"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithInvalidLabel)

    val processRootResource = s"/api/processes/${process.id}"

    createEmptyScenario(processRootResource)

    Post("/api/processValidation", TestFactory.posting.toEntity(process))~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      val invalidNodes = extractInvalidNodes
      invalidNodes.asObject.value should have size 1
      invalidNodes.hcursor.downField(VariableNodeId).downN(0).downField("typ").as[String].rightValue shouldEqual "ExpressionParserCompilationError"
    }

    val invalidNodesAfterSave = extractValidationResult(processRootResource, process)
    invalidNodesAfterSave.asObject.value should have size 1
    invalidNodesAfterSave.hcursor.downField(VariableNodeId).downN(0).downField("typ").as[String].rightValue shouldEqual "ExpressionParserCompilationError"

    Get(processRootResource) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      val returnedEndResultExpression = extractVariableExpression(responseAs[Json].hcursor.downField("json"))
      returnedEndResultExpression shouldEqual expressionUsingDictWithInvalidLabel
      val invalidNodesAfterGet = extractInvalidNodesFromValidationResult
      invalidNodesAfterGet.asObject.value should have size 1
      invalidNodesAfterGet.hcursor.downField(VariableNodeId).downN(0).downField("typ").as[String].rightValue shouldEqual "ExpressionParserCompilationError"
    }
  }

  test("save process with expression using dicts and test it") {
    val expressionUsingDictWithLabel = s"#DICT['$Label']"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
    saveProcessAndTestIt(process, expressionUsingDictWithLabel, Key)
  }

  test("save process with expression using dict values as property and test it") {
    val expressionUsingDictWithLabel = s"#DICT.$Label"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
    saveProcessAndTestIt(process, expressionUsingDictWithLabel, Key)
  }

  test("export process with expression using dict") {
    val expressionUsingDictWithLabel = s"#DICT.$Label"
    val expressionUsingDictWithKey = s"#DICT.$Key"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)

    Post(s"/api/processes/${process.id}/Category1?isSubprocess=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.Created
    }

    Post(s"/api/processesExport", TestProcessUtil.toJson(process)) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      val returnedEndResultExpression = extractVariableExpression(responseAs[Json].hcursor)
      returnedEndResultExpression shouldEqual expressionUsingDictWithKey
    }
  }

  private def saveProcessAndTestIt(process: EspProcess, expressionUsingDictWithLabel: String, expectedResult: String) = {
    saveProcessAndExtractValidationResult(process, expressionUsingDictWithLabel).asObject.value shouldBe empty

    val multiPart = MultipartUtils.prepareMultiParts("testData" -> "record1|field2", "processJson" -> TestProcessUtil.toJson(process).noSpaces)()
    Post(s"/api/processManagement/test/${process.id}", multiPart) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      val endInvocationResult = extractedVariableResult()
      endInvocationResult shouldEqual expectedResult
    }
  }

  private def sampleProcessWithExpression(processId: String, variableExpression: String) =
    ScenarioBuilder
      .streaming(processId)
      .additionalFields(properties = Map("param1" -> "true"))
      .source("source", "csv-source")
      .buildSimpleVariable(VariableNodeId, VariableName, variableExpression)
      .emptySink(EndNodeId, "monitor")

  private def saveProcessAndExtractValidationResult(process: EspProcess, endResultExpressionToPost: String): Json = {
    val processRootResource = s"/api/processes/${process.id}"

    createEmptyScenario(processRootResource)

    Post("/api/processValidation", TestFactory.posting.toEntity(process))~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      extractInvalidNodes.asObject.value shouldBe empty
    }

    extractValidationResult(processRootResource, process).asObject.value shouldBe empty

    Get(processRootResource) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      val returnedEndResultExpression = extractVariableExpression(responseAs[Json].hcursor.downField("json"))
      returnedEndResultExpression shouldEqual endResultExpressionToPost
      extractInvalidNodesFromValidationResult
    }
  }

  private def createEmptyScenario(processRootResource: String) =
    Post(s"$processRootResource/Category1?isSubprocess=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.Created
    }

  private def extractValidationResult(processRootResource: String, process: EspProcess): Json =
    Put(processRootResource, TestFactory.posting.toEntityAsProcessToSave(process)) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      extractInvalidNodes
    }

  private def extractVariableExpression(cursor: ACursor) = {
      cursor.downField("nodes")
      .downAt(_.hcursor.get[String]("id").rightValue == VariableNodeId)
      .downField("value")
      .downField("expression")
      .as[String].rightValue
  }

  private def extractedVariableResult() = {
    val response = responseAs[Json]
    response.hcursor
      .downField("results")
      .downField("nodeResults")
      .downField(EndNodeId)
      .downArray
      .downField("context")
      .downField("variables")
      .downField(VariableName)
      .downField("pretty")
      .as[String].rightValue
  }

  private def extractInvalidNodes: Json = {
    val response = responseAs[Json]

    response.hcursor
      .downField("errors")
      .downField("invalidNodes")
      .as[Json].rightValue
  }

  private def extractInvalidNodesFromValidationResult: Json = {
    val response = responseAs[Json]

    response.hcursor
      .downField("json")
      .downField("validationResult")
      .downField("errors")
      .downField("invalidNodes")
      .as[Json].rightValue
  }

  def checkWithClue[T](body: ⇒ T): RouteTestResult ⇒ T = check {
    withClue(responseAs[String]) {
      body
    }
  }

}
