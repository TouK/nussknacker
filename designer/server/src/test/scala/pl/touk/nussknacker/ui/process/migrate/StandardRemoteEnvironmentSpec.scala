package pl.touk.nussknacker.ui.process.migrate

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.restmodel.processdetails.{BasicProcess, ValidatedProcessDetails}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType, ValidationErrors, ValidationResult}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.toValidatedDisplayable
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.Streaming
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestCategories, TestFactory, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.security.api.LoggedUser
import io.circe.parser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StandardRemoteEnvironmentSpec extends AnyFlatSpec with Matchers with PatientScalaFutures with FailFastCirceSupport with EitherValues {

  implicit val system = ActorSystem("nussknacker-designer")

  implicit val user = LoggedUser("1", "test")

  trait MockRemoteEnvironment extends StandardRemoteEnvironment {
    override def environmentId = "testEnv"

    def config: StandardRemoteEnvironmentConfig = StandardRemoteEnvironmentConfig(
      uri = "http://localhost:8087/api",
      batchSize = 100
    )

    override implicit val materializer = Materializer(system)

    override def testModelMigrations: TestModelMigrations = new TestModelMigrations(mapProcessingTypeDataProvider(Streaming -> new TestMigrations(1, 2)), TestFactory.processValidation)

  }

  private trait TriedToAddProcess {
    var triedToAddProcess: Boolean = false
    var addedSubprocess: Option[Boolean] = None
  }

  private def statefulEnvironment(expectedProcessId: String,
                                  expectedProcessCategory: String,
                                  initialRemoteProcessList: List[String],
                                  onMigrate: Future[UpdateProcessCommand] => Unit) = new MockRemoteEnvironment with TriedToAddProcess {
    private var remoteProcessList = initialRemoteProcessList

    override protected def request(uri: Uri, method: HttpMethod, request: MessageEntity) : Future[HttpResponse] = {
      import HttpMethods._
      import StatusCodes._

      // helpers
      def is(relative: String, m: HttpMethod): Boolean = {
        uri.toString.startsWith(s"$baseUri$relative") && method == m
      }

      object Validation {
        def unapply(arg: (String, HttpMethod)): Boolean = is("/processValidation", POST)
      }

      object UpdateProcess {
        def unapply(arg: (String, HttpMethod)): Boolean = is(s"/processes/$expectedProcessId", PUT)
      }

      object CheckProcess {
        def unapply(arg: (String, HttpMethod)): Boolean = is(s"/processes/$expectedProcessId", GET)
      }

      object AddProcess {
        def unapply(arg: (String, HttpMethod)): Option[Boolean] = {
          if (is(s"/processes/$expectedProcessId/$expectedProcessCategory", POST)) {
            uri.query().get("isSubprocess").map(_.toBoolean).orElse(Some(false))
          } else {
            None
          }
        }
      }
      // end helpers

      (uri.toString(), method) match {
        case Validation() =>
          val requestJson = parseBodyToJson(request)
          requestJson.hcursor.downField("category").as[String] shouldBe Right(expectedProcessCategory)
          Marshal(ValidationResult.errors(Map(), List(), List())).to[RequestEntity].map { entity =>
            HttpResponse(OK, entity = entity)
          }

        case CheckProcess() if remoteProcessList contains expectedProcessId =>
          Future.successful(HttpResponse(OK))

        case CheckProcess() =>
          Future.successful(HttpResponse(NotFound))

        case AddProcess(isSubprocess) =>
          remoteProcessList = expectedProcessId :: remoteProcessList
          triedToAddProcess = true
          addedSubprocess = Some(isSubprocess)

          Marshal(ProcessTestData.validProcessDetails).to[RequestEntity].map { entity =>
            HttpResponse(OK, entity = entity)
          }

        case UpdateProcess() if remoteProcessList contains expectedProcessId =>
          onMigrate(Unmarshal(request).to[UpdateProcessCommand])

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
      case _ => throw new IllegalStateException("Unhandled MessageEntity type")
    }
    parser.parse(stringBody)
      .right.getOrElse(throw new IllegalStateException("Validation request should be a json"))
  }

  private def environmentForTestMigration(processes: List[ValidatedProcessDetails],
                                          subProcesses: List[ValidatedProcessDetails]) = new MockRemoteEnvironment {

    private def basicProcesses: List[BasicProcess] = (processes ++ subProcesses).map(BasicProcess.apply(_))

    override protected def request(uri: Uri, method: HttpMethod, request: MessageEntity): Future[HttpResponse] = {
      object GetBasicProcesses {
        def unapply(arg: (Uri, HttpMethod)): Boolean = {
          arg._1.toString() == s"$baseUri/processes?isArchived=false&isSubprocess=false" && arg._2 == HttpMethods.GET
        }
      }

      object GetProcessesDetails {
        def unapply(arg: (Uri, HttpMethod)): Option[Set[String]] = {
          val uri = arg._1
          if (uri.toString().startsWith(s"$baseUri/processesDetails") && uri.query().get("isArchived").contains("false") && arg._2 == HttpMethods.GET) {
            uri.query().get("names").map(_.split(",").toSet)
          } else {
            None
          }
        }
      }

      object GetSubProcessesDetails {
        def unapply(arg: (Uri, HttpMethod)): Boolean = {
          arg._1.toString() == s"$baseUri/subProcessesDetails" && arg._2 == HttpMethods.GET
        }
      }

      (uri, method) match {
        case GetBasicProcesses() =>
          Marshal(basicProcesses).to[ResponseEntity].map { entity => HttpResponse(entity = entity) }
        case GetProcessesDetails(names) =>
          Marshal(processes.filter(p => names(p.name))).to[ResponseEntity].map { entity => HttpResponse(entity = entity) }
        case GetSubProcessesDetails() =>
          Marshal(subProcesses).to[ResponseEntity].map { entity => HttpResponse(entity = entity) }
      }
    }

  }

  it should "not migrate not validating scenario" in {

    val remoteEnvironment = new MockRemoteEnvironment {
      override protected def request(path: Uri, method: HttpMethod, request: MessageEntity) : Future[HttpResponse] = {
        if (path.toString.contains("processValidation") && method == HttpMethods.POST) {
          Marshal(ValidationResult.errors(Map("n1" -> List(NodeValidationError("bad", "message", "", None, NodeValidationErrorType.SaveAllowed))), List(), List())).to[RequestEntity].map { entity =>
            HttpResponse(StatusCodes.OK, entity = entity)
          }
        } else {
          throw new AssertionError(s"Not expected $path")
        }
      }

    }

    whenReady(remoteEnvironment.migrate(ProcessTestData.validDisplayableProcess.toDisplayable, ProcessTestData.validProcessDetails.processCategory)) { result =>
      result shouldBe 'left
      result.left.get shouldBe MigrationValidationError(ValidationErrors(Map("n1" -> List(NodeValidationError("bad","message","" ,None, NodeValidationErrorType.SaveAllowed))),List(),List()))
      result.left.get.getMessage shouldBe "Cannot migrate, following errors occurred: n1 - message"
    }

  }

  it should "handle spaces in scenario id" in {
    val process = ProcessTestData.toValidatedDisplayable(ProcessTestData.validProcessWithId("a b c")).toDisplayable

    val remoteEnvironment = new MockRemoteEnvironment {

      override protected def request(path: Uri, method: HttpMethod, request: MessageEntity) : Future[HttpResponse] = {
        if (path.toString().startsWith(s"$baseUri/processes/a") && method == HttpMethods.GET) {
          Marshal(displayableToProcess(process)).to[RequestEntity].map { entity =>
            HttpResponse(StatusCodes.OK, entity = entity)
          }
        } else {
          throw new AssertionError(s"Not expected $path")
        }
      }
    }


    whenReady(remoteEnvironment.compare(process, None)) { result =>
      result shouldBe 'right
    }

  }

  it should "handle non-ascii signs in scenario id" in {
    val process = ProcessTestData.toValidatedDisplayable(ProcessTestData.validProcessWithId("łódź")).toDisplayable

    val remoteEnvironment = new MockRemoteEnvironment {

      override protected def request(path: Uri, method: HttpMethod, request: MessageEntity) : Future[HttpResponse] = {
        if (path.toString().startsWith(s"$baseUri/processes/%C5%82%C3%B3d%C5%BA") && method == HttpMethods.GET) {
          Marshal(displayableToProcess(process)).to[RequestEntity].map { entity =>
            HttpResponse(StatusCodes.OK, entity = entity)
          }
        } else {
          throw new AssertionError(s"Not expected $path")
        }
      }
    }
    whenReady(remoteEnvironment.compare(process, None)) { result =>
      result shouldBe 'right
    }

  }

  it should "migrate valid existing scenario" in {
    var migrated : Option[Future[UpdateProcessCommand]] = None
    val remoteEnvironment: MockRemoteEnvironment with TriedToAddProcess = statefulEnvironment(
      ProcessTestData.validProcess.id,
      ProcessTestData.validProcessDetails.processCategory,
      ProcessTestData.validDisplayableProcess.id :: Nil,
      migrationFuture => migrated = Some(migrationFuture)
    )

    whenReady(remoteEnvironment.migrate(ProcessTestData.validDisplayableProcess.toDisplayable, ProcessTestData.validProcessDetails.processCategory)) { result =>
      result shouldBe 'right
    }

    migrated shouldBe 'defined
    remoteEnvironment.triedToAddProcess shouldBe false
    remoteEnvironment.addedSubprocess shouldBe None

    whenReady(migrated.get) { processToSave =>
      processToSave.comment shouldBe UpdateProcessComment("Scenario migrated from testEnv by test")
      processToSave.process shouldBe ProcessTestData.validDisplayableProcess.toDisplayable
    }
  }

  it should "migrate valid non-existing scenario" in {
    var migrated : Option[Future[UpdateProcessCommand]] = None
    val remoteEnvironment: MockRemoteEnvironment with TriedToAddProcess = statefulEnvironment(
      ProcessTestData.validProcess.id,
      ProcessTestData.validProcessDetails.processCategory,
      Nil,
      migrationFuture => migrated = Some(migrationFuture)
    )

    whenReady(remoteEnvironment.migrate(ProcessTestData.validDisplayableProcess.toDisplayable, ProcessTestData.validProcessDetails.processCategory)) { result =>
      result shouldBe 'right
    }

    migrated shouldBe 'defined
    remoteEnvironment.triedToAddProcess shouldBe true
    remoteEnvironment.addedSubprocess shouldBe Some(false)

    whenReady(migrated.get) { processToSave =>
      processToSave.comment shouldBe UpdateProcessComment("Scenario migrated from testEnv by test")
      processToSave.process shouldBe ProcessTestData.validDisplayableProcess.toDisplayable
    }
  }

  it should "migrate fragment" in {
    val category = TestCategories.Category1
    var migrated : Option[Future[UpdateProcessCommand]] = None
    val subprocess = ProcessConverter.toDisplayable(ProcessTestData.sampleSubprocess, TestProcessingTypes.Streaming, category)
    val remoteEnvironment: MockRemoteEnvironment with TriedToAddProcess = statefulEnvironment(
      expectedProcessId = subprocess.id,
      expectedProcessCategory = category,
      initialRemoteProcessList = Nil,
      onMigrate = migrationFuture => migrated = Some(migrationFuture)
    )

    remoteEnvironment.migrate(subprocess, category).futureValue shouldBe 'right
    migrated shouldBe 'defined
    remoteEnvironment.triedToAddProcess shouldBe true
    remoteEnvironment.addedSubprocess shouldBe Some(true)

    whenReady(migrated.get) { processToSave =>
      processToSave.comment shouldBe UpdateProcessComment("Scenario migrated from testEnv by test")
      processToSave.process shouldBe subprocess
    }
  }

  it should "test migration" in {
    val remoteEnvironment = environmentForTestMigration(
      processes = ProcessTestData.validProcessDetails :: Nil,
      subProcesses = TestProcessUtil.validatedToProcess(toValidatedDisplayable(ProcessTestData.sampleSubprocess)) :: Nil
    )

    val migrationResult = remoteEnvironment.testMigration().futureValue.value

    migrationResult should have size 2
    migrationResult.map(_.converted.id) should contain only (ProcessTestData.validProcessDetails.name, ProcessTestData.sampleSubprocess.id)
  }
}
