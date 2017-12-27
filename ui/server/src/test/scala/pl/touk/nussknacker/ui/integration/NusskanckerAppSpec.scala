package pl.touk.nussknacker.ui.integration

import java.io.File
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.stream.ActorMaterializer
import argonaut.Argonaut._
import org.apache.commons.io.FileUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.ui.NussknackerApp
import pl.touk.nussknacker.ui.api.UISettings
import pl.touk.nussknacker.ui.process.uiconfig.SingleNodeConfig
import pl.touk.nussknacker.ui.util.{AvailablePortFinder, MultipartUtils}

import scala.concurrent.Future

//TODO: consider rewriting to use NussknackerApp.initializeRoute without running full app on real http server
class NusskanckerAppSpec extends FlatSpec with BeforeAndAfterEach with Matchers with ScalaFutures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(1, Seconds)))


  import argonaut.ArgonautShapeless._

  import scala.concurrent.ExecutionContext.Implicits.global

  it should "ensure config is properly parsed e2e style" in {
    val port = AvailablePortFinder.findAvailablePort()
    NussknackerApp.main(Array(port.toString))

    val settings = invoke(port, "settings").map { _.decodeOption[UISettings].get }.futureValue

    settings.nodes shouldBe
      Map(
        "test1" -> SingleNodeConfig(None, Some("Sink.svg")),
        "enricher" -> SingleNodeConfig(Some(Map("param" -> "'default value'")), Some("Filter.svg"))
      )
  }

  def invoke(port: Int, endpoint: String): Future[String] = {
    implicit val system = ActorSystem("test")
    implicit val materializer = ActorMaterializer()
    val http = Http()
    val req = http.singleRequest(
      HttpRequest(
        uri = s"http://localhost:${port}/api/$endpoint",
        headers = List(Authorization(BasicHttpCredentials("admin", "admin"))))
    )
    req.flatMap { resp => MultipartUtils.readFile(resp.entity.dataBytes) }
  }
}