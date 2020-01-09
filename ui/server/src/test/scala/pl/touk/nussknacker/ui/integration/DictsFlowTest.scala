package pl.touk.nussknacker.ui.integration

import java.util.UUID

import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{ACursor, Json}
import org.scalatest._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.NussknackerApp
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.util.{ConfigWithScalaVersion, MultipartUtils}

class DictsFlowTest extends FunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll with EitherValues with OptionValues {

  import pl.touk.nussknacker.engine.spel.Implicits._

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val mainRoute = NussknackerApp.initializeRoute(system.settings.config)

  private val credentials = HttpCredentials.createBasicHttpCredentials("admin", "admin")

  override def testConfig: Config = ConfigWithScalaVersion.config

  private val DictId = "dict"
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

  test("save process with expression using dicts and load get it") {
    val expressionUsingDictWithLabel = s"#DICT['$Label']"
    val process = sampleProcessWithExpression(UUID.randomUUID().toString, expressionUsingDictWithLabel)
    saveProcessAndCheckIfCanBeGet(process, expressionUsingDictWithLabel)
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
    Post(s"/api/processesExport", TestProcessUtil.toJson(process)) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      val returnedEndResultExpression = extractEndResultExpression(responseAs[Json].hcursor)
      returnedEndResultExpression shouldEqual expressionUsingDictWithKey
    }
  }

  private def saveProcessAndTestIt(process: EspProcess, expressionUsingDictWithLabel: String, expectedResult: String) = {
    saveProcessAndCheckIfCanBeGet(process, expressionUsingDictWithLabel)

    val multiPart = MultipartUtils.prepareMultiParts("testData" -> "record1|field2", "processJson" -> TestProcessUtil.toJson(process).noSpaces)()
    Post(s"/api/processManagement/test/${process.id}", multiPart) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      val endInvocationResult = extractedEndInvocationResult()
      endInvocationResult shouldEqual expectedResult
    }
  }

  private def sampleProcessWithExpression(processId: String, endResultExpression: String) =
    EspProcessBuilder
      .id(processId)
      .additionalFields(properties = Map("param1" -> "true"))
      .exceptionHandler()
      .source("source", "csv-source")
      .sink(EndNodeId, endResultExpression, "monitor")

  private def saveProcessAndCheckIfCanBeGet(process: EspProcess,
                                            endResultExpressionToPost: String) = {


    val processRootResource = s"/api/processes/${process.id}"
    Post("/api/processValidation", TestFactory.posting.toEntity(process))~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      checkNoInvalidNodesDirect()
    }

    saveProcess(processRootResource, process)

    Get(processRootResource) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      checkNoInvalidNodesInValidationResul()
      val returnedEndResultExpression = extractEndResultExpression(responseAs[Json].hcursor.downField("json"))
      returnedEndResultExpression shouldEqual endResultExpressionToPost
    }
  }

  private def saveProcess(processRootResource: String, process: EspProcess) = {
    Post(s"$processRootResource/Category1?isSubprocess=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.Created
    }

    Put(processRootResource, TestFactory.posting.toEntityAsProcessToSave(process)) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
      checkNoInvalidNodesDirect()
    }
  }

  private def extractEndResultExpression(cursor: ACursor) = {
      cursor.downField("nodes")
      .downAt(_.hcursor.get[String]("id").right.value == EndNodeId)
      .downField("endResult")
      .downField("expression")
      .as[String].right.value
  }

  private def extractedEndInvocationResult() = {
    val response = responseAs[Json]
    response.hcursor
      .downField("results")
      .downField("invocationResults")
      .downField(EndNodeId)
      .downArray
      .first
      .downField("value")
      .downField("pretty")
      .as[String].right.value
  }

  private def checkNoInvalidNodesDirect() = {
    val response = responseAs[Json]

    val invalidNodes = response.hcursor
      .downField("errors")
      .downField("invalidNodes")
      .as[Json].right.value
    val invalidNodesObj = invalidNodes.asObject.value

    invalidNodesObj shouldBe empty
  }

  private def checkNoInvalidNodesInValidationResul() = {
    val response = responseAs[Json]

    val invalidNodes = response.hcursor
      .downField("json")
      .downField("validationResult")
      .downField("errors")
      .downField("invalidNodes")
      .as[Json].right.value
    val invalidNodesObj = invalidNodes.asObject.value

    invalidNodesObj shouldBe empty
  }

  def checkWithClue[T](body: ⇒ T): RouteTestResult ⇒ T = check {
    withClue(responseAs[String]) {
      body
    }
  }

}
