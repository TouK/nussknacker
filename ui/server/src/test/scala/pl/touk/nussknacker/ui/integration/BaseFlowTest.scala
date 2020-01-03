package pl.touk.nussknacker.ui.integration

import java.util.UUID

import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Json}
import org.scalatest._
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedExpressionValues}
import pl.touk.nussknacker.engine.api.process.{ParameterConfig, SingleNodeConfig}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.NussknackerApp
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.util.{ConfigWithScalaVersion, MultipartUtils}

import scala.concurrent.duration._

class BaseFlowTest extends FunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with BeforeAndAfterEach with BeforeAndAfterAll {

  override def testConfig: Config = ConfigWithScalaVersion.config

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val mainRoute = NussknackerApp.initializeRoute(ConfigWithScalaVersion.config)

  private val credentials = HttpCredentials.createBasicHttpCredentials("admin", "admin")

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(1.minute)

  test("saves, updates and retrieves sample process") {

    val processId = UUID.randomUUID().toString
    val endpoint = s"/api/processes/$processId"

    val process = EspProcessBuilder
      .id(processId)
      .exceptionHandler()
      .source("source", "csv-source").processorEnd("end", "monitor")

    saveProcess(endpoint, process)
  }

  test("initializes custom processes") {
    Get("/api/processes/customProcess1") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
    }
  }


  test("ensure config is properly parsed") {
    Post("/api/processDefinitionData/streaming?isSubprocess=false", HttpEntity(ContentTypes.`application/json`, "{}")) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      val settingsJson = responseAs[Json].hcursor.downField("nodesConfig").focus.get
      val settings = Decoder[Map[String, SingleNodeConfig]].decodeJson(settingsJson).right.get

      val underTest = Map(
        //docs url comes from reference.conf in managementSample
        "filter" -> SingleNodeConfig(None, None, Some("https://touk.github.io/nussknacker/filter"), None),
        "test1" -> SingleNodeConfig(None, Some("Sink.svg"), None, None),
        "enricher" -> SingleNodeConfig(Some(Map("param" -> ParameterConfig(Some("'default value'"), Some(
          FixedExpressionValues(List(
            FixedExpressionValue("'default value'", "first"),
            FixedExpressionValue("'other value'", "second")
          )))
          //docs url comes from reference.conf in managementSample
        ))), Some("Filter.svg"), Some("https://touk.github.io/nussknacker/enricher"), None),
        "accountService" -> SingleNodeConfig(None, None, Some("accountServiceDocs"), None)
      )

      val (relevant, other) = settings.partition { case (k, _) => underTest.keySet contains k }
      relevant shouldBe underTest
      other.values.forall(_.docsUrl.isEmpty) shouldBe true
    }
  }

  test("be able to work with subprocess with custom class inputs") {
    val processId = UUID.randomUUID().toString
    val endpoint = s"/api/processes/$processId"

    val process = DisplayableProcess(
      id = processId,
      properties = ProcessProperties(StreamMetaData(), ExceptionHandlerRef(List()), isSubprocess = true, subprocessVersions = Map()),
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
          "Fatal error: Failed to load subprocess parameter: i.do.not.exist for input1, please check configuration")

        Get(endpoint) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
          status shouldEqual StatusCodes.OK
        }
      }
    }
  }

  import spel.Implicits._

  test("should test process with complexReturnObjectService") {
    val processId = "complexObjectProcess" + UUID.randomUUID().toString
    val endpoint = s"/api/processes/$processId"

    val process = EspProcessBuilder
      .id(processId)
      .exceptionHandler()
      .source("source", "csv-source")
      .enricher("enricher", "out", "complexReturnObjectService")
      .sink("end", "#input", "sendSms")

    saveProcess(endpoint, process)

    val multiPart = MultipartUtils.prepareMultiParts("testData" -> "record1|field2", "processJson" -> TestProcessUtil.toJson(process).noSpaces)()
    Post(s"/api/processManagement/test/${process.id}", multiPart) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.OK
    }
  }

  private def saveProcess(endpoint: String, process: EspProcess) = {
    Post(s"$endpoint/Category1?isSubprocess=false") ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
      status shouldEqual StatusCodes.Created
      Put(endpoint, TestFactory.posting.toEntityAsProcessToSave(process)) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
        status shouldEqual StatusCodes.OK
        Get(endpoint) ~> addCredentials(credentials) ~> mainRoute ~> checkWithClue {
          status shouldEqual StatusCodes.OK
        }
      }
    }
  }

  def checkWithClue[T](body: => T): RouteTestResult => T = check {
    withClue(s"response: '${responseAs[String]}'") {
      body
    }
  }

}
