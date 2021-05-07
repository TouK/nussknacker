package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.syntax._
import org.scalatest._
import pl.touk.nussknacker.engine.api.definition.FixedValuesValidator
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
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Json]
    }
  }

  it("should return info about raw editor based on annotation") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor = getParamEditor("simpleTypesService", "rawIntParam")

      editor shouldBe Json.obj("type" -> Json.fromString("RawParameterEditor"))
    }
  }

  it("should return info about simple editor based on annotation") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = getParamEditor("simpleTypesService", "booleanParam")

      editor shouldBe Json.obj("type" -> Json.fromString("BoolParameterEditor"))
    }
  }

  it("should return info about dual editor based on annotation") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor = getParamEditor("simpleTypesService", "DualParam")

      editor shouldBe Json.obj(
        "simpleEditor" -> Json.obj("type" -> Json.fromString("StringParameterEditor")),
        "defaultMode" -> Json.fromString("SIMPLE"),
        "type" -> Json.fromString("DualParameterEditor")
      )
    }
  }

  it("should return info about editor based on config file") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = getParamEditor("enricher", "param")

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
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = getParamEditor("multipleParamsService", "foo")

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
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = getParamEditor("multipleParamsService", "bar")

      editor shouldBe Json.obj("type" -> Json.fromString("StringParameterEditor"))
    }
  }

  it("should override dev config with config from file") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = getParamEditor("multipleParamsService", "baz")

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
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = getParamEditor("echoEnumService", "id")

      val cur = editor.hcursor
      cur.downField("type").as[String].right.value shouldEqual "DualParameterEditor"
      cur.downField("defaultMode").as[String].right.value shouldEqual "SIMPLE"

      val simpleEditorCur = cur.downField("simpleEditor")
      simpleEditorCur.downField("type").as[String].right.value shouldEqual "FixedValuesParameterEditor"
      simpleEditorCur.downField("possibleValues").downN(0).downField("expression").as[String].right.value shouldEqual "T(pl.touk.sample.JavaSampleEnum).FIRST_VALUE"
      simpleEditorCur.downField("possibleValues").downN(0).downField("label").as[String].right.value shouldEqual "first_value"
      simpleEditorCur.downField("possibleValues").downN(1).downField("expression").as[String].right.value shouldEqual "T(pl.touk.sample.JavaSampleEnum).SECOND_VALUE"
      simpleEditorCur.downField("possibleValues").downN(1).downField("label").as[String].right.value shouldEqual "second_value"
    }
  }

  it("should return info about editor based on string type") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = getParamEditor("multipleParamsService", "quax")

      editor shouldBe Json.obj(
        "simpleEditor" -> Json.obj("type" -> Json.fromString("StringParameterEditor")),
        "type" -> Json.fromString("DualParameterEditor"),
        "defaultMode" -> Json.fromString("RAW")
      )
    }
  }

  it("should return info about editor based on annotation for Duration param") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = getParamEditor("datesTypesService", "durationParam")

      editor shouldBe Json.obj(
        "simpleEditor" -> Json.obj(
          "type" -> Json.fromString("DurationParameterEditor"),
          "timeRangeComponents" -> Json.arr(Json.fromString("DAYS"), Json.fromString("HOURS"))
        ),
        "type" -> Json.fromString("DualParameterEditor"),
        "defaultMode" -> Json.fromString("SIMPLE")
      )
    }
  }

  it("should return info about editor based on annotation for Period param") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = getParamEditor("datesTypesService","periodParam" )

      editor shouldBe Json.obj(
        "simpleEditor" -> Json.obj(
          "type" -> Json.fromString("PeriodParameterEditor"),
          "timeRangeComponents" -> Json.arr(Json.fromString("YEARS"), Json.fromString("MONTHS"))
        ),
        "type" -> Json.fromString("DualParameterEditor"),
        "defaultMode" -> Json.fromString("SIMPLE")
      )
    }
  }

  it("should return info about editor based on annotation for Cron param") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val editor: Json = getParamEditor("datesTypesService","cronScheduleParam" )

      editor shouldBe Json.obj("type" -> Json.fromString("CronParameterEditor"))
    }
  }

  it("return mandatory value validator by default") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val validator: Json = getParamValidator("datesTypesService", "periodParam")


      validator shouldBe Json.arr(Json.obj("type" -> Json.fromString("MandatoryParameterValidator")))
    }
  }

  it("not return mandatory value validator for parameter marked with @Nullable") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val validator: Json = getParamValidator("optionalTypesService", "nullableParam")

      validator shouldBe Json.arr()
    }
  }

  it("override validator based on annotation with validator based on dev config") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val validator: Json = getParamValidator("optionalTypesService", "overriddenByDevConfigParam")

      validator shouldBe Json.arr(Json.obj("type" -> Json.fromString("MandatoryParameterValidator")))
    }
  }

  it("override validator based on dev config with validator based on file config") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val validator: Json = getParamValidator("optionalTypesService", "overriddenByFileConfigParam")
      val validatorForSimple: Json = getParamValidator("simpleTypesService", "booleanParam")

      validator shouldBe Json.arr()
    }
  }

  it("return info about validator based on param fixed value editor for node parameters") {
    getProcessDefinitionServices ~> check {
      status shouldBe StatusCodes.OK

      val validator: Json = getParamValidator("paramService", "param")

      validator shouldBe Json.arr(
        Json.obj("type" -> Json.fromString("MandatoryParameterValidator")),
        Json.obj(
          "possibleValues" -> Json.arr(
            Json.obj(
              "expression" -> Json.fromString("'a'"),
              "label" -> Json.fromString("a")
            ),
            Json.obj(
              "expression" -> Json.fromString("'b'"),
              "label" -> Json.fromString("b")
            ),
            Json.obj(
              "expression" -> Json.fromString("'c'"),
              "label" -> Json.fromString("c")
            )
          ),
          "type" -> Json.fromString("FixedValuesValidator")
      ))
    }
  }

  it("return info about validator based on param fixed value editor for additional properties") {
    getProcessDefinitionData(existingProcessingType, Map.empty[String, Long].asJson) ~> check {
      status shouldBe StatusCodes.OK

      val validators: Json = responseAs[Json].hcursor
        .downField("additionalPropertiesConfig")
        .downField("numberOfThreads")
        .downField("validators")
        .focus.get

      validators shouldBe
        Json.arr(
          Json.obj(
            "possibleValues" -> Json.arr(
              Json.obj(
                "expression" -> Json.fromString("1"),
                "label" -> Json.fromString("1")
              ),
              Json.obj(
                "expression" -> Json.fromString("2"),
                "label" -> Json.fromString("2")
              )
            ),
            "type" -> Json.fromString("FixedValuesValidator")
          )
        )
    }
  }

  it("return default value based on editor possible values") {
    getProcessDefinitionData(existingProcessingType, Map.empty[String, Long].asJson) ~> check {
      status shouldBe StatusCodes.OK

      val defaultExpression: Json = responseAs[Json].hcursor
        .downField("nodesToAdd")
        .downAt(_.hcursor.get[String]("name").right.value == "enrichers")
        .downField("possibleNodes")
        .downAt(_.hcursor.get[String]("label").right.value == "echoEnumService")
        .downField("node")
        .downField("service")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").right.value == "id")
        .downField("expression")
        .downField("expression")
        .focus.get

      defaultExpression shouldBe Json.fromString("T(pl.touk.sample.JavaSampleEnum).FIRST_VALUE")
    }
  }

  private def getParamEditor(serviceName: String, paramName: String) = {
    responseAs[Json].hcursor
      .downField("streaming")
      .downField(serviceName)
      .downField("parameters")
      .downAt(_.hcursor.get[String]("name").right.value == paramName)
      .downField("editor")
      .focus.get
  }

  private def getParamValidator(serviceName: String, paramName: String) = {
    responseAs[Json].hcursor
      .downField("streaming")
      .downField(serviceName)
      .downField("parameters")
      .downAt(_.hcursor.get[String]("name").right.value == paramName)
      .downField("validators")
      .focus.get
  }
}
