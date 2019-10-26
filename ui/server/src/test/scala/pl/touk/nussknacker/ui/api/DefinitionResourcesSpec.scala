package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.syntax._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.ui.api.helpers.EspItTest

class DefinitionResourcesSpec extends FunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with ScalaFutures with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(100, Millis)))

  it("should handle missing processing type") {
    getProcessDefinitionData("foo", Map.empty[String, Long].asJson) ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it("should return definition data for existing processing type") {
    getProcessDefinitionData(existingProcessingType, Map.empty[String, Long].asJson) ~> check {
      status shouldBe StatusCodes.OK

      val noneReturnType = responseAs[Json].hcursor
        .downField("processDefinition")
        .downField("customStreamTransformers")
        .downField("noneReturnTypeTransformer")

      noneReturnType.downField("returnType").focus shouldBe Some(Json.Null)
    }
  }

  it("should return all definition services") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Json]
    }
  }

}
