package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.syntax._
import org.scalatest._
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData, SampleProcess, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

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

  it("should return info about raw editor based on annotation") {
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

  it("should return info about simple editor based on annotation") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("simpleTypesService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "booleanParam")
        .downField("editor")
        .focus.get

      editor shouldBe Json.obj("type" -> Json.fromString("BoolParameterEditor"))
    }
  }

  it("should return info about dual editor based on annotation") {
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
        "simpleEditor" -> Json.obj("type" -> Json.fromString("StringParameterEditor")),
        "defaultMode" -> Json.fromString("SIMPLE"),
        "type" -> Json.fromString("DualParameterEditor")
      )
    }
  }

  it("should return info about editor based on config file") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("enricher")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "param")
        .downField("editor")
        .focus.get
      (List(FixedExpressionValue("test", "test")))

      editor shouldBe Json.obj("type" -> Json.fromString("StringParameterEditor"))
    }
  }

  it("should return info about editor based on subprocess node configuration") {
    val processName = ProcessName(SampleProcess.process.id)
    val processWithSubProcess = ProcessTestData.validProcessWithSubprocess(processName)
    val displayableSubProcess = ProcessConverter.toDisplayable(processWithSubProcess.subprocess, TestProcessingTypes.Streaming)
    saveSubProcess(displayableSubProcess)(succeed)
    saveProcess(processName, processWithSubProcess.process)(succeed)

    getProcessDefinitionData(existingProcessingType, Map.empty[String, Long].asJson) ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[Json].hcursor

      val editor = response
        .downField("processDefinition")
        .downField("subprocessInputs")
        .downField("sub1")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "param1")
        .downField("editor")
        .focus.get

      editor shouldBe Json.obj("type" -> Json.fromString("StringParameterEditor"))
    }
  }

  it("should return info about editor based on dev config") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("multipleParamsService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "foo")
        .downField("editor")
        .focus.get

      editor shouldBe Json.obj(
        "type" -> Json.fromString("FixedValuesParameterEditor"),
        "possibleValues" -> Json.arr(
          Json.obj(
            "expression" -> Json.fromString("test"),
            "label" -> Json.fromString("test")
          )
        )
      )
    }
  }

  it("should override annotation config with dev config") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("multipleParamsService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "bar")
        .downField("editor")
        .focus.get

      editor shouldBe Json.obj("type" -> Json.fromString("StringParameterEditor"))
    }
  }

  it("should override dev config with config from file") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("multipleParamsService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "baz")
        .downField("editor")
        .focus.get

      editor shouldBe Json.obj(
        "type" -> Json.fromString("FixedValuesParameterEditor"),
        "possibleValues" -> Json.arr(
          Json.obj(
            "expression" -> Json.fromString("1"),
            "label" -> Json.fromString("1")
          ),
          Json.obj(
            "expression" -> Json.fromString("2"),
            "label" -> Json.fromString("2")
          )
        )
      )
    }
  }

  it("should return info about editor based on enum type") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("echoEnumService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "id")
        .downField("editor")
        .focus.get

      editor shouldBe Json.obj(
        "type" -> Json.fromString("FixedValuesParameterEditor"),
        "possibleValues" -> Json.arr(
          Json.obj(
            "expression" -> Json.fromString("T(pl.touk.sample.JavaSampleEnum).FIRST_VALUE"),
            "label" -> Json.fromString("first_value")
          ),
          Json.obj(
            "expression" -> Json.fromString("T(pl.touk.sample.JavaSampleEnum).SECOND_VALUE"),
            "label" -> Json.fromString("second_value")
          )
        )
      )
    }
  }

  it("should return info about editor based on string type") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val params = responseAs[Json].hcursor
        .downField("streaming")
        .downField("multipleParamsService")
        .downField("parameters")
      val editor: Json = params
        .downAt(_.hcursor.get[String]("name").right.value == "quax")
        .downField("editor")
        .focus.get

      editor shouldBe Json.obj(
        "simpleEditor" -> Json.obj("type" -> Json.fromString("StringParameterEditor")),
        "type" -> Json.fromString("DualParameterEditor"),
        "defaultMode" -> Json.fromString("RAW")
      )
    }
  }

  it("return NotEmptyValidator by default") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("datesTypesService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "periodParam")
        .downField("validators")
        .focus.get

      editor shouldBe Json.arr(Json.obj("type" -> Json.fromString("MandatoryValueValidator")))
    }
  }

  it("not return NotEmptyValidator for parameter marked with @Nullable") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("optionalTypesService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "nullableParam")
        .downField("validators")
        .focus.get

      editor shouldBe Json.arr()
    }
  }

  it("override validator based on annotation with validator based on dev config") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("multipleParamsService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "foo")
        .downField("validators")
        .focus.get

      editor shouldBe Json.arr(Json.obj("type" -> Json.fromString("MandatoryValueValidator")))
    }
  }

  it("override validator based on dev config with validator based on file config") {
    getProcessDefinitionServices() ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = responseAs[Json].hcursor
        .downField("streaming")
        .downField("multipleParamsService")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "baz")
        .downField("validators")
        .focus.get

      editor shouldBe Json.arr()
    }
  }
}
