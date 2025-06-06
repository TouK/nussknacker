package pl.touk.nussknacker.ui.api.livedata

import io.circe.Json
import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.{ContextId, NodeId}
import pl.touk.nussknacker.engine.api.deployment.{LiveDataPreviewSupported, NoLiveDataPreviewSupport}
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported._
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
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

import java.time.Instant
import scala.concurrent.Future

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

  private val exampleScenario: CanonicalProcess =
    ScenarioBuilder
      .streaming("scenario_2")
      .source(
        "Event Generator",
        "event-generator",
        "count"    -> "1".spel,
        "value"    -> "1".spel,
        "schedule" -> "T(java.time.Duration).parse('PT1M')".spel,
      )
      .emptySink("end", "dead-end")

  private val mockedInstant = Instant.ofEpochSecond(1748382500)

  "The endpoint for live data should" - {
    "return present, but empty live data" in {
      val mockedResults = LiveData(mockedInstant, Map.empty, Map.empty, Map.empty, Map.empty)
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureLiveDataPreviewSupport(
            new LiveDataPreviewSupported {
              override def getLiveData(
                  processIdWithName: ProcessIdWithName
              ): Future[Either[LiveDataError, LiveData]] = Future.successful(Right(mockedResults))
            }
          )
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/liveData/${exampleScenario.name}")
        .Then()
        .statusCode(StatusCodes.OK.intValue)
        .equalsJsonBody(
          s"""{
             |  "timestamp": "2025-05-27T21:48:20Z",
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
      val mockedResults = LiveData(
        timestamp = mockedInstant,
        nodeTransitions = Map(
          NodeTransition("start", Some("variable")) -> LiveDataForNodeTransition(
            samples = List(
              LiveDataSample(
                contextId = ContextId("mocked-scenario-id", "source", 0, 0),
                timestamp = mockedInstant,
                variables = Map(
                  "v1" -> Json.obj("a" -> "aaa".asJson, "b" -> 1.asJson)
                ),
              )
            ),
            totalCount = 101,
            currentThroughput = 0.9811,
          )
        ),
        invocationResults = Map(
          NodeId("start") -> List(
            InvocationResult(
              ContextId("mocked-scenario-id", "source", 0, 0),
              mockedInstant,
              "var",
              Json.obj("pretty" -> 1.asJson)
            )
          )
        ),
        externalInvocationResults = Map(
          NodeId("start") -> List(
            InvocationResult(
              ContextId("mocked-scenario-id", "source", 0, 0),
              mockedInstant,
              "var",
              Json.obj("pretty" -> 1.asJson)
            ),
          )
        ),
        exceptions = Map(
          NodeId("start") -> List(
            ExceptionResult(
              ContextId("mocked-scenario-id", "source", 0, 0),
              mockedInstant,
              Map("var1" -> Json.obj("pretty" -> "abc".asJson)),
              new Exception("Something bad happened")
            ),
          )
        ),
      )
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureLiveDataPreviewSupport(
            new LiveDataPreviewSupported {
              override def getLiveData(
                  processIdWithName: ProcessIdWithName
              ): Future[Either[LiveDataError, LiveData]] = Future.successful(Right(mockedResults))
            }
          )
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/liveData/${exampleScenario.name}")
        .Then()
        .statusCode(StatusCodes.OK.intValue)
        .equalsJsonBody(
          s"""{
             |  "timestamp": "2025-05-27T21:48:20Z",
             |  "results": {
             |    "nodeResults": null,
             |    "nodeTransitionResults": [
             |      {
             |        "sourceNodeId": "start",
             |        "destinationNodeId": "variable",
             |        "results": [
             |          {
             |            "cid": {
             |              "sid": "mocked-scenario-id",
             |              "nid": "source",
             |              "tid": 0,
             |              "idx": 0,
             |              "t": [
             |              ]
             |            },
             |            "id": "mocked-scenario-id-source-0-0",
             |            "timestamp": "2025-05-27T21:48:20Z",
             |            "variables": {
             |              "v1": {
             |                "a": "aaa",
             |                "b": 1
             |              }
             |            }
             |          }
             |        ],
             |        "totalCount": 101,
             |        "currentThroughput": 0.9811
             |      }
             |    ],
             |    "invocationResults": {
             |      "start": [
             |        {
             |          "cid": {
             |            "sid": "mocked-scenario-id",
             |            "nid": "source",
             |            "tid": 0,
             |            "idx": 0,
             |            "t": [
             |            ]
             |          },
             |          "contextId": "mocked-scenario-id-source-0-0",
             |          "timestamp": "2025-05-27T21:48:20Z",
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
             |            "sid": "mocked-scenario-id",
             |            "nid": "source",
             |            "tid": 0,
             |            "idx": 0,
             |            "t": [
             |            ]
             |          },
             |          "contextId": "mocked-scenario-id-source-0-0",
             |          "timestamp": "2025-05-27T21:48:20Z",
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
             |            "sid": "mocked-scenario-id",
             |            "nid": "source",
             |            "tid": 0,
             |            "idx": 0,
             |            "t": [
             |            ]
             |          },
             |          "id": "mocked-scenario-id-source-0-0",
             |          "timestamp": "2025-05-27T21:48:20Z",
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
             |              "sid": "mocked-scenario-id",
             |              "nid": "source",
             |              "tid": 0,
             |              "idx": 0,
             |              "t": [
             |              ]
             |            },
             |            "id": "mocked-scenario-id-source-0-0",
             |            "timestamp": "2025-05-27T21:48:20Z",
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
    "return not present live data" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureLiveDataPreviewSupport(
            new LiveDataPreviewSupported {
              override def getLiveData(
                  processIdWithName: ProcessIdWithName
              ): Future[Either[LiveDataError, LiveData]] =
                Future.successful(Left(LiveDataError.NoLiveDataAvailableForScenario))
            }
          )
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/liveData/${exampleScenario.name}")
        .Then()
        .statusCode(StatusCodes.NoContent.intValue)
    }
    "return live data not supported error" in {
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
