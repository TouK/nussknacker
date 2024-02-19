package pl.touk.nussknacker.engine.management.rest

import io.circe.syntax.EncoderOps
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span.convertSpanToDuration
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.FlinkConfig
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{JarFile, JarsResponse, UploadJarResponse}
import pl.touk.nussknacker.engine.sttp.HttpClientError
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{HttpError, Response}
import sttp.model.{Method, StatusCode}
import sttp.monad.FutureMonad

import java.io.File
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.util.Failure

class FlinkHttpClientTest extends AnyFunSuite with Matchers with ScalaFutures with PatientScalaFutures {

  private val jarFileName  = "example.jar"
  private val jarFile      = new File(s"/tmp/${jarFileName}")
  private val jarId        = s"${UUID.randomUUID()}-example.jar"
  private val flinkJarFile = JarFile(jarId, jarFileName)
  private val deploymentId = ExternalDeploymentId("someDeploymentId")

  val config: FlinkConfig = FlinkConfig(Some("http://localhost:12345/"))

  test("uploadJarFileIfNotExists - should upload jar") {
    implicit val backend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req if req.uri.path == List("jars") => Response.ok(Right(JarsResponse(files = Some(Nil))))
      case req if req.uri.path == List("jars", "upload") =>
        Response.ok(Right(UploadJarResponse(filename = jarId)))
    }
    val flinkClient = HttpFlinkClient.createUnsafe(config)

    val result = flinkClient.uploadJarFileIfNotExists(jarFile).futureValue

    result shouldBe flinkJarFile
  }

  test("uploadJarFileIfNotExists - should not upload if already exist") {
    implicit val backend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req if req.uri.path == List("jars") =>
        Response.ok(Right(JarsResponse(files = Some(List(JarFile(id = jarId, name = jarFileName))))))
    }
    val flinkClient = HttpFlinkClient.createUnsafe(config)

    val result = flinkClient.uploadJarFileIfNotExists(jarFile).futureValue

    result shouldBe flinkJarFile
  }

  test("uploadJarFileIfNotExists - should upload if not recognized jars uploaded") {
    implicit val backend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req if req.uri.path == List("jars") =>
        Response.ok(Right(JarsResponse(files = Some(List(JarFile(id = "123-other.jar", name = "other.jar"))))))
      case req if req.uri.path == List("jars", "upload") =>
        Response.ok(Right(UploadJarResponse(filename = jarId)))
    }
    val flinkClient = HttpFlinkClient.createUnsafe(config)

    val result = flinkClient.uploadJarFileIfNotExists(jarFile).futureValue

    result shouldBe flinkJarFile
  }

  test("deleteJarIfExists - should do so") {
    implicit val backend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req if req.uri.path == List("jars") =>
        Response.ok(Right(JarsResponse(files = Some(List(flinkJarFile)))))
      case req if req.uri.path == List("jars", jarId) && req.method == Method.DELETE =>
        Response.ok(Right(()))
    }
    val flinkClient = HttpFlinkClient.createUnsafe(config)

    val result = flinkClient.deleteJarIfExists(jarFileName).futureValue

    result shouldBe (())
  }

  test("deleteJarIfExists - should do nothing if file not found") {
    implicit val backend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req if req.uri.path == List("jars") => Response.ok(Right(JarsResponse(files = Some(Nil))))
    }
    val flinkClient = HttpFlinkClient.createUnsafe(config)

    val result = flinkClient.deleteJarIfExists(jarFileName).futureValue

    result shouldBe (())
  }

  test("deleteJarIfExists - should recover if deleting fails") {
    implicit val backend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req if req.uri.path == List("jars") =>
        Response.ok(Right(JarsResponse(files = Some(List(JarFile(id = jarId, name = jarFileName))))))
      case req if req.uri.path == List("jars", jarId) && req.method == Method.DELETE =>
        Response(Right(()), StatusCode.InternalServerError)
    }
    val flinkClient = HttpFlinkClient.createUnsafe(config)

    val result = flinkClient.deleteJarIfExists(jarFileName).futureValue

    result shouldBe (())
  }

  test("should throw FlinkError if action failed") {
    implicit val backend: SttpBackendStub[Future, Any] = new SttpBackendStub[Future, Any](
      new FutureMonad(),
      {
        case req if req.uri.path == List("jars") =>
          Future.successful(
            Response.ok(Right(JarsResponse(files = Some(List(JarFile(id = jarId, name = jarFileName))))))
          )
        case req if req.uri.path == List("jars", jarId, "run") =>
          Future.failed(HttpError("Error, error".asJson.noSpaces, StatusCode.InternalServerError))
        case req if req.uri.path == List("jobs", deploymentId.value) && req.method == Method.PATCH =>
          Future.failed(HttpError("Error, error".asJson.noSpaces, StatusCode.InternalServerError))
      },
      None
    )

    val flinkClient = HttpFlinkClient.createUnsafe(config)

    def checkIfWrapped(action: Future[_]) = {
      Await.ready(action, convertSpanToDuration(patienceConfig.timeout)).value should matchPattern {
        case Some(Failure(_: HttpClientError)) =>
      }
    }

    checkIfWrapped(flinkClient.cancel(deploymentId))
    checkIfWrapped(flinkClient.runProgram(jarFile, "any", Nil, None))
  }

}
