package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.hamcrest.Matchers._
import org.scalatest.concurrent.Eventually
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers.include
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, NodeId, NodeName}
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterName,
  ParameterValueInput,
  ValueInputWithDictEditor,
  ValueInputWithFixedValuesProvided
}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.{canonicalnode, CanonicalProcess}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails,
  StandardPatientScalaFutures
}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.test.base.it.{NuItTest, WithCategoryUsedMoreThanOnceConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts

// FIXME: For migrating between different API version should be written end to end test (e2e-tests directory)
class MigrationApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithCategoryUsedMoreThanOnceDesignerConfig
    with WithScenarioActivitySpecAsserts
    with WithCategoryUsedMoreThanOnceConfigScenarioHelper
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with Eventually
    with StandardPatientScalaFutures {

  "The endpoint for migration api version should" - {
    "return current api version" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/migration/scenario/description/version")
        .Then()
        .statusCode(200)
        .body(
          "version",
          equalTo[Int](3)
        )
    }
  }

  "The endpoint for scenario migration between environments should" - {
    "migrate scenario and add update comment when scenario does not exist on target environment when" - {
      "migration from current data version" in {
        given()
          .when()
          .basicAuthAllPermUser()
          .impersonateRemoteUser()
          .jsonBody(validRequestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .verifyApplicationState {
            eventually {
              verifyCommentExists(exampleProcessName.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
              verifyIncomingMigrationActivityExists(
                scenarioName = exampleProcessName.value,
                migrateFrom = "DEV"
              )
              verifyScenarioAfterMigration(
                exampleProcessName.value,
                processVersionId = 2,
                isFragment = false,
                scenarioLabels = exampleScenarioLabels,
                modifiedBy = "remoteUser",
                createdBy = "remoteUser",
                modelVersion = 0,
                historyProcessVersions = List(1, 2),
                scenarioGraphNodeIds = List("sink", "source")
              )
            }
          }
      }
      "migration from data version 1" in {
        given()
          .when()
          .basicAuthAllPermUser()
          .impersonateRemoteUser()
          .jsonBody(prepareRequestJsonDataV1(exampleProcessName.value, exampleGraph, isFragment = false))
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .verifyApplicationState {
            eventually {
              verifyCommentExists(exampleProcessName.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
              verifyScenarioAfterMigration(
                exampleProcessName.value,
                processVersionId = 2,
                isFragment = false,
                scenarioLabels = List.empty,
                modifiedBy = "remoteUser",
                createdBy = "remoteUser",
                modelVersion = 0,
                historyProcessVersions = List(1, 2),
                scenarioGraphNodeIds = List("sink", "source")
              )
            }
          }
      }
    }
    "migrate scenario and add update comment when scenario exists on target environment when" - {
      "migration from current data version" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario)
          )
          .when()
          .basicAuthAllPermUser()
          .impersonateRemoteUser()
          .jsonBody(validRequestDataV2)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .verifyApplicationState {
            eventually {
              verifyCommentExists(exampleProcessName.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
              verifyScenarioAfterMigration(
                scenarioName = exampleProcessName.value,
                processVersionId = 2,
                isFragment = false,
                scenarioLabels = exampleScenarioLabels,
                modifiedBy = "remoteUser",
                createdBy = "admin",
                modelVersion = 0,
                historyProcessVersions = List(1, 2),
                scenarioGraphNodeIds = List("sink2", "source2")
              )
            }
          }
      }
      "migration from data version 1" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario)
          )
          .when()
          .basicAuthAllPermUser()
          .impersonateRemoteUser()
          .jsonBody(prepareRequestJsonDataV1(exampleProcessName.value, exampleGraphV2, isFragment = false))
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .verifyApplicationState {
            eventually {
              verifyCommentExists(exampleProcessName.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
              verifyScenarioAfterMigration(
                scenarioName = exampleProcessName.value,
                processVersionId = 2,
                isFragment = false,
                scenarioLabels = List.empty,
                modifiedBy = "remoteUser",
                createdBy = "admin",
                modelVersion = 0,
                historyProcessVersions = List(1, 2),
                scenarioGraphNodeIds = List("sink2", "source2")
              )
            }
          }
      }
    }
    "migrate scenario when parameter adaptation is required" - {
      "adapt dictionary value from label in source to spel expression in target when label defined" in {
        testAdaptingParameter(
          sourceExpression = Expression.dictKeyWithLabel("H000", Some("Black")),
          targetEditor = None,
          expectedParameterJsonPart = """[{"name":"param1","expression":{"language":"spel","expression":"'Black'"}}]"""
        )
      }
      "adapt dictionary value from key in source to spel expression in target when label not defined" in {
        testAdaptingParameter(
          sourceExpression = Expression.dictKeyWithLabel("H000", None),
          targetEditor = None,
          expectedParameterJsonPart = """[{"name":"param1","expression":{"language":"spel","expression":"'H000'"}}]"""
        )
      }
      "adapt dictionary value in source to fixed list expression in target taking value from label" in {
        testAdaptingParameter(
          sourceExpression = Expression.dictKeyWithLabel("H000", Some("Black")),
          targetEditor = Some(
            ValueInputWithFixedValuesProvided(
              fixedValuesList = List(FixedExpressionValue("'Black'", "Black")),
              allowOtherValue = false
            )
          ),
          expectedParameterJsonPart = """[{"name":"param1","expression":{"language":"spel","expression":"'Black'"}}]"""
        )
      }
      "adapt dictionary value in source to fixed list expression in target taking value from key" in {
        testAdaptingParameter(
          sourceExpression = Expression.dictKeyWithLabel("Black", Some("ABC")),
          targetEditor = Some(
            ValueInputWithFixedValuesProvided(
              fixedValuesList = List(FixedExpressionValue("'Black'", "Black")),
              allowOtherValue = false
            )
          ),
          expectedParameterJsonPart = """[{"name":"param1","expression":{"language":"spel","expression":"'Black'"}}]"""
        )
      }
      "cannot adapt dictionary value in source to fixed list expression in target" in {
        testAdaptingParameterNotPossible(
          sourceExpression = Expression.dictKeyWithLabel("Green", Some("Green")),
          targetEditor = Some(
            ValueInputWithFixedValuesProvided(
              fixedValuesList = List(FixedExpressionValue("'Black'", "Black")),
              allowOtherValue = false
            )
          ),
          expectedError =
            "There is an incompatible parameter [param1] in component [fragmentWithConfigurableParameterEditor]. Its value cannot be used for target editors [FixedValuesParameterEditor(List(FixedExpressionValue(,), FixedExpressionValue('Black',Black)))]"
        )
      }
      "adapt spel value in source to fixed list expression in target" in {
        testAdaptingParameter(
          sourceExpression = Expression.spel("'abc'"),
          targetEditor = Some(
            ValueInputWithFixedValuesProvided(
              fixedValuesList = List(FixedExpressionValue("'abc'", "abc")),
              allowOtherValue = false
            )
          ),
          expectedParameterJsonPart = """[{"name":"param1","expression":{"language":"spel","expression":"'abc'"}}]"""
        )
      }
      "cannot adapt spel value in source to fixed list expression in target" in {
        testAdaptingParameterNotPossible(
          sourceExpression = Expression.spel("'Green'"),
          targetEditor = Some(
            ValueInputWithFixedValuesProvided(
              fixedValuesList = List(FixedExpressionValue("'Black'", "Black")),
              allowOtherValue = false
            )
          ),
          expectedError = "Property param1 has invalid value"
        )
      }
      // todo: perhaps we should allow adapting spel value to dictionary value in the future
      "cannot adapt spel value in source to dict expression in target" in {
        testAdaptingParameterNotPossible(
          sourceExpression = Expression.spel("'Black'"),
          targetEditor = Some(
            ValueInputWithDictEditor(
              dictId = "rgb",
              allowOtherValue = false
            )
          ),
          expectedError =
            """There is an incompatible parameter [param1] in component [fragmentWithConfigurableParameterEditor]. Its value cannot be used for target editors [DictParameterEditor(rgb)]"""
        )
      }
    }
    "fail when scenario name contains illegal character(s)" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .impersonateRemoteUser()
        .jsonBody(requestDataWithInvalidScenarioName)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(400)
        .equalsPlainBody(
          "Cannot migrate, following errors occurred: Invalid scenario name #test. Only digits, letters, underscore (_), hyphen (-) and space in the middle are allowed"
        )
    }
    "fail when scenario is archived on target environment" in {
      given()
        .applicationState(
          createArchivedExampleScenario(exampleProcessName)
        )
        .when()
        .basicAuthAllPermUser()
        .impersonateRemoteUser()
        .jsonBody(validRequestData)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(400)
        .equalsPlainBody(
          s"Cannot migrate, scenario ${exampleProcessName.value} is archived on test. You have to unarchive scenario on test in order to migrate."
        )
    }
    "migrate fragment and add update comment when fragment exists in target environment when" - {
      "migration from current data version" in {
        given()
          .applicationState(
            createSavedFragment(validFragment)
          )
          .when()
          .basicAuthAllPermUser()
          .impersonateRemoteUser()
          .jsonBody(validRequestDataForFragmentV2)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .verifyApplicationState {
            eventually {
              verifyCommentExists(validFragment.name.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
              verifyScenarioAfterMigration(
                validFragment.name.value,
                processVersionId = 2,
                isFragment = true,
                scenarioLabels = exampleScenarioLabels,
                modifiedBy = "remoteUser",
                createdBy = "admin",
                modelVersion = 0,
                historyProcessVersions = List(1, 2),
                scenarioGraphNodeIds = List("sink2", "csv-source-lite")
              )
            }
          }
      }
      "migration from data version 1" in {
        given()
          .applicationState(
            createSavedFragment(validFragment)
          )
          .when()
          .basicAuthAllPermUser()
          .impersonateRemoteUser()
          .jsonBody(prepareRequestJsonDataV1(validFragment.name.value, exampleFragmentGraphV2, isFragment = true))
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .verifyApplicationState {
            eventually {
              verifyCommentExists(validFragment.name.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
              verifyScenarioAfterMigration(
                validFragment.name.value,
                processVersionId = 2,
                isFragment = true,
                scenarioLabels = List.empty,
                modifiedBy = "remoteUser",
                createdBy = "admin",
                modelVersion = 0,
                historyProcessVersions = List(1, 2),
                scenarioGraphNodeIds = List("sink2", "csv-source-lite")
              )
            }
          }
      }

    }
    "migrate fragment and add update comment when fragment does not exist in target environment when" - {
      "migration from current data version" in {
        given()
          .when()
          .basicAuthAllPermUser()
          .impersonateRemoteUser()
          .jsonBody(validRequestDataForFragment)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .verifyApplicationState {
            eventually {
              verifyCommentExists(validFragment.name.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
              verifyScenarioAfterMigration(
                validFragment.name.value,
                processVersionId = 2,
                isFragment = true,
                scenarioLabels = exampleScenarioLabels,
                modifiedBy = "remoteUser",
                createdBy = "remoteUser",
                modelVersion = 0,
                historyProcessVersions = List(1, 2),
                scenarioGraphNodeIds = List("sink", "csv-source-lite")
              )
            }
          }
      }
      "migration from data version 1" in {
        given()
          .when()
          .basicAuthAllPermUser()
          .impersonateRemoteUser()
          .jsonBody(prepareRequestJsonDataV1(validFragment.name.value, exampleFragmentGraph, isFragment = true))
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .verifyApplicationState {
            eventually {
              verifyCommentExists(validFragment.name.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
              verifyScenarioAfterMigration(
                validFragment.name.value,
                processVersionId = 2,
                isFragment = true,
                scenarioLabels = List.empty,
                modifiedBy = "remoteUser",
                createdBy = "remoteUser",
                modelVersion = 0,
                historyProcessVersions = List(1, 2),
                scenarioGraphNodeIds = List("sink", "csv-source-lite")
              )
            }
          }
      }
    }
  }

  private lazy val sourceEnvironmentId = "DEV"

  private lazy val exampleProcessName    = ProcessName("test2")
  private lazy val exampleScenarioLabels = List("tag1", "tag2")
  private lazy val illegalProcessName    = ProcessName("#test")

  private lazy val exampleScenario =
    ScenarioBuilder
      .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
      .source("source", "csv-source-lite")
      .emptySink("sink", "dead-end-lite")

  private lazy val exampleScenarioV2 =
    ScenarioBuilder
      .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
      .source("source2", "csv-source-lite")
      .emptySink("sink2", "dead-end-lite")

  private lazy val validFragment =
    ScenarioBuilder.fragmentWithInputNodeId("source", "csv-source-lite").emptySink("sink", "dead-end-lite")

  private lazy val validFragmentV2 =
    ScenarioBuilder.fragmentWithInputNodeId("source2", "csv-source-lite").emptySink("sink2", "dead-end-lite")

  private lazy val exampleGraph = exampleScenario.toScenarioGraph

  private lazy val exampleGraphV2 = exampleScenarioV2.toScenarioGraph

  private lazy val exampleFragmentGraph = validFragment.toScenarioGraph

  private lazy val exampleFragmentGraphV2 = validFragmentV2.toScenarioGraph

  private def prepareRequestJsonDataV1(
      scenarioName: String,
      scenarioGraph: ScenarioGraph,
      isFragment: Boolean
  ): String =
    s"""
       |{
       |  "version": "1",
       |  "sourceEnvironmentId": "$sourceEnvironmentId",
       |  "remoteUserName": "remoteUser",
       |  "processingMode": "Unbounded-Stream",
       |  "engineSetupName": "Mockable",
       |  "processName": "$scenarioName",
       |  "isFragment": $isFragment,
       |  "processCategory": "${Category1.stringify}",
       |  "scenarioGraph": ${scenarioGraph.asJson.noSpaces}
       |}
       |""".stripMargin

  private def prepareRequestJsonDataV2(
      scenarioName: String,
      scenarioGraph: ScenarioGraph,
      isFragment: Boolean,
      scenarioLabels: List[String]
  ): String =
    s"""
       |{
       |  "version": "2",
       |  "sourceEnvironmentId": "$sourceEnvironmentId",
       |  "remoteUserName": "remoteUser",
       |  "processingMode": "Unbounded-Stream",
       |  "engineSetupName": "Mockable",
       |  "processName": "$scenarioName",
       |  "isFragment": $isFragment,
       |  "processCategory": "${Category1.stringify}",
       |  "scenarioLabels": ${scenarioLabels.asJson.noSpaces},
       |  "scenarioGraph": ${scenarioGraph.asJson.noSpaces}
       |}
       |""".stripMargin

  private lazy val validRequestData: String =
    prepareRequestJsonDataV2(
      exampleProcessName.value,
      exampleGraph,
      isFragment = false,
      scenarioLabels = List("tag1", "tag2")
    )

  private lazy val validRequestDataV2: String =
    prepareRequestJsonDataV2(
      exampleProcessName.value,
      exampleGraphV2,
      isFragment = false,
      scenarioLabels = exampleScenarioLabels
    )

  private lazy val requestDataWithInvalidScenarioName: String =
    prepareRequestJsonDataV2(
      illegalProcessName.value,
      exampleGraph,
      isFragment = false,
      scenarioLabels = exampleScenarioLabels
    )

  private lazy val validRequestDataForFragment: String =
    prepareRequestJsonDataV2(
      validFragment.name.value,
      exampleFragmentGraph,
      isFragment = true,
      scenarioLabels = exampleScenarioLabels
    )

  private lazy val validRequestDataForFragmentV2: String =
    prepareRequestJsonDataV2(
      validFragment.name.value,
      exampleFragmentGraphV2,
      isFragment = true,
      scenarioLabels = exampleScenarioLabels
    )

  private def verifyScenarioAfterMigration(
      scenarioName: String,
      processVersionId: Int,
      isFragment: Boolean,
      scenarioLabels: List[String],
      modifiedBy: String,
      createdBy: String,
      modelVersion: Int,
      historyProcessVersions: List[Int],
      scenarioGraphNodeIds: List[String]
  ): ValidatableResponse =
    given()
      .when()
      .basicAuthAllPermUser()
      .get(
        s"$nuDesignerHttpAddress/api/processes/$scenarioName?skipValidateAndResolve=true&skipNodeResults=true"
      )
      .Then()
      .body(
        "name",
        equalTo(scenarioName),
        "processVersionId",
        equalTo(processVersionId),
        "isFragment",
        equalTo(isFragment),
        "labels",
        containsInAnyOrder(scenarioLabels: _*),
        "modifiedBy",
        equalTo(modifiedBy),
        "createdBy",
        equalTo(createdBy),
        "history.processVersionId",
        containsInAnyOrder(historyProcessVersions: _*),
        "scenarioGraph.nodes.id",
        containsInAnyOrder(scenarioGraphNodeIds: _*),
        "modelVersion",
        equalTo(modelVersion)
      )

  private def fetchScenario(scenarioName: String): String =
    given()
      .when()
      .basicAuthAllPermUser()
      .get(
        s"$nuDesignerHttpAddress/api/processes/$scenarioName?skipValidateAndResolve=true&skipNodeResults=true"
      )
      .Then()
      .extract()
      .body()
      .asString()

  private def testAdaptingParameter(
      sourceExpression: Expression,
      targetEditor: Option[ParameterValueInput],
      expectedParameterJsonPart: String,
  ) = {
    val scenario = scenarioWithConfigurableParameterExpression(sourceExpression)
    given()
      .applicationState {
        createSavedFragment(fragmentWithConfigurableParameterEditor(targetEditor))
        createSavedScenario(scenario)
      }
      .when()
      .when()
      .basicAuthAllPermUser()
      .impersonateRemoteUser()
      .jsonBody(prepareRequestJsonDataV1(scenario.name.value, scenario.toScenarioGraph, isFragment = false))
      .post(s"$nuDesignerHttpAddress/api/migrate")
      .Then()
      .statusCode(200)
      .equalsPlainBody("")

    val scenarioGraphJsonStr = fetchScenario(scenario.name.value)
    scenarioGraphJsonStr should include(expectedParameterJsonPart)
  }

  private def testAdaptingParameterNotPossible(
      sourceExpression: Expression,
      targetEditor: Option[ParameterValueInput],
      expectedError: String,
  ) = {
    val scenario = scenarioWithConfigurableParameterExpression(sourceExpression)
    given()
      .applicationState {
        createSavedFragment(fragmentWithConfigurableParameterEditor(targetEditor))
        createSavedScenario(scenario)
      }
      .when()
      .when()
      .basicAuthAllPermUser()
      .impersonateRemoteUser()
      .jsonBody(prepareRequestJsonDataV1(scenario.name.value, scenario.toScenarioGraph, isFragment = false))
      .post(s"$nuDesignerHttpAddress/api/migrate")
      .Then()
      .statusCode(400)
      .body(containsString(expectedError))
  }

  private def scenarioWithConfigurableParameterExpression(parameterValueExpression: Expression) =
    ScenarioBuilder
      .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
      .source("source", "csv-source-lite")
      .fragment(
        fragmentWithConfigurableParameterEditorName.value,
        fragmentWithConfigurableParameterEditorName.value,
        List(("param1", parameterValueExpression)),
        Map("output" -> "fragmentResult"),
        Map(
          "output" -> GraphBuilder.emptySink("sink", "dead-end-lite")
        )
      )

  private def fragmentWithConfigurableParameterEditor(editor: Option[ParameterValueInput]): CanonicalProcess =
    CanonicalProcess(
      MetaData(fragmentWithConfigurableParameterEditorName.value, FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("in"),
            NodeName("in"),
            List(
              FragmentParameter(
                ParameterName("param1"),
                FragmentClazzRef[String]
              ).copy(valueEditor = editor)
            )
          )
        ),
        canonicalnode.FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
      ),
      List.empty
    )

  private def fragmentWithConfigurableParameterEditorName: ProcessName = ProcessName(
    "fragmentWithConfigurableParameterEditor"
  )

}
