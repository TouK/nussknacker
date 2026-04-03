package pl.touk.nussknacker.ui.api.testing

import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax._
import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.hamcrest.Matchers.containsString
import org.scalatest.Assertion
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.KafkaFactory
import pl.touk.nussknacker.engine.test.testcase.{EnricherMock, TestCase}
import pl.touk.nussknacker.engine.test.testcase.Assertion.{AssertionOperator, PredicateAssertion}
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
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.ScenarioTestData
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.PerformTestCaseRequest
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Validate.ScenarioTestValidationRequest
import sttp.client3.{quickRequest, Response, UriContext}
import sttp.model.StatusCode

import java.util.UUID

trait ScenarioTestingApiHttpServiceGenericSpec
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
    with WithAdHocTestsLogic
    with Matchers
    with LazyLogging {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  protected val generatedSamplesCount = 3

  protected def expectedTestDataJson: String

  protected val inputValueNotMatchingExpectedType = "false"

  protected def verifyTestFromFileWithCorrectTestDataResponse(response: Response[String]): Assertion

  protected def exampleScenarioInputVariableType: TypingResult

  "The endpoint for capabilities should" - {
    "return valid capabilities for scenario with all capabilities" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(exampleScenarioGraph.asJson.spaces2)
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
        .jsonBody(exampleScenarioGraph.asJson.spaces2)
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
        .jsonBody(exampleScenarioGraph.asJson.spaces2)
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
        .jsonBody(testDataGenerationRequest(exampleScenarioGraph.asJson.spaces2, generatedSamplesCount))
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/generatedTestData")
        .Then()
        .statusCode(200)
        .equalsJsonBody(expectedTestDataJson)
    }
    "refuses to generate too much data" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(testDataGenerationRequest(exampleScenarioGraph.asJson.spaces2, 100))
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
      shouldProperlyGetTestParameters()
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
              |  "scenarioGraph": ${exampleScenarioGraph.asJson.spaces2},
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
          .post(uri"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/performTest")
          .multipartBody(
            sttp.client3.multipart("scenarioGraph", exampleScenario.toScenarioGraph.asJson.noSpaces),
            sttp.client3.multipart("testData", testDataJson),
          )
          .auth
          .basic("allpermuser", "allpermuser")
      )
    }

    "properly parse file and run tests" in {
      createSavedScenario(exampleScenario)

      val response = runTestsFromFile(expectedTestDataJson)

      logger.debug(s"The response from the endpoint running scenario test from files was: $response")
      response.code shouldEqual StatusCode.Ok
      verifyTestFromFileWithCorrectTestDataResponse(response)
    }

    "return error for empty test data" in {
      createSavedScenario(exampleScenario)

      val response = runTestsFromFile("[]")

      response.code shouldEqual StatusCode.BadRequest
      response.body shouldEqual "Test data is empty"
    }

    "return error for test data containing non existing variable" in {
      createSavedScenario(exampleScenario)

      val response = runTestsFromFile(s"""[
          |  {"sourceId":"$exampleScenarioSourceId","variables":{"nonExistingVariable": 123},"upstreamTimestamp":"1970-01-01T00:00:00.123Z"}
          |]""".stripMargin)

      response.code shouldEqual StatusCode.BadRequest
      response.body shouldEqual
        s"Problem in sample 1 detected: Unexpected variable [nonExistingVariable] for source [$exampleScenarioSourceId (id: $exampleScenarioSourceId)]"
    }

    "return error for test data containing variable that doesn't match expected type" in {
      createSavedScenario(exampleScenario)

      val response = runTestsFromFile(s"""[
                                         |  {"sourceId":"$exampleScenarioSourceId","variables":{"input":$inputValueNotMatchingExpectedType},"upstreamTimestamp":"1970-01-01T00:00:00.123Z"}
                                         |]""".stripMargin)

      response.code shouldEqual StatusCode.BadRequest
      response.body should startWith(
        s"""Problem in sample 1 detected: Variable [name=input, type=${exampleScenarioInputVariableType.display}, encoded value=$inputValueNotMatchingExpectedType] decoding error for source [$exampleScenarioSourceId (id: $exampleScenarioSourceId)]: """
      )
    }
  }

  "The endpoint for source capabilities should" - {
    "return source parameters for a single source node" in {
      shouldProperlyGetSourceCapabilities()
    }
  }

  "The endpoint for adhoc validate should" - {
    "return no errors on valid parameters" in {
      shouldValidateParametersProperly()
    }
  }

  "The endpoint for adhoc test run should" - {
    "run scenario and return result" in {
      shouldProperlyRunAdHocTest()
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

  private def testDataGenerationRequest(
      scenarioGraphStr: String,
      numberOfSamples: Int,
  ) =
    s"""{
       |  "scenarioGraph": $scenarioGraphStr,
       |  "numberOfSamples": $numberOfSamples
       |}""".stripMargin

}
