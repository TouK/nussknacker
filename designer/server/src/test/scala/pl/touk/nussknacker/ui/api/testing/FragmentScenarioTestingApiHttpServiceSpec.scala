package pl.touk.nussknacker.ui.api.testing

import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.{ParameterName, ValueInputWithFixedValuesProvided}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.test.testcase.TestCase
import pl.touk.nussknacker.test.{
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLoggingIfValidationFails,
  WithTestHttpClient
}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.PerformTestCaseRequest
import pl.touk.nussknacker.ui.process.test.testdataformat.CommonDataFormatHandler.InputVariablesParameterName

import java.util.UUID

class FragmentScenarioTestingApiHttpServiceSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithTestHttpClient
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures
    with Matchers
    with LazyLogging {

  private val fragmentFixedParameter = FragmentParameter(
    ParameterName("paramFixedString"),
    FragmentClazzRef[java.lang.String],
    initialValue = Some(FixedExpressionValue("'uno'", "uno")),
    hintText = None,
    valueEditor = Some(
      ValueInputWithFixedValuesProvided(
        fixedValuesList = List(
          FixedExpressionValue("'uno'", "uno"),
          FixedExpressionValue("'due'", "due"),
        ),
        allowOtherValue = false
      )
    ),
    valueCompileTimeValidation = None
  )

  private val fragmentRawStringParameter = FragmentParameter(
    ParameterName("paramRawString"),
    FragmentClazzRef[java.lang.String],
    initialValue = None,
    hintText = None,
    valueEditor = None,
    valueCompileTimeValidation = None
  )

  "The endpoint for generating test parameters should" - {
    "generate parameters for fragment with fixed list parameter" in {
      val fragment = exampleFragment(fragmentFixedParameter)
      val expectedTestParameters =
        s"""[
           |  {
           |    "sourceId": "fragment",
           |    "sourceName": "fragment",
           |    "parameters": [
           |      {
           |        "name": "$InputVariablesParameterName",
           |        "typ": {
           |          "display": "Record{paramFixedString: String}",
           |          "type": "TypedObjectTypingResult",
           |          "fields": {
           |            "paramFixedString": {
           |              "display": "String",
           |              "type": "TypedClass",
           |              "refClazzName": "java.lang.String",
           |              "params": []
           |            }
           |          },
           |          "refClazzName": "java.util.Map",
           |          "params": [
           |            {
           |              "display": "String",
           |              "type": "TypedClass",
           |              "refClazzName": "java.lang.String",
           |              "params": []
           |            },
           |            {
           |              "display": "String",
           |              "type": "TypedClass",
           |              "refClazzName": "java.lang.String",
           |              "params": []
           |            }
           |          ]
           |        },
           |        "editors": [
           |          {
           |            "type": "JsonParameterEditor"
           |          }
           |        ],
           |        "defaultValue": {
           |          "language": "json",
           |          "expression": "{\\n  \\"paramFixedString\\" : \\"\\"\\n}"
           |        },
           |        "additionalVariables": {},
           |        "variablesToHide": [],
           |        "branchParam": false,
           |        "hintText": null,
           |        "label": "$InputVariablesParameterName",
           |        "requiredParam": true,
           |        "category": "Standard",
           |        "changesCanReloadParameters": false,
           |        "nonImportantForExecution": false
           |      }
           |    ]
           |  }
           |]""".stripMargin
      given()
        .applicationState {
          createSavedScenario(fragment)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(canonicalGraphStr(fragment))
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${fragment.name}/capabilities")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |    "testWithParameters": {
             |      "status": "AVAILABLE",
             |      "sourceParameters": $expectedTestParameters
             |    },
             |    "testWithLiveData": {
             |      "status": "NOT_AVAILABLE",
             |      "reason":"NOT_SUPPORTED_BY_SOURCES"
             |    }
             |}""".stripMargin
        )
    }

    "Generate parameters for fragment with raw string parameter" in {
      val fragment = exampleFragment(fragmentRawStringParameter)
      val expectedTestParameters =
        s"""[
           |  {
           |    "sourceId": "fragment",
           |    "sourceName": "fragment",
           |    "parameters": [
           |      {
           |        "name": "$InputVariablesParameterName",
           |        "typ": {
           |          "display": "Record{paramRawString: String}",
           |          "type": "TypedObjectTypingResult",
           |          "fields": {
           |            "paramRawString": {
           |              "display": "String",
           |              "type": "TypedClass",
           |              "refClazzName": "java.lang.String",
           |              "params": []
           |            }
           |          },
           |          "refClazzName": "java.util.Map",
           |          "params": [
           |            {
           |              "display": "String",
           |              "type": "TypedClass",
           |              "refClazzName": "java.lang.String",
           |              "params": []
           |            },
           |            {
           |              "display": "String",
           |              "type": "TypedClass",
           |              "refClazzName": "java.lang.String",
           |              "params": []
           |            }
           |          ]
           |        },
           |        "editors": [
           |          {
           |            "type": "JsonParameterEditor"
           |          }
           |        ],
           |        "defaultValue": {
           |          "language": "json",
           |          "expression": "{\\n  \\"paramRawString\\" : \\"\\"\\n}"
           |        },
           |        "additionalVariables": {},
           |        "variablesToHide": [],
           |        "branchParam": false,
           |        "hintText": null,
           |        "label": "$InputVariablesParameterName",
           |        "requiredParam": true,
           |        "category": "Standard",
           |        "changesCanReloadParameters": false,
           |        "nonImportantForExecution": false
           |      }
           |    ]
           |  }
           |]""".stripMargin
      given()
        .applicationState {
          createSavedScenario(fragment)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(canonicalGraphStr(fragment))
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${fragment.name}/capabilities")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |    "testWithParameters": {
             |      "status": "AVAILABLE",
             |      "sourceParameters": $expectedTestParameters
             |    },
             |    "testWithLiveData": {
             |      "status": "NOT_AVAILABLE",
             |      "reason":"NOT_SUPPORTED_BY_SOURCES"
             |    }
             |}""".stripMargin
        )
    }
  }

  "The endpoint for a testCase should" - {
    "run a test case for fragment with variables without input wrapper" in {
      val fragment = exampleFragment(fragmentRawStringParameter)
      val scenarioGraphWithEnvironment = {
        val graph = fragment.toScenarioGraph
        graph.copy(
          properties = graph.properties.copy(
            additionalFields = graph.properties.additionalFields.copy(
              properties = graph.properties.additionalFields.properties + ("environment" -> "test")
            )
          )
        )
      }
      val testCase = TestCase(
        id = UUID.randomUUID(),
        name = "fragment-test-case",
        inputs = """[
            |  {"sourceId":"fragment","variables":{"paramRawString":"abc"}}
            |]""".stripMargin,
        mocks = Map.empty[NodeId, pl.touk.nussknacker.engine.test.testcase.EnricherMock],
        assertions = Map.empty,
      )

      given()
        .applicationState {
          createSavedScenario(fragment)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(PerformTestCaseRequest(scenarioGraphWithEnvironment, testCase).asJson.spaces2)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${fragment.name}/performTestCase")
        .Then()
        .statusCode(200)
    }
  }

  private def exampleFragment(parameter: FragmentParameter) = ScenarioBuilder
    .fragmentWithRawParameters("fragment", parameter)
    .fragmentOutput("fragmentEnd", "output", "out" -> "'hola'".spel)

  private def canonicalGraphStr(canonical: CanonicalProcess) =
    canonical.toScenarioGraph.asJson.spaces2

}
