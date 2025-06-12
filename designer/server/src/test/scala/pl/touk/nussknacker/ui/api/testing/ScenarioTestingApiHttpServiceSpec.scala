package pl.touk.nussknacker.ui.api.testing

import io.circe.Encoder
import io.circe.syntax._
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
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.ScenarioTestData
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Validate.ScenarioTestValidationRequest
import pl.touk.nussknacker.ui.util.MultipartUtils.sttpPrepareMultiParts
import sttp.client3.{quickRequest, UriContext}
import sttp.model.{MediaType, StatusCode}

trait ScenarioTestingApiHttpServiceSpec
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
             |    "testWithParameters": {
             |      "status": "AVAILABLE",
             |      "sourceParameters": $expectedTestParametersJson
             |    },
             |    "testWithGeneratedData": {
             |      "status": "AVAILABLE"
             |    },
             |    "testWithLiveData": {
             |      "status": "AVAILABLE"
             |    }
             |}""".stripMargin
        )
    }
    "return valid capabilities for scenario with all capabilities, but user not allowed to deploy" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthWriter()
        .jsonBody(exampleScenarioGraphStr)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/capabilities")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |    "testWithParameters": {
             |      "status": "NOT_AVAILABLE",
             |      "reason": "USER_DOES_NOT_HAVE_PERMISSION"
             |    },
             |    "testWithGeneratedData": {
             |      "status": "NOT_AVAILABLE",
             |      "reason": "USER_DOES_NOT_HAVE_PERMISSION"
             |    },
             |    "testWithLiveData": {
             |      "status": "NOT_AVAILABLE",
             |      "reason": "USER_DOES_NOT_HAVE_PERMISSION"
             |    }
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
        .jsonBody(testDataGenerationRequest(exampleScenarioGraphStr, 3))
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/generatedTestData")
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
        .jsonBody(testDataGenerationRequest(exampleScenarioGraphStr, 100))
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/generatedTestData")
        .Then()
        .statusCode(StatusCodes.BadRequest.intValue)
        .equalsPlainBody(
          "Too many records requested. The maximum number of records permitted is 20. Contact the system administrator to increase this limit."
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
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/capabilities")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          responseWithParameters(
            s"""[
             |    {
             |        "sourceId": "$exampleScenarioSourceId",
             |        "parameters": [$expectedSourceTestingParametersJson]
             |    }
             |]
             |""".stripMargin
          )
        )
    }
    "generate parameters for fragment with fixed list parameter" in {
      val fragment = exampleFragment(fragmentFixedParameter)
      val expectedTestParameters =
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
             |                "category": "Standard",
             |                "changesCanReloadParameters": false
             |            }
             |        ]
             |    }
             |]
             |""".stripMargin
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
             |    "testWithGeneratedData": {
             |      "status": "NOT_AVAILABLE",
             |      "reason":"NOT_SUPPORTED_BY_SOURCES"
             |    },
             |    "testWithLiveData": {
             |      "status": "NOT_AVAILABLE",
             |      "reason":"NOT_SUPPORTED_BY_SOURCES"
             |    }
             |}""".stripMargin
        )
    }
    "Generate parameters with simplified (single) editor for fragment with raw string parameter" in {
      val fragment = exampleFragment(fragmentRawStringParameter)
      val expectedTestParameters =
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
           |                "category": "Standard",
           |                "changesCanReloadParameters": false
           |            }
           |        ]
           |    }
           |]
           |""".stripMargin
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
             |    "testWithGeneratedData": {
             |      "status": "NOT_AVAILABLE",
             |      "reason":"NOT_SUPPORTED_BY_SOURCES"
             |    },
             |    "testWithLiveData": {
             |      "status": "NOT_AVAILABLE",
             |      "reason":"NOT_SUPPORTED_BY_SOURCES"
             |    }
             |}""".stripMargin
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
        .jsonBody(s"""{
              |  "scenarioGraph": $exampleScenarioGraphStr,
              |  "numberOfSamples": 100
              |}""".stripMargin)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/$notExistingScenarioName/generatedTestData")
        .Then()
        .statusCode(StatusCodes.NotFound.intValue)
        .equalsPlainBody(s"No scenario $notExistingScenarioName found")
    }
  }

  "The endpoint for running tests from file should" - {

    def runTestsFromFile(testDataJson: String) = {
      httpClient.send(
        quickRequest
          .post(uri"$nuDesignerHttpAddress/api/processManagement/test/${exampleScenario.name}")
          .contentType(MediaType.MultipartFormData)
          .multipartBody(
            sttpPrepareMultiParts(
              "testData"      -> testDataJson,
              "scenarioGraph" -> toJson(exampleScenario).noSpaces
            )()
          )
          .auth
          .basic("allpermuser", "allpermuser")
      )
    }

    "properly parse file and run tests" in {
      createSavedScenario(exampleScenario)

      val response = runTestsFromFile(expectedTestDataJson)

      response.code shouldEqual StatusCode.Ok
    }

    "return error for empty test data" in {
      createSavedScenario(exampleScenario)

      val response = runTestsFromFile("")

      response.code shouldEqual StatusCode.BadRequest
      response.body shouldEqual "Test data is empty"
    }
  }

  "The endpoint for adhoc validate should" - {
    "return no errors on valid parameters" in {
      shouldValidateParametersProperly()
    }

    "return errors on missing source" in {
      val missingSourceId = "missing source"
      val scenarioWithMissingSource: CanonicalProcess =
        ScenarioBuilder
          .streaming("scenario with missing source")
          .source(missingSourceId, "missing source", "a parameter" -> "{'test'}".spel)
          .emptySink("end", "monitor")

      given()
        .applicationState {
          createSavedScenario(scenarioWithMissingSource)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          ScenarioTestValidationRequest(
            testData = ScenarioTestData.WithParameters(
              TestSourceParameters(missingSourceId, Map(ParameterName("a parameter") -> "{'123'}".spel))
            ),
            scenarioGraph = scenarioWithMissingSource.toScenarioGraph
          ).asJson.toString()
        )
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${scenarioWithMissingSource.name}/validate")
        .Then()
        .statusCode(400)
        .equalsPlainBody(
          """Scenario is invalid.
            |Node errors:
            |  missing source: Missing source: missing source""".stripMargin
        )
    }
  }

  "The endpoint for adhoc test run should" - {
    "run scenario and return result" in {
      shouldProperlyRunAdHocTest()
    }
  }

  "The endpoint for adhoc test parameters should" - {
    "return test parameters" in {
      shouldProperlyGetTestParameters()
    }
  }

  "The endpoint for test with live data should" - {
    "return error if trying to test with 0 samples" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          ScenarioTestValidationRequest(
            testData = ScenarioTestData.WithLiveData(0),
            scenarioGraph = exampleScenario.toScenarioGraph
          ).asJson.toString()
        )
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/performTest")
        .Then()
        .statusCode(404)
        .equalsPlainBody(
          "No live test data available. Please ensure that the storage used by source contains at least one data sample"
        )
    }
  }

  private def exampleScenarioGraphStr = Encoder[ScenarioGraph].apply(exampleScenarioGraph).toString()

  private def testDataGenerationRequest(
      scenarioGraphStr: String,
      numberOfSamples: Int,
  ) =
    s"""{
       |  "scenarioGraph": $scenarioGraphStr,
       |  "numberOfSamples": $numberOfSamples
       |}""".stripMargin

  private def canonicalGraphStr(canonical: CanonicalProcess) =
    Encoder[ScenarioGraph].apply(canonical.toScenarioGraph).toString()
}
