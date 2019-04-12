package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.Parse
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.ui.api.helpers.EspItTest

class DefinitionResourcesSpec extends FunSpec with ScalatestRouteTest
  with Matchers with ScalaFutures with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  import argonaut.Argonaut._

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

      val data = Parse.parse(responseAs[String]).right.value
      val noneReturnType = for {
        processDefinitionField <- data.cursor.downField("processDefinition")
        customStreamTransformersField <- processDefinitionField.downField("customStreamTransformers")
        noneReturnTypeTransformerField <- customStreamTransformersField.downField("noneReturnTypeTransformer")
        returnTypeField <- noneReturnTypeTransformerField.downField("returnType")
      } yield returnTypeField.right

      noneReturnType.get shouldBe None
      data
    }
  }

  it("should return all definition services") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK
      Parse.parse(responseAs[String]).right.value
    }
  }

}
