package pl.touk.nussknacker.ui.process.migrate

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType, ValidationErrors, ValidationResult}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StandardRemoteEnvironmentSpec extends FlatSpec with Matchers with PatientScalaFutures with FailFastCirceSupport {

  implicit val system = ActorSystem("nussknacker-ui")

  implicit val user = LoggedUser("1", "test")

  trait MockRemoteEnvironment extends StandardRemoteEnvironment {
    override def environmentId = "testEnv"

    def config: StandardRemoteEnvironmentConfig = StandardRemoteEnvironmentConfig(
      uri = "http://localhost:8087/api",
      batchSize = 100
    )

    override implicit val materializer = ActorMaterializer()

    override def testModelMigrations: TestModelMigrations = ???

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

    override protected def request(path: Uri, method: HttpMethod, request: MessageEntity) : Future[HttpResponse] = {
      import HttpMethods._
      import StatusCodes._

      // helpers
      def is(relative: String, m: HttpMethod): Boolean = {
        path.toString.startsWith(s"$baseUri$relative") && method == m
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
            path.query().get("isSubprocess").map(_.toBoolean).orElse(Some(false))
          } else {
            None
          }
        }
      }
      // end helpers

      (path.toString(), method) match {
        case Validation() =>
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
          throw new AssertionError(s"Not expected $path")
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
      processToSave.comment shouldBe "Scenario migrated from testEnv by test"
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
      processToSave.comment shouldBe "Scenario migrated from testEnv by test"
      processToSave.process shouldBe ProcessTestData.validDisplayableProcess.toDisplayable
    }
  }

  it should "migrate fragment" in {
    var migrated : Option[Future[UpdateProcessCommand]] = None
    val subprocess = ProcessConverter.toDisplayable(ProcessTestData.sampleSubprocess, TestProcessingTypes.Streaming)
    val category = "Category"
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
      processToSave.comment shouldBe "Scenario migrated from testEnv by test"
      processToSave.process shouldBe subprocess
    }
  }
}
