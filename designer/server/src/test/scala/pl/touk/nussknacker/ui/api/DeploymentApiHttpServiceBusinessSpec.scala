package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import org.testcontainers.containers.BindMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.docker.FileSystemBind
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.test.base.it.{NuItTest, WithBatchConfigScenarioHelper, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBatchDesignerConfig,
  WithBusinessCaseRestAssuredUsersExtensions,
  WithFlinkContainersDeploymentManager
}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}

class DeploymentApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithBatchDesignerConfig
    with WithFlinkContainersDeploymentManager
    with WithBatchConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with LazyLogging
    with PatientScalaFutures {

  override protected def jobManagerExtraFSBinds: List[FileSystemBind] = List(
    FileSystemBind(
      "designer/server/src/test/resources/config/business-cases/tables-definition.sql",
      "/opt/flink/designer/server/src/test/resources/config/business-cases/tables-definition.sql",
      BindMode.READ_ONLY
    )
  )

  "The endpoint for deployment requesting should" - {
    "work" in {
      val scenarioName = "test"
      val scenario = ScenarioBuilder
        .streaming(scenarioName)
        .source("source", "table", "Table" -> Expression.spel("'transactions'"))
        .emptySink(
          "sink",
          "table",
          "Table" -> Expression.spel("'transactions_summary'"),
          "Value" -> Expression.spel("#input")
        )
      val requestedDeploymentId = "some-requested-deployment-id"

      given()
        .applicationState {
          createSavedScenario(scenario)
        }
        .when()
        .basicAuthAdmin()
        .jsonBody("{}")
        .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/$requestedDeploymentId")
        .Then()
        .statusCode(200)
        // TODO: check that deployment was done
        .body(
          equalTo("{}")
        )
    }
  }

}
