package pl.touk.nussknacker.ui.process.migrate

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.ui.process.ProcessToSave
import pl.touk.nussknacker.ui.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType, ValidationErrors, ValidationResult}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.security.api.LoggedUser
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StandardRemoteEnvironmentSpec extends FlatSpec with Matchers with ScalaFutures with Argonaut62Support {

  implicit val system = ActorSystem("nussknacker-ui")

  implicit val user = LoggedUser("test",  List(), List())

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  trait MockRemoteEnvironment extends StandardRemoteEnvironment {

    override def environmentId = "testEnv"

    override def targetEnvironmentId = "targetTestEnv"

    override def baseUrl: Uri = Uri("http://localhost:8087/api")

    override implicit val materializer = ActorMaterializer()

    override def testModelMigrations: TestModelMigrations = ???

  }

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._


  it should "not migrate not validating process" in {

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

    whenReady(remoteEnvironment.migrate(ProcessTestData.validDisplayableProcess.toDisplayable)) { result =>
      result shouldBe 'left
      result.left.get shouldBe MigrationValidationError(ValidationErrors(Map("n1" -> List(NodeValidationError("bad","message","" ,None, NodeValidationErrorType.SaveAllowed))),List(),List()))
      result.left.get.getMessage shouldBe "Cannot migrate, following errors occurred: n1 - message"
    }

  }

  it should "handle spaces in process id" in {
    val process = ProcessTestData.toValidatedDisplayable(ProcessTestData.validProcessWithId("a b c")).toDisplayable

    val remoteEnvironment = new MockRemoteEnvironment {

      override protected def request(path: Uri, method: HttpMethod, request: MessageEntity) : Future[HttpResponse] = {
        if (path.toString().startsWith(s"$baseUrl/processes/a") && method == HttpMethods.GET) {
          Marshal(ProcessTestData.toDetails(process)).to[RequestEntity].map { entity =>
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

  it should "handle non-ascii signs in process id" in {
    val process = ProcessTestData.toValidatedDisplayable(ProcessTestData.validProcessWithId("łódź")).toDisplayable

    val remoteEnvironment = new MockRemoteEnvironment {

      override protected def request(path: Uri, method: HttpMethod, request: MessageEntity) : Future[HttpResponse] = {
        if (path.toString().startsWith(s"$baseUrl/processes/%C5%82%C3%B3d%C5%BA") && method == HttpMethods.GET) {
          Marshal(ProcessTestData.toDetails(process)).to[RequestEntity].map { entity =>
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


  it should "migrate valid process" in {

    var migrated : Option[Future[ProcessToSave]] = None

    val remoteEnvironment = new MockRemoteEnvironment {
      override protected def request(path: Uri, method: HttpMethod, request: MessageEntity) : Future[HttpResponse] = {
        if (path.toString().startsWith(s"$baseUrl/processValidation") && method == HttpMethods.POST) {
          Marshal(ValidationResult.errors(Map(), List(), List())).to[RequestEntity].map { entity =>
            HttpResponse(StatusCodes.OK, entity = entity)
          }
        } else if (path.toString().startsWith(s"$baseUrl/processes/${ProcessTestData.validDisplayableProcess.id}") && method == HttpMethods.PUT) {
          migrated = Some(Unmarshal(request).to[ProcessToSave])
          Marshal(ValidationResult.errors(Map(), List(), List())).to[RequestEntity].map { entity =>
            HttpResponse(StatusCodes.OK, entity = entity)
          }
        } else {
          throw new AssertionError(s"Not expected $path")
        }
      }

    }

    whenReady(remoteEnvironment.migrate(ProcessTestData.validDisplayableProcess.toDisplayable)) { result =>
      result shouldBe 'right
    }

    migrated shouldBe 'defined

    whenReady(migrated.get) { processToSave =>
      processToSave.comment shouldBe "Process migrated from testEnv by test"
      processToSave.process shouldBe ProcessTestData.validDisplayableProcess.toDisplayable

    }

  }

}
