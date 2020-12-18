package pl.touk.nussknacker.engine.management.periodic

import java.io.File
import java.util.UUID

import pl.touk.nussknacker.engine.management.periodic.PeriodicFlinkRestModel.{JarFile, JarsResponse}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.management.flinkRestModel.UploadJarResponse
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client.testing.SttpBackendStub
import sttp.client.{Response, UriContext}
import sttp.model.{Method, StatusCode}

class FlinkClientTest extends FunSuite
  with Matchers
  with ScalaFutures
  with PatientScalaFutures {

  private val flinkUri = uri"http://localhost:12345/flink"
  private val jarFile = new File("/tmp/example.jar")
  private val jarId = s"${UUID.randomUUID()}-example.jar"
  private val jarFileName = "example.jar"

  test("uploadJarFileIfNotExists - should upload jar") {
    implicit val backend = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      // 404 is returned when checking for jar existence.
      case req if req.uri.path == List("jars", "upload") =>
        Response.ok(Right(UploadJarResponse(filename = jarId)))
    }
    val flinkClient = new HttpFlinkClient(flinkUri)

    val result = flinkClient.uploadJarFileIfNotExists(jarFile).futureValue

    result shouldBe jarId
  }

  test("uploadJarFileIfNotExists - should not upload if already exist") {
    implicit val backend = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req if req.uri.path == List("jars") =>
        Response.ok(Right(JarsResponse(files = Some(List(JarFile(id = jarId, name = jarFileName))))))
    }
    val flinkClient = new HttpFlinkClient(flinkUri)

    val result = flinkClient.uploadJarFileIfNotExists(jarFile).futureValue

    result shouldBe jarId
  }

  test("uploadJarFileIfNotExists - should upload if not recognized jars uploaded") {
    implicit val backend = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req if req.uri.path == List("jars") =>
        Response.ok(Right(JarsResponse(files = Some(List(JarFile(id = "123-other.jar", name = "other.jar"))))))
      case req if req.uri.path == List("jars", "upload") =>
        Response.ok(Right(UploadJarResponse(filename = jarId)))
    }
    val flinkClient = new HttpFlinkClient(flinkUri)

    val result = flinkClient.uploadJarFileIfNotExists(jarFile).futureValue

    result shouldBe jarId
  }

  test("deleteJarIfExists - should do so") {
    implicit val backend = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req if req.uri.path == List("jars") =>
        Response.ok(Right(JarsResponse(files = Some(List(JarFile(id = jarId, name = jarFileName))))))
      case req if req.uri.path == List("jars", jarFileName) && req.method == Method.DELETE =>
        Response.ok(Right(()))
    }
    val flinkClient = new HttpFlinkClient(flinkUri)

    val result = flinkClient.deleteJarIfExists(jarFileName).futureValue

    result shouldBe (())
  }

  test("deleteJarIfExists - should do nothing if file not found") {
    implicit val backend = SttpBackendStub.asynchronousFuture
    val flinkClient = new HttpFlinkClient(flinkUri)

    val result = flinkClient.deleteJarIfExists(jarFileName).futureValue

    result shouldBe (())
  }

  test("deleteJarIfExists - should recover if deleting fails") {
    implicit val backend = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req if req.uri.path == List("jars") =>
        Response.ok(Right(JarsResponse(files = Some(List(JarFile(id = jarId, name = jarFileName))))))
      case req if req.uri.path == List("jars", jarFileName) && req.method == Method.DELETE =>
        Response(Right(()), StatusCode.InternalServerError)
    }
    val flinkClient = new HttpFlinkClient(flinkUri)

    val result = flinkClient.deleteJarIfExists(jarFileName).futureValue

    result shouldBe (())
  }
}
