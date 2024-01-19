package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Json, parser}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.CirceUtil.RichACursor
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.ValueInputWithFixedValuesProvided
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withPermissions
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.definition.{
  DefinitionsService,
  ModelDefinitionEnricher,
  ScenarioPropertiesConfigFinalizer,
  TestAdditionalUIConfigProvider
}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

class DefinitionResourcesSpec
    extends AnyFunSpec
    with ScalatestRouteTest
    with FailFastCirceSupport
    with Matchers
    with PatientScalaFutures
    with EitherValuesDetailedMessage
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuResourcesTest
    with OptionValues {

  private implicit final val string: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val definitionResources = new DefinitionResources(
    serviceProvider = testProcessingTypeDataProvider.mapValues { processingTypeData =>
      val modelDefinitionEnricher = ModelDefinitionEnricher(
        processingTypeData.modelData,
        processingTypeData.staticModelDefinition
      )

      (
        DefinitionsService(
          processingTypeData,
          modelDefinitionEnricher,
          new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, processingTypeData.processingType),
          fragmentRepository
        ),
        processingTypeData.modelData.designerDictServices.dictQueryService
      )
    }
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
        .downField("components")
        .downField("custom-noneReturnTypeTransformer")

      noneReturnType.downField("returnType").focus shouldBe Some(Json.Null)
    }
  }

  it("should return definition data for allowed classes") {
    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val typesInformation = responseAs[Json].hcursor
        .downField("classes")
        .downAt(_.hcursor.get[String]("display").rightValue == "ReturningTestCaseClass")
        .downField("display")

      typesInformation.focus.value shouldBe Json.fromString("ReturningTestCaseClass")
    }
  }

  it("should return info about editor based on fragment parameter definition") {
    val fragmentWithFixedValuesEditor = {
      CanonicalProcess(
        MetaData("sub1", FragmentSpecificData()),
        List(
          FlatNode(
            FragmentInputDefinition(
              "in",
              List(
                FragmentParameter("param1", FragmentClazzRef[String]).copy(
                  valueEditor = Some(
                    ValueInputWithFixedValuesProvided(
                      fixedValuesList = List(FixedExpressionValue("'someValue'", "someValue")),
                      allowOtherValue = false
                    )
                  ),
                )
              )
            )
          ),
          canonicalnode.FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
        ),
        List.empty
      )
    }

    val processName         = SampleScenario.scenario.name
    val processWithFragment = ProcessTestData.validProcessWithFragment(processName, fragmentWithFixedValuesEditor)
    val displayableFragment = ProcessConverter.toDisplayable(
      processWithFragment.fragment,
      TestProcessingTypes.Streaming,
      TestCategories.Category1
    )
    saveFragment(displayableFragment)(succeed)
    saveProcess(processName, processWithFragment.process, TestCategories.Category1)(succeed)

    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[Json].hcursor

      val editor = response
        .downField("components")
        .downField("fragment-sub1")
        .downField("parameters")
        .downAt(_.hcursor.get[String]("name").rightValue == "param1")
        .downField("editor")
        .focus
        .value

      editor shouldBe parser
        .parse("""{"possibleValues" : [
                   |    {
                   |      "expression" : "",
                   |      "label" : ""
                   |    },
                   |    {
                   |      "expression" : "'someValue'",
                   |      "label" : "someValue"
                   |    }
                   |  ],
                   |  "type" : "FixedValuesParameterEditor"
                   |}""".stripMargin)
        .toOption
        .get
    }
  }

  it("return default value based on editor possible values") {
    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val responseJson = responseAs[Json]
      val defaultExpression = responseJson.hcursor
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
        .focus
        .value

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
        .focus
        .value

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
        .downAt(_.hcursor.get[String]("label").rightValue == "communicationSource")
        .downField("node")
        .downField("ref")
        .downField("parameters")
        .focus
        .value
        .asArray
        .value

      val initialParamNames = parameters.map(_.hcursor.downField("name").focus.value.asString.value)
      initialParamNames shouldEqual List(
        "communicationType",
        "Number",
        "Text",
      )
      val initialExpressions =
        parameters.map(_.hcursor.downField("expression").downField("expression").focus.value.asString.value)
      initialExpressions shouldEqual List("'SMS'", "''", "''")
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
        .focus
        .value
        .asArray
        .value

      val initialParamNames = parameters.map(_.hcursor.downField("name").focus.value.asString.value)
      initialParamNames shouldEqual List("foo", "bar", "baz")
      val initialExpressions =
        parameters.map(_.hcursor.downField("expression").downField("expression").focus.value.asString.value)
      initialExpressions shouldEqual List(
        "'fooValueFromConfig'",
        "'barValueFromProviderCode'",
        "'fooValueFromConfig' + '-' + 'barValueFromProviderCode'"
      )
    }
  }

  it("return edgesForNodes") {
    getProcessDefinitionData(TestProcessingTypes.Streaming) ~> check {
      status shouldBe StatusCodes.OK

      val responseJson = responseAs[Json]
      val edgesForNodes = responseJson.hcursor
        .downField("edgesForNodes")
        .focus
        .value
        .asArray
        .value

      edgesForNodes.map(_.asObject.get("componentId").value.asString.value).sorted should contain theSameElementsAs
        List(
          "builtin-choice",
          "builtin-filter",
          "builtin-split",
          "custom-enrichWithAdditionalData",
          "custom-unionWithEditors",
        )
    }
  }

  private def getProcessDefinitionData(processingType: String): RouteTestResult = {
    Get(s"/processDefinitionData/$processingType?isFragment=false") ~> withPermissions(
      definitionResources,
      testPermissionRead
    )
  }

}
