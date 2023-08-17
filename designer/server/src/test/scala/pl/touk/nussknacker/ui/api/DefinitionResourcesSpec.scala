package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.CirceUtil.RichACursor
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.api.helpers.TestCategories.TestCat
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withPermissions
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import scala.annotation.nowarn

@nowarn("cat=deprecation")
class DefinitionResourcesSpec extends AnyFunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with EitherValuesDetailedMessage with BeforeAndAfterEach with BeforeAndAfterAll
  with NuResourcesTest with OptionValues {

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val definitionResources = new DefinitionResources(
    modelDataProvider = testModelDataProvider,
    processingTypeDataProvider = testProcessingTypeDataProvider,
    fragmentRepository,
    processCategoryService
  )

  it("should handle missing scenario type") {
    getProcessDefinitionData("foo") ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it("should return definition data for existing scenario type") {
    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val noneReturnType = responseAs[Json].hcursor
        .downField("processDefinition")
        .downField("customStreamTransformers")
        .downField("noneReturnTypeTransformer")

      noneReturnType.downField("returnType").focus shouldBe Some(Json.Null)
    }
  }

  it("should return definition data for allowed classes") {
    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val typesInformation = responseAs[Json].hcursor
        .downField("processDefinition")
        .downField("typesInformation")
        .downAt(_.hcursor.downField("clazzName").get[String]("display").rightValue == "ReturningTestCaseClass")
        .downField("clazzName")
        .downField("display")

      typesInformation.focus.value shouldBe Json.fromString("ReturningTestCaseClass")
    }
  }

  it("should return info about editor based on fragment node configuration") {
    val processName = ProcessName(SampleProcess.process.id)
    val processWithfragment = ProcessTestData.validProcessWithFragment(processName)
    val displayablefragment = ProcessConverter.toDisplayable(processWithfragment.fragment, TestProcessingTypes.Streaming, TestCategories.TestCat)
    savefragment(displayablefragment)(succeed)
    saveProcess(processName, processWithfragment.process, TestCat)(succeed)

    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[Json].hcursor

      val editor = response
        .downField("processDefinition")
        .downField("fragmentInputs")
        .downField("sub1")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").rightValue == "param1")
        .downField("editor")
        .focus.value

      editor shouldBe Json.obj("type" -> Json.fromString("StringParameterEditor"))
    }
  }

  it("return info about validator based on param fixed value editor for additional properties") {
    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val validators: Json = responseAs[Json].hcursor
        .downField("additionalPropertiesConfig")
        .downField("numberOfThreads")
        .downField("validators")
        .focus.value

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
    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val defaultExpression: Json = responseAs[Json].hcursor
        .downField("componentGroups")
        .downAt(_.hcursor.get[String]("name").rightValue == "enrichers")
        .downField("components")
        .downAt(_.hcursor.get[String]("label").rightValue == "echoEnumService")
        .downField("node")
        .downField("service")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").rightValue == "id")
        .downField("expression")
        .downField("expression")
        .focus.value

      defaultExpression shouldBe Json.fromString("T(pl.touk.sample.JavaSampleEnum).FIRST_VALUE")
    }
  }

  // TODO: currently branch parameters must be determined on node template level - aren't enriched dynamically during node validation
  it("return branch parameters definition with standard parameters enrichments") {
    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val responseJson = responseAs[Json]
      val defaultExpression: Json = responseJson.hcursor
        .downField("componentGroups")
        .downAt(_.hcursor.get[String]("name").rightValue == "base")
        .downField("components")
        .downAt(_.hcursor.get[String]("label").rightValue == "enrichWithAdditionalData")
        .downField("branchParametersTemplate")
        .downAt(_.hcursor.get[String]("name").rightValue == "role")
        .downField("expression")
        .downField("expression")
        .focus.value

      defaultExpression shouldBe Json.fromString("'Events'")
    }
  }

  it("return initial parameters for dynamic components") {
    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val responseJson = responseAs[Json]
      val parameters = responseJson.hcursor
        .downField("componentGroups")
        .downAt(_.hcursor.get[String]("name").rightValue == "sources")
        .downField("components")
        .downAt(_.hcursor.get[String]("label").rightValue == "kafka")
        .downField("node")
        .downField("ref")
        .downField("parameters")
        .focus.value.asArray.value

      val initialParamNames = parameters.map(_.hcursor.downField("name").focus.value.asString.value)
      initialParamNames shouldEqual List(KafkaUniversalComponentTransformer.TopicParamName, KafkaUniversalComponentTransformer.SchemaVersionParamName)
      val initialExpressions = parameters.map(_.hcursor.downField("expression").downField("expression").focus.value.asString.value)
      initialExpressions shouldEqual List("", s"'${SchemaVersionOption.LatestOptionName}'")
    }
  }

  it("initial parameters for dynamic components should take into account static component configuration in file") {
    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val responseJson = responseAs[Json]
      val parameters = responseJson.hcursor
        .downField("componentGroups")
        .downAt(_.hcursor.get[String]("name").rightValue == "services")
        .downField("components")
        .downAt(_.hcursor.get[String]("label").rightValue == "dynamicMultipleParamsService")
        .downField("node")
        .downField("service")
        .downField("parameters")
        .focus.value.asArray.value

      val initialParamNames = parameters.map(_.hcursor.downField("name").focus.value.asString.value)
      initialParamNames shouldEqual List("foo", "bar", "baz")
      val initialExpressions = parameters.map(_.hcursor.downField("expression").downField("expression").focus.value.asString.value)
      initialExpressions shouldEqual List("'fooValueFromConfig'", "'barValueFromProviderCode'", "'fooValueFromConfig' + '-' + 'barValueFromProviderCode'")
    }
  }
  private def getServices: Option[Iterable[String]] = {
    responseAs[Json].hcursor.downField("streaming").keys
  }

  private def getParamEditor(serviceName: String, paramName: String) = {
    responseAs[Json].hcursor
      .downField("streaming")
      .downField(serviceName)
      .downField("parameters")
      .downAt(_.hcursor.get[String]("name").rightValue == paramName)
      .downField("editor")
      .focus.value
  }

  private def getParamValidator(serviceName: String, paramName: String) = {
    responseAs[Json].hcursor
      .downField("streaming")
      .downField(serviceName)
      .downField("parameters")
      .downAt(_.hcursor.get[String]("name").rightValue == paramName)
      .downField("validators")
      .focus.value
  }

  private def getProcessDefinitionData(processingType: String): RouteTestResult = {
    Get(s"/processDefinitionData/$processingType?isFragment=false") ~> withPermissions(definitionResources, testPermissionRead)
  }
}
