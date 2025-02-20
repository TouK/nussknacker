package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.parameter.{ParameterName, ValueInputWithFixedValuesProvided}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{AdhocTestParametersRequest, TestSourceParameters}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

class TestingApiHttpServiceSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val exampleScenarioSourceId = "sourceId"

  private val exampleScenario = ScenarioBuilder
    .streaming("scenario_1")
    .source(exampleScenarioSourceId, "genericSourceWithCustomVariables", "elements" -> "{'test'}".spel)
    .emptySink(
      "sinkId",
      "table",
      "Table"      -> Expression.spel("'`default_catalog`.`default_database`.`transactions_summary`'"),
      "Raw editor" -> Expression.spel("true"),
      "Value"      -> Expression.spel("#input")
    )

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
    )
  )

  private val fragmentRawStringParameter = FragmentParameter(
    ParameterName("paramRawString"),
    FragmentClazzRef[java.lang.String],
    initialValue = None,
    hintText = None,
    valueEditor = None
  )

  private def exampleFragment(parameter: FragmentParameter) = ScenarioBuilder
    .fragmentWithRawParameters("fragment", parameter)
    .fragmentOutput("fragmentEnd", "output", "out" -> "'hola'".spel)

  "The endpoint for capabilities should" - {
    "return valid capabilities for scenario with all capabilities" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(exampleScenarioGraphStr)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/capabilities")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |    "canBeTested": true,
             |    "canGenerateTestData": true,
             |    "canTestWithForm": true
             |}""".stripMargin
        )
    }
    "return Forbidden for user without permissions" in {
      // TODO lets talk about it, I've changed behaviour of API, in old definition user without permission still got response, but with capabilities disabled.
      // I thought it might be confusing to return valid response rather then inform user that he cannot get that information.
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthNoPermUser()
        .jsonBody(exampleScenarioGraphStr)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/capabilities")
        .Then()
        .statusCode(403)
    }
  }

  "The endpoint for test data generation should" - {
    "generate test data" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(exampleScenarioGraphStr)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/generate/3")
        .Then()
        .statusCode(200)
        .equalsPlainBody(
          s"""{"sourceId":"sourceId","record":"test-0"}
             |{"sourceId":"sourceId","record":"test-1"}
             |{"sourceId":"sourceId","record":"test-2"}""".stripMargin
        )
    }
    "refuses to generate too much data" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(exampleScenarioGraphStr)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/generate/100")
        .Then()
        .statusCode(StatusCodes.BadRequest.intValue)
    }
  }

  "The endpoint for generating test parameters should" - {
    "properly generate parameters for source with support of testParametersDefinition" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(exampleScenarioGraphStr)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/parameters")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""[
             |    {
             |        "sourceId": "sourceId",
             |        "parameters": [
             |            {
             |                "name": "elements",
             |                "typ": {
             |                    "display": "List[String]",
             |                    "type": "TypedClass",
             |                    "refClazzName": "java.util.List",
             |                    "params": [
             |                        {
             |                            "display": "String",
             |                            "type": "TypedClass",
             |                            "refClazzName": "java.lang.String",
             |                            "params": [
             |
             |                            ]
             |                        }
             |                    ]
             |                },
             |                "editor": {
             |                    "type": "RawParameterEditor"
             |                },
             |                "defaultValue": {
             |                    "language": "spel",
             |                    "expression": ""
             |                },
             |                "additionalVariables": {
             |
             |                },
             |                "variablesToHide": [
             |
             |                ],
             |                "branchParam": false,
             |                "requiredParam": true,
             |                "hintText": null,
             |                "label": "elements"
             |            }
             |        ]
             |    }
             |]
             |""".stripMargin
        )
    }
    "generate parameters for fragment with fixed list parameter" in {
      val fragment = exampleFragment(fragmentFixedParameter)
      given()
        .applicationState {
          createSavedScenario(fragment)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(canonicalGraphStr(fragment))
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${fragment.name}/parameters")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""[
             |    {
             |        "sourceId": "fragment",
             |        "parameters": [
             |            {
             |                "name": "paramFixedString",
             |                "typ": {
             |                    "display": "String",
             |                    "type": "TypedClass",
             |                    "refClazzName": "java.lang.String",
             |                    "params": [
             |
             |                    ]
             |                },
             |                "editor": {
             |                    "possibleValues": [
             |                        {
             |                            "expression": "",
             |                            "label": ""
             |                        },
             |                        {
             |                            "expression": "'uno'",
             |                            "label": "uno"
             |                        },
             |                        {
             |                            "expression": "'due'",
             |                            "label": "due"
             |                        }
             |                    ],
             |                    "type": "FixedValuesParameterEditor"
             |                },
             |                "defaultValue": {
             |                    "language": "spel",
             |                    "expression": "'uno'"
             |                },
             |                "additionalVariables": {
             |
             |                },
             |                "variablesToHide": [
             |
             |                ],
             |                "branchParam": false,
             |                "hintText": null,
             |                "label": "paramFixedString",
             |                "requiredParam": false
             |            }
             |        ]
             |    }
             |]
             |""".stripMargin
        )
    }
    "Generate parameters with simplified (single) editor for fragment with raw string parameter" in {
      val fragment = exampleFragment(fragmentRawStringParameter)
      given()
        .applicationState {
          createSavedScenario(fragment)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(canonicalGraphStr(fragment))
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${fragment.name}/parameters")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""[
             |    {
             |        "sourceId": "fragment",
             |        "parameters": [
             |            {
             |                "name": "paramRawString",
             |                "typ": {
             |                    "display": "String",
             |                    "type": "TypedClass",
             |                    "refClazzName": "java.lang.String",
             |                    "params": [
             |
             |                    ]
             |                },
             |                "editor": {
             |                    "type": "StringParameterEditor"
             |                },
             |                "defaultValue": {
             |                    "language": "spel",
             |                    "expression": ""
             |                },
             |                "additionalVariables": {
             |
             |                },
             |                "variablesToHide": [
             |
             |                ],
             |                "branchParam": false,
             |                "hintText": null,
             |                "label": "paramRawString",
             |                "requiredParam": false
             |            }
             |        ]
             |    }
             |]
             |""".stripMargin
        )
    }
    "return error if scenario does not exists" in {
      val notExistingScenarioName = exampleScenario.name.value + "_2"
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(exampleScenarioGraphStr)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/$notExistingScenarioName/generate/100")
        .Then()
        .statusCode(StatusCodes.NotFound.intValue)
        .equalsPlainBody(s"No scenario $notExistingScenarioName found")
    }
  }

  "The endpoint for adhoc validate should" - {
    "return no errors on valid parameters" in {
      val request = AdhocTestParametersRequest(
        TestSourceParameters(exampleScenarioSourceId, Map(ParameterName("elements") -> "{'123'}".spel)),
        exampleScenarioGraph
      ).asJson.toString()

      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(request)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/adhoc/validate")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |    "validationErrors": [],
             |    "validationPerformed": true
             |}""".stripMargin
        )
    }
    "return errors if passed parameter is not valid" in {
      val request = AdhocTestParametersRequest(
        TestSourceParameters(exampleScenarioSourceId, Map(ParameterName("elements") -> "0L".spel)),
        exampleScenarioGraph
      ).asJson.toString()

      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(request)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/adhoc/validate")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |    "validationErrors": [
             |        {
             |            "typ": "ExpressionParserCompilationError",
             |            "message": "Failed to parse expression: Bad expression type, expected: List[String], found: Long(0)",
             |            "description": "There is problem with expression in field Some(elements) - it could not be parsed.",
             |            "fieldName": "elements",
             |            "errorType": "SaveAllowed",
             |            "details": null
             |        }
             |    ],
             |    "validationPerformed": true
             |}
             |""".stripMargin
        )
    }
  }

  private val exampleScenarioGraph    = CanonicalProcessConverter.toScenarioGraph(exampleScenario)
  private val exampleScenarioGraphStr = Encoder[ScenarioGraph].apply(exampleScenarioGraph).toString()

  private def canonicalGraphStr(canonical: CanonicalProcess) =
    Encoder[ScenarioGraph].apply(CanonicalProcessConverter.toScenarioGraph(canonical)).toString()
}
