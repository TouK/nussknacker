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
import pl.touk.nussknacker.ui.util.MultipartUtils.sttpPrepareMultiParts
import sttp.client3.{quickRequest, Response, UriContext}
import sttp.model.{MediaType, StatusCode}

import java.util.UUID

class ScenarioTestingApiHttpServiceSpec
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

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  "The endpoint for adhoc validate should" - {
    "return OK even if non-existing source component was used" in {
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
              TestSourceParameters(
                missingSourceId,
                Map(
                  ParameterName("Input variables") ->
                    """{
                  |  "input": 123
                  |}""".stripMargin.jsonTemplate
                )
              )
            ),
            scenarioGraph = scenarioWithMissingSource.toScenarioGraph
          ).asJson.toString()
        )
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${scenarioWithMissingSource.name}/validate")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          """{
            |    "validationErrors": [],
            |    "validationPerformed": true
            |}""".stripMargin
        )
    }
  }

  "The endpoint for single testCase should" - {
    "run a test case and return assertion results" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
          |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
          |]""".stripMargin
      val testCase = TestCase(
        UUID.randomUUID(),
        "dummy",
        testDataContent,
        Map.empty,
        Map(
          NodeId("endsuffix") -> List(
            PredicateAssertion(AssertionOperator.Equals, "'ala'".spel, "#contexts[0].input[0]".spel),
            PredicateAssertion(AssertionOperator.Equals, "'ala'".spel, "#contexts[1].input[0]".spel),
            PredicateAssertion(
              AssertionOperator.Equals,
              "{message: 'message'}".spel,
              "#contexts[0].output".spel,
            ),
          ),
          NodeId("messagesuffix") -> List(
            PredicateAssertion(AssertionOperator.Equals, "'ala'".spel, "#contexts[0].input[0]".spel),
          )
        )
      )

      given()
        .applicationState {
          createSavedScenario(testCaseScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(PerformTestCaseRequest(testCaseScenario.toScenarioGraph, testCase).asJson.spaces2)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseScenario.name}/testCase")
        .Then()
        .statusCode(200)
        .body("assertionsResults.endsuffix[0].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
        .body("assertionsResults.endsuffix[1].type", org.hamcrest.Matchers.equalTo("FailedAssertion"))
        .body("assertionsResults.endsuffix[2].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
        .body("assertionsResults.messagesuffix[0].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
    }

    "run a test case with mocked enricher service" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}}
          |]""".stripMargin
      val testCase = TestCase(
        UUID.randomUUID(),
        "dummy",
        testDataContent,
        mocks = Map(NodeId("someEnricher") -> EnricherMock("'b'".spel)),
        assertions = Map(
          NodeId("endsuffix") -> List(
            PredicateAssertion(AssertionOperator.Equals, "'b'".spel, "#contexts[0].out1".spel),
          )
        )
      )

      given()
        .applicationState {
          createSavedScenario(testCaseEnricherScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(PerformTestCaseRequest(testCaseEnricherScenario.toScenarioGraph, testCase).asJson.spaces2)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseEnricherScenario.name}/testCase")
        .Then()
        .statusCode(200)
        .body("assertionsResults.endsuffix[0].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
    }

    "return bad request when asserting on a non-existing node" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
          |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
          |]""".stripMargin
      val invalidTestCase = TestCase(
        UUID.randomUUID(),
        "dummy",
        testDataContent,
        mocks = Map.empty,
        assertions = Map(
          NodeId("someNotExistingNode") -> List(
            PredicateAssertion(AssertionOperator.Equals, "'ala'".spel, "#contexts[0].input[0]".spel),
          )
        )
      )

      given()
        .applicationState {
          createSavedScenario(testCaseScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(PerformTestCaseRequest(testCaseScenario.toScenarioGraph, invalidTestCase).asJson.spaces2)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseScenario.name}/testCase")
        .Then()
        .statusCode(400)
        .equalsPlainBody("Assertions configured for not existing nodes: someNotExistingNode")
    }

    "return bad request when mocking a non-existing node" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
          |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
          |]""".stripMargin
      val invalidTestCase = TestCase(
        UUID.randomUUID(),
        "dummy",
        testDataContent,
        mocks = Map(NodeId("notExistingEnricher") -> EnricherMock("'b'".spel)),
        assertions = Map.empty
      )

      given()
        .applicationState {
          createSavedScenario(testCaseScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(PerformTestCaseRequest(testCaseScenario.toScenarioGraph, invalidTestCase).asJson.spaces2)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseScenario.name}/testCase")
        .Then()
        .statusCode(400)
        .equalsPlainBody("Mocks configured for not existing nodes: notExistingEnricher")
    }

    "return bad request when asserting on a non-existing context variable" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}}
          |]""".stripMargin
      val invalidTestCase = TestCase(
        UUID.randomUUID(),
        "dummy",
        testDataContent,
        Map.empty,
        Map(
          NodeId("messagesuffix") -> List(
            // output variable is only visible at endsuffix, not at messagesuffix
            PredicateAssertion(
              AssertionOperator.Equals,
              "{message: 'message'}".spel,
              "#contexts[0].output".spel,
            ),
          )
        )
      )

      given()
        .applicationState {
          createSavedScenario(testCaseScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(PerformTestCaseRequest(testCaseScenario.toScenarioGraph, invalidTestCase).asJson.spaces2)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseScenario.name}/testCase")
        .Then()
        .statusCode(400)
        .body(containsString("There is no property 'output' in type"))
    }

    "return bad request when running an invalid scenario" in {
      val testDataContent =
        """[
          |  {"sourceId":"source","variables":{"input":["ala"]}},
          |  {"sourceId":"source","variables":{"input":["bela"]}}
          |]""".stripMargin
      val testCase = TestCase(UUID.randomUUID(), "dummy", testDataContent, Map.empty, Map.empty)

      given()
        .applicationState {
          createSavedScenario(ProcessTestData.invalidProcess)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(PerformTestCaseRequest(ProcessTestData.invalidProcess.toScenarioGraph, testCase).asJson.spaces2)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${ProcessTestData.invalidProcess.name}/testCase")
        .Then()
        .statusCode(400)
        .body(containsString("Only scenario without validation errors can be tested. Errors: "))
    }
  }

  private val testCaseScenario: CanonicalProcess =
    ScenarioBuilder
      .streaming("testCaseScenario")
      .parallelism(1)
      .additionalFields(properties = Map("environment" -> "test"))
      .source("startProcess", "csv-source")
      .filter("input", "#input != null".spel)
      .to(
        GraphBuilder
          .buildVariable("messagesuffix", "output", "message" -> "'message'".spel)
          .emptySink(
            "endsuffix",
            "kafka-string",
            KafkaFactory.TopicParamName.value     -> "'end.topic'".spel,
            KafkaFactory.SinkValueParamName.value -> "#output".spel
          )
      )

  private val testCaseEnricherScenario: CanonicalProcess =
    ScenarioBuilder
      .streaming("testCaseEnricherScenario")
      .parallelism(1)
      .additionalFields(properties = Map("environment" -> "test"))
      .source("startProcess", "csv-source")
      .enricher("someEnricher", "out1", "paramService", "param" -> "'a'".spel)
      .filter("input", "#input != null".spel)
      .to(
        GraphBuilder
          .buildVariable("messagesuffix", "output", "message" -> "'message'".spel)
          .emptySink(
            "endsuffix",
            "kafka-string",
            KafkaFactory.TopicParamName.value     -> "'end.topic'".spel,
            KafkaFactory.SinkValueParamName.value -> "#output".spel
          )
      )

}
