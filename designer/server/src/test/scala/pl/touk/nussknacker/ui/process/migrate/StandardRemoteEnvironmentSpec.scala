package pl.touk.nussknacker.ui.process.migrate

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.parser
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  ValidationErrors,
  ValidationResult
}
import pl.touk.nussknacker.test.utils.domain.ProcessTestData.{sampleFragmentName, sampleProcessName, validProcess}
import pl.touk.nussknacker.test.utils.domain.TestFactory.{flinkProcessValidator, mapProcessingTypeDataProvider}
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.wrapGraphWithScenarioDetailsEntity
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestProcessUtil}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.process.ProcessService.UpdateScenarioCommand
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class StandardRemoteEnvironmentSpec
    extends AnyFlatSpec
    with Matchers
    with PatientScalaFutures
    with FailFastCirceSupport
    with EitherValuesDetailedMessage
    with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem("nussknacker-designer")
  implicit val user: LoggedUser    = LoggedUser("1", "test")

  it should "handle spaces in scenario id" in {
    val name          = ProcessName("a b c")
    val scenarioGraph = ProcessTestData.validScenarioGraph

    val remoteEnvironment = new MockRemoteEnvironment {

      override protected def request(
          path: Uri,
          method: HttpMethod,
          request: MessageEntity,
          header: Seq[HttpHeader]
      ): Future[HttpResponse] = {
        if (path.toString().startsWith(s"$baseUri/processes/a") && method == HttpMethods.GET) {
          Marshal(
            ScenarioWithDetailsConversions.fromEntityWithScenarioGraph(
              wrapGraphWithScenarioDetailsEntity(name, scenarioGraph),
              ProcessTestData.sampleScenarioParameters
            )
          )
            .to[RequestEntity]
            .map { entity =>
              HttpResponse(StatusCodes.OK, entity = entity)
            }
        } else {
          throw new AssertionError(s"Not expected $path")
        }
      }
    }

    whenReady(remoteEnvironment.compare(scenarioGraph, name, None)) { result =>
      result shouldBe Symbol("right")
    }

  }

  it should "handle non-ascii signs in scenario id" in {
    val name          = ProcessName("łódź")
    val scenarioGraph = ProcessTestData.validScenarioGraph

    val remoteEnvironment = new MockRemoteEnvironment {

      override protected def request(
          path: Uri,
          method: HttpMethod,
          request: MessageEntity,
          headers: Seq[HttpHeader]
      ): Future[HttpResponse] = {
        if (path.toString().startsWith(s"$baseUri/processes/%C5%82%C3%B3d%C5%BA") && method == HttpMethods.GET) {
          Marshal(
            ScenarioWithDetailsConversions.fromEntityWithScenarioGraph(
              wrapGraphWithScenarioDetailsEntity(name, scenarioGraph),
              ProcessTestData.sampleScenarioParameters
            )
          )
            .to[RequestEntity]
            .map { entity =>
              HttpResponse(StatusCodes.OK, entity = entity)
            }
        } else {
          throw new AssertionError(s"Not expected $path")
        }
      }
    }
    whenReady(remoteEnvironment.compare(scenarioGraph, name, None)) { result =>
      result shouldBe Symbol("right")
    }

  }

  it should "test migration" in {
    val remoteEnvironment = environmentForTestMigration(
      processes = ProcessTestData.validScenarioDetailsForMigrations :: Nil,
      fragments = TestProcessUtil.wrapWithDetailsForMigration(
        CanonicalProcessConverter.toScenarioGraph(ProcessTestData.sampleFragment),
        ProcessTestData.sampleFragment.name
      ) :: Nil
    )

    val migrationResult = remoteEnvironment
      .testMigration(
        batchingExecutionContext = ExecutionContext.global
      )
      .futureValueEnsuringInnerException(10 seconds)
      .rightValue

    migrationResult should have size 2
    migrationResult.map(
      _.processName
    ) should contain only (ProcessTestData.validScenarioDetailsForMigrations.name, ProcessTestData.sampleFragment.name)
  }

  override protected def afterAll(): Unit = {
    system.terminate().futureValue
    super.afterAll()
  }

  trait MockRemoteEnvironment extends StandardRemoteEnvironment {
    override def environmentId = "testEnv"

    def config: StandardRemoteEnvironmentConfig = StandardRemoteEnvironmentConfig(
      uri = "http://localhost:8087/api",
      batchSize = 100
    )

    override implicit val materializer: Materializer = Materializer(system)

    override def testModelMigrations: TestModelMigrations = new TestModelMigrations(
      mapProcessingTypeDataProvider(
        "streaming" -> new ProcessModelMigrator(new TestMigrations(1, 2))
      ),
      mapProcessingTypeDataProvider("streaming" -> flinkProcessValidator)
    )

  }

  private trait TriedToAddProcess {
    var triedToAddProcess: Boolean     = false
    var addedFragment: Option[Boolean] = None
  }

  private def statefulEnvironment(
      expectedProcessDetails: ScenarioWithDetailsForMigrations,
      expectedProcessCategory: String,
      initialRemoteProcessList: List[ProcessName],
      onMigrate: Future[UpdateScenarioCommand] => Unit
  ) = new MockRemoteEnvironment with TriedToAddProcess {
    private var remoteProcessList = initialRemoteProcessList

    override protected def request(
        uri: Uri,
        method: HttpMethod,
        request: MessageEntity,
        header: Seq[HttpHeader]
    ): Future[HttpResponse] = {
      import HttpMethods._
      import StatusCodes._
      // helpers
      def is(relative: String, m: HttpMethod): Boolean = {
        uri.toString.startsWith(s"$baseUri$relative") && method == m
      }

      object Validation {
        def unapply(arg: (String, HttpMethod)): Boolean = is(s"/processValidation/${expectedProcessDetails.name}", POST)
      }

      object UpdateProcess {
        def unapply(arg: (String, HttpMethod)): Boolean = is(s"/processes/${expectedProcessDetails.name}", PUT)
      }

      object CheckProcess {
        def unapply(arg: (String, HttpMethod)): Boolean = is(s"/processes/${expectedProcessDetails.name}", GET)
      }

      object AddProcess {
        def unapply(arg: (String, HttpMethod)): Option[Boolean] = {
          if (is(s"/processes", POST)) {
            parseBodyToJson(request).hcursor.downField("isFragment").as[Boolean].toOption
          } else {
            None
          }
        }
      }
      // end helpers

      (uri.toString(), method) match {
        case Validation() =>
          Marshal(ValidationResult.errors(Map(), List(), List())).to[RequestEntity].map { entity =>
            HttpResponse(OK, entity = entity)
          }
        case CheckProcess() if remoteProcessList contains expectedProcessDetails.name =>
          Marshal(expectedProcessDetails).to[RequestEntity].map { entity =>
            HttpResponse(OK, entity = entity)
          }
        case CheckProcess() =>
          Future.successful(HttpResponse(NotFound))
        case AddProcess(isFragment) =>
          remoteProcessList = expectedProcessDetails.name :: remoteProcessList
          triedToAddProcess = true
          addedFragment = Some(isFragment)

          Marshal(ProcessTestData.validScenarioDetailsForMigrations).to[RequestEntity].map { entity =>
            HttpResponse(OK, entity = entity)
          }
        case UpdateProcess() if remoteProcessList contains expectedProcessDetails.name =>
          onMigrate(Unmarshal(request).to[UpdateScenarioCommand])

          Marshal(ValidationResult.errors(Map(), List(), List())).to[RequestEntity].map { entity =>
            HttpResponse(OK, entity = entity)
          }
        case UpdateProcess() =>
          Future.failed(new Exception("Scenario does not exist"))
        case _ =>
          throw new AssertionError(s"Not expected $uri")
      }
    }

  }

  private def parseBodyToJson(request: MessageEntity) = {
    val stringBody = request match {
      case HttpEntity.Strict(_, byteString) => byteString.decodeString(HttpCharsets.`UTF-8`.nioCharset())
      case _                                => throw new IllegalStateException("Unhandled MessageEntity type")
    }
    parser.parse(stringBody).toOption.getOrElse(throw new IllegalStateException("Validation request should be a json"))
  }

  private def environmentForTestMigration(
      processes: List[ScenarioWithDetailsForMigrations],
      fragments: List[ScenarioWithDetailsForMigrations]
  ) = new MockRemoteEnvironment {

    private def allProcesses: List[ScenarioWithDetailsForMigrations] = processes ++ fragments

    override protected def request(
        uri: Uri,
        method: HttpMethod,
        request: MessageEntity,
        header: Seq[HttpHeader]
    ): Future[HttpResponse] = {
      object GetProcessesDetailsWithoutScenarioGraph {
        def unapply(arg: (Uri, HttpMethod)): Boolean = {
          arg._1.toString() == s"$baseUri/processes?isArchived=false" && arg._2 == HttpMethods.GET
        }
      }

      object GetProcessesDetails {
        def unapply(arg: (Uri, HttpMethod)): Option[Set[ProcessName]] = {
          val uri = arg._1
          if (uri.toString().startsWith(s"$baseUri/processesDetails") && uri
              .query()
              .get("isArchived")
              .contains("false") && arg._2 == HttpMethods.GET) {
            uri.query().get("names").map(_.split(",").map(ProcessName(_)).toSet)
          } else {
            None
          }
        }
      }

      (uri, method) match {
        case GetProcessesDetailsWithoutScenarioGraph() =>
          Marshal(allProcesses.map(_.copy(scenarioGraph = None))).to[ResponseEntity].map { entity =>
            HttpResponse(entity = entity)
          }
        case GetProcessesDetails(names) =>
          Marshal(allProcesses.filter(p => names(p.name))).to[ResponseEntity].map { entity =>
            HttpResponse(entity = entity)
          }
        case _ => throw new IllegalArgumentException()
      }
    }

  }

}
