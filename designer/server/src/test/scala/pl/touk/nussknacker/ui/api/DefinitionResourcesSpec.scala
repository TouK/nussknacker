package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Json, parser}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.CirceUtil.RichACursor
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.{ParameterName, ValueInputWithFixedValuesProvided}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.utils.domain.TestFactory.withPermissions
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.mock.TestAdditionalUIConfigProvider
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.definition.{
  AlignedComponentsDefinitionProvider,
  DefinitionsService,
  ScenarioPropertiesConfigFinalizer
}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

class DefinitionResourcesSpec
    extends AnyFunSpec
    with ScalatestRouteTest
    with FailFastCirceSupport
    with Matchers
    with PatientScalaFutures
    with EitherValuesDetailedMessage
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuResourcesTest {

  private val definitionResources = new DefinitionResources(
    definitionsServices = testProcessingTypeDataProvider.mapValues { processingTypeData =>
      val modelDefinitionEnricher = AlignedComponentsDefinitionProvider(processingTypeData.designerModelData)

      DefinitionsService(
        processingTypeData,
        modelDefinitionEnricher,
        new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, processingTypeData.name),
        fragmentRepository,
        None
      )
    }
  )

  it("should handle missing scenario type") {
    getProcessDefinitionDataUsingRawProcessingType(processingType = "not-existing-processing-type") ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it("should return definition data for existing scenario type") {
    getProcessDefinitionData() ~> check {
      status shouldBe StatusCodes.OK

      val noneReturnType = responseAs[Json].hcursor
        .downField("components")
        .downField("custom-noneReturnTypeTransformer")

      noneReturnType.downField("returnType").focus shouldBe Some(Json.Null)
    }
  }

  it("should return enriched definition data for existing scenario type") {
    getProcessDefinitionData(enrichedWithUiConfig = Some(true)) ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[Json]
      hasScenarioProperty(response, "someScenarioProperty1") should be(true)
    }
  }

  it("should return basic definition data without enrichments for existing scenario type") {
    getProcessDefinitionData(enrichedWithUiConfig = Some(false)) ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[Json]
      hasScenarioProperty(response, "someScenarioProperty1") should be(false)
    }
  }

  it("should return definition data for allowed classes") {
    getProcessDefinitionData() ~> check {
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
        MetaData("fragment1", FragmentSpecificData()),
        List(
          FlatNode(
            FragmentInputDefinition(
              "in",
              List(
                FragmentParameter(ParameterName("param1"), FragmentClazzRef[String]).copy(
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

    val processName         = ProcessTestData.sampleScenario.name
    val processWithFragment = ProcessTestData.validProcessWithFragment(processName, fragmentWithFixedValuesEditor)
    val fragmentGraph       = CanonicalProcessConverter.toScenarioGraph(processWithFragment.fragment)
    saveFragment(fragmentGraph)(succeed)
    saveCanonicalProcess(processWithFragment.process)(succeed)

    getProcessDefinitionData() ~> check {
      status shouldBe StatusCodes.OK

      val response = responseAs[Json].hcursor

      val editor = response
        .downField("components")
        .downField("fragment-fragment1")
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
    getProcessDefinitionData() ~> check {
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
    getProcessDefinitionData() ~> check {
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
    getProcessDefinitionData() ~> check {
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
    getProcessDefinitionData() ~> check {
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
    getProcessDefinitionData() ~> check {
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

  private def getProcessDefinitionData(
      processingType: String = "streaming",
      enrichedWithUiConfig: Option[Boolean] = None
  ): RouteTestResult = {
    getProcessDefinitionDataUsingRawProcessingType(processingType, enrichedWithUiConfig)
  }

  private def getProcessDefinitionDataUsingRawProcessingType(
      processingType: String,
      enrichedWithUiConfig: Option[Boolean] = None
  ) = {
    val maybeEnrichedWithUiConfig =
      enrichedWithUiConfig.fold("")(value => s"&enrichedWithUiConfig=$value")
    Get(
      s"/processDefinitionData/$processingType?isFragment=false$maybeEnrichedWithUiConfig"
    ) ~> withPermissions(
      definitionResources,
      Permission.Read
    )
  }

  private def hasScenarioProperty(response: Json, property: String) = {
    response.hcursor
      .downField("scenarioProperties")
      .downField("propertiesConfig")
      .downField(property)
      .succeeded
  }

}
