package pl.touk.nussknacker.ui.api.livedata

import io.circe.Json
import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.{Context, ContextId}
import pl.touk.nussknacker.engine.api.component.ComponentType.Source
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.deployment.{LiveDataPreviewStoredInDesignerJvm, NoLiveDataPreviewSupport}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails, WithTestHttpClient}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithMockableDeploymentManager, WithSimplifiedDesignerConfig}

import java.time.Instant
import java.util.UUID
import scala.util.Try

class ScenarioLiveDataApiHttpServiceSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithTestHttpClient
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private def createExampleScenario(): CanonicalProcess =
    ScenarioBuilder
      .streaming(UUID.randomUUID().toString)
      .source(
        "Event Generator",
        "event-generator",
        "count"    -> "1".spel,
        "value"    -> "1".spel,
        "schedule" -> "T(java.time.Duration).parse('PT1M')".spel,
      )
      .emptySink("end", "dead-end")

  "The endpoint for live data should" - {
    "return present, but empty live data" in {
      val exampleScenario = createExampleScenario()
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureLiveDataPreviewSupport(LiveDataPreviewStoredInDesignerJvm)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/liveData/${exampleScenario.name}")
        .Then()
        .statusCode(StatusCodes.OK.intValue)
        .matchJsonWithRegexValuesBody(
          s"""{
             |  "timestamp": "${regexes.zuluDateRegex}",
             |  "results": {
             |    "nodeResults": null,
             |    "nodeTransitionResults": [],
             |    "invocationResults": {},
             |    "externalInvocationResults": {},
             |    "exceptions": [],
             |    "exceptionsByNodeId": {}
             |  },
             |  "counts": {
             |    "Event Generator": {
             |      "all": 0,
             |      "errors": 0,
             |      "fragmentCounts": {}
             |    },
             |    "end": {
             |      "all": 0,
             |      "errors": 0,
             |      "fragmentCounts": {}
             |    }
             |  }
             |}""".stripMargin
        )
    }
    "return present data" in {
      val exampleScenario = createExampleScenario()
      val listener = LiveDataCollectingListenerHolder.createListenerFor(
        processName = exampleScenario.name,
        maxNumberOfSamples = 10,
        throughputTimeWindowInSeconds = 10
      )
      listener.transitionToNextNode(
        nodeId = "start",
        nextNodeId = "variable",
        context = Context(ContextId(scenarioId = "mocked-scenario-id", originatingNodeId = "source", taskId = 0, index = 0), Map("v1" -> Json.obj("a" -> "aaa".asJson, "b" -> 1.asJson))),
        processMetaData = exampleScenario.metaData
      )
      listener.expressionEvaluated(
        nodeId = "start",
        expressionId = "var",
        expression = "ignored_by_live_data_collector",
        context = Context(ContextId(scenarioId = "mocked-scenario-id", originatingNodeId = "source", taskId = 0, index = 0), Map.empty),
        processMetaData = exampleScenario.metaData,
        result = 1
      )
      listener.serviceInvoked(
        nodeId = "start",
        id = "var",
        context = Context(ContextId(scenarioId = "mocked-scenario-id", originatingNodeId = "source", taskId = 0, index = 0), Map.empty),
        processMetaData = exampleScenario.metaData,
        result = Try(1),
      )
      listener.exceptionThrown(
        NuExceptionInfo(
          nodeComponentInfo = Some(NodeComponentInfo("start", Source, "start")),
          throwable = new Exception("Something bad happened"),
          context = Context(ContextId(scenarioId = "mocked-scenario-id", originatingNodeId = "source", taskId = 0, index = 0), Map("var1" -> Json.obj("pretty" -> "abc".asJson))),
          input = "ignored_by_live_data_collector",
          timestamp = Instant.now,
        )
      )

      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureLiveDataPreviewSupport(LiveDataPreviewStoredInDesignerJvm)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/liveData/${exampleScenario.name}")
        .Then()
        .statusCode(StatusCodes.OK.intValue)
        .matchJsonWithRegexValuesBody(
          s"""{
             |  "timestamp": "${regexes.zuluDateRegex}",
             |  "results": {
             |    "nodeResults": null,
             |    "nodeTransitionResults": [
             |      {
             |        "sourceNodeId": "start",
             |        "destinationNodeId": "variable",
             |        "results": [
             |          {
             |            "cid": {
             |              "nid": "source",
             |              "tid": 0,
             |              "idx": 0,
             |              "path": [
             |              ]
             |            },
             |            "id": "mocked-scenario-id-source-0-0",
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "v1": {
             |                "a": "aaa",
             |                "b": 1
             |              }
             |            }
             |          }
             |        ],
             |        "totalCount": 1,
             |        "currentThroughput": "${regexes.decimalRegex}"
             |      }
             |    ],
             |    "invocationResults": {
             |      "start": [
             |        {
             |          "cid": {
             |            "nid": "source",
             |            "tid": 0,
             |            "idx": 0,
             |            "path": [
             |            ]
             |          },
             |          "contextId": "mocked-scenario-id-source-0-0",
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "var",
             |          "value": {
             |            "pretty": 1
             |          }
             |        }
             |      ]
             |    },
             |    "externalInvocationResults": {
             |      "start": [
             |        {
             |          "cid": {
             |            "nid": "source",
             |            "tid": 0,
             |            "idx": 0,
             |            "path": [
             |            ]
             |          },
             |          "contextId": "mocked-scenario-id-source-0-0",
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "var",
             |          "value": {
             |            "pretty": 1
             |          }
             |        }
             |      ]
             |    },
             |    "exceptions": [
             |      {
             |        "context": {
             |          "cid": {
             |            "nid": "source",
             |            "tid": 0,
             |            "idx": 0,
             |            "path": [
             |            ]
             |          },
             |          "id": "mocked-scenario-id-source-0-0",
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "variables": {
             |            "var1": {
             |              "pretty": "abc"
             |            }
             |          }
             |        },
             |        "nodeId": "start",
             |        "throwable": "Something bad happened"
             |      }
             |    ],
             |    "exceptionsByNodeId": {
             |      "start": [
             |        {
             |          "context": {
             |            "cid": {
             |              "nid": "source",
             |              "tid": 0,
             |              "idx": 0,
             |              "path": [
             |              ]
             |            },
             |            "id": "mocked-scenario-id-source-0-0",
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "var1": {
             |                "pretty": "abc"
             |              }
             |            }
             |          },
             |          "nodeId": "start",
             |          "throwable": "Something bad happened"
             |        }
             |      ]
             |    }
             |  },
             |  "counts": {
             |    "Event Generator": {
             |      "all": 0,
             |      "errors": 0,
             |      "fragmentCounts": {
             |      }
             |    },
             |    "end": {
             |      "all": 0,
             |      "errors": 0,
             |      "fragmentCounts": {
             |      }
             |    }
             |  }
             |}""".stripMargin
        )
    }
    "return live data not supported error" in {
      val exampleScenario = createExampleScenario()
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureLiveDataPreviewSupport(NoLiveDataPreviewSupport)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/liveData/${exampleScenario.name}")
        .Then()
        .statusCode(StatusCodes.NotImplemented.intValue)
    }
  }

}
