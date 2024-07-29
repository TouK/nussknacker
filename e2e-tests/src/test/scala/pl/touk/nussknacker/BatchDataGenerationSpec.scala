package pl.touk.nussknacker

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.config.WithE2EInstallationExampleRestAssuredUsersExtensions
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, VeryPatientScalaFutures}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

class BatchDataGenerationSpec
    extends AnyFreeSpecLike
    with DockerBasedInstallationExampleNuEnvironment
    with Matchers
    with VeryPatientScalaFutures
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with WithE2EInstallationExampleRestAssuredUsersExtensions {

  private val simpleBatchTableScenario = ScenarioBuilder
    .streaming("SumTransactions")
    .source("sourceId", "table", "Table" -> "'transactions'".spel)
    .emptySink("end", "dead-end")

  private val designerServiceUrl = "http://localhost:8080"

  private val randomDataGenScenarioName = "SumTransactions-RandomData"
  private val realDataGenScenarioName   = "SumTransactions-RealData"

  override def beforeAll(): Unit = {
    createEmptyBatchScenario(randomDataGenScenarioName, "Default")
    createEmptyBatchScenario(realDataGenScenarioName, "BatchTestOnRealData")
    super.beforeAll()
  }

  "Generate file endpoint for scenario with table source should generate" - {
    "randomized records when configured with random mode" in {
      given()
        .when()
        .request()
        .basicAuthAdmin()
        .jsonBody(toScenarioGraph(simpleBatchTableScenario).asJson.spaces2)
        .post(
          s"$designerServiceUrl/api/testInfo/$randomDataGenScenarioName/generate/10"
        )
        .Then()
        .statusCode(200)
        .body(
          matchAllNdJsonWithRegexValues(s"""
               |{
               |   "sourceId": "sourceId",
               |   "record": {
               |      "datetime": "${regexes.localDateRegex}",
               |      "client_id": "[a-z\\\\d]{100}",
               |      "amount": "${regexes.decimalRegex}"
               |   }
               |}
               |""".stripMargin)
        )
    }
    "real records from data source when configured with real data mode" in {
      given()
        .when()
        .request()
        .basicAuthAdmin()
        .jsonBody(toScenarioGraph(simpleBatchTableScenario).asJson.spaces2)
        .post(
          s"$designerServiceUrl/api/testInfo/$realDataGenScenarioName/generate/1"
        )
        .Then()
        .statusCode(200)
        .body(
          equalsJson(s"""
               |{
               |   "sourceId": "sourceId",
               |   "record": {
               |      "datetime": "2024-01-01 10:00:00",
               |      "client_id": "client1",
               |      "amount": 100.1
               |   }
               |}
               |""".stripMargin)
        )
    }
  }

  "Test on generated data endpoint should return results and counts for scenario with table source" in {
    given()
      .when()
      .request()
      .basicAuthAdmin()
      .jsonBody(toScenarioGraph(simpleBatchTableScenario).asJson.spaces2)
      .post(
        s"$designerServiceUrl/api/processManagement/generateAndTest/$realDataGenScenarioName/1"
      )
      .Then()
      .statusCode(200)
      .body(
        matchJsonWithRegexValues(s"""{
             |  "results": {
             |    "nodeResults": {
             |      "sourceId": [
             |        {
             |          "id": "SumTransactions-sourceId-0-0",
             |          "variables": {
             |            "input": {
             |              "pretty": {
             |                "datetime": "2024-01-01T10:00:00",
             |                "client_id": "client1",
             |                "amount": 100.1
             |              }
             |            }
             |          }
             |        }
             |      ],
             |      "end": [
             |        {
             |          "id": "SumTransactions-sourceId-0-0",
             |          "variables": {
             |            "input": {
             |              "pretty": {
             |                "datetime": "2024-01-01T10:00:00",
             |                "client_id": "client1",
             |                "amount": 100.1
             |              }
             |            }
             |          }
             |        }
             |      ]
             |    },
             |    "invocationResults": {},
             |    "externalInvocationResults": {},
             |    "exceptions": []
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
             |  }
             |}""".stripMargin)
      )
  }

  "Test from file endpoint should return results and counts for scenario with table source" in {
    given()
      .when()
      .request()
      .basicAuthAdmin()
      .multiPart(
        "scenarioGraph",
        toScenarioGraph(simpleBatchTableScenario).asJson.spaces2,
        "application/json"
      )
      .multiPart(
        "testData",
        """{"sourceId":"sourceId","record":{"datetime":"2024-07-19 08:56:08.485","client_id":"aClientId","amount":123123.12}}""",
        "text/ plain"
      )
      .post(
        s"$designerServiceUrl/api/processManagement/test/$realDataGenScenarioName"
      )
      .Then()
      .statusCode(200)
      .body(
        matchJsonWithRegexValues(s"""{
             |  "results": {
             |    "nodeResults": {
             |      "sourceId": [
             |        {
             |          "id": "SumTransactions-sourceId-0-0",
             |          "variables": {
             |            "input": {
             |              "pretty": {
             |                 "datetime": "2024-07-19T08:56:08.485",
             |                 "client_id": "aClientId",
             |                 "amount": "123123.12"
             |              }
             |            }
             |          }
             |        }
             |      ],
             |      "end": [
             |        {
             |          "id": "SumTransactions-sourceId-0-0",
             |          "variables": {
             |            "input": {
             |              "pretty": {
             |                 "datetime": "2024-07-19T08:56:08.485",
             |                 "client_id": "aClientId",
             |                 "amount": "123123.12"
             |              }
             |            }
             |          }
             |        }
             |      ]
             |    },
             |    "invocationResults": {},
             |    "externalInvocationResults": {},
             |    "exceptions": []
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
             |  }
             |}""".stripMargin)
      )
  }

  private def createEmptyBatchScenario(scenarioName: String, category: String): Unit = {
    given()
      .when()
      .request()
      .basicAuthAdmin()
      .jsonBody(s"""
                   |{
                   |    "name" : "$scenarioName",
                   |    "category" : "$category",
                   |    "isFragment" : false,
                   |    "processingMode" : "Bounded-Stream"
                   |}
                   |""".stripMargin)
      .post(s"$designerServiceUrl/api/processes")
      .Then()
      .statusCode(201)
  }

}
