package pl.touk.nussknacker.ui.api.testing

import io.circe.Encoder
import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.parameter.{ParameterName, ValueInputWithFixedValuesProvided}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.test.{
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLoggingIfValidationFails,
  WithTestHttpClient
}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.toJson
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.util.MultipartUtils.sttpPrepareMultiParts
import sttp.client3.{quickRequest, UriContext}
import sttp.model.{MediaType, StatusCode}

trait TestingApiHttpServiceSpec
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
    with WithAdHocTestParameters
    with WithAdHocTestsLogic {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  protected def expectedSourceTestingParametersJson: String

  protected def expectedTestDataJson: String

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
        .equalsPlainBody(expectedTestDataJson)
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
        .equalsPlainBody(
          "Too many samples requested. Please configure 'testDataSettings.maxSamplesCount' to increase the limit (20)"
        )
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
             |        "sourceId": "$exampleScenarioSourceId",
             |        "parameters": [$expectedSourceTestingParametersJson]
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
             |                "editors": [{
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
             |                }],
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
             |                "requiredParam": false,
             |                "parameterSection": {
             |                    "type": "Standard"
             |                }
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
             |                "editors": [
             |                    {
             |                        "type": "SpelTemplateParameterEditor"
             |                    },
             |                    {
             |                        "type": "SpelParameterEditor"
             |                    }
             |                ],
             |                "defaultValue": {
             |                    "language": "spelTemplate",
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
             |                "requiredParam": false,
             |                "parameterSection": {
             |                    "type": "Standard"
             |                }
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

  "The endpoint for running tests from file should" - {
    "properly parse file and run tests" in {
      createSavedScenario(exampleScenario)

      val response = httpClient.send(
        quickRequest
          .post(uri"$nuDesignerHttpAddress/api/processManagement/test/${exampleScenario.name}")
          .contentType(MediaType.MultipartFormData)
          .multipartBody(
            sttpPrepareMultiParts(
              "testData"      -> expectedTestDataJson,
              "scenarioGraph" -> toJson(exampleScenario).noSpaces
            )()
          )
          .auth
          .basic("allpermuser", "allpermuser")
      )
      response.code shouldEqual StatusCode.Ok
    }
  }

  "The endpoint for adhoc validate should" - {
    "return no errors on valid parameters" in {
      shouldValidateParametersProperly()
    }
    "return errors if passed parameter is not valid" in {
      shouldReturnErrorsForInvalidParameters()
    }
  }

  "The endpoint for adhoc test run should" - {
    "run scenario and return result" in {
      shouldProperlyRunAdHocTest()
    }
  }

  private def exampleScenarioGraphStr = Encoder[ScenarioGraph].apply(exampleScenarioGraph).toString()

  private def canonicalGraphStr(canonical: CanonicalProcess) =
    Encoder[ScenarioGraph].apply(CanonicalProcessConverter.toScenarioGraph(canonical)).toString()
}
