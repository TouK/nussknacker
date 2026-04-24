package pl.touk.nussknacker.ui.api.testing

import cats.data.NonEmptyList
import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser
import io.circe.syntax._
import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.hamcrest.Matchers.{containsString, equalTo, nullValue}
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.KafkaFactory
import pl.touk.nussknacker.engine.test.testcase.{Assertion, EnricherMock, TestCase, TestCaseId}
import pl.touk.nussknacker.engine.test.testcase.Assertion.{AssertionOperator, PredicateAssertion}
import pl.touk.nussknacker.test.{
  EitherValuesDetailedMessage,
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
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{
  PerformMultipleTestCasesRequest,
  PerformTestCaseRequest
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Validate.ScenarioTestValidationRequest
import sttp.model.sse.ServerSentEvent

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
    with OptionValues
    with EitherValuesDetailedMessage
    with LazyLogging {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  override def designerRawConfig: Config = super.designerRawConfig
    .withValue("testCasesSettings.multipleEnabled", ConfigValueFactory.fromAnyRef(true))

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

  "The endpoint for a testCase should" - {
    "run a test case and return assertion results" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
          |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
          |]""".stripMargin
      val testCase = TestCase(
        id = UUID.randomUUID(),
        name = "dummy",
        inputs = testDataContent,
        mocks = Map.empty,
        assertions = Map(
          NodeId("end") -> List(
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "'ala'".spel,
              "#records[0].input[0]".spel,
            ),
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "'ala'".spel,
              "#records[1].input[0]".spel,
            ),
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "{message: 'message'}".spel,
              "#records[0].output".spel,
            ),
            // #outgoingRecords on sink nodes contain a representation of the record received by a sink.
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "{message: 'message'}".spel,
              "#outgoingRecords[1]['end']".spel,
            )
          ),
          NodeId("messageVariable") -> List(
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "'ala'".spel,
              "#records[0].input[0]".spel,
            ),
            // The output variable produced by the messageVariable node is visible in #outgoingRecords at that node.
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "'message'".spel,
              "#outgoingRecords[0].output.message".spel,
            ),
          ),
          NodeId("processor end") -> List(
            // outgoing records should be the same as incoming records for processor that is an end node
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "#records".spel,
              "#outgoingRecords".spel,
            ),
          ),
          NodeId("custom end") -> List(
            // outgoing records should be the same as incoming records for custom node with void return type
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "#records".spel,
              "#outgoingRecords".spel,
            ),
          ),
        )
      )

      given()
        .applicationState {
          createSavedScenario(testCaseScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(PerformTestCaseRequest(testCaseScenario.toScenarioGraph, testCase).asJson.spaces2)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseScenario.name}/performTestCase")
        .Then()
        .statusCode(200)
        .body("assertionsResults.end[0].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
        .body("assertionsResults.end[1].type", org.hamcrest.Matchers.equalTo("FailedAssertion"))
        .body(
          "assertionsResults.end[1].message",
          org.hamcrest.Matchers.equalTo("Expected: ['ala'] but found ['bela']")
        )
        .body("assertionsResults.end[2].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
        .body("assertionsResults.end[3].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
        .body("assertionsResults.messageVariable[0].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
        .body("assertionsResults.messageVariable[1].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
        .body("assertionsResults.\"processor end\"[0].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
        .body("assertionsResults.\"custom end\"[0].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
    }

    "run a test case with mocked enricher service" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}}
          |]""".stripMargin
      val testCase = TestCase(
        id = UUID.randomUUID(),
        name = "dummy",
        inputs = testDataContent,
        mocks = Map(NodeId("someEnricher1") -> EnricherMock("'b'".spel)),
        assertions = Map(
          NodeId("end") -> List(
            PredicateAssertion(AssertionOperator.Equals, "'b'".spel, "#records[0].out1".spel),
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
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseEnricherScenario.name}/performTestCase")
        .Then()
        .statusCode(200)
        .body("assertionsResults.end[0].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
    }

    "ignore enricher mocks with empty expression or disabled and use the actual service result" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}}
          |]""".stripMargin
      val testCase = TestCase(
        id = UUID.randomUUID(),
        name = "dummy",
        inputs = testDataContent,
        mocks = Map(
          NodeId("someEnricher1") -> EnricherMock(Expression.spel("")),
          NodeId("someEnricher2") -> EnricherMock(Expression.spel("invalid expression"), enabled = false),
        ),
        assertions = Map(
          NodeId("end") -> List(
            PredicateAssertion(AssertionOperator.Equals, "'a'".spel, "#records[0].out1".spel),
            PredicateAssertion(AssertionOperator.Equals, "'a'".spel, "#records[0].out2".spel),
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
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseEnricherScenario.name}/performTestCase")
        .Then()
        .statusCode(200)
        .body("assertionsResults.end[0].type", org.hamcrest.Matchers.equalTo("SuccessfulAssertion"))
    }

    "return bad request when asserting on a non-existing node" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
          |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
          |]""".stripMargin
      val invalidTestCase = TestCase(
        id = UUID.randomUUID(),
        name = "dummy",
        inputs = testDataContent,
        mocks = Map.empty,
        assertions = Map(
          NodeId("someNotExistingNode") -> List(
            PredicateAssertion(AssertionOperator.Equals, "'ala'".spel, "#records[0].input[0]".spel),
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
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseScenario.name}/performTestCase")
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
        id = UUID.randomUUID(),
        name = "dummy",
        inputs = testDataContent,
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
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseScenario.name}/performTestCase")
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
        id = UUID.randomUUID(),
        name = "dummy",
        inputs = testDataContent,
        mocks = Map.empty,
        assertions = Map(
          NodeId("messageVariable") -> List(
            // output variable is only visible at endsuffix, not at messagesuffix
            PredicateAssertion(
              AssertionOperator.Equals,
              "{message: 'message'}".spel,
              "#records[0].output".spel,
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
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseScenario.name}/performTestCase")
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
      val testCase = TestCase(
        id = UUID.randomUUID(),
        name = "dummy",
        inputs = testDataContent,
        mocks = Map.empty,
        assertions = Map.empty
      )

      given()
        .applicationState {
          createSavedScenario(ProcessTestData.invalidProcess)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(PerformTestCaseRequest(ProcessTestData.invalidProcess.toScenarioGraph, testCase).asJson.spaces2)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${ProcessTestData.invalidProcess.name}/performTestCase")
        .Then()
        .statusCode(400)
        // todo: should we return something more structured than toString of errors?
        .body(containsString("Only scenario without validation errors can be tested. Errors: "))
    }
  }

  "The endpoint for running tests from file should" - {
    "return test results" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
          |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
          |]""".stripMargin
      given()
        .applicationState {
          createSavedScenario(ProcessTestData.sampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .multiPart("scenarioGraph", ProcessTestData.sampleScenario.toScenarioGraph.asJson.noSpaces, "application/json")
        .multiPart("testData", testDataContent)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${ProcessTestData.sampleScenario.name}/performTest")
        .Then()
        .statusCode(200)
        .body("results.nodeResults.endsuffix[0].variables.output.pretty.message", equalTo("message"))
        .body("results.nodeResults.endsuffix[0].variables.input.pretty[0]", equalTo("ala"))
    }

    "return null variables in nodeTransitionResults" in {
      val scenario = ScenarioBuilder
        .streaming(ProcessTestData.sampleProcessName.value)
        .parallelism(1)
        .source("startProcess", "csv-source")
        .emptySink(
          "end",
          "kafka-string",
          KafkaFactory.TopicParamName.value     -> "'end.topic'".spel,
          KafkaFactory.SinkValueParamName.value -> "'foo'".spel
        )
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":null}}
          |]""".stripMargin
      given()
        .applicationState {
          createSavedScenario(scenario)
        }
        .when()
        .basicAuthAllPermUser()
        .multiPart("scenarioGraph", scenario.toScenarioGraph.asJson.noSpaces, "application/json")
        .multiPart("testData", testDataContent)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${scenario.name}/performTest")
        .Then()
        .statusCode(200)
        .body("results.nodeTransitionResults[0].results[0].variables.input", nullValue())
    }

    "return test results of errors, including null" in {
      val process = ScenarioBuilder
        .streaming(ProcessTestData.sampleProcessName.value)
        .parallelism(1)
        .source("startProcess", "csv-source")
        .filter("input", "new java.math.BigDecimal(null) == 0".spel)
        .emptySink(
          "end",
          "kafka-string",
          KafkaFactory.TopicParamName.value     -> "'end.topic'".spel,
          KafkaFactory.SinkValueParamName.value -> "''".spel
        )
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
          |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
          |]""".stripMargin
      given()
        .applicationState {
          createSavedScenario(process)
        }
        .when()
        .basicAuthAllPermUser()
        .multiPart("scenarioGraph", process.toScenarioGraph.asJson.noSpaces, "application/json")
        .multiPart("testData", testDataContent)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${process.name}/performTest")
        .Then()
        .statusCode(200)
    }

    "refuse to test if too many records received" in {
      val process = ScenarioBuilder
        .streaming(ProcessTestData.sampleProcessName.value)
        .parallelism(1)
        .source("startProcess", "csv-source")
        .emptySink(
          "end",
          "kafka-string",
          KafkaFactory.TopicParamName.value     -> "'end.topic'".spel,
          KafkaFactory.SinkValueParamName.value -> "''".spel
        )
      val tooManyRecords = List
        .fill(50)("""{"sourceId":"startProcess","variables":{"input":[]}}""")
        .mkString("[", ",", "]")
      given()
        .applicationState {
          createSavedScenario(process)
        }
        .when()
        .basicAuthAllPermUser()
        .multiPart("scenarioGraph", process.toScenarioGraph.asJson.noSpaces, "application/json")
        .multiPart("testData", tooManyRecords)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${process.name}/performTest")
        .Then()
        .statusCode(400)
        .equalsPlainBody(
          "Test data has too many records (50). The maximum number of records permitted is 20. Contact the system administrator to increase this limit."
        )
    }

    "refuse to test if too many characters in test data" in {
      val process = ScenarioBuilder
        .streaming(ProcessTestData.sampleProcessName.value)
        .parallelism(1)
        .source("startProcess", "csv-source")
        .emptySink(
          "end",
          "kafka-string",
          KafkaFactory.TopicParamName.value     -> "'end.topic'".spel,
          KafkaFactory.SinkValueParamName.value -> "''".spel
        )
      val longString = "a long json string".repeat(50)
      val tooManyCharacters = List
        .fill(20)(s"""{"sourceId":"startProcess","variables":{"input":"[$longString]"}}""")
        .mkString("[", ",", "]")
      given()
        .applicationState {
          createSavedScenario(process)
        }
        .when()
        .basicAuthAllPermUser()
        .multiPart("scenarioGraph", process.toScenarioGraph.asJson.noSpaces, "application/json")
        .multiPart("testData", tooManyCharacters)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${process.name}/performTest")
        .Then()
        .statusCode(400)
        .equalsPlainBody(
          "Test data has too many characters (19101). The maximum numbers of permitted characters is 10000. Contact the system administrator to increase this limit."
        )
    }

    "refuse to test if too big test results generated" in {
      val bigListExpression = (1 to 1000).mkString("{", ",", "}").spel
      val process = ScenarioBuilder
        .streaming(ProcessTestData.sampleProcessName.value)
        .parallelism(1)
        .source("startProcess", "csv-source")
        .buildSimpleVariable("big list", "bigList", bigListExpression)
        .emptySink(
          "end",
          "kafka-string",
          KafkaFactory.TopicParamName.value     -> "'end.topic'".spel,
          KafkaFactory.SinkValueParamName.value -> "''".spel
        )
      val testDataContent = List
        .fill(10)("""{"sourceId":"startProcess","variables":{"input":[]}}""")
        .mkString("[", ",", "]")
      given()
        .applicationState {
          createSavedScenario(process)
        }
        .when()
        .basicAuthAllPermUser()
        .multiPart("scenarioGraph", process.toScenarioGraph.asJson.noSpaces, "application/json")
        .multiPart("testData", testDataContent)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${process.name}/performTest")
        .Then()
        .statusCode(400)
        .body(containsString("Test results size exceeded"))
    }

    "reject test record with non-existing source" in {
      val testDataContent =
        """[
          |  {"sourceId":"startProcess","variables":{"input":[]}},
          |  {"sourceId":"unknown","variables":{"input":"foo"}}
          |]""".stripMargin
      given()
        .applicationState {
          createSavedScenario(ProcessTestData.sampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .multiPart("scenarioGraph", ProcessTestData.sampleScenario.toScenarioGraph.asJson.noSpaces, "application/json")
        .multiPart("testData", testDataContent)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${ProcessTestData.sampleScenario.name}/performTest")
        .Then()
        .statusCode(400)
        .equalsPlainBody("Problem in sample 2 detected: source with id 'unknown' doesn't exist in the scenario")
    }
  }

  "The endpoint for performing multiple test cases should" - {
    "stream results for each test case" in {
      val validInputs =
        """[
          |  {"sourceId":"startProcess","variables":{"input":["ala"]}}
          |]""".stripMargin
      val invalidInputs =
        """[
          |  {"sourceId":"unknownSource","variables":{"input":["ala"]}}
          |]""".stripMargin
      val completedTestCase1 = TestCase(
        id = UUID.randomUUID(),
        name = "test case 1",
        inputs = validInputs,
        mocks = Map.empty,
        assertions = Map(
          NodeId("end") -> List(
            PredicateAssertion(Assertion.AssertionOperator.Equals, "'ala'".spel, "#records[0].input[0]".spel)
          )
        )
      )
      val failingTestCase = TestCase(
        id = UUID.randomUUID(),
        name = "invalid test case",
        inputs = invalidInputs,
        mocks = Map.empty,
        assertions = Map.empty
      )
      val completedTestCase2 = TestCase(
        id = UUID.randomUUID(),
        name = "test case 2",
        inputs = validInputs,
        mocks = Map.empty,
        assertions = Map(
          NodeId("end") -> List(
            PredicateAssertion(Assertion.AssertionOperator.Equals, "'notAla'".spel, "#records[0].input[0]".spel)
          )
        )
      )

      val responseBody = given()
        .applicationState {
          createSavedScenario(testCaseScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          PerformMultipleTestCasesRequest(
            testCaseScenario.toScenarioGraph,
            NonEmptyList.of(completedTestCase1, failingTestCase, completedTestCase2)
          ).asJson.spaces2
        )
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${testCaseScenario.name}/performMultipleTestCases")
        .Then()
        .statusCode(200)
        .extract()
        .asString()

      val parsedEvents = responseBody
        .split("\n\n")
        .map(block => ServerSentEvent.parse(block.split("\n").toList))
        .filter(_.data.isDefined)
        .map(event => parser.parse(event.data.value).rightValue)
        .toList

      parsedEvents should have size 3

      val eventById = parsedEvents
        .map(json => json.hcursor.downField("testCaseId").as[TestCaseId].rightValue -> json)
        .toMap

      val testCase1Event = eventById(completedTestCase1.id)
      testCase1Event.hcursor.downField("type").as[String].rightValue shouldBe "Completed"
      testCase1Event.hcursor
        .downField("result")
        .downField("assertionsResults")
        .downField("end")
        .downArray
        .downField("type")
        .as[String]
        .rightValue shouldBe "SuccessfulAssertion"

      val testCase2Event = eventById(completedTestCase2.id)
      testCase2Event.hcursor.downField("type").as[String].rightValue shouldBe "Completed"
      testCase2Event.hcursor
        .downField("result")
        .downField("assertionsResults")
        .downField("end")
        .downArray
        .downField("type")
        .as[String]
        .rightValue shouldBe "FailedAssertion"

      val errorEvent = eventById(failingTestCase.id)
      errorEvent.hcursor.downField("type").as[String].rightValue shouldBe "Error"
      errorEvent.hcursor.downField("error").as[String].rightValue should include("unknownSource")
    }
  }

  private val testCaseScenario = ScenarioBuilder
    .streaming("testCaseScenario")
    .parallelism(1)
    .additionalFields(properties = Map("environment" -> "test"))
    .source("startProcess", "csv-source")
    .filter("input", "#input != null".spel)
    .split(
      "split",
      GraphBuilder
        .buildVariable("messageVariable", "output", "message" -> "'message'".spel)
        .emptySink(
          "end",
          "kafka-string",
          KafkaFactory.TopicParamName.value     -> "'end.topic'".spel,
          KafkaFactory.SinkValueParamName.value -> "#output".spel
        ),
      GraphBuilder
        .processorEnd("processor end", "componentService"),
      GraphBuilder
        .endingCustomNode("custom end", None, "noneReturnTypeEndingTransformer")
    )

  private val testCaseEnricherScenario = ScenarioBuilder
    .streaming("testCaseEnricherScenario")
    .parallelism(1)
    .additionalFields(properties = Map("environment" -> "test"))
    .source("startProcess", "csv-source")
    .enricher("someEnricher1", "out1", "paramService", "param" -> "'a'".spel)
    .enricher("someEnricher2", "out2", "paramService", "param" -> "'a'".spel)
    .filter("input", "#input != null".spel)
    .buildVariable("messageVariable", "output", "message" -> "'message'".spel)
    .emptySink(
      "end",
      "kafka-string",
      KafkaFactory.TopicParamName.value     -> "'end.topic'".spel,
      KafkaFactory.SinkValueParamName.value -> "#output".spel
    )

}
