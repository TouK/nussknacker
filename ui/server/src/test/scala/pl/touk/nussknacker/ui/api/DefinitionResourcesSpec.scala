package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.syntax._
import org.scalatest._
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest

class DefinitionResourcesSpec extends FunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

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

  it("should return info about raw editor") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor = responseAs[Json].hcursor
        .downField("streaming")
        .downField("simpleTypesService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "intParam")
        .downField("editor")
        .focus.get

      editor shouldBe Json.obj("type" -> Json.fromString("RawParameterEditor"))
    }
  }

  it("should return info about simple editor") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("simpleTypesService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "booleanParam")
        .downField("editor")
        .focus.get

      editor shouldBe Json.obj(
        "simpleEditorType" -> Json.fromString("BOOL_EDITOR"),
        "possibleValues" -> Json.fromValues(List.empty),
        "type" -> Json.fromString("SimpleParameterEditor")
      )
    }
  }

  it("should return info about dual editor") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor = responseAs[Json].hcursor
        .downField("streaming")
        .downField("simpleTypesService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "stringParam")
        .downField("editor")
        .focus.get

      editor shouldBe Json.obj(
        "simpleEditor" -> Json.obj(
          "simpleEditorType" -> Json.fromString("STRING_EDITOR"),
          "possibleValues" -> Json.fromValues(List.empty)
        ),
        "defaultMode" -> Json.fromString("SIMPLE"),
        "type" -> Json.fromString("DualParameterEditor")
      )
    }
  }
}
