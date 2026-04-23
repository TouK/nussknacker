package pl.touk.nussknacker

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.collection.IsIn._
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import pl.touk.nussknacker.config.WithE2EInstallationExampleRestAssuredUsersExtensions
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, VeryPatientScalaFutures}

@Slow
class BatchDataGenerationSpec
    extends AnyFreeSpecLike
    with BaseE2ESpec
    with Matchers
    with VeryPatientScalaFutures
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with WithE2EInstallationExampleRestAssuredUsersExtensions {

  private val scenarioName = "E2ETest-SumTransactions"

  private def simpleBatchTableScenario(sourceId: String) = ScenarioBuilder
    .streaming(scenarioName)
    .source("sourceId", sourceId, "Table" -> "'`default_catalog`.`default_database`.`transactions`'".spel)
    .emptySink("end", "dead-end")

  private val simpleBatchTableScenarioLiveMode = simpleBatchTableScenario("live-test-mode-table")

  override def beforeAll(): Unit = {
    super.beforeAll()
    createEmptyBatchScenario(scenarioName)
  }

  "Generate file endpoint for scenario with table source should generate" - {
    "live records from data source with default configuration" in {
      given()
        .when()
        .request()
        .basicAuthAdmin()
        .jsonBody(testDataGenerationRequest(simpleBatchTableScenarioLiveMode.toScenarioGraph.asJson.spaces2, 1))
        .post(
          s"$designerServiceUrl/api/scenarioTesting/$scenarioName/generatedTestData"
        )
        .Then()
        .statusCode(200)
        .body(
          equalsJson(s"""[
                        |  {
                        |     "sourceId": "sourceId",
                        |     "variables": {
                        |        "input": {
                        |          "datetime": "2024-01-01T10:00:00",
                        |          "client_id": "client1",
                        |          "amount": 100.1,
                        |          "amountDoubled": 200.2,
                        |          "file.name": "transactions.ndjson"
                        |        }
                        |     }
                        |  }
                        |]""".stripMargin)
        )
    }
  }

  "Test on live data endpoint should return results and counts for scenario with table source" in {
    given()
      .when()
      .request()
      .basicAuthAdmin()
      .jsonBody(
        s"""{
           | "testData": {
           |   "type": "WITH_LIVE_DATA",
           |   "numberOfSamples": 1
           | },
           | "scenarioGraph": ${simpleBatchTableScenarioLiveMode.toScenarioGraph.asJson.spaces2}
           |}""".stripMargin
      )
      .post(
        s"$designerServiceUrl/api/scenarioTesting/$scenarioName/performTest"
      )
      .Then()
      .statusCode(200)
      .matchJsonWithRegexValuesBody(
        s"""{
           |  "timestamp": "${regexes.zuluDateRegex}",
           |  "results": {
           |    "nodeResults": {
           |      "sourceId": [
           |        {
           |          "cid":{"nid":"sourceId","tid":0,"idx":0,"path":[]},
           |          "id": "E2ETest-SumTransactions-sourceId-0-0",
           |          "timestamp": "${regexes.zuluDateRegex}",
           |          "variables": {
           |            "input": {
           |              "pretty": {
           |                "datetime": "2024-01-01T10:00:00",
           |                "client_id": "client1",
           |                "amount": 100.1,
           |                "amountDoubled": 200.20,
           |                "file.name": "transactions.ndjson"
           |              }
           |            }
           |          }
           |        }
           |      ],
           |      "end": [
           |        {
           |          "cid":{"nid":"sourceId","tid":0,"idx":0,"path":[]},
           |          "id": "E2ETest-SumTransactions-sourceId-0-0",
           |          "timestamp": "${regexes.zuluDateRegex}",
           |          "variables": {
           |            "input": {
           |              "pretty": {
           |                "datetime": "2024-01-01T10:00:00",
           |                "client_id": "client1",
           |                "amount": 100.10,
           |                "amountDoubled": 200.20,
           |                "file.name": "transactions.ndjson"
           |              }
           |            }
           |          }
           |        }
           |      ]
           |    },
           |    "nodeTransitionResults": [
           |      {
           |        "sourceNodeId": "sourceId",
           |        "destinationNodeId": "end",
           |        "results": [
           |        {
           |          "cid":{"nid":"sourceId","tid":0,"idx":0,"path":[]},
           |          "id": "E2ETest-SumTransactions-sourceId-0-0",
           |          "timestamp": "${regexes.zuluDateRegex}",
           |          "variables": {
           |            "input": {
           |              "pretty": {
           |                "datetime": "2024-01-01T10:00:00",
           |                "client_id": "client1",
           |                "amount": 100.10,
           |                "amountDoubled": 200.20,
           |                "file.name": "transactions.ndjson"
           |              }
           |            }
           |          }
           |        }
           |        ]
           |      }
           |    ],
           |    "expressionEvaluationResults": {
           |      "sourceId": [
           |        {
           |          "contextId": "E2ETest-SumTransactions-sourceId-0-0",
           |          "cid": {
           |            "nid": "sourceId",
           |            "tid": 0,
           |            "idx": 0,
           |            "path": []
           |          },
           |          "timestamp": "${regexes.zuluDateRegex}",
           |          "name": "Event time",
           |          "value": null
           |        }
           |      ]
           |    },
           |    "externalServiceInvocationResults": {},
           |    "exceptions": [],
           |    "exceptionsByNodeId": {}
           |  },
           |  "counts": {
           |      "sourceId": {
           |        "all": 1,
           |        "errors": 0,
           |        "fragmentCounts": {}
           |      },
           |      "end": {
           |        "all": 1,
           |        "errors": 0,
           |        "fragmentCounts": {}
           |      }
           |  },
           |  "assertionsResults": {}
           |}""".stripMargin
      )
  }

  "Test from file endpoint should return results and counts for scenario with table source" in {
    given()
      .when()
      .request()
      .basicAuthAdmin()
      .multiPart(
        "scenarioGraph",
        simpleBatchTableScenarioLiveMode.toScenarioGraph.asJson.spaces2,
        "application/json"
      )
      .multiPart(
        "testData",
        """[
          |  {
          |    "sourceId":"sourceId",
          |    "variables": {
          |      "input": {
          |        "datetime":"2024-07-19T08:56:08.485",
          |        "client_id":"aClientId",
          |        "amount":123123.12,
          |        "amountDoubled":246246.24,
          |        "file.name":"foo.ndjson"
          |      }
          |    }
          |  }
          |]""".stripMargin,
        "application/json"
      )
      .post(
        s"$designerServiceUrl/api/scenarioTesting/$scenarioName/performTest"
      )
      .Then()
      .statusCode(200)
      .matchJsonWithRegexValuesBody(
        s"""{
           |  "timestamp": "${regexes.zuluDateRegex}",
           |  "results": {
           |    "nodeResults": {
           |      "sourceId": [
           |        {
           |          "cid":{"nid":"sourceId","tid":0,"idx":0,"path":[]},
           |          "id": "E2ETest-SumTransactions-sourceId-0-0",
           |          "timestamp": "${regexes.zuluDateRegex}",
           |          "variables": {
           |            "input": {
           |              "pretty": {
           |                "datetime": "2024-07-19T08:56:08.485",
           |                "client_id": "aClientId",
           |                "amount": 123123.12,
           |                "amountDoubled": 246246.24,
           |                "file.name": "foo.ndjson"
           |              }
           |            }
           |          }
           |        }
           |      ],
           |      "end": [
           |        {
           |          "cid":{"nid":"sourceId","tid":0,"idx":0,"path":[]},
           |          "id": "E2ETest-SumTransactions-sourceId-0-0",
           |          "timestamp": "${regexes.zuluDateRegex}",
           |          "variables": {
           |            "input": {
           |              "pretty": {
           |                 "datetime": "2024-07-19T08:56:08.485",
           |                 "client_id": "aClientId",
           |                 "amount": 123123.12,
           |                 "amountDoubled": 246246.24,
           |                 "file.name": "foo.ndjson"
           |              }
           |            }
           |          }
           |        }
           |      ]
           |    },
           |    "nodeTransitionResults": [
           |      {
           |        "sourceNodeId": "sourceId",
           |        "destinationNodeId": "end",
           |        "results": [
           |        {
           |          "cid":{"nid":"sourceId","tid":0,"idx":0,"path":[]},
           |          "id": "E2ETest-SumTransactions-sourceId-0-0",
           |          "timestamp": "${regexes.zuluDateRegex}",
           |          "variables": {
           |            "input": {
           |              "pretty": {
           |                "datetime": "2024-07-19T08:56:08.485",
           |                "client_id": "aClientId",
           |                "amount": 123123.12,
           |                "amountDoubled": 246246.24,
           |                "file.name": "foo.ndjson"
           |              }
           |            }
           |          }
           |        }
           |        ]
           |      }
           |    ],
           |    "expressionEvaluationResults": {
           |      "sourceId": [
           |        {
           |          "contextId": "E2ETest-SumTransactions-sourceId-0-0",
           |          "cid": {
           |            "nid": "sourceId",
           |            "tid": 0,
           |            "idx": 0,
           |            "path": []
           |          },
           |          "timestamp": "${regexes.zuluDateRegex}",
           |          "name": "Event time",
           |          "value": null
           |        }
           |      ]
           |    },
           |    "externalServiceInvocationResults": {},
           |    "exceptions": [],
           |    "exceptionsByNodeId": {}
           |  },
           |  "counts": {
           |      "sourceId": {
           |        "all": 1,
           |        "errors": 0,
           |        "fragmentCounts": {}
           |      },
           |      "end": {
           |        "all": 1,
           |        "errors": 0,
           |        "fragmentCounts": {}
           |      }
           |  },
           |  "assertionsResults": {}
           |}""".stripMargin
      )
  }

  private def createEmptyBatchScenario(scenarioName: String): Unit = {
    given()
      .when()
      .request()
      .basicAuthAdmin()
      .jsonBody(s"""
                   |{
                   |    "name" : "$scenarioName",
                   |    "category" : "Default",
                   |    "isFragment" : false,
                   |    "processingMode" : "Bounded-Stream"
                   |}
                   |""".stripMargin)
      .post(s"$designerServiceUrl/api/processes")
      .Then()
      .statusCode(in(Array[Integer](201, 400)))
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
