package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.pekko.http.scaladsl.model.{StatusCode, StatusCodes}
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.Assertion
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.{canonicalnode, CanonicalProcess}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig,
  WithMockableDeploymentManager
}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.ProcessTestData.sampleFragmentName
import pl.touk.nussknacker.test.utils.scalas.PekkoHttpExtensions.toRequestEntity
import pl.touk.nussknacker.ui.process.ProcessService.{CreateScenarioCommand, UpdateScenarioCommand}
import pl.touk.nussknacker.ui.server.RouteInterceptor

class MigrationApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigScenarioHelper
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with WithMockableDeploymentManager
    with NuRestAssureMatchers
    with ScalatestRouteTest
    with RestAssuredVerboseLoggingIfValidationFails {

  private lazy val applicationRoute = RouteInterceptor.get()

  val sampleFragmentWithDict: CanonicalProcess =
    CanonicalProcess(
      MetaData(sampleFragmentName.value, FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            "in",
            List(
              FragmentParameter(
                ParameterName("param1"),
                FragmentClazzRef[String]
              )
            )
          )
        ),
        canonicalnode.FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )

  "The endpoint for scenario migration between environments when" - {
    "authenticated should" - {
      "return response for allowed scenario" in {
        saveFragment(
          scenarioName = ProcessName(sampleFragmentWithDict.name.value),
          scenarioGraph = sampleFragmentWithDict.toScenarioGraph,
          category = Category1
        )(succeed)
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthAllPermUser()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .equalsPlainBody("")
      }
    }
    "not authenticated should" - {
      "forbid access for user with limited reading permissions" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category2)
          )
          .when()
          .basicAuthLimitedReader()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied user [limitedReader] is not authorized to access this resource")
      }
      "forbid access for user with limited writing permissions" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category2)
          )
          .when()
          .basicAuthLimitedWriter()
          .jsonBody(prepareRequestData(exampleProcessName.value, Category2))
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied user [limitedWriter] is not authorized to access this resource")
      }
    }
    "no credentials were passed should" - {
      "forbid access" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .noAuth()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied user [anonymous] is not authorized to access this resource")
      }
    }
    "impersonating user has permission to impersonate should" - {
      "allow migration for impersonated user with appropriate permissions" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthAllPermUser()
          .impersonateWriterUser()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .equalsPlainBody("")
      }
      "forbid access for impersonated user with limited reading permissions" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthAllPermUser()
          .impersonateReaderUser()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied user [reader] is not authorized to access this resource")
      }
      "forbid admin impersonation with default configuration" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthAllPermUser()
          .impersonateAdminUser()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to impersonate")
      }
    }
    "impersonating user does not have permission to impersonate should" - {
      "forbid access" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthWriter()
          .impersonateWriterUser()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to impersonate")
      }
    }
  }

  private def saveFragment(scenarioName: ProcessName, scenarioGraph: ScenarioGraph, category: TestCategory)(
      testCode: => Assertion
  ): Assertion = {
    saveProcess(scenarioName, scenarioGraph, category, isFragment = true)(testCode)
  }

  private def saveProcess(
      scenarioName: ProcessName,
      scenarioGraph: ScenarioGraph,
      category: TestCategory,
      isFragment: Boolean
  )(testCode: => Assertion): Assertion = {
    createProcessRequest(scenarioName, category, isFragment) { code =>
      code shouldBe StatusCodes.Created
      updateProcess(scenarioGraph, scenarioName)(testCode)
    }
  }

  private def updateProcess(process: ScenarioGraph, name: ProcessName = ProcessTestData.sampleProcessName)(
      testCode: => Assertion
  ): Assertion =
    doUpdateProcess(
      UpdateScenarioCommand(process, comment = None, scenarioLabels = Some(List.empty)),
      name
    )(
      testCode
    )

  private def doUpdateProcess(command: UpdateScenarioCommand, name: ProcessName)(
      testCode: => Assertion
  ): Assertion =
    Put(s"/api/processes/$name", command.toJsonRequestEntity()) ~> withAllPermUser() ~> applicationRoute ~> check {
      testCode
    }

  private def createProcessRequest(processName: ProcessName, category: TestCategory, isFragment: Boolean)(
      callback: StatusCode => Assertion
  ): Assertion = {
    val command = CreateScenarioCommand(
      processName,
      Some(category.stringify),
      processingMode = None,
      engineSetupName = None,
      isFragment = isFragment,
    )
    Post("/api/processes", command.toJsonRequestEntity()) ~> withAllPermUser() ~> applicationRoute ~> check {
      callback(status)
    }
  }

  private def withAllPermUser() = addBasicAuth("allpermuser", "allpermuser")

  private def addBasicAuth(name: String, secret: String) = addCredentials(BasicHttpCredentials(name, secret))

  private lazy val sourceEnvironmentId = "DEV"

  private lazy val exampleProcessName = ProcessName("test")

  private lazy val exampleScenario = ScenarioBuilder
    .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
    .source("source", "csv-source-lite")
    .fragment(
      sampleFragmentWithDict.name.value,
      sampleFragmentWithDict.name.value,
      List(("param1", Expression.spel(""))),
      Map("output" -> "fragmentResult"),
      Map(
        "output" -> GraphBuilder.emptySink("sink", "dead-end-lite")
      )
    )

  private lazy val exampleScenario2 = ScenarioBuilder
    .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
    .source("source", "csv-source-lite")
    .fragment(
      sampleFragmentWithDict.name.value,
      sampleFragmentWithDict.name.value,
      List(("param1", Expression.dictKeyWithLabel("H000000", Some("Black")))),
      Map("output" -> "fragmentResult"),
      Map(
        "output" -> GraphBuilder.emptySink("sink", "dead-end-lite")
      )
    )

  private def prepareRequestData(scenarioName: String, processCategory: TestCategory): String =
    s"""
       |{
       |  "version": "2",
       |  "sourceEnvironmentId": "$sourceEnvironmentId",
       |  "remoteUserName": "remoteUser",
       |  "processingMode": "Unbounded-Stream",
       |  "engineSetupName": "Mockable",
       |  "processName": "$scenarioName",
       |  "isFragment": false,
       |  "processCategory": "${processCategory.stringify}",
       |  "scenarioLabels": ["tag1", "tag2"],
       |  "scenarioGraph": ${exampleScenario2.toScenarioGraph.asJson.noSpaces}
       |}
       |""".stripMargin

  private lazy val requestData: String =
    prepareRequestData(exampleProcessName.value, processCategory = Category1) // .replace("\\\"Black\\\"", "null")

}
