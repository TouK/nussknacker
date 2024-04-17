package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.test.base.it.{NuItTest, WithBatchConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBatchDesignerConfig, WithBusinessCaseRestAssuredUsersExtensions}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLogging, VeryPatientScalaFutures}
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.RequestedDeploymentId

class DeploymentApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithBatchDesignerConfig
    with BaseDeploymentApiHttpServiceBusinessSpec
    with WithBatchConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with LazyLogging
    with VeryPatientScalaFutures
    with Matchers {

  private val scenarioName = ProcessName("batch-test")

  private val sourceNodeId = "fooSourceNodeId"

  private val scenario = ScenarioBuilder
    .streaming(scenarioName.value)
    .source(sourceNodeId, "table", "Table" -> Expression.spel("'transactions'"))
    .emptySink(
      "sink",
      "table",
      "Table" -> Expression.spel("'transactions_summary'"),
      "Value" -> Expression.spel("#input")
    )

  private val correctDeploymentRequest = s"""{
                                            |  "nodesDeploymentData": {
                                            |    "$sourceNodeId": "`date` = '2024-01-01'"
                                            |  }
                                            |}""".stripMargin

  "The deployment requesting endpoint" - {
    "authenticated as user with deploy access should" - {
      "run deployment that will process input files" in {
        val requestedDeploymentId = RequestedDeploymentId.generate
        given()
          .applicationState {
            createSavedScenario(scenario)
          }
          .when()
          .basicAuthAdmin()
          .jsonBody(correctDeploymentRequest)
          .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/$requestedDeploymentId")
          .Then()
          .statusCode(202)
          .verifyApplicationState {
            waitForDeploymentStatusMatches(scenarioName, requestedDeploymentId, SimpleStateStatus.Finished)
          }
          .verifyExternalState {
            outputTransactionSummaryContainsExpectedResult()
          }
      }
    }

    "not authenticated should" - {
      "return unauthenticated status code" in {
        given()
          .applicationState {
            createSavedScenario(scenario)
          }
          .when()
          .jsonBody(correctDeploymentRequest)
          .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/${RequestedDeploymentId.generate}")
          .Then()
          .statusCode(401)
      }
    }

    "badly authenticated should" - {
      "return unauthenticated status code" in {
        given()
          .applicationState {
            createSavedScenario(scenario)
          }
          .when()
          .basicAuthUnknownUser()
          .jsonBody(correctDeploymentRequest)
          .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/${RequestedDeploymentId.generate}")
          .Then()
          .statusCode(401)
      }
    }

    "authenticated without read access to category should" - {
      "forbid access" in {
        given()
          .applicationState {
            createSavedScenario(scenario)
          }
          .when()
          .basicAuthNoPermUser()
          .jsonBody(correctDeploymentRequest)
          .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/${RequestedDeploymentId.generate}")
          .Then()
          .statusCode(403)
      }
    }

    "authenticated without deploy access to category should" - {
      "forbid access" in {
        given()
          .applicationState {
            createSavedScenario(scenario)
          }
          .when()
          .basicAuthWriter()
          .jsonBody(correctDeploymentRequest)
          .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/${RequestedDeploymentId.generate}")
          .Then()
          .statusCode(403)
      }
    }
  }

}
